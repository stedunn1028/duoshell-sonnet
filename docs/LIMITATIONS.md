# Known limitations (honest list)

- **Not compiled or run here.** No Android SDK/network for Gradle was available in the authoring environment. `core/` (geometry, paging, morph maths, settings codec, search) compiles and passes 26 JVM tests; all Compose/Android files are unverified until CI runs. Expect possible first-build compile errors to fix.
- **No device validation, screenshots, recordings or FPS numbers.** The app includes a frame-time sampler (Settings → Developer) so you can measure; nothing is claimed.
- **System fold animation is not controlled.** Samsung's own transition is outside app control. DuoShell animates its own content from the previous layout to the new one, and uses a persisted layout memo as a fallback if Android recreates the Activity.
- **System wallpaper cannot be read by apps.** With "System" wallpaper, Liquid Glass uses a tinted translucent body (no true backdrop blur). Built-in or chosen-photo wallpapers get real blur on Android 12+.
- **Shortcuts in the long-press menu** only appear when DuoShell is the default Home app (platform rule).
- **Island** shows clock, battery and now-playing only (now-playing requires you to grant Notification access). No notification content, no fake notifications.
- **Developer preview** scales a virtual window; glass backdrop alignment inside the scaled preview is approximate.
- No recents / app-switcher / gesture-navigation replacement (system-owned). No icon packs, no folders, no multi-user/work-profile apps.
- Debug build only; no release signing/minification.
