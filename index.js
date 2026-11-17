const fs = require("node:fs");
const path = require("node:path");

const mode = process.argv[2];
const allowed = ["afk", "chat", "follow", "guard"];

if (!allowed.includes(mode)) {
  console.log("Użycie: node index.js <afk|chat|follow|guard>");
  process.exit(1);
}

const configPath = path.join(__dirname, "config.json");
if (!fs.existsSync(configPath)) {
  console.error("Brak config.json. Skopiuj config.example.json do config.json i uzupełnij ustawienia.");
  process.exit(1);
}

const config = require(configPath);
const start = require(path.join(__dirname, "bots", mode));
start(config);
