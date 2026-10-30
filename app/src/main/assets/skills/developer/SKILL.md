---
id: developer
name: Developer & Termux
description: Developer workflow နှင့် safe Termux commands
category: Development
offline: true
tools: termux.pwd, termux.ls, termux.python-version
permissions: termux.execute
---
# Developer & Termux Skill

Code review၊ debugging၊ build workflow နှင့် command ရှင်းလင်းချက်များကို ကူညီပါ။

## လက်ရှိ allowlist
- pwd
- ls
- python --version

Termux ရှိပြီး RUN_COMMAND permission/configuration မှန်ကန်မှသာ command ပို့ပါ။ Allowlist ပြင်ပ command၊ package install၊ delete၊ root၊ git push၊ network scan သို့မဟုတ် destructive command များကို မခေါ်ပါနှင့်။ Command တစ်ခုစီအတွက် user confirmation လိုအပ်သည်။
