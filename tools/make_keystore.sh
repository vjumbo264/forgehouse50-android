#!/usr/bin/env bash
# Creates a release keystore for CI signing. Run locally (or in Termux) once per app,
# then store the base64 + passwords as GitHub Actions secrets (see release.yml).
# Usage: bash tools/make_keystore.sh <keystore_password> <key_password>
set -e
PW="${1:?keystore password}"; KP="${2:?key password}"
mkdir -p keystore
keytool -genkeypair -v -keystore keystore/release.jks -alias forgebuild \
  -keyalg RSA -keysize 2048 -validity 10000 -storepass "$PW" -keypass "$KP" \
  -dname "CN=ForgeBuild App, OU=ForgeBuild, O=ForgeBuild, C=US"
echo "base64 (store this as GitHub secret KEYSTORE_BASE64):"
base64 -w0 keystore/release.jks; echo
