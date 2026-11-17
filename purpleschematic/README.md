# PurpleSchematic

Desktopowy Minecraft Control Center + adapter Fabric.

## Aplikacja desktopowa

Wymagania: Node.js 20 lub nowszy.

```bash
cd purpleschematic
npm install
npm start
```

## Pierwsza integracja Minecraft

Pierwszy adapter jest przygotowany dla **Minecraft Java 1.20.1 + Fabric + Fabric API**. Jest to adapter po stronie serwera, więc działa w świecie singleplayer (zintegrowany serwer) oraz na serwerze, na którym administrator zainstalował mod.

### Budowanie adaptera

Wymagania: JDK 17 i Gradle.

```bash
cd purpleschematic/adapter-fabric
gradle build
```

Gotowy plik JAR znajdziesz w `adapter-fabric/build/libs/`. Skopiuj go do folderu `mods` instancji Minecraft 1.20.1 z Fabric API, uruchom grę i wejdź do świata.

W czacie wpisz `/purpleschem status`. Aplikacja desktopowa sprawdza połączenie z adapterem pod adresem `127.0.0.1:8766`.

### Import i budowanie

- Obsługiwany import: Sponge `.schem` z tagami `Palette` i `BlockData` (VarInt).
- Limit pojedynczego schematu w tej wersji: 100 000 bloków.
- Ustawiasz tryb, współrzędne i obrót w aplikacji, a następnie klikasz **Buduj**.
- Adapter umieszcza bloki stopniowo, po jednym na tick.
- Creative: bez zużywania przedmiotów.
- Survival: pobiera odpowiedni przedmiot z ekwipunku przed umieszczeniem bloku; zatrzymuje pracę, gdy brakuje materiału.
- Wybrany tryb musi odpowiadać rzeczywistemu trybowi gracza w Minecraft.
- Wymagany jest gracz z uprawnieniami operatora (poziom 2). Adapter nie omija uprawnień serwera.

## Ograniczenia wersji 0.2.0

- Legacy `.schematic` nie jest jeszcze obsługiwany przez parser.
- Analiza wykrywa brakujące mody/bloki i powiązane itemy, ale nie instaluje automatycznie brakujących modów. Mod musi być zgodny z Fabric 1.20.1 i zainstalowany po stronie serwera/świata.
- Obsługiwane są nazwy bloków z palety, ale ich właściwości (np. kierunek schodów, stan drzwi, połączenia redstone) nie są jeszcze odtwarzane — używany jest domyślny stan bloku.
- Obrót zmienia współrzędne, ale nie obraca jeszcze właściwości bloków.
- Nie ma jeszcze pauzy, wznowienia ani anulowania.
- Adapter jest tylko dla Fabric 1.20.1; Forge, NeoForge i inne wersje wymagają osobnych adapterów.
- Nie uruchomiono jeszcze kompilacji Gradle ani testu w prawdziwym świecie Minecraft, więc adapter jest dodany jako kod źródłowy do sprawdzenia, a nie jako potwierdzony działający plik JAR.

Nie instaluj moda na serwerze publicznym bez zgody administratora. Przed budowaniem sprawdź pozycję i zrób kopię świata.
