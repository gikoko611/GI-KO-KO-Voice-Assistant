package com.gi.koko.voiceassistant

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext

private val SkillGold = Color(0xFFFFC857)
private val SkillDark = Color(0xFF090B12)
private val SkillPanel = Color(0xFF171B27)

class SkillCenterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
        setContent { SkillCenterScreen(onBack = { finish() }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillCenterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("8 built-in skills • Custom SKILL.md supported") }
    var selected by remember { mutableStateOf<AssistantSkill?>(null) }
    val skills = remember(refresh) { SkillRepository.all(context) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalArgumentException("File ဖတ်မရပါ")
                val skill = SkillRepository.importSkill(context, text)
                message = "Imported: ${skill.name}"
                refresh++
            } catch (e: Exception) { message = "Import failed: ${e.message}" }
        }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri: Uri? ->
        val skill = selected
        if (uri != null && skill != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { SkillRepository.exportSkill(context, skill, it) }
                message = "Exported: ${skill.name}"
            } catch (e: Exception) { message = "Export failed: ${e.message}" }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = SkillGold, background = SkillDark, surface = SkillPanel)) {
        Scaffold(
            containerColor = SkillDark,
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SkillGold)
                        }
                    },
                    title = { Text("SKILL CENTER", color = SkillGold, fontWeight = FontWeight.Black) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SkillDark)
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("G.I KO KO • PRO SKILL PACK", color = SkillGold, fontWeight = FontWeight.Bold)
                Text(message, color = Color.LightGray, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { importPicker.launch(arrayOf("text/*", "application/octet-stream")) },
                        colors = ButtonDefaults.buttonColors(containerColor = SkillGold, contentColor = SkillDark)) {
                        Text("IMPORT SKILL.md")
                    }
                    OutlinedButton(onClick = { selected = null; message = "Export ကို Skill တစ်ခုချင်းစီမှရွေးပါ" }) {
                        Text("CUSTOM SKILLS", color = SkillGold)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Require confirmation for actions", color = Color.White, fontSize = 13.sp)
                    Switch(checked = SkillRepository.confirmationRequired(context),
                        onCheckedChange = { SkillRepository.setConfirmationRequired(context, it); refresh++ })
                }
                Text("SKILLS • ${skills.size}", color = SkillGold, fontWeight = FontWeight.Bold)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(skills, key = { it.id }) { skill ->
                        val enabled = SkillRepository.isEnabled(context, skill)
                        Card(colors = CardDefaults.cardColors(containerColor = SkillPanel), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(skill.name, color = SkillGold, fontWeight = FontWeight.Bold)
                                        Text("${skill.category} • ${if (skill.offline) "Offline-first" else "Online required"}",
                                            color = Color.LightGray, fontSize = 10.sp)
                                    }
                                    Switch(checked = enabled, onCheckedChange = {
                                        SkillRepository.setEnabled(context, skill, it); refresh++
                                    })
                                }
                                Text(skill.description, color = Color.White, fontSize = 12.sp)
                                Text("Tools: ${if (skill.tools.isEmpty()) "No registered tools" else skill.tools.joinToString()}",
                                    color = Color.LightGray, fontSize = 10.sp)
                                if (skill.permissions.isNotEmpty()) {
                                    skill.permissions.forEach { permission ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(permission, color = Color.LightGray, fontSize = 11.sp)
                                            Switch(checked = SkillRepository.hasPermission(context, skill, permission),
                                                onCheckedChange = { SkillRepository.setPermission(context, skill, permission, it); refresh++ })
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { selected = skill; exportPicker.launch("${skill.id}.SKILL.md") }) {
                                        Text("EXPORT", color = SkillGold)
                                    }
                                    TextButton(onClick = { message = if (skill.builtIn)
                                        "Built-in skill ကို disable လုပ်နိုင်သည်။ ဖျက်မရပါ။"
                                        else "Custom skill ကို SKILL.md အသစ်ဖြင့် အစားထိုး Import လုပ်နိုင်သည်။" }) {
                                        Text("DETAILS", color = Color.LightGray)
                                    }
                                }
                            }
                        }
                    }
                }
                Text("Safety: SKILL.md သည် instruction/data သာဖြစ်ပြီး shell/eval code ကို run မလုပ်ပါ။ Tool များသည် app allowlist ထဲမှသာ ခေါ်နိုင်သည်။",
                    color = Color.LightGray, fontSize = 10.sp)
            }
        }
    }
}
