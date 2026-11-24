# Kaiser's Slayers

Slayer-Mod für **Hypixel SkyBlock** · Minecraft **26.2** · **Fabric** · Sprache im Spiel: **Englisch** (SkyBlock-Begriffe)

## Menü: `/ks`
Eigenes Menü mit Seitenleiste, Suche, Schaltern, Reglern und Farbwähler (Farbfeld, Farbton, Hex, Schnellfarben):
- **Overview** – alle Slayer auf einen Blick (Level, Kills, Rekord, Gesamt-Profit, RNG Meter)
- **Alerts** – Boss spawnt / spawnt gleich, Minibosse, Zeitlimit, Quest failed + klickbares **[CALL MADDOX]**, Drops, Alert-Größe
- **World & Highlights** – fremde Bosse/Minibosse während deines Bosses ausblenden, Lichtstrahlen, Miniboss-Tracer, Farben, fremde Bosse markieren
- **Tracker** – Kill-/Quest-Zeiten im Chat, Level-Prognose, Debug-Datei
- **Profit Tracker** – Tracker im Inventar, Session/Total, Sack-Drops, Preis-Modus, Aatrox-Rabatt, Item-Tabelle (Klick = ausblenden)
- **HUD** – HUD-Editor (Drag & Drop + Mausrad), Boss-Panel, Waypoint-Pfeil, Akzentfarbe
- **Gambling** – Animation, Dauer, Effekte, Farben, Vorschau
- **Ein Menü pro Slayer** mit allgemeinen und eigenen Funktionen

## Für jeden Slayer
Boss-Highlight (Farbe wählbar) · **Timer direkt am Boss** (Zeit, HP, Phase als Schild in der Welt) · Boss-HP-Leiste · Boss-Info-Panel · Miniboss-Highlight + **Lichtstrahl** + **Tracer-Linie** · Drops am Boden (aus Spoiler-Gründen standardmäßig aus) · Low-HP-Alert · Gambling an/aus

| Slayer | Eigene Funktionen |
|---|---|
| Revenant Horror | TNT-Warnung + Highlight (Atoned Horror), Heal-Erkennung |
| Tarantula Broodfather | Sprung-Warnung, Brood-Zähler + Highlight, Cocoon-Alert (Conjoined Brood) |
| Sven Packmaster | Pup-Highlight, Pup-Zähler, Pup-Alert |
| Voidgloom Seraph | **Beacon-Overlay** (Hand → Wurf/Yang Glyph → platziert, mit Strahl, Pfeil und 5-s-Countdown), **Skull-Overlay**, Hits, Laser-Timer (8 s), Phasen-Farbe (optional) |
| Inferno Demonlord | **Dagger-Anzeige** (richtige Attunement/Dagger, Warnung bei falschem), IMMUNE-Warnung, Fire-Pillar-Timer + Alert, Demons |
| Riftstalker Bloodfiend | Steak-Stake-Alert (҉ / ≤ 20 %), Holy-Ice/Twinclaws-Alert + Timer, Mania-Alert (grüner Boden), Ichor/Spring-Highlight |

## Profit-Tracker
Eigene Karte mit allen Drops (Chat **und** `[Sacks]`), Live-Bazaar-Preisen (Lowest BIN für AH-Items), Quest-Kosten, **Gewinn**, Gewinn/h und Gewinn pro Boss – getrennt nach Slayer, umschaltbar Session ↔ Total (`/ks profit`).
Auch **neben Inventar und Kisten** – mit Buttons für Session/Total, Instant Sell/Sell Offer und Reset Session.

## Gambling-Animation
Vollbild beim Boss-Kill: **Kisten-Öffnung, Slot-Machine, Glücksrad oder Karten** (oder zufällig). Ab dem Kill sind HUD, Chat, Titel und Scoreboard ausgeblendet – kein Spoiler. Landet immer auf deinem echten besten Drop, mit Lichtstrahlen, Partikeln, Konfetti beim Jackpot, Blitz und Wackeln (alles einstellbar). Überspringen mit **U** (änderbar in *Steuerung*).

Eigene Items für die Walzen: `config/kaisers_slayers.json` → `gamblingExtraItems`, z. B. `"zombie": ["Warden Heart|5"]` (Stufe 0–5).

## Befehle
`/ks` Menü · `/ks hud` HUD-Editor · `/ks profit` Session/Gesamt · `/ks gamble <case|slots|wheel|cards>` Animation testen · `/ks test` Demo-Daten (HUDs außerhalb von Hypixel ansehen) · `/ks debug` Debug-Datei · `/ks lifetime` · `/ks reset` · `/ks help`

## Etwas funktioniert nicht?
`/ks debug` neben dem Boss ausführen. Die Datei `config/kaisers_slayers_debug.txt` enthält alle Namensschilder in der Nähe – damit lässt sich die Erkennung genau anpassen.

Die Mod zeigt nur Informationen an. Sie klickt, bewegt und automatisiert nichts.

## Bauen & Installieren
Jeder Push baut automatisch (GitHub → **Actions** → Artifacts). Tag `v2.1.0` pushen → Release mit `.jar`.
Installieren: Fabric Loader 26.2 + **Fabric API** + `kaisers-slayers-2.0.0.jar` in den `mods`-Ordner.

Der **Visual Test** (Actions) startet das echte Spiel, lädt eine Welt und macht Screenshots von HUD, Menü, Strahlen, Boss-Timer und allen Gambling-Animationen.
