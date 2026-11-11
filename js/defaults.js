// Default content. Owner can change all of this from the admin page (admin.html).
// {name} is replaced with the girlfriend's name.

export const DEFAULT_QUESTION = {
  text: "Do you love me? 💖",
  options: [
    { label: "Yes 😍", action: "happy" },
    { label: "No 🙄", action: "retry" },
  ],
  retry: {
    text: "Ek baar aur soch lo 🥺",
    options: [
      { label: "Soch liya, nahi banana boyfriend 😒", action: "sad" },
      { label: "Haan bana liya ❤️", action: "happy" },
    ],
  },
  happyMessage: "Love you baby ❤️",
  sadMessage: "Please ek baar aur soch lo 😢",
};

export const DEFAULT_CONFIG = {
  herName: "Baby",
  intro: {
    title: "Hey {name} 💕",
    text: "Mere paas tumhare liye ek bahut zaroori sawal hai...",
    button: "Pucho na 🙈",
  },
  questions: [DEFAULT_QUESTION],
  happyTitle: "Yayyy! 🥰",
  nextButton: "Agla sawal 💌",
  finalMessage: "Tum meri duniya ho, {name} 💞",
  replayButton: "Phir se shuru karo 🔁",
  sadButton: "Theek hai, phir se poocho 🥺",
  // Empty url = built-in happy tune.
  song: { url: "" },
  // Boy says the happy message out loud (phone's voice).
  voice: true,
};
