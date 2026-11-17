const fs = require("node:fs");
const zlib = require("node:zlib");
const nbt = require("prismarine-nbt");

function unwrap(root, name) {
  const item = root && root.value && root.value[name];
  return item && Object.prototype.hasOwnProperty.call(item, "value") ? item.value : item;
}
function num(root, name) {
  const value = unwrap(root, name);
  return typeof value === "number" ? value : Number(value || 0);
}
function parseNbt(buffer) {
  return new Promise((resolve, reject) => nbt.parse(buffer, (err, data) => err ? reject(err) : resolve(data)));
}
async function loadSchematic(filePath) {
  if (!/\.(schem|schematic)$/i.test(filePath)) throw new Error("Wybierz plik .schem albo .schematic.");
  let buffer = fs.readFileSync(filePath);
  if (buffer[0] === 0x1f && buffer[1] === 0x8b) buffer = zlib.gunzipSync(buffer);
  const parsed = await parseNbt(buffer);
  const root = parsed && parsed.value ? parsed.value : parsed;
  const width = num(root, "Width") || num(root, "width");
  const height = num(root, "Height") || num(root, "height");
  const length = num(root, "Length") || num(root, "length");
  if (!width || !height || !length) throw new Error("Nie udało się odczytać wymiarów. Plik może używać nieobsługiwanego formatu.");
  const palette = unwrap(root, "Palette") || unwrap(root, "BlockStatePalette");
  return {
    fileName: require("node:path").basename(filePath),
    width, height, length,
    volume: width * height * length,
    paletteSize: palette && typeof palette === "object" ? Object.keys(palette).length : 0,
    note: "Odczytano wymiary NBT. Pełne mapowanie bloków i budowanie wymagają dalszej obsługi formatów oraz adaptera Minecraft."
  };
}
module.exports = { loadSchematic };
