# Changelog

## 2.5.0 – erstes öffentliches Release
- **Updates im Spiel**: Beim Start wird auf GitHub nach einer neuen Version gesucht. Gibt es eine, erscheint im Chat „Update available“ mit **[UPDATE NOW]** – ein Klick lädt die neue Version aus dem offiziellen Release, prüft sie (Prüfsumme, Mod-ID) und ersetzt die alte .jar. Nach einem Neustart ist sie aktiv. Auch über den Button unten im `/ks`-Menü oder `/ks update`.
- Abschaltbar unter HUD → Check for Updates

## 2.4.0
- **Neue Schrift**: Menü und alle HUDs in serifenloser Schrift (Roboto, OFL-Lizenz); abschaltbar unter HUD → Clean Font
- **Voidgloom**: Beacons (Hand/Wurf/platziert) und Nukekubi-Skulls leuchten in EINER grellen Farbe (Standard Neon-Grün) – Glow plus pulsierender, gefüllter Kasten durch Wände; Farbe im Voidgloom-Menü einstellbar
- **Mehrere Drops**: Gambling nimmt immer den wertvollsten Drop; Drops, die erst während des Drehens im Chat kommen, werden noch übernommen; Wartezeit vor dem Drehen 2,2 s
- **Profit Tracker erkennt viel mehr Items**: Drops, die ohne Chat-Nachricht ins Inventar gehen (Derelict Ashe, Null Sphere, Blaze Rod …), werden gezählt; Menüs/Kisten werden ignoriert, Chat-Drops nicht doppelt gezählt; zweite Lowest-BIN-Quelle
- **Magic Find** (und Pet Luck) wird aus Itemnamen entfernt; „x2“-Mengen werden erkannt
- **Session-Auto-Reset**: nach 15 min ohne Slayer startet automatisch eine neue Session (einstellbar, 0 = aus)
- **Profit-Tracker-Design** wie im Referenzbild: gelber Titel, Anzahl · Item in echter Seltenheits-Farbe · Wert-Spalte, frisch gedroppte Items kurz grün, darunter Spawn Costs, Bosses killed, Profit, Profit/h, Uptime

