# Kalorien-Tracker (Android)

Eigene Kalorien-App, Version 0.1 (Durchstich). Alles läuft lokal auf dem Handy, ohne Konto und ohne Server.

## Schnellster Weg: fertige App installieren

Im Ordner liegt `Kalorien-Tracker-0.2.apk`. Diese Datei aufs Handy bringen (z. B. per USB-Kabel, Google Drive oder als Mail an dich selbst), auf dem Handy antippen und „Installieren“ wählen. Beim ersten Mal fragt Android, ob die App (z. B. „Eigene Dateien“ oder „Drive“) Apps installieren darf: einmal erlauben. Neue Versionen einfach drüberinstallieren, deine Einträge bleiben erhalten.

## App selbst bauen (Android Studio)

1. **Android Studio** öffnen (falls noch nicht installiert: developer.android.com/studio).
2. *File → Open…* und den Ordner **Schreibtisch/Kalorien-Tracker** wählen (den Ordner, in dem `settings.gradle.kts` liegt).
3. Beim ersten Öffnen lädt Android Studio Gradle und die Bibliotheken. Das dauert ein paar Minuten. Falls es fragt, das fehlende Android-SDK 35 installieren lassen.
4. Handy per USB anschließen. Am Handy einmalig: *Einstellungen → Telefoninfo → Softwareinformationen → 7× auf Buildnummer tippen*, dann unter *Entwickleroptionen* **USB-Debugging** einschalten.
5. Oben in Android Studio das Handy auswählen und auf den grünen **▶ Run**-Knopf drücken.

## Was schon geht

- Beim ersten Start: Pflicht-Rechner mit deinen eigenen Daten (Geschlecht, Geburtsdatum, Größe, Gewicht, Aktivität, Ziel, Tempo). Ergebnis: Tagesziel, Grundumsatz, Gesamtbedarf, Zieldatum, Warnungen.
- Tagebuch: Wochenleiste, Bilanz „Ziel − gegessen + verbrannt = übrig“, Makros, sechs Mahlzeiten mit Uhrzeit-Fenstern, Einträge löschen (mit Rückgängig).
- Suche: 67 Grundnahrungsmittel als Startdaten, „Zuletzt gegessen“, Schnelleintrag mit einem Tipp.
- Eintragen: Menge und Portion wählen, kcal/kJ und Makros live, Mahlzeit wählbar, Nährwerttabelle pro Portion und pro 100 g.
- Profil: eigene Daten ansehen, „Ziel neu berechnen“.
- Barcode scannen: erst eigene Datenbank, dann Open Food Facts. Gefundene Produkte werden gespeichert und nie zweimal abgefragt. Unbekannter Barcode führt direkt zu „Produkt anlegen“ mit eingetragenem Barcode.
- Online suchen in Open Food Facts, alle Treffer bleiben danach auch offline in der Suche.
- Produkt anlegen mit Nährwerten pro 100 g/ml (kcal oder kJ), optional Portion; Warnung, wenn kcal nicht zu den Makros passen.
- Eintrag antippen: Menge, Portion, Tag, Mahlzeit und Uhrzeit ändern oder löschen. Eigene Portionsgrößen hinzufügen.

## Was als Nächstes kommt

Großer Lebensmittel-Katalog, Gewicht/Körperwerte, Health Connect (Samsung Health), Kalorien-Ring und Diagramme.

## Aufbau

- `core/` reine Rechenlogik (Bedarfsrechner, Nährwerte, Mahlzeiten, Suche, Format) mit Unit-Tests
- `app/` die Android-App (Jetpack Compose, Room-Datenbank)
- `app/src/main/assets/startdaten.json` Startdaten (gerundete Näherungswerte)
- `app/src/main/res/values/strings.xml` alle Texte (Deutsch)
