# DuoShell Sonnet

A from-scratch Android Home launcher in Kotlin + Jetpack Compose that recreates an iPhone Duo / iOS-style Home experience for the Samsung Galaxy Z Fold 8 (folded cover screen and unfolded inner screen).

- Package `com.duoshell.sonnet`, app label **DuoShell Sonnet**, debug build only, no Material dependency.
- Flagship feature: **Fold Scenes** (see `docs/FLAGSHIP.md`).
- Signature feature: fold/unfold continuity morph (see `docs/ARCHITECTURE.md`).

Docs: [INSTALL](docs/INSTALL.md) · [ARCHITECTURE](docs/ARCHITECTURE.md) · [FLAGSHIP](docs/FLAGSHIP.md) · [FEATURE PARITY](docs/FEATURES.md) · [SIGNING](docs/SIGNING.md) · [KNOWN LIMITATIONS](docs/LIMITATIONS.md)

**Status, honestly:** the pure logic (`core/`) is unit tested (26 tests). The Compose/Android layer was written without an Android SDK available, so its first real compile is the CI run. It has not been run on a Fold 8 or any device, and no frame-rate figure is claimed. See `docs/LIMITATIONS.md`.
