# excel-strecke-georef

Java-Konsolenanwendung. Sie liest dieselbe Excel-Datei wie [excel-strecke-geojson](https://github.com/WernerScholtes/excel-strecke-geojson): Blatt `Konfiguration`, Kopfzeile 3, Spalten `Standort`, `PermanentId`, `Latitude`, `Longitude`, `Strecke`.

In den GeoService gehen nur die Koordinaten. Jede Koordinate wird wie in [excel-bkm-geo](https://github.com/WernerScholtes/excel-bkm-geo) mit `convertLocations` (`POST /locations`) umgewandelt:

- Source: `de.e21x.data.location.GeographicalReference$Bean` (GeographicalReference-Locations)
- Target: `de.e21x.data.location.MultiReference` (MultiReference-Locations)

Aus jeder Target-Location wird die GeographicalReference-Location übernommen und als eine GeoJSON-Datei geschrieben.

## Voraussetzungen

- JDK 8 oder neuer
- Maven 3.9+

## Konfiguration

Werte in `src/main/resources/application.properties` oder in einer lokalen `application.properties` im Arbeitsverzeichnis. Keine Secrets ins Repository legen.

- `excel.input` / `excel.output`
- Keycloak Client Credentials (`keycloak.token-url`, `keycloak.client-id`, `keycloak.client-secret`)
- GeoService-Basis-URL (`geo.base-url`)

## Start

```powershell
mvn -q exec:java "-Dexec.args=C:\pfad\datei.xlsx C:\pfad\target-locations.geojson"
```

Ohne Ausgabedatei entsteht `geojson/target-locations.geojson` neben der Excel-Datei.

```powershell
mvn -q package
java -jar target/excel-strecke-georef-1.0.0.jar C:\pfad\datei.xlsx C:\pfad\target-locations.geojson
```

## Request

```json
{
  "targetType": "de.e21x.data.location.MultiReference",
  "locations": [
    {
      "@type": "de.e21x.data.location.GeographicalReference$Bean",
      "geometry": {
        "type": "Point",
        "coordinates": [6.083887, 50.775346]
      }
    }
  ]
}
```

Koordinaten stehen in GeoJSON-Reihenfolge: Länge, dann Breite. Die Antwort ist ein Array von MultiReference-Locations. Enthaltene GeographicalReference-Locations werden Features der Ausgabedatei. `properties` enthalten `Standort`, `PermanentId` und `Strecke` aus der Excel-Zeile.
