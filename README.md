# excel-strecke-georef

Java-Programm, das Koordinaten aus einer Strecken-Excel liest, jede Koordinate mit einem `convertLocation`-Request umwandelt und die geografische Lage der Zielorte als GeoJSON schreibt.

Je Request gilt:

- Source: `GeographicalReference-Locations`
- Target: `MultiReference-Locations`

Aus jeder Target-Location wird die `GeographicalReference-Location` übernommen. Liegen mehrere Target-Locations vor, wird jede davon ein GeoJSON-Punkt.

## Voraussetzungen

- Java 21
- Maven 3.8 oder neuer

## Bauen

```bash
mvn -q package
```

Das ausführbare Jar liegt unter `target/excel-strecke-georef-1.0.0.jar`.

## Ausführen

```bash
java -jar target/excel-strecke-georef-1.0.0.jar \
  --excel data/strecke.xlsx \
  --output output/target-locations.geojson \
  --endpoint https://example.test/convertLocation
```

Ein Bearer-Token kann über `--token` oder die Variable `CONVERT_LOCATION_TOKEN` gesetzt werden. Der Endpunkt kann alternativ in `CONVERT_LOCATION_ENDPOINT` stehen.

Ohne Dienst, nur zum Prüfen von Excel-Lesen und GeoJSON-Schreiben:

```bash
java -jar target/excel-strecke-georef-1.0.0.jar --mock
```

## Excel

Es wird das erste Blatt gelesen. Die Kopfzeile darf in den ersten 25 Zeilen stehen. Weitere Spalten bleiben ungenutzt.

Erkannte Koordinatenspalten:

- ein Punkt je Zeile: `Breite`/`Länge`, `Latitude`/`Longitude` oder `Lat`/`Lon`
- zwei Punkte je Zeile: `Breite Von`/`Länge Von` und `Breite Nach`/`Länge Nach`

Eine Spalte `Länge` wird nicht als Koordinate gelesen, sobald `Länge Von` oder `Länge Nach` vorhanden sind. So bleibt eine Abschnittslänge von den Koordinaten getrennt. Deutsche Dezimalkommas in Textzellen werden gelesen.

`data/strecke.xlsx` ist eine Beispielstrecke Aachen–Köln-West auf der A4. Die Originaldatei aus der App excel-strecke-geojson kann mit `--excel` übergeben werden. Weichen die Spaltennamen ab, lassen sie sich festlegen:

```bash
java -jar target/excel-strecke-georef-1.0.0.jar \
  --excel strecke.xlsx \
  --lat-column Breite \
  --lon-column Laenge \
  --endpoint https://example.test/convertLocation
```

## convertLocation

Pro Koordinate geht ein POST mit `Content-Type: application/json` an den Endpunkt:

```json
{
  "source": "GeographicalReference-Locations",
  "target": "MultiReference-Locations",
  "location": {
    "type": "GeographicalReference-Location",
    "latitude": 50.775346,
    "longitude": 6.083887
  }
}
```

Ausgewertet wird der Target-Teil der Antwort, als JSON oder XML. Je Target-Location wird das Objekt vom Typ `GeographicalReference-Location` gelesen. Eine mitgeschickte Source-Location wird nicht in die GeoJSON-Datei übernommen.

```json
{
  "targetLocations": [
    {
      "type": "MultiReference-Location",
      "locations": [
        { "type": "BkmReference-Location", "road": "A4", "kilometer": 12.4 },
        { "type": "GeographicalReference-Location", "latitude": 50.776, "longitude": 6.084 }
      ]
    }
  ]
}
```

## GeoJSON

`output/target-locations.geojson` ist eine `FeatureCollection` aus Punkten. Die Koordinaten stehen in der Reihenfolge Länge, Breite. `properties` enthalten die Excel-Zeile, die Rolle (`punkt`, `von` oder `nach`) und den Index der Target-Location.

## Tests

```bash
mvn test
```
