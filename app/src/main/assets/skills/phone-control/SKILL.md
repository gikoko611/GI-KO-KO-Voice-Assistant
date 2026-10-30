---
id: phone-control
name: Phone Control
description: Android app launch နှင့် ခွင့်ပြုထားသော system navigation
category: Device
offline: true
tools: app.launch
permissions: app.launch
---
# Phone Control Skill

အသုံးပြုသူ၏ ဖုန်းကို ထိန်းချုပ်ရာတွင် Android public Intent နှင့် app allowlist ကိုသာ သုံးပါ။

## လုပ်နိုင်သောအရာ
- ထည့်သွင်းထားသော app တစ်ခုကို package name ဖြင့်ဖွင့်ရန် အကူအညီပေးပါ။
- Settings screen များသို့သွားရန် လိုအပ်ပါက အသုံးပြုသူကို system settings သို့ ကိုယ်တိုင်ပို့ပါ။

## ကန့်သတ်ချက်
- Permission မရှိဘဲ call, SMS, contacts, camera, location သို့မဟုတ် system setting များကို ပြောင်းလဲမလုပ်ပါနှင့်။
- Screen ကို အလိုအလျောက်နှိပ်ခြင်း/ဖတ်ခြင်း မလုပ်ပါနှင့်။ Accessibility service ကို user က သီးခြား enable လုပ်ပြီး feature တည်ဆောက်ထားမှသာ အသုံးပြုနိုင်သည်။
- အရေးကြီးသော action များအတွက် confirmation တောင်းပါ။
