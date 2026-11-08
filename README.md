# Bedrock Client (Android)

A Flarial-style client for **Minecraft: Bedrock Edition** on Android. It launches the Minecraft you already have installed and adds a customizable in-game HUD and mods on top of it.

> Requires a legitimate copy of Minecraft Bedrock installed from Google Play, on an arm64 device (Android 9+).

## Download

Every push to `main` is built by GitHub Actions and published on the **[Releases page](../../releases)**. Download the latest `BedrockClient-vX.Y.Z.apk` and install it.

## Features

Open the **mod menu** (floating button in game) to toggle and customize everything.

| Mod | What it does |
| --- | --- |
| **Back Button** | Floating back button for menus and in game (on by default) |
| **Restart Game** | Restarts Minecraft instantly, works on the loading screen too. Tap twice to confirm (on by default) |
| **Zoom** | Hold-to-zoom with adjustable level and smooth transition |
| **FPS / CPS display** | Draggable counters |
| **Info Display** | Clock, battery % and session timer |
| **HUD customization** | Every element: drag, resize, opacity, lock, show everywhere. Text elements: text color, background color, rainbow text, text shadow |
| Also | Toggle HUD, auto sprint, camera perspective, snaplook, gyro aim, virtual cursor, hotbar slots, extra buttons, keybinds |

Use **HUD editor** mode in the mod menu to move and resize elements.

## Building

GitHub Actions does it for you (`.github/workflows/android.yml`). Locally: JDK 21, Android SDK 36, NDK r28c, then

```bash
git submodule update --init --recursive
./gradlew :app:assembleRelease
```

You also need an `app/google-services.json` (the workflow writes a placeholder one).

## Credits & license

Bedrock Client is a modified fork of [LeviLauncher (LeviLaunchroid)](https://github.com/LiteLDev/LeviLaunchroid) by the LeviMC team, licensed under the Apache License 2.0 (see `LICENSE`). Changes in this fork: rebranding, Discord SDK and auto-updater removed, new Back Button / Restart Game / Info Display mods, and HUD color customization.

Not affiliated with Mojang, Microsoft, Flarial or LeviMC.
