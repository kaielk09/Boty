const { app, BrowserWindow, dialog, ipcMain } = require("electron");
const path = require("node:path");
const { loadSchematic } = require("./schematic");

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
    filters: [{ name: "Schematy Minecraft", extensions: ["schem", "schematic"] }]
  });
  if (result.canceled || !result.filePaths[0]) return null;
  return loadSchematic(result.filePaths[0]);
});
app.whenReady().then(() => {
  createWindow();
  app.on("activate", () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});
app.on("window-all-closed", () => { if (process.platform !== "darwin") app.quit(); });
