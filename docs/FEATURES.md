# Feature parity checklist
Legend: ✅ implemented (unverified on device) · ⚠️ partial

| Area | Status | Notes |
|---|---|---|
| Folded iPhone-style Home (rounded icons, labels, dock, wallpaper look) | ✅ | iOS-ratio tiles, bottom/side dock |
| App Library + fast search | ✅ | category cards, A–Z, ranked search with frequency bonus |
| Long-press shortcuts | ⚠️ | shortcuts need default-Home role; other menu actions always work |
| Editable pinned apps / persistent arrangement | ✅ | drag reorder, dock pin/unpin, JSON persisted |
| Back/Home behaviour | ✅ | overlays → edit → first page; Home resets |
| Unfolded Duo: two pages, Today, adaptive dock, hinge gutter, insets | ✅ | |
| Fold/unfold continuity animation + fallback | ✅ ⚠️ | implemented; not measured on device |
| Dev preview without folding + FPS measurement | ✅ | Settings → Developer |
| Liquid Glass (dark/light readable) | ✅ ⚠️ | blur needs own wallpaper + Android 12+ |
| Dynamic-Island-style surface (clock, battery, media, expandable) | ✅ | no fake notifications |
| Personalisation (wallpaper, theme, density, dock, widgets, pins, animation) | ✅ | |
| HOME filters, default-launcher control, live app refresh, unavailable apps | ✅ | |
| No root / no mandatory overlay or accessibility | ✅ | |
| Restore One UI Home docs | ✅ | docs/INSTALL.md + in-app |
| Flagship feature | ✅ | Fold Scenes |
| CI (APK, tests, lint, install docs, stable signing) | ✅ | untested until first push |
