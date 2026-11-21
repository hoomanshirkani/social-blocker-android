#!/usr/bin/env bash
# ساخت کلید آپلود گوگل‌پلی — فقط یک بار، روی کامپیوتر خودت اجرا کن.
# Creates the Google Play upload key. Run once on your own computer.
#
#   bash tools/make-upload-key.sh
#
# رمز را خودت انتخاب می‌کنی (keytool می‌پرسد). فایل‌ها در ~/socialblocker-keys ساخته می‌شوند
# و هرگز نباید در git قرار بگیرند.
set -euo pipefail
command -v keytool >/dev/null || { echo "keytool not found. Install a JDK: sudo apt install openjdk-17-jdk-headless"; exit 1; }

DIR="$HOME/socialblocker-keys"
mkdir -p "$DIR" && chmod 700 "$DIR"
KS="$DIR/upload.jks"
[ -e "$KS" ] && { echo "⚠️  $KS already exists — not overwriting."; exit 1; }

echo "== Choose a strong password (store it in your password manager) =="
keytool -genkeypair -v -keystore "$KS" -storetype JKS -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Social Blocker, O=Hooman, C=DE"

base64 -w0 "$KS" > "$DIR/upload.jks.b64"
chmod 600 "$KS" "$DIR/upload.jks.b64"

cat <<MSG

✅ Done.  Files in $DIR
   upload.jks       ← BACK THIS UP (USB stick / password manager). Never commit it.
   upload.jks.b64   ← paste into the GitHub secret UPLOAD_KEYSTORE_BASE64

GitHub → repository → Settings → Secrets and variables → Actions → New repository secret:
   UPLOAD_KEYSTORE_BASE64   = contents of upload.jks.b64   (xclip -sel clip < $DIR/upload.jks.b64)
   UPLOAD_STORE_PASSWORD    = the password you just chose
   UPLOAD_KEY_ALIAS         = upload
   UPLOAD_KEY_PASSWORD      = the same password (JKS uses one password for both)

Then push any commit — the release will contain SocialBlocker-vX.Y.Z-play.aab
MSG
