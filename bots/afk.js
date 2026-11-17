const mineflayer = require("mineflayer");

module.exports = function start(config) {
  const bot = mineflayer.createBot(config);

  bot.once("spawn", () => {
    console.log("[AFK] Bot jest na serwerze.");
    setInterval(() => {
      if (!bot.entity) return;
      const yaw = Math.random() * Math.PI * 2;
      bot.look(yaw, 0, true).catch(() => {});
    }, 7000);
  });

  bot.on("kicked", reason => console.log("[AFK] Wyrzucono:", reason));
  bot.on("error", err => console.error("[AFK] Błąd:", err.message));
  bot.on("end", () => console.log("[AFK] Rozłączono."));
};
