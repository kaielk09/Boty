const title = document.getElementById("page-title");
const pages = ["build", "schematics", "inventory", "automation", "other"];
const labels = {build:"Budowanie",schematics:"Schematy",inventory:"Ekwipunek",automation:"Automatyzacja",other:"Inne"};
let selectedMode = "creative";
let selectedRotation = 0;
let hasSchematic = false;
document.querySelectorAll(".nav").forEach(button => button.addEventListener("click", () => {
  const page = button.dataset.page;
  document.querySelectorAll(".nav").forEach(item => item.classList.toggle("active", item === button));
  pages.forEach(name => document.getElementById("page-" + name).classList.toggle("hidden", name !== page));
  title.textContent = labels[page];
}));
document.querySelectorAll("[data-mode]").forEach(button => button.addEventListener("click", () => {
  selectedMode = button.dataset.mode;
  document.querySelectorAll("[data-mode]").forEach(item => item.classList.toggle("selected", item === button));
}));
document.querySelectorAll("[data-rotation]").forEach(button => button.addEventListener("click", () => {
  selectedRotation = Number(button.dataset.rotation);
  document.querySelectorAll("[data-rotation]").forEach(item => item.classList.toggle("selected", item === button));
}));
const statusText = document.getElementById("connection-text");
const statusBadge = document.getElementById("connection-badge");
const buildButton = document.getElementById("build-button");
async function refreshConnection() {
  const status = await window.purpleSchematic.getMinecraftStatus();
  statusText.textContent = status.connected ? "Adapter Minecraft połączony" : "Adapter Minecraft niepodłączony";
  statusBadge.textContent = status.connected ? "POŁĄCZONO" : "BRAK POŁĄCZENIA";
  statusBadge.classList.toggle("connected", !!status.connected);
  const mainStatus = document.getElementById("connection-text-main");
  if (mainStatus) mainStatus.textContent = status.connected ? (status.status || "Połączenie z Minecraftem aktywne") : "Brak połączenia z adapterem Minecraft";
  buildButton.disabled = !status.connected || !hasSchematic;
}
document.getElementById("open-file").addEventListener("click", async () => {
  const output = document.getElementById("file-result");
  output.textContent = "Odczytywanie schematu…";
  try {
    const result = await window.purpleSchematic.openSchematic();
    if (!result) { output.textContent = "Nie wybrano pliku."; return; }
    hasSchematic = true;
    output.replaceChildren();
    const heading = document.createElement("strong"); heading.textContent = result.fileName;
    const details = document.createElement("div");
    details.textContent = `Wymiary: ${result.width} × ${result.height} × ${result.length} | Bloki: ${result.volume.toLocaleString("pl-PL")} | Paleta: ${result.paletteSize}`;
    const note = document.createElement("div"); note.className = "muted"; note.textContent = result.note;
    output.append(heading, details, note);
    await refreshConnection();
  } catch (error) { hasSchematic = false; output.textContent = "Błąd odczytu: " + error.message; }
});
buildButton.addEventListener("click", async () => {
  buildButton.disabled = true;
  const status = document.getElementById("build-status");
  status.textContent = "Wysyłanie projektu do Minecrafta…";
  try {
    const result = await window.purpleSchematic.startBuild({
      mode: selectedMode, rotation: selectedRotation,
      x: document.getElementById("coord-x").value,
      y: document.getElementById("coord-y").value,
      z: document.getElementById("coord-z").value
    });
    status.textContent = result.message || "Zadanie budowania przyjęte przez adapter.";
  } catch (error) { status.textContent = "Nie udało się rozpocząć: " + error.message; }
  await refreshConnection();
});
refreshConnection();
setInterval(refreshConnection, 3000);
