const mineflayer = require("mineflayer");
const { pathfinder, Movements, goals } = require("mineflayer-pathfinder");

module.exports = function start(config) {
  const bot = mineflayer.createBot(config);
  bot.loadPlugin(pathfinder);

  let target = null;
  bot.once("spawn", () => {
    bot.pathfinder.setMovements(new Movements(bot));
    console.log("[FOLLOW] Szukam gracza:", config.targetPlayer);
  });

  bot.on("physicsTick", () => {
    if (!bot.entity) return;
    target = bot.players[config.targetPlayer]?.entity || null;
    if (!target) {
      bot.pathfinder.setGoal(null);
      return;
    }
    const distance = bot.entity.position.distanceTo(target.position);
    if (distance > 3) {
      bot.pathfinder.setGoal(new goals.GoalFollow(target, 2), true);
    } else {
      bot.pathfinder.setGoal(null);
    }
  });

  bot.on("kicked", reason => console.log("[FOLLOW] Wyrzucono:", reason));
  bot.on("error", err => console.error("[FOLLOW] Błąd:", err.message));
  bot.on("end", () => console.log("[FOLLOW] Rozłączono."));
};
