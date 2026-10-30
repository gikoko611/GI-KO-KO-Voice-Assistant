package com.gi.koko.voiceassistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AssistantSkill(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val offline: Boolean,
    val tools: List<String>,
    val permissions: List<String>,
    val content: String,
    val builtIn: Boolean
)

object SkillRepository {
    private const val PREFS = "gi_skill_center"
    private const val ENABLED = "enabled_"
    private const val PERMISSION = "permission_"
    private const val CONFIRM = "confirm_actions"
    private const val CUSTOM = "custom_skills"

    private data class Definition(
        val id: String, val name: String, val description: String,
        val category: String, val offline: Boolean,
        val tools: List<String>, val permissions: List<String>, val asset: String
    )

    private val definitions = listOf(
        Definition("phone-control", "Phone Control", "ဖုန်း App ဖွင့်ခြင်း၊ Settings သို့သွားခြင်းနှင့် ခွင့်ပြုထားသော Android Intent များ", "Device", true, listOf("app.launch"), listOf("app.launch"), "phone-control"),
        Definition("developer", "Developer & Termux", "Termux allowlist command၊ code workflow နှင့် debugging အကူအညီ", "Development", true, listOf("termux.pwd", "termux.ls", "termux.python-version"), listOf("termux.execute"), "developer"),
        Definition("file-manager", "File Manager", "SAF ဖြင့် user ရွေးချယ်ထားသော file များကို ဖတ်ရှု/တင်သွင်း/ထုတ်ပေးခြင်း", "Files", true, listOf("file.open-document", "file.export-document"), listOf("file.read", "file.write"), "file-manager"),
        Definition("web-research", "Web Research", "Online research၊ webpage ရှာဖွေခြင်းနှင့် အချက်အလက်စိစစ်ခြင်း", "Research", false, listOf("network.search"), listOf("network.access"), "web-research"),
        Definition("personal-assistant", "Personal Assistant", "မှတ်စု၊ reminder နှင့် user ခွင့်ပြုထားသော personal workflow", "Productivity", true, listOf("notes.local"), listOf("notes.read", "notes.write"), "personal-assistant"),
        Definition("multimedia", "Multimedia", "ပုံစစ်ဆေးခြင်း၊ OCR၊ audio/video workflow နှင့် media script များ", "Media", false, listOf("media.inspect", "media.ocr", "media.transcribe"), listOf("media.access"), "multimedia"),
        Definition("cybersecurity", "Cybersecurity", "ကိုယ်ပိုင်/ခွင့်ပြုချက်ရှိသော system များအတွက် defensive diagnostics", "Security", true, listOf("security.diagnostics"), listOf("security.diagnostics"), "cybersecurity"),
        Definition("automation", "Automation", "အဆင့်လိုက် workflow များနှင့် allowlisted tool များကို ချိတ်ဆက်ခြင်း", "Automation", true, listOf("workflow.chain"), listOf("workflow.execute"), "automation")
    )

