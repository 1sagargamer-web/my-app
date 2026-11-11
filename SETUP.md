# Setup Guide 💖

Do kaam karne hain: **(A) Firebase** (taaki sirf aap questions badal sako) aur **(B) Link live karna** (taaki GF khol sake).
Sab phone se ho sakta hai, browser mein "Desktop site" on kar lena to aasan rahega.

---

## A. Firebase setup (10 minute, free)

### 1. Project banao
1. https://console.firebase.google.com kholo, Google account se login karo.
2. **Create a project** → koi bhi naam (jaise `love-app`) → Google Analytics **off** → **Create**.

### 2. Web app jodo aur config lo
1. Project Overview pe **`</>`** (Web) icon dabao.
2. Nickname: `love-app` → **Register app**.
3. Ek `firebaseConfig = { apiKey: ..., authDomain: ..., ... }` dikhega.
4. Ye values mujhe chat mein bhej do **ya** khud GitHub pe `js/firebase-config.js` file kholke paste kar do.
   (Ye values secret nahi hain, inhe share karna safe hai.)

### 3. Database banao
1. Left menu → **Build → Firestore Database → Create database**.
2. Location: **asia-south1 (Mumbai)** → **Production mode** → **Create**.
3. **Rules** tab kholo, sab mita ke `firestore.rules` file ka content paste karo.
4. Usme `OWNER_EMAIL_HERE` ki jagah **apna email** likho → **Publish**.

### 4. Owner login banao
1. **Build → Authentication → Get started**.
2. **Email/Password** → Enable → Save.
3. **Users** tab → **Add user** → apna email (wahi jo rules mein likha) + strong password.
4. **Settings → User actions** → "Enable create (sign-up)" ka tick **hata do** (taaki koi aur account na bana sake).
5. **Settings → Authorized domains → Add domain** → apni website ka domain (jaise `1sagargamer-web.github.io`).

Ab sirf aap hi `admin.html` pe login karke cheezein badal sakte ho. Koi aur (GF bhi) login nahi kar payega.

---

## B. Link live karna (GitHub Pages, free)

Abhi repo **private** hai. Free GitHub Pages ke liye repo **public** karna hoga.
Code mein koi password nahi hai, saare questions Firebase mein safe hain, to public karna safe hai.

1. GitHub pe repo kholo → **Settings → General** → neeche **Danger Zone → Change visibility → Public**.
2. **Settings → Pages** → Source: **Deploy from a branch** → branch chuno → folder **/ (root)** → **Save**.
3. 1-2 minute baad link milega: `https://1sagargamer-web.github.io/my-app/`

Repo private hi rakhna hai? To **Vercel** (vercel.com) pe GitHub se login karke ye repo import karo, wo free mein link de dega.

---

## Use kaise karein

| Kaun | Link | Kya hoga |
|---|---|---|
| GF | `.../my-app/` | Sawal, Yes/No, happy/sad boy, song |
| Aap (owner) | `.../my-app/admin.html` | Login → questions, options, song, messages badlo → **Save** |

- Save karte hi GF ke app mein update ho jata hai, link wahi rehta hai.
- **Song badalna:** apna mp3 GitHub repo ke `music` folder mein upload karo, phir admin mein `music/song.mp3` likho. Ya kisi bhi mp3 ka direct link daalo. Khaali chhodoge to built-in tune bajegi.
- **Text mein naam:** kahin bhi `{name}` likho, wahan GF ka naam aa jayega.
- Firebase setup se pehle bhi app chalta hai ("preview mode"), lekin tab changes sirf aapke browser mein save hote hain.
