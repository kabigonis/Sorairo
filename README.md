# Sorairo — Android launcher

Sorairo is a keyboard-first Android home-screen launcher inspired by Omarchy and Linux tiling-desktop workflows. It brings a terminal-like prompt, multiple workspaces, pinned apps, a system monitor, and a compact music player to Android tablets.

> Work in progress. Tested on a Lenovo tablet running Android 16.

## Screenshots

| Home workspace | App drawer and search |
|---|---|
| ![Sorairo home workspace](docs/screenshots/home-screen.png) | ![Sorairo app drawer](docs/screenshots/app-drawer.png) |

| Quick settings | Keyboard launch |
|---|---|
| ![Sorairo quick settings panel](docs/screenshots/quick-settings.png) | ![Sorairo keyboard app search](docs/screenshots/keyboard-launch.png) |

| Settings |
|---|
| ![Sorairo settings](docs/screenshots/settings.png) |

## Features

- **Five workspaces:** move between workspaces by swiping or tapping the numbers in the top bar.
- **Type-to-launch:** type an app name into the terminal-style prompt to launch it, or use a command (the help command lists available commands).
- **App drawer:** text-first app list with fuzzy search and automatic launch.
- **Pinned and hidden apps:** keep frequently used apps close and hide apps you don't want in the launcher.
- **Fastfetch-style system panel:** hardware and software information, with built-in ASCII logos or a custom image/GIF.
- **System monitor:** RAM, internal storage, SD storage, battery, and network activity graph.
- **Music player:** compact playback controls and now-playing information.
- **Appearance controls:** themes (Tokyo Night, Catppuccin, Gruvbox, Nord, and Dracula), fonts, terminal colors, window opacity, and custom wallpaper.
- **Top bar:** clock, Wi-Fi link speed, headphones, battery, now-playing information, and a small animated visualizer.
- **Quick settings panel:** status tiles for Wi-Fi, Bluetooth, silent mode, airplane mode, battery saver, mic, camera, and VPN, plus brightness and volume sliders.

## Future Updates

These are ideas for future versions, not promises that they are already implemented:

- **Collapsible windows:** minimize and expand any window when needed.
- **Interchangeable windows:** rearrange, swap, and customize windows to create a preferred layout.
- **More flexible tabs and windows:** support opening many tabs or windows without a fixed app-imposed limit, inspired by the flexibility of Hyprland workspaces.
- **Remembered window state:** remember where tabs/windows were opened and restore their positions and state between sessions instead of resetting everything each time.
- **More commands:** add commands and shortcuts for faster navigation and control.
- **More customization:** add more ways to personalize the launcher's appearance, layout, and behavior.
- **Streaming queue integration:** where a supported streaming service exposes its queue, display that queue directly in Sorairo's built-in player.

## Known Issues

The bottom window/panel, including its monitor and music-player areas, is still being refined. Some bugs remain, and I plan to address them before the final v1.1.0 release.

## Compatibility and Security Notice

Sorairo is still under development and may not work the same way on every device or Android version. Because it is distributed outside Google Play and some features use sensitive Android permissions, Google Play Protect or a device's security software may show a warning or prevent installation. This can vary by device and build; it does not by itself prove that an app is malicious. Review the requested permissions and only install APKs from a source you trust. Do not bypass a warning unless you understand and accept the risk.

## Android limitations

Android does not let ordinary third-party apps directly toggle every system setting. Some quick-settings tiles show the current state and open the corresponding Android settings screen instead. The VPN tile opens the VPN app you choose; Sorairo is not itself a VPN.

The visualizer beside the song name is decorative and does not perform real audio analysis. Available system information and controls can vary by Android version and device.

## Permissions

| Permission | Why it is used |
|---|---|
| Notification access | Read currently playing media information (optional) |
| Modify system settings | Brightness slider (optional) |
| Do Not Disturb access | Silent-mode control (optional) |
| Network and Wi-Fi state | Show connection type and link speed |

Permissions are requested when you use the matching feature.

## Install

Download the APK from the [Sorairo Releases page](https://github.com/snorlaxmeow/Sorairo/releases). For a development build, open [GitHub Actions](https://github.com/snorlaxmeow/Sorairo/actions), select the latest successful **Build APK** run, and download its **Sorairo-apk-<run number>** artifact. That artifact is the APK produced by that exact workflow run.

When a version tag such as `v1.1.0` is pushed, the workflow builds one APK file, uploads that same file as the workflow artifact, and attaches it to the matching GitHub Release. Downloading both from the same tagged run/release gives you the same APK file.

After installing, press Home and choose **Sorairo** if you want to use it as your default launcher.

## Build

Open the repository in Android Studio, allow Gradle to sync, and run the app. GitHub Actions also builds a debug APK using the Gradle `assembleDebug` task.

## Developer

Sorairo is developed by a 16-year-old student who is still learning to code. I rely heavily on AI coding tools such as Claude Code and OpenAI Codex to help write and understand the code, and I am still learning the fundamentals myself.

Since I have school to focus on, development will mostly happen on weekends. Updates may be slow, but I'll keep working on Sorairo whenever I have time.

## Credits

- **Olauncher:** inspiration for the minimal, text-first launcher experience and the interaction where typing an app's name launches it directly. Sorairo is an independent project and is not affiliated with Olauncher.
- **Omarchy / Linux tiling-desktop workflows:** inspiration for the workspace-oriented layout and terminal-like presentation.

## License

MIT
