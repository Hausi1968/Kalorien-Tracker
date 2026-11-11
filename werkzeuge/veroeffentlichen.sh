#!/usr/bin/env bash
# Baut die signierte App und legt sie mit latest.json in den Zweig "updates".
# Die installierten Apps finden das Update dort beim nächsten Start (höchstens einmal am Tag)
# oder sofort über Profil → "Nach Updates suchen".
# Aufruf: werkzeuge/veroeffentlichen.sh "Neu: ... (Text für den Update-Dialog)"
set -euo pipefail
cd "$(dirname "$0")/.."
NOTIZEN="${1:?Bitte Text für die Update-Notizen angeben}"
[ -f keystore.properties ] || { echo "keystore.properties fehlt: ohne eigenen Schlüssel keine Updates"; exit 1; }

./gradlew assembleRelease -q || gradle assembleRelease -q
VERSION=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
CODE=$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts)
APK="Kalorien-Tracker-$VERSION.apk"

ARBEIT=$(mktemp -d)
if git ls-remote --exit-code --heads origin updates >/dev/null 2>&1; then
  git fetch -q origin updates
  git worktree add -q "$ARBEIT" origin/updates
  (cd "$ARBEIT" && git checkout -q -B updates)
else
  git worktree add -q --detach "$ARBEIT"
  (cd "$ARBEIT" && git checkout -q --orphan updates && git rm -rqf . )
fi
cp app/build/outputs/apk/release/app-release.apk "$ARBEIT/$APK"
python3 - "$ARBEIT/latest.json" "$VERSION" "$CODE" "$APK" "$NOTIZEN" <<'PY'
import json, sys
p, version, code, apk, notizen = sys.argv[1:]
json.dump({"version": version, "versionCode": int(code), "apk": apk, "notizen": notizen},
          open(p, "w"), ensure_ascii=False, indent=2)
PY
# Nur die letzten 3 APKs behalten, damit das Repository klein bleibt
(cd "$ARBEIT" && ls -1t Kalorien-Tracker-*.apk | tail -n +4 | xargs -r git rm -q --cached --ignore-unmatch && ls -1t Kalorien-Tracker-*.apk | tail -n +4 | xargs -r rm -f)
(cd "$ARBEIT" && git add -A && git commit -q -m "Version $VERSION" && git push -q -u origin updates)
git worktree remove --force "$ARBEIT"
echo "Veröffentlicht: $VERSION (Code $CODE)"
