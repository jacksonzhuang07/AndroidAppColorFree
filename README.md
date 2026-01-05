# AndroidColorFree

A digital wellbeing app that forces your phone into Grayscale mode to reduce screen addiction.

## Features
*   **Grayscale by Default**: The app enforces 0 saturation.
*   **Unlock Color**: You can temporarily unlock color.
    *   **5 Minutes**: Free unlock.
    *   **15+ Minutes**: Requires solving a math puzzle.
*   **Auto-Relock**: A foreground service counts down and re-enables grayscale when time is up.

## Installation & Setup

1.  **Open in Android Studio**: Open this folder as an Android Project.
2.  **Build & Run**: Install the app on your device.
3.  **Grant Permissions (Important)**:
    Since this app modifies Secure System Settings, you must grant it permission manually via ADB (Android Debug Bridge) on your computer.

    Run the following command in your terminal:
    ```bash
    adb shell pm grant com.example.androidcolorfree android.permission.WRITE_SECURE_SETTINGS
    ```

4.  **Launch App**: Open the app. If the permission was granted, it will ask to turn on Grayscale.

## Troubleshooting
*   **Permission Denied**: Ensure you enabled "USB Debugging" on your phone.
*   **Settings not changing**: Some heavily modified Android skins (MIUI, ColorOS) might block `WRITE_SECURE_SETTINGS` even with ADB. You might need to enable "USB Debugging (Security Settings)" in Developer Options.
