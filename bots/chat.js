const mineflayer = require("mineflayer");

module.exports = function start(config) {
  const bot = mineflayer.createBot(config);

  bot.on("chat", (username, message) => {
    if (username === bot.username) return;

    if (message.trim() === "!bot") {
      bot.chat("Cześć! Jestem botem Minecraft.");
    } else if (message.trim() === "!ping") {
      bot.chat("Działam!");
    }
  });

  bot.once("spawn", () => console.log("[CHAT] Bot jest gotowy."));
  bot.on("kicked", reason => console.log("[CHAT] Wyrzucono:", reason));
  bot.on("error", err => console.error("[CHAT] Błąd:", err.message));
  bot.on("end", () => console.log("[CHAT] Rozłączono."));
};
