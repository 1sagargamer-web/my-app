# For You 💖

Ek cute "Do you love me?" web app.

- **Yes** → happy boy naachta hai, "Love you baby ❤️" bolta hai, song bajta hai, hearts udte hain.
- **No** → "Ek baar aur soch lo 🥺" → "Haan bana liya ❤️" (happy) ya "Soch liya, nahi banana" (sad boy, wapas sawal).
- **Owner panel** (`admin.html`): password se khulta hai. Sawal, options, messages, song badlo → "Link banao" → GF ko bhejo. Koi server/account nahi chahiye, saari settings link ke andar hoti hain.

Setup ke steps: [SETUP.md](SETUP.md)

## Files

| File | Kaam |
|---|---|
| `index.html`, `js/app.js` | GF wala app |
| `admin.html`, `js/admin.js` | Owner panel |
| `js/defaults.js` | Default questions/text |
| `js/store.js` | Settings ko link mein daalna/padhna, owner password |
| `js/boy.js` | Boy character (SVG) |
| `js/music.js` | Built-in happy tune + mp3 player |
| `music/` | Apne mp3 songs yahan |

Local mein chalane ke liye: `python3 -m http.server` aur `http://localhost:8000` kholo.
