# 🔮 Baby AI — Aapki apni voice assistant (Android)

Bolo aur kaam ho jaaye! Baby AI ek **free** Android app hai jo aapki **Hindi / Hinglish** awaaz samajh kar phone ke kaam karti hai.
Koi API key nahi, koi paisa nahi.

## ✨ Kya-kya kar sakti hai

| Kaam | Aise bolo |
|---|---|
| 📱 Koi bhi app kholna | "YouTube kholo", "Instagram open karo", "Free Fire chalao" |
| ▶️ YouTube / gaane | "YouTube pe Arijit Singh ke gaane chalao", "Kesariya song bajao" |
| 💬 WhatsApp message (khud bhejta hai) | "Rahul ko message bhejo ki main 5 baje aaunga", "WhatsApp kholo aur Mummy ko good night bhejo", khuli chat me "Isko message karo ki hello" |
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

Adhoori baat bologe to Baby poochhegi: **"Message bhejo"** → *"Kisko bhejna hai?"* → **"Rahul"** → *"Kya message bhejna hai?"* → **"Kal milte hain"** ✅

## 🆕 Naya (behtar samajh)
- **"YouTube kholo aur hamdard song lagao"** → YouTube khulta hai aur pehla video **khud chalta hai** (sirf "search karo" bolo to sirf dikhata hai). Iske liye Background Control ON hona chahiye.
- **"Play Store kholo aur Free Fire install karo"** / **"Free Fire download karo"** → Play Store me search karke **Install** khud dabata hai. Phir **"open karo"** bolo to app khul jaata hai.
- Ek hi baar me do kaam: "WhatsApp kholo aur Mummy ko hello bhejo", "Play Store me pubg dhundo aur install karo".
- **Contact ka naam**: bilkul match ho to seedha bhej deta hai. Thoda alag lage to poochhta hai *"Exact wahi naam nahi mila, kya X ko bhejun? Haan bolo ya sahi naam batao."* Bilkul na mile to naam phir se maangta hai.
- **🕓 History**: app me upar 🕓 dabao — Baby ne is phone par jo kuch kiya (command, message, photo) sab yahin dikhta hai. Yeh **kahin bheja nahi jaata**, sirf isi phone par rehta hai; "🗑 Saara mitado" se mita sakte ho.

> **Note:** "Owner panel jisme doosron ki activity/photos chupke mil jayein" — woh jaan-bujhkar nahi banaya. Woh doosre logon ki jasoosi (spyware) ban jaata, jo galat aur khatarnak hai. Iske badle upar wali **History** hai, jo usi phone par khuli-aam dikhti hai.

## 🎧 Background me har baat sunti hai
- Baby ON karte hi ek **notification** aata hai ("Baby ON hai ✨") jisme **🎤 Bolo / ⏸ Roko / ✕ Band** buttons hain.
- WhatsApp, YouTube, home screen, kahin bhi raho, bas bolo. Jaise WhatsApp me chat khuli hai to: **"Isko message karo ki hello"**.
- Front camera ke charon taraf **chamakti light** ghoomti hai: neeli/rangeen jab sun rahi ho, gulabi jab bol rahi ho.
- Koi haan/na nahi poochhti. Message bhejkar kehti hai: *"Ho gaya! Maine message bhej diya. Ab aage bolo, kya karna hai?"*
- Sab kuch **Hinglish (English letters)** me: "मैं आ रहा हूँ" bologe to message jayega **"Main aa raha hoon"**.
- Video/gaana chal raha ho to Baby ruk jaati hai (taaki video na atke). Tab notification me **🎤 Bolo** dabao, ya Settings me "Video chalte waqt bhi suno" ON karo.

## 🎙️ Apni awaaz lagao (Awaaz Pack)
App me upar **🎙️** dabao. Har jawab ke saamne:
- **⏺ Record**: apni awaaz me boliye, phir ■ Stop
- **📁**: phone se audio file chuniye
- **📂 Saari files ek saath**: agar files ke naam `ready.mp3`, `msg_sent.mp3`… hain to sab apne aap lag jayengi

Saare file names `app/src/main/assets/voice/README.txt` me hain. Files is folder me daal do to woh APK ke andar hi aa jayengi.
Jin jawabon me naam/time/number hota hai (jaise "Abhi 3 baje hain"), woh phone ki awaaz me bole jaate hain; uski pitch/speed Settings me badal sakte ho.

## 📲 Phone me kaise install karein

1. **Releases** page se sabse naya **BabyAI.apk** download karo (purana uninstall karne ki zarurat nahi).
2. Install karke kholo, saari **permissions** allow karo (Mic, Camera, Contacts, SMS, Call, Notification).
3. Yellow card me diye buttons dabao:
   - **Background Control ON karo** (sabse zaroori): Settings → Accessibility → Installed apps → **Baby AI – Background Control** → ON.
     Android 13+ par "Restricted setting" aaye to: Settings → Apps → Baby AI → ⋮ → **Allow restricted settings**, phir dobara.
   - **Battery restriction hatao**, taaki phone Baby ko band na kare. (Xiaomi/Vivo/Oppo me "Autostart" bhi ON karo.)
4. Bolo! 🎉

## 💡 Tips
- Phone me **Google app** aur **internet** chahiye (awaaz pehchanne ke liye).
- Phone restart hone ke baad ek baar app kholna padta hai (Android ka rule hai).
- "Chup" bolo to Baby ruk jaati hai. Notification me **▶ Chalu** dabao ya gole ko chhoo kar phir shuru karo.
- ⚙ Settings: country code, WhatsApp/SMS, beep band, camera light, awaaz pitch/speed.

## 🛠️ Developers ke liye
- Kotlin, minSdk 26 (Android 8+), CameraX, Android SpeechRecognizer + TextToSpeech.
- Command samajhne ka code: `app/src/main/java/com/sagar/baby/core/` (Hindi→Roman transliteration + fuzzy matching + parser). Tests: `./gradlew testDebugUnitTest`.
- Har push par GitHub Actions APK banata hai (`.github/workflows/build.yml`) aur Release me daal deta hai.
- Android Studio me bhi khol kar Run kar sakte ho.
