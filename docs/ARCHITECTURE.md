# Architecture

```
core/   pure Kotlin (no Android): Models+SettingsCodec, Geometry (layout engine), Paging, Morph (keyed placements + easing), Library (categorise/search/LayoutMemo)  -> JVM unit tests
data/   Store (SharedPreferences), AppRepository (LauncherApps + live callback), IconRenderer, Device (battery, media), WidgetHost
LauncherController   single state holder (settings, editing, panels, scenes, dev preview, island toast)
ui/     Compose: Root, HomeSurface, MorphUi, Island, Today, LibraryScreen, Menu, Settings, Scenes, Glass, Wallpaper, Gestures
MainActivity         HOME activity, WindowInfoTracker hinge -> controller, pickers/widget binding
```

## Fold / unfold continuity
1. Layout is a pure function: `GeometryEngine.compute(window, insets, hinge, settings)` then `Placements.compute` gives keyed rects (`app:<key>`, `dock:<key>`, `dock:panel`, `pane:*`, `pill`, `dots`).
2. The Activity handles all configuration changes (no recreation). When the window size/hinge changes, `beginMorph` (during composition, so no final-layout flash) keeps the previous placements, maps them into the new window with a fit viewport, and starts a progress 0→1 animation.
3. Every element is laid out once at its final rect and animated only in `graphicsLayer` (translate/scale/alpha) with per-element easing and a stagger by distance from the hinge. Elements that vanish shrink out as ghosts; a frost veil and (Standard/Expressive) blur clear as content settles. The wallpaper is a function of window size, so it stays continuous.
4. Focus is a pane index that survives fold/unfold (folded shows one pane, unfolded two). Folding back keeps the page.
5. If the system recreates the Activity anyway, a persisted `LayoutMemo` rebuilds the previous layout so the same morph can run.
6. Timing: `withFrameNanos` frame deltas are recorded per fold and shown in Settings → Developer.

Unfolded: two coordinated panes (Today | page, page | page, … | Library) separated by a hinge-aware gutter; horizontal hinge stacks panes (tabletop). Dock goes to a side (auto) with the status island on the opposite edge; Samsung nav/status/cutout insets are respected.
