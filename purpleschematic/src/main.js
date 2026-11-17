const { app, BrowserWindow, dialog, ipcMain } = require("electron");
const path = require("node:path");
const http = require("node:http");
const { loadSchematic } = require("./schematic");

let currentSchematic = null;
const adapterUrl = "http://127.0.0.1:8766";
function requestAdapter(method, route, body) {
  return new Promise((resolve, reject) => {
    const payload = body ? Buffer.from(JSON.stringify(body)) : null;
    const req = http.request(adapterUrl + route, {
      method, timeout: 2500,
      headers: payload ? { "Content-Type": "application/json", "Content-Length": payload.length } : {}
    }, res => {
      let data = "";
      res.setEncoding("utf8");
      res.on("data", chunk => data += chunk);
      res.on("end", () => {
        let parsed;
        try { parsed = JSON.parse(data || "{}"); } catch { return reject(new Error("Adapter zwrócił nieprawidłową odpowiedź.")); }
        if (res.statusCode < 200 || res.statusCode >= 300) return reject(new Error(parsed.error || "Błąd adaptera."));
        resolve(parsed);
      });
    });
    req.on("timeout", () => req.destroy(new Error("Przekroczono czas oczekiwania na Minecraft.")));
    req.on("error", reject);
    if (payload) req.write(payload);
    req.end();
  });
}
function createWindow() {
  const win = new BrowserWindow({
    width: 1280, height: 820, minWidth: 980, minHeight: 680,
    backgroundColor: "#090711", title: "PurpleSchematic",
    webPreferences: { preload: path.join(__dirname, "preload.js"), contextIsolation: true, nodeIntegration: false }
  });
  win.loadFile(path.join(__dirname, "index.html"));
}
ipcMain.handle("schematic:open", async () => {
  const result = await dialog.showOpenDialog({
    title: "Wybierz schemat Minecraft", properties: ["openFile"],
    filters: [{ name: "Schematy Minecraft", extensions: ["schem"] }]
  });
  if (result.canceled || !result.filePaths[0]) return null;
  currentSchematic = await loadSchematic(result.filePaths[0]);
  let modAnalysis = null;
  try {
    const ids = [...new Set(currentSchematic.blocks.map(block => block.id))];
    if (ids.length <= 5000) {
      const [analysis, modList] = await Promise.all([
        requestAdapter("POST", "/analyze", { ids }),
        requestAdapter("GET", "/mods")
      ]);
      modAnalysis = { ...analysis, mods: modList.mods || [] };
    }
  } catch (error) {
    modAnalysis = { unavailable: true, message: error.message };
  }
  return { ...currentSchematic, blocks: undefined, modAnalysis };
});
ipcMain.handle("minecraft:status", async () => {
  try { return await requestAdapter("GET", "/status"); }
  catch (error) { return { connected: false, error: error.message }; }
});
ipcMain.handle("minecraft:build", async (_event, settings) => {
  if (!currentSchematic || !currentSchematic.blocks) throw new Error("Najpierw wczytaj obsługiwany schemat Sponge .schem.");
  const origin = {
    x: Math.trunc(Number(settings.x)), y: Math.trunc(Number(settings.y)), z: Math.trunc(Number(settings.z))
  };
  if (!Object.values(origin).every(Number.isFinite) || origin.y < -64 || origin.y > 319) throw new Error("Podaj prawidłowe współrzędne.");
  return requestAdapter("POST", "/build", {
    origin, mode: settings.mode, rotation: Number(settings.rotation) || 0,
    blocks: currentSchematic.blocks
  });
});
app.whenReady().then(() => {
  createWindow();
  app.on("activate", () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});
app.on("window-all-closed", () => { if (process.platform !== "darwin") app.quit(); });
