# Install and test on a Galaxy Z Fold 8 (phone only)

1. Push this project to a GitHub repository (branch `main`). The workflow `.github/workflows/build.yml` runs automatically.
2. On the phone, open the repo in the browser (or GitHub app) → **Actions** → latest run → artifact **DuoShell-Sonnet-debug-apk**. On pushes to `main` the APK is also attached to a pre-release under **Releases**, which is easier to download from a phone.
3. Tap the downloaded `.apk`. If Android asks, allow "Install unknown apps" for your browser. If Play Protect warns about an unrecognised developer, choose "Install anyway" (it is a debug build signed with your own CI key).
4. Open **DuoShell Sonnet** from the app drawer. It starts as a normal app and does nothing to your phone yet.
5. To use it as Home: Settings → Apps → Choose default apps → Home app → DuoShell Sonnet (or press Home and choose it with "Always"). The in-app Settings has a button that opens that screen.
6. **Restore One UI Home:** Settings → Apps → Choose default apps → Home app → One UI Home. Uninstalling DuoShell also restores it. Nothing else on the phone is modified.

## What to test on the device
- Fold and unfold while on different pages: focus page should be preserved, icons should glide, dock should switch side/bottom.
- Settings → Developer: preview folded/unfolded without folding; "Run 3 fold cycles" shows measured frame times.
- Long-press an icon (menu), long-press empty space (edit mode), drag icons to the Dock, swipe up for search, swipe to the last page for App Library.
- Settings → Fold Scenes: create a scene, give it a schedule, check the Home filter.
- Optional: enable Notification access to see now-playing in the island.

Reports to look at in the CI run: unit tests, lint HTML (artifact `reports`).
