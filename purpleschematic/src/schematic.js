const fs = require("node:fs");
const path = require("node:path");
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
function bytesOf(value) {
  if (Buffer.isBuffer(value)) return value;
  if (value instanceof Uint8Array) return Buffer.from(value);
  if (Array.isArray(value)) return Buffer.from(value);
  throw new Error("Schemat nie zawiera poprawnych danych bloków.");
}
function decodeVarints(data, count) {
  const values = [];
  let value = 0, shift = 0;
  for (const byte of data) {
    value |= (byte & 0x7f) << shift;
    if (byte & 0x80) shift += 7;
    else {
      values.push(value >>> 0);
      value = 0; shift = 0;
      if (values.length === count) break;
    }
    if (shift > 28) throw new Error("Nieprawidłowy zapis palety schematu.");
  }
  if (values.length !== count) throw new Error("Dane bloków są niekompletne lub używają nieobsługiwanego formatu.");
  return values;
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
  const volume = width * height * length;
  if (!width || !height || !length || !Number.isSafeInteger(volume)) throw new Error("Nie udało się odczytać wymiarów schematu.");
  if (volume > 100000) throw new Error("Schemat ma ponad 100 000 bloków. Zmniejsz go przed importem.");

  const palette = unwrap(root, "Palette");
  const rawData = unwrap(root, "BlockData");
  if (!palette || !rawData) {
    throw new Error("Ten plik nie zawiera palety Sponge .schem z BlockData. Legacy .schematic i starsze warianty wymagają osobnego parsera.");
  }
  const paletteEntries = Object.entries(palette).map(([id, state]) => [Number(state), id]);
  if (!paletteEntries.length || paletteEntries.some(([id, name]) => !Number.isInteger(id) || !/^minecraft:[a-z0-9_]+$/.test(name) && !/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(name))) {
    throw new Error("Paleta zawiera nieprawidłowe identyfikatory bloków.");
  }
  const paletteByIndex = new Map(paletteEntries);
  const indices = decodeVarints(bytesOf(rawData), volume);
  const blocks = [];
  for (let i = 0; i < indices.length; i++) {
    const id = paletteByIndex.get(indices[i]);
    if (!id) throw new Error("Schemat odwołuje się do brakującego wpisu palety.");
    const x = i % width;
    const z = Math.floor(i / width) % length;
    const y = Math.floor(i / (width * length));
    blocks.push({ x, y, z, id });
  }
  return { fileName: path.basename(filePath), width, height, length, volume, blocks,
    paletteSize: paletteEntries.length, format: "Sponge .schem",
    note: "Schemat odczytany. Budowanie wymaga uruchomionego adaptera Fabric 1.20.1 i uprawnień operatora." };
}
module.exports = { loadSchematic };
