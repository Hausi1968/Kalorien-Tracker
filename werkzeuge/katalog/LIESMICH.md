# Katalog aus Open Food Facts bauen

Daten: Open Food Facts, Lizenz ODbL (Datenbank) / DbCL (Inhalte). In der App unter Profil → Datenquellen genannt.

```bash
curl -sfL https://openfoodfacts-ds.s3.eu-west-3.amazonaws.com/en.openfoodfacts.org.products.csv.gz \
  | gzip -dc | python3 -I filter.py dach.jsonl          # ~1,5 GB Download, Produkte aus D/A/CH mit kcal
python3 -I baue.py dach.jsonl katalog.tsv.gz 100000      # plausible, deutschsprachige, beliebteste 100.000
cp katalog.tsv.gz ../../app/src/main/assets/katalog.tsv.gzip
```

Danach `KatalogImport.VERSION` hochzählen, sonst lesen bestehende Installationen die neue Datei nicht ein.
Die Endung bleibt `.gzip`, weil das Build-Werkzeug `.gz`-Dateien in assets beim Bauen entpackt.