## 2.3.0
- **Carry-Tracker** für Carry-Verkäufer:
  - `/ks carry add <Spieler> <Anzahl> [Preis pro Carry]` (z. B. `/ks carry add Technoblade 10 2.5m`), dazu `+`, `-`, `set`, `price`, `paid`, `remove`, `clearfinished`, `clear`, `list`
  - Erkennt die Bosse deiner Kunden („Name's SLAYER BOSS“) und zählt automatisch hoch, wenn sie sterben: 0/10 → 1/10 → …; abgelaufene Bosse (Quest failed) zählen nicht
  - **Carry-HUD** mit Spielerkopf, Fortschritt, Balken, Live-Boss-Info (HP, Zeit), offenem Betrag; Kompakt-Modus, Akzentfarbe, im HUD-Editor verschiebbar
  - Kunden-Bosse leuchten in eigener Farbe, Alert beim Spawn, Chat-Zeile pro Carry mit **[Party]**-Knopf (legt die Nachricht nur in die Chat-Leiste), Alert + Sound wenn fertig, Ø-Kill-Zeit pro Kunde
  - Eigene Menü-Seite „Carries“
- **Slayer-HUD neu**: Kopf mit Level-Chip, Status mit schlankem Balken, 2-spaltiges Statistik-Raster, Level-/RNG-Zeilen

## 2.2.0
- **Mob Kill Coins** im Profit-Tracker: Coins, die Mobs während des Quests in die Purse geben (Verkäufe, Bank und Menüs werden ignoriert)
- **Nur eigene Minibosse**: Highlight, Strahl, Tracer und Waypoint nur für Minibosse aus deiner Quest (Zuordnung über die Chat-Meldung „SLAYER MINI-BOSS … HAS SPAWNED!“), abschaltbar
- **Profit-HUD im Inventar bearbeitbar** statt eigenem Panel: Items anklicken = ausblenden, SESSION/TOTAL-Chip, Instant Sell ↔ Sell Offer, Reset, Karte ziehen = verschieben, Mausrad = Größe
- **Neues Tracker-Design**: Tabelle mit Anzahl · Item (in Drop-Farbe) · Wert, darunter Quest Cost, Bosses Killed, Profit (volle Zahl), Profit/Hour, Uptime; Breite passt sich an
- **Aufgeräumtes Menü**: Einstellungen in Abschnitten mit je einer Karte statt vieler einzelner Kästen

## 2.1.0
- **Boss-Erkennung repariert**: Hypixel zeigt jetzt „<Name>'s SLAYER BOSS“ statt „Spawned by“ – dein eigener Boss wird wieder sicher erkannt (Timer, Overlays, Glow)
- **Miniboss-Tracer**: Linie vom Spieler direkt zum Miniboss (Farbe einstellbar, pro Slayer schaltbar)
- **Neuer Farbwähler** für jede Farbe: Farbfeld + Farbton-Leiste, Hex-Eingabe, 16 Schnellfarben, alt/neu-Vorschau, „Default“; Änderungen sofort live in der Welt
- „Phase Color“ (Voidgloom) ist jetzt standardmäßig aus, damit deine eigene Boss-Farbe gilt
- **Profit-Tracker im Inventar**: neben Inventar und Kisten, mit Buttons für Session/Total, Instant Sell/Sell Offer und Reset (mit Bestätigung)
- **[CALL MADDOX]**: klickbare Chat-Nachricht bei Quest Failed (führt erst beim Klick `/call slayer` aus; nicht im Rift)
- **Während deines Bosses** werden Waypoints, Strahlen, Tracer und Highlights fremder Bosse und Minibosse ausgeblendet
- **Kein Drop-Spoiler**: Drops am Boden werden standardmäßig nicht mehr hervorgehoben (und nie während der Gambling-Animation)
- **Gambling**: alle Stile teilen sich dieselbe Bildschirmmitte, Ergebnis-Karte passt sich dem Inhalt an, Texte unter Icons sauber zentriert, Partikel hinter dem Text, Hinweis zeigt die echte Taste
- **Sprache**: alle Texte im Spiel (Menü, HUD, Alerts, Chat, Befehle) auf Englisch mit SkyBlock-Begriffen

## 2.0.0
- **Neues Menü** (/ks): eigene Oberfläche mit Seitenleiste, Suche, Schaltern, Reglern, Farbwahl, Übersicht aller Slayer, Profit-Tabelle
- **HUD-Editor**: HUDs per Drag & Drop verschieben, mit dem Mausrad skalieren
- **Erkennung überarbeitet**: T5-Namen (Atoned Horror, Conjoined Brood), Text-Displays, Fallbacks (zuletzt geschlagener Boss), robusteres Scoreboard
- **Voidgloom**: Beacon in der Hand / Wurf (Yang Glyph) / platziert mit rotem Strahl und 5-s-Countdown, Skull-Overlay über Spielerköpfe, Laser-Timer (8 s)
- **Timer direkt am Boss**: Zeit, HP und Phase als Namensschild über dem Boss
- **Lichtstrahlen** über Minibossen (Farbe einstellbar), Boss-HP-Leiste
- **Profit-Tracker neu**: eigene Karte, Sack-Drops, pro Slayer, Session/Gesamt, Gewinn/h und pro Boss, Aatrox-Rabatt, Items ausblenden
- **Gambling im Vollbild**: Kisten-Öffnung, Slot-Machine, Glücksrad, Karten; HUD/Chat ab dem Kill ausgeblendet; Partikel, Konfetti, Blitz, Wackeln; überspringen mit U
- Neue Features: Cocoon-Alert, Mania-Alert, IMMUNE-Warnung, Holy-Ice-Timer, Steak (҉), Level-Up-Alert, Quest-Zeiten, fremde Bosse markieren, Drops am Boden, Miniboss-Chat-Erkennung, `/ks debug`, `/ks test` (Demo)
- Automatischer Spieltest mit Screenshots in GitHub Actions

## 1.1.0
- `/ks` öffnet ein eigenes Menü (Allgemein: Tracker & Profit, Alerts, HUD, Gambling + ein Menü pro Slayer)
- Alerts: „Boss spawnt gleich“, Miniboss-Alert mit kurzzeitigem Waypoint-Pfeil, Boss-Zeitwarnung (nutzt den Hypixel-Timer)
- Boss-Highlight (Farbe pro Slayer wählbar), Miniboss-Highlight, Boss-Info-Panel
- Eigene Funktionen für jeden Slayer (Beacon-/Skull-Overlay, Dagger-Anzeige, Fire-Pillar-Timer, Pups, TNT, Steak-Stake, Twinclaws, …)
- Gambling-Animation beim Boss-Kill (Walze oder Slots), landet auf dem echten Drop; anpassbar

## 1.0.0
- Slayer-HUD, Quest-Tracker, Kill-/Spawn-Zeiten, Profit-Tracker mit Live-Bazaar-Preisen, Drop-Tracker, Level-Prognose, RNG Meter, Gesamtstatistik
