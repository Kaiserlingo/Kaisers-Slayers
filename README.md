# Kaiser's Slayers

Slayer-Mod für **Hypixel SkyBlock** · Minecraft **26.2** · **Fabric**

## Menü: `/ks`
`/ks` öffnet das Menü. Jede Option hat einen Tooltip (Maus drüberhalten).

**Allgemein**
- **Tracker & Profit:** Profit-Tracker (Live-Bazaar-Preise), Drop-Tracker, Level-Prognose, Kill-Zeit im Chat, Preis-Modus
- **Alerts:** Boss-Spawn-Alert, *Boss spawnt gleich* (ab 75–95 % Fortschritt), Miniboss-Alert + kurzzeitiger **Waypoint-Pfeil**, Boss-Zeitwarnung, Quest-Failed, Drop-Alert, Sounds
- **HUD:** Position, Größe, Hintergrund, Waypoint-Pfeil, Boss-Panel
- **Gambling-Animation:** Stil, Dauer, Auslöser, Jackpot-Grenze, Größe, Sounds, Vorschau
- **Session zurücksetzen**

**Ein Menü pro Slayer.** Für jeden Slayer gibt es Tracking, Boss-Highlight (Farbe wählbar), Boss-Info-Panel, Miniboss-Highlight, Gambling an/aus, Low-HP-Alert und Gambling-Vorschau. Dazu kommen diese eigenen Funktionen:

| Slayer | Eigene Funktionen |
|---|---|
| Revenant Horror | TNT-Warnung (Atoned Horror T5), TNT-Highlight, Heal-Erkennung |
| Tarantula Broodfather | Sprung-Warnung, Brood-Zähler, Brood-Highlight |
| Sven Packmaster | Pup-Highlight, Pup-Zähler (Boss unverwundbar), Pup-Alert |
| Voidgloom Seraph | **Beacon-Overlay** (in der Hand / im Flug / platziert + Pfeil + Timer), Beacon-Alert, **Skull-Overlay**, Skull-Zähler, Hits-Anzeige, Laser-Timer, Phasen-Farbe |
| Inferno Demonlord | **Dagger-Anzeige** (benötigte Attunement + richtiger Dagger, Warnung bei falschem), Attunement-Farbe, Fire-Pillar-Timer + Alert, Demon-Highlight |
| Riftstalker Bloodfiend | Steak-Stake-Alert (≤ 20 % HP), Twinclaws-Alert, Twinclaws-Timer, Ichor/Spring-Highlight |

## Gambling-Animation
Wenn du einen Boss tötest, läuft eine Walze (Kisten-Öffnung) oder eine Slot-Machine mit 3 Walzen. Die Animation **landet immer auf deinem echten besten Drop** dieses Kills. Kommt kein seltener Drop, zeigt sie „nur Common Drops“. Der Jackpot-Effekt kommt ab INSANE DROP oder ab einem einstellbaren Coin-Wert.

**Eigene Items** für die Walze kannst du in `config/kaisers_slayers.json` unter `gamblingExtraItems` eintragen. Format: `"Itemname|Stufe"`, Stufe 0 bis 5, zum Beispiel:
```json
"gamblingExtraItems": { "zombie": ["Warden Heart|5", "Mein Lieblingsitem|3"] }
```

## Befehle
`/ks` Menü · `/ks help` alle Befehle · `/ks stats` · `/ks lifetime` · `/ks reset` · `/ks hud pos <x> <y>` · `/ks hud scale <0.5–3>` · `/ks toggle <…>` · `/ks prices` · `/ks test`

Die Mod zeigt nur Informationen an. Sie klickt, bewegt und automatisiert nichts.

## Gut zu wissen
- **Glow-Highlights** nutzen den Minecraft-Glow-Umriss. Sollte das Highlight einmal nicht funktionieren, stürzt das Spiel trotzdem nicht ab: Die Mixins sind optional eingestellt.
- **Waypoints** werden als Pfeil mit Entfernung oben in der Bildschirmmitte angezeigt, nicht als Lichtstrahl in der Welt.
- Die Erkennung von Namensschildern (Pillar, Twinclaws, Skulls, Ichor) ist nach dem Vorbild bestehender Mods gebaut. Sie muss im Spiel geprüft werden, weil Hypixel Texte ändern kann.

## Mod bauen
**GitHub (empfohlen):**
1. Neues Repository anlegen.
2. Alle Dateien hochladen, auch die Ordner `.github` und `gradle`.
3. Reiter **Actions** öffnen. Wenn der Build fertig ist, unter **Artifacts** die `.jar` herunterladen.

**Lokal (Windows):**
1. JDK 25 installieren.
2. `gradlew.bat build` ausführen.
3. Die Mod liegt in `build\libs\`.

## Installieren
Fabric Loader für 26.2 installieren. Dann **Fabric API** und `kaisers-slayers-1.1.0.jar` in den `mods`-Ordner legen.
