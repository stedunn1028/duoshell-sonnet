# Signing

A debug APK must be signed with the *same* certificate every build, otherwise Android refuses to update in place.
No key is ever committed (`*.jks`, `*.keystore` are git-ignored). The workflow chooses, in order:

1. **Repository secrets (recommended, permanent):** `KEYSTORE_B64` (base64 of the keystore), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
2. **Fallback:** a key generated on first run and kept in the GitHub Actions cache (`duoshell-ci-key-v1`). If that cache is ever evicted the certificate changes; uninstall the old build before installing the new one.

To pin a permanent key from your phone: run the workflow manually (**Actions → Build → Run workflow**) with `export_keystore` ticked. It uploads a 1-day artifact containing `KEYSTORE_B64.txt` and `passwords.txt`; copy them into the four secrets, then delete the artifact. Treat that artifact as sensitive.
The certificate SHA-256 is printed in each run's summary.
