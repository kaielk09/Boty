# PurpleSchematic

Desktopowy Minecraft Control Center w ciemnym, fioletowym motywie.

## Uruchomienie

Wymagania: Node.js 20 lub nowszy.

```bash
cd purpleschematic
npm install
npm start
```

## Co działa w wersji 0.1.0

- Osobna aplikacja desktopowa oparta na Electron.
- Nawigacja między zakładkami.
- Wybór trybu Creative / Survival, współrzędnych i obrotu w interfejsie.
- Wybieranie plików `.schem` i `.schematic`.
- Wstępny odczyt wymiarów i metadanych NBT dla obsługiwanych plików.

## Ważne ograniczenia

Ta wersja **nie łączy się jeszcze z uruchomionym Minecraftem i nie buduje automatycznie**. Przycisk budowania jest celowo zablokowany, aby interfejs nie udawał, że wykonał operację. Odczyt metadanych nie oznacza jeszcze pełnego odtworzenia palety i danych wszystkich bloków.

Następne etapy:
1. Pełny parser formatów Sponge `.schem` i legacy `.schematic`, z testowymi plikami.
2. Podgląd 3D, przesuwanie i obracanie projektu.
3. Lokalny protokół komunikacji oraz pierwszy adapter dla wybranej wersji i loadera Minecraft.
4. Bezpieczne budowanie z kontrolą ekwipunku, postępem, pauzą i anulowaniem.
5. Kolejne adaptery i funkcje. Docelowe 500+ funkcji ma być wdrażane i testowane etapami; nie są one jeszcze dostępne w tej wersji.

Kompatybilność z różnymi wersjami i loaderami (Fabric, Forge, NeoForge) wymaga osobnych adapterów. Nie jest obiecywana w wersji 0.1.0.
