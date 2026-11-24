# Changelog

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