    fun all(context: Context): List<AssistantSkill> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val builtIns = definitions.map { d ->
            AssistantSkill(d.id, d.name, d.description, d.category, d.offline, d.tools, d.permissions,
                context.assets.open("skills/${d.asset}/SKILL.md").bufferedReader().use { it.readText() }, true)
        }
        val customJson = prefs.getString(CUSTOM, "[]") ?: "[]"
        val custom = mutableListOf<AssistantSkill>()
        try {
            val array = JSONArray(customJson)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                custom += AssistantSkill(
                    o.getString("id"), o.getString("name"), o.optString("description", "Custom skill"),
                    o.optString("category", "Custom"), o.optBoolean("offline", false),
                    jsonList(o.optJSONArray("tools")), jsonList(o.optJSONArray("permissions")),
                    o.optString("content"), false
                )
            }
        } catch (_: Exception) { }
        return builtIns + custom
    }

    fun isEnabled(context: Context, skill: AssistantSkill): Boolean =
        context.getSharedPreferences(PREFS, 0).getBoolean(ENABLED + skill.id, true)

    fun setEnabled(context: Context, skill: AssistantSkill, enabled: Boolean) {
        context.getSharedPreferences(PREFS, 0).edit().putBoolean(ENABLED + skill.id, enabled).apply()
    }

    fun hasPermission(context: Context, skill: AssistantSkill, permission: String): Boolean =
        context.getSharedPreferences(PREFS, 0).getBoolean(PERMISSION + skill.id + "_" + permission, true)

    fun setPermission(context: Context, skill: AssistantSkill, permission: String, allowed: Boolean) {
        context.getSharedPreferences(PREFS, 0).edit()
            .putBoolean(PERMISSION + skill.id + "_" + permission, allowed).apply()
    }

    fun confirmationRequired(context: Context): Boolean =
        context.getSharedPreferences(PREFS, 0).getBoolean(CONFIRM, true)

    fun setConfirmationRequired(context: Context, required: Boolean) {
        context.getSharedPreferences(PREFS, 0).edit().putBoolean(CONFIRM, required).apply()
    }

    fun importSkill(context: Context, content: String): AssistantSkill {
        require(content.length <= 128 * 1024) { "SKILL.md သည် 128 KB ထက်မကြီးရပါ" }
        val fields = parseHeader(content)
        val name = fields["name"]?.take(60) ?: throw IllegalArgumentException("SKILL.md ထဲတွင် name: ထည့်ပါ")
        val id = (fields["id"] ?: name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'))
            .take(48).ifBlank { "custom-skill" }
        require(id.matches(Regex("[a-z0-9][a-z0-9-]{0,47}"))) { "Invalid skill id" }
        val tools = fields["tools"].orEmpty().split(",").map { it.trim() }.filter { it in allowedTools }
        val permissions = fields["permissions"].orEmpty().split(",").map { it.trim() }.filter { it in allowedPermissions }
        val skill = AssistantSkill(id, name, fields["description"] ?: "Imported custom skill",
            fields["category"] ?: "Custom", fields["offline"]?.equals("true", true) ?: false,
            tools, permissions, content, false)
        val prefs = context.getSharedPreferences(PREFS, 0)
        val array = JSONArray(prefs.getString(CUSTOM, "[]") ?: "[]")
        val updated = JSONArray()
        for (i in 0 until array.length()) {
            val old = array.getJSONObject(i)
            if (old.optString("id") != id) updated.put(old)
        }
        updated.put(JSONObject().apply {
            put("id", skill.id); put("name", skill.name); put("description", skill.description)
            put("category", skill.category); put("offline", skill.offline)
            put("tools", JSONArray(skill.tools)); put("permissions", JSONArray(skill.permissions))
            put("content", skill.content)
        })
        prefs.edit().putString(CUSTOM, updated.toString()).putBoolean(ENABLED + id, true).apply()
        return skill
    }

    fun exportSkill(context: Context, skill: AssistantSkill, output: java.io.OutputStream) {
        output.write(skill.content.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private val allowedTools = setOf(
        "app.launch", "termux.pwd", "termux.ls", "termux.python-version",
        "file.open-document", "file.export-document", "network.search", "notes.local",
        "media.inspect", "media.ocr", "media.transcribe", "security.diagnostics", "workflow.chain"
    )
    private val allowedPermissions = setOf(
        "app.launch", "termux.execute", "file.read", "file.write", "network.access",
        "notes.read", "notes.write", "media.access", "security.diagnostics", "workflow.execute"
    )

    private fun parseHeader(content: String): Map<String, String> {
        val lines = content.lineSequence().take(40).toList()
        if (lines.firstOrNull()?.trim() != "---") return emptyMap()
        val end = lines.indexOfFirst { it.trim() == "---" && lines.indexOf(it) > 0 }
        if (end < 0) return emptyMap()
        return lines.subList(1, end).mapNotNull { line ->
            val split = line.indexOf(':')
            if (split <= 0) null else line.substring(0, split).trim() to line.substring(split + 1).trim().trim('"', '\'')
        }.toMap()
    }

    private fun jsonList(array: JSONArray?): List<String> =
        if (array == null) emptyList() else (0 until array.length()).map { array.optString(it) }
}
