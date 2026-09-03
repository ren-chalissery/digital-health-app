#!/usr/bin/env bash
#
# Creates the upload keystore Play Console expects for the first signed AAB.
# Run once, then copy keystore.properties.example to keystore.properties and fill in the
# passwords you choose below.

set -euo pipefail

readonly ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly KEYSTORE="$ROOT/upload-keystore.jks"

if [[ -f "$KEYSTORE" ]]; then
  echo "Already exists: $KEYSTORE" >&2
  exit 1
fi

read -r -s -p "Keystore password: " STORE_PASSWORD
echo
read -r -s -p "Key password (Enter to match keystore): " KEY_PASSWORD
echo
KEY_PASSWORD="${KEY_PASSWORD:-$STORE_PASSWORD}"

keytool -genkeypair \
  -keystore "$KEYSTORE" \
  -storepass "$STORE_PASSWORD" \
  -keypass "$KEY_PASSWORD" \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 9125 \
  -dname "CN=Simplicity Training, OU=Engineering, O=Simplicity, L=Sydney, ST=NSW, C=AU"

cat > "$ROOT/keystore.properties" <<EOF
storeFile=upload-keystore.jks
storePassword=$STORE_PASSWORD
keyAlias=upload
keyPassword=$KEY_PASSWORD
EOF
chmod 600 "$ROOT/keystore.properties"

echo
echo "Created:"
echo "  $KEYSTORE"
echo "  $ROOT/keystore.properties"
echo
echo "Back up the keystore and passwords somewhere safe. Losing them means you cannot"
echo "upload updates under the same Play App Signing arrangement."
echo
echo "For GitHub Actions, add repository secrets — see RELEASING.md."
