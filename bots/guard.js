const mineflayer = require("mineflayer");

module.exports = function start(config) {
  const bot = mineflayer.createBot(config);
  const radius = Number(config.guardRadius) > 0 ? Number(config.guardRadius) : 8;
  const warned = new Set();

  bot.once("spawn", () => console.log("[GUARD] Obserwuję graczy w promieniu", radius, "bloków."));

  bot.on("physicsTick", () => {
    if (!bot.entity) return;
    for (const [name, player] of Object.entries(bot.players)) {
      if (name === bot.username || !player.entity) continue;
      const distance = bot.entity.position.distanceTo(player.entity.position);
      if (distance <= radius && !warned.has(name)) {
        warned.add(name);
        bot.chat(name + " jest blisko!");
        console.log("[GUARD] Wykryto gracza:", name, "odległość:", distance.toFixed(1));
      } else if (distance > radius + 2) {
        warned.delete(name);
      }
    }
  });

  bot.on("kicked", reason => console.log("[GUARD] Wyrzucono:", reason));
  bot.on("error", err => console.error("[GUARD] Błąd:", err.message));
  bot.on("end", () => console.log("[GUARD] Rozłączono."));
};
