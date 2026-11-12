# Flagship: Fold Scenes

A **scene** is a named Home profile (name, glyph, accent, optional app subset, optional custom Dock, optional weekly schedule). Activating one filters which Home apps show and can swap the Dock; the layout re-flows with the same keyed morph used for folding, so it also works unfolded across both pages.

- Switch by hand (Today card, status island, Settings) or automatically by schedule (checked every 20 s, handles windows that cross midnight, auto-off when the window ends).
- "Fill with suggestions" builds a scene from the apps *you* launch around this time of day (weekday/weekend aware). The launch log is stored only on-device (last 600 launches) and is never sent anywhere.
- Persisted across rotation, folding and recreation. Pure logic (`SceneLogic`, `Schedule`) is unit tested.

Why it fits a foldable: the cover screen wants a tiny, focused set of apps; the inner screen can show everything. Scenes make "work", "commute", "evening" one tap or automatic.
