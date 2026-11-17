const title = document.getElementById("page-title");
const pages = ["build", "schematics", "inventory", "automation", "other"];
const labels = {build:"Budowanie",schematics:"Schematy",inventory:"Ekwipunek",automation:"Automatyzacja",other:"Inne"};
document.querySelectorAll(".nav").forEach(button => button.addEventListener("click", () => {
  const page = button.dataset.page;
  document.querySelectorAll(".nav").forEach(item => item.classList.toggle("active", item === button));
  pages.forEach(name => document.getElementById("page-" + name).classList.toggle("hidden", name !== page));
  title.textContent = labels[page];
}));
document.querySelectorAll("[data-mode]").forEach(button => button.addEventListener("click", () => {
  document.querySelectorAll("[data-mode]").forEach(item => item.classList.toggle("selected", item === button));
}));
document.querySelectorAll("[data-rotation]").forEach(button => button.addEventListener("click", () => {
  document.querySelectorAll("[data-rotation]").forEach(item => item.classList.toggle("selected", item === button));
}));
document.getElementById("open-file").addEventListener("click", async () => {
  const output = document.getElementById("file-result");
  output.textContent = "Odczytywanie pliku…";
  try {
    const result = await window.purpleSchematic.openSchematic();
    if (!result) { output.textContent = "Nie wybrano pliku."; return; }
    output.replaceChildren();
    const heading = document.createElement("strong"); heading.textContent = result.fileName;
    const details = document.createElement("div");
    details.textContent = `Wymiary: ${result.width} × ${result.height} × ${result.length} | Objętość: ${result.volume.toLocaleString("pl-PL")} bloków`;
    const note = document.createElement("div"); note.className = "muted"; note.textContent = result.note;
    output.append(heading, details, note);
  } catch (error) { output.textContent = "Błąd odczytu: " + error.message; }
});
