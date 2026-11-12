# 🔮 Baby AI — Aapki apni voice assistant (Android)

Bolo aur kaam ho jaaye! Baby AI ek **free** Android app hai jo aapki **Hindi / Hinglish** awaaz samajh kar phone ke kaam karti hai.
Koi API key nahi, koi paisa nahi.

## ✨ Kya-kya kar sakti hai

| Kaam | Aise bolo |
|---|---|
| 📱 Koi bhi app kholna | "YouTube kholo", "Instagram open karo", "Free Fire chalao" |
| ▶️ YouTube / gaane | "YouTube pe Arijit Singh ke gaane chalao", "Kesariya song bajao" |
| 💬 WhatsApp message (khud bhejta hai) | "Rahul ko message bhejo ki main 5 baje aaunga", "Mummy ko WhatsApp pe good night bhejo" |
| ✉️ SMS | "Papa ko SMS bhejo ki ghar pahunch gaya" |
| 📞 Call | "Papa ko call karo", "Mummy ko phone lagao" |
| 📸 Camera + photo | "Camera kholo aur photo click karo", "Selfie lo", "Photo khicho" |
| 🔦 Torch | "Torch jalao", "Torch band karo" |
| 🔊 Volume | "Awaaz badhao", "Volume kam karo", "Mute karo" |
| ⏰ Alarm / Timer | "Subah 7 baje ka alarm lagao", "5 minute ka timer lagao" |
| 🕒 Time / Date / Battery | "Time kya hua", "Aaj ki date batao", "Battery kitni hai" |
| 🌐 Google search | "Google pe cricket score search karo", "Taj Mahal ke bare me batao" |
| 🗺️ Rasta | "Delhi ka rasta dikhao" |
| ⚙️ Settings | "WiFi kholo", "Bluetooth on karo", "Home screen pe jao" |
| 😄 Masti | "Joke sunao", "Tumhara naam kya hai", "Chup" (sunna band) |

Devanagari me bolna bhi chalega — "यूट्यूब खोलो", "राहुल को मैसेज भेजो कि मैं आ रहा हूँ".

Agar aap adhoori baat bolo, Baby poochhegi: **"Message bhejo"** → *"Kisko?"* → **"Rahul"** → *"Kya message?"* → **"Kal milte hain"** ✅

## 📲 Phone me kaise install karein

1. Is repo ke **Releases** page par jaao (GitHub app/website me right side "Releases"), sabse upar wala **BabyAI.apk** download karo.
2. APK kholo → "Unknown apps install karne do" allow karo → **Install**.
3. App kholo → saari **permissions** allow karo (Mic, Camera, Contacts, SMS, Call).
4. Bolna shuru karo! Gola **neela** ho to Baby sun rahi hai.

### WhatsApp message khud bhejne ke liye (Auto-send)
WhatsApp kisi app ko seedha message bhejne nahi deta, isliye Baby chat kholkar message likhti hai aur phir
**Accessibility** se "Send" button daba deti hai:

1. App me **"Auto-send chalu karo"** dabao → Settings khulegi
2. **Installed/Downloaded apps → "Baby AI – WhatsApp Auto Send" → ON**
3. Android 13+ par "Restricted setting" aaye to: **Settings → Apps → Baby AI → ⋮ (upar 3 dots) → "Allow restricted settings"**, phir step 2 dobara.

Auto-send band ho tab bhi chalega — bas WhatsApp khulne par aapko khud Send dabana hoga.

## 💡 Tips
- **Google app** phone me hona chahiye (awaaz pehchanne ke liye) aur internet on ho.
- "Hamesha suno" switch ON ho to app khula rehte hue Baby baar-baar sunti rehti hai. "Chup" bolo ya gole ko dabao to ruk jaati hai.
- Contact ka naam waise bolo jaise phone me save hai. Do milte-julte naam ho to Baby "haan/na" poochhegi.
- ⚙ Settings me: country code (India = 91), WhatsApp ya SMS, Hindi ya English.

## 🛠️ Developers ke liye
- Kotlin, minSdk 26 (Android 8+), CameraX, Android SpeechRecognizer + TextToSpeech.
- Command samajhne ka code: `app/src/main/java/com/sagar/baby/core/` (Hindi→Roman transliteration + fuzzy matching + parser). Tests: `./gradlew testDebugUnitTest`.
- Har push par GitHub Actions APK banata hai (`.github/workflows/build.yml`) aur Release me daal deta hai.
- Android Studio me bhi khol kar Run kar sakte ho.
