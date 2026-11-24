# Entwicklung – so ist die Mod aufgebaut

Diese Datei erklärt, wo was liegt und wie man neue Funktionen hinzufügt.

## Aufbau (`src/main/java/at/kaiser/slayers/`)

| Ordner / Datei | Aufgabe |
|---|---|
| `KaisersSlayers.java` | Einstiegspunkt: registriert HUDs, Events, Befehle, Tasten. Der Tick-Loop ruft Scanner und Tracker auf. |
| `slayer/SlayerTracker.java` | Erkennt Quest-Phasen aus Scoreboard und Chat, führt die Statistik. **Ohne Minecraft-Code → testbar.** |
| `slayer/SlayerType.java` | Alle Slayer: Boss-Name, Kosten, XP, Minibosse, Standardfarbe. |
| `world/WorldScanner.java` | Schaut sich die Entities an: eigener Boss, Minibosse, Slayer-spezifische Dinge. Befüllt `Highlights`, `Waypoints` und `BossState`. |
| `world/NameParser.java` | Liest Namensschilder (HP, Hits, Attunement, Timer, Pillar). **Testbar.** |
| `mixin/` | Glow-Umriss für markierte Entities. Die Mixins sind optional (`defaultRequire: 0`), ein Fehler darin bringt das Spiel nicht zum Absturz. |
| `hud/` | `SlayerHud` (Info-Fenster), `BossPanelHud` (Boss-Infos + Waypoint-Pfeile), `AlertOverlay` (große Einblendungen) |
| `gambling/` | Gambling-Animation und Loot-Listen |
| `gui/` | `/ks`-Menü. `Option` = ein Button, `OptionsScreen` baut daraus Seiten. |
| `config/` | `ModConfig` (JSON in `config/kaisers_slayers.json`), `SlayerFeature` (alle Features je Slayer) |
| `price/PriceService.java` | Bazaar-Preise (Hypixel API) + Lowest BIN |
| `command/SlayerCommands.java` | `/ks …` |

## Neues Slayer-Feature hinzufügen (Beispiel)

1. **`config/SlayerFeature.java`**: neuen Eintrag anlegen, z. B.
   `ZOMBIE_MEIN_FEATURE(SlayerType.ZOMBIE, "Mein Feature", "Beschreibung für den Tooltip", true),`
   → erscheint **automatisch** im Slayer-Menü.
2. **`world/WorldScanner.java`**: in der passenden `scanZombie(...)`-Methode die Erkennung einbauen und das Ergebnis in `BossState` speichern.
3. **`hud/BossPanelHud.java`**: im passenden `case` eine Zeile ins Boss-Panel schreiben.
4. Bei Bedarf einen Alert: `Feedback.alert("§c§lTITEL", "§7Untertitel", 1500, SoundEvents.ANVIL_LAND, 1.0f);`

## Neue allgemeine Einstellung

1. Feld in `config/ModConfig.java` anlegen (mit Standardwert).
2. In `gui/MainMenuScreen.java` → `Menus` als `Option.toggle(...)` oder `Option.cycle(...)` eintragen.

## Neuer Slayer (z. B. wenn Hypixel einen hinzufügt)

1. Eintrag in `slayer/SlayerType.java`.
2. Loot-Liste in `gambling/LootPool.java`.
3. Optional: eigene Features + `case` in `WorldScanner`/`BossPanelHud`.

## Bauen & Release

- Jeder Push baut automatisch (GitHub → **Actions**). Die `.jar` liegt beim Build unter **Artifacts**.
- **Release:** Version in `gradle.properties` erhöhen, `CHANGELOG.md` ergänzen, dann ein Tag pushen (z. B. `v1.2.0`). Daraus wird automatisch ein GitHub-Release mit der `.jar` erstellt.
- Lokal: JDK 25, dann `gradlew.bat build` (Windows) bzw. `./gradlew build`.
- Zum Testen im Spiel: `gradlew.bat runClient` startet Minecraft mit der Mod.

## Regeln

- **Nur anzeigen, nichts automatisieren.** Keine Klicks, Bewegungen oder Makros. Die Hypixel-Regeln erlauben nur reine Anzeige.
- SkyBlock-Begriffe bleiben Englisch (Bazaar, Slayer, Miniboss, …), Texte für Spieler auf Deutsch.
- Logik ohne Minecraft-Code (Tracker, NameParser, LootPool) möglichst in eigenen Klassen halten, damit man sie testen kann.
