# Minecraft Boty

Proste boty do Minecraft Java Edition napisane w JavaScript i Mineflayer.

## Wymagania
- Node.js 18 lub nowszy
- Serwer Minecraft Java Edition, na którym możesz legalnie używać botów

## Instalacja
```bash
npm install
```

## Konfiguracja
Skopiuj `config.example.json` do `config.json` i ustaw adres serwera, port, nick oraz wersję Minecraft.

- `auth: "offline"` — tylko dla serwerów skonfigurowanych do logowania offline.
- `auth: "microsoft"` — logowanie kontem Microsoft; Mineflayer poprowadzi autoryzację.
- Nie publikuj danych logowania ani prywatnego pliku `config.json`.

## Uruchamianie
```bash
node index.js afk
node index.js chat
node index.js follow
node index.js guard
```

Boty:
- **afk** — stoi i co kilka sekund lekko zmienia kierunek patrzenia.
- **chat** — odpowiada na `!bot` i `!ping`.
- **follow** — podąża za graczem wskazanym w `targetPlayer`.
- **guard** — obserwuje otoczenie i informuje na czacie, gdy inny gracz podejdzie blisko. Nie atakuje.

Używaj botów zgodnie z zasadami serwera. Administrator może zabronić botów.
