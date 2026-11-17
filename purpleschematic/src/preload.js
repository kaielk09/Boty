const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld("purpleSchematic", {
  openSchematic: () => ipcRenderer.invoke("schematic:open"),
  getMinecraftStatus: () => ipcRenderer.invoke("minecraft:status"),
  startBuild: settings => ipcRenderer.invoke("minecraft:build", settings)
});
