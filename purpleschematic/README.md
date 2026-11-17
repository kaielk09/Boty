# PurpleSchematic

Desktopowy Minecraft Control Center z adapterami serwerowymi.

## Aplikacja desktopowa

Wymagania: Node.js 20 lub nowszy.

```bash
cd purpleschematic
npm install
npm start
```

## Dostępne adaptery Minecraft

Każdy adapter jest osobnym modem. Nie instaluj jednocześnie adapterów Forge, NeoForge i Fabric w tej samej instancji Minecraft — wszystkie używają tego samego lokalnego portu `127.0.0.1:8766`.

| Loader | Minecraft | Java | Folder projektu |
|---|---:|---:|---|
| Fabric | 1.20.1 | 17 | `adapter-fabric` |
| Forge | 1.21.1 | 21 | `adapter-forge/1.21.1` |
| NeoForge | 1.21.1 | 21 | `adapter-neoforge/1.21.1` |
| Forge | 26.3 | 25 | `adapter-forge/26.3` |
| NeoForge | 26.3 | 25 | `adapter-neoforge/26.3` |

To są obecnie dodane profile bazowe. Nie oznaczają jeszcze potwierdzonej obsługi każdej wersji pośredniej, np. 1.21.4, 1.21.10 czy 26.1/26.2. Dla tych wydań trzeba dodać i skompilować osobne profile zgodne z ich wersjami loaderów i API.

## Budowanie adapterów

W każdym katalogu adaptera uruchom `gradle build`. Przykład dla NeoForge 1.21.1:

```bash
cd purpleschematic/adapter-neoforge/1.21.1
gradle build
```

JAR znajdziesz w `build/libs/`. Skopiuj go do folderu `mods` właściwej instancji Minecraft razem z wymaganymi bibliotekami danego loadera. Wersja Java musi odpowiadać wersji Minecrafta: Java 21 dla 1.21.x i Java 25 dla 26.3.

## Funkcje adapterów

- Lokalny most HTTP nasłuchuje wyłącznie na `127.0.0.1:8766`.
- `/mods` zwraca listę załadowanych modów i ich wersje.
- `/analyze` sprawdza identyfikatory bloków z palety schematu, np. `create:andesite_casing`, w rejestrach uruchomionej gry.
- `/build` przyjmuje zadanie budowania schematu.
- Creative nie zużywa przedmiotów; Survival wymaga przedmiotu przypisanego do bloku.
- Komenda `/purpleschem status` pokazuje stan adaptera.
- Budowanie wymaga gracza z uprawnieniami operatora poziomu 2 i zgodności wybranego trybu z trybem gracza.

## Ograniczenia

- Mod wymagany przez schemat musi być zainstalowany i załadowany na serwerze/świecie. PurpleSchematic nie instaluje automatycznie modów.
- W Survival adapter korzysta z `block.asItem()`; specjalne bloki wymagające innego przedmiotu lub receptury mogą wymagać osobnej obsługi.
- Parser obecnie obsługuje Sponge `.schem`; legacy `.schematic` nie jest jeszcze obsługiwany.
- Właściwości bloków (np. kierunek schodów, stany drzwi, połączenia redstone i konfiguracje maszyn) nie są jeszcze odtwarzane — ustawiany jest domyślny stan bloku.
- Obrót zmienia współrzędne, ale nie obraca właściwości bloków.
- Profile Forge/NeoForge są dodane jako kod źródłowy; nie należy uznawać ich za gotowe pliki JAR, dopóki GitHub Actions nie zakończy kompilacji i nie zostaną przetestowane w grze.

Przed budowaniem zrób kopię świata. Na serwerze publicznym instaluj adapter wyłącznie za zgodą administratora.
