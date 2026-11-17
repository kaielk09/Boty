const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld("purpleSchematic", {
  openSchematic: () => ipcRenderer.invoke("schematic:open")
});
