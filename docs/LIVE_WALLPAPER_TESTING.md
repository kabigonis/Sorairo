# Live wallpaper testing

This build includes animated previews in Sorairo and the Android live-wallpaper service for imported GIF and video files.

## Device test checklist

1. Install the debug APK on an Android device.
2. Import a small GIF and select it as the current wallpaper in Sorairo. Confirm that the in-app preview animates.
3. Use the system apply action and choose Sorairo in Android's live-wallpaper picker.
4. Confirm the animation appears on the Android home screen. If the device offers a lock-screen placement option, test that separately.
5. Repeat with an MP4 video and confirm playback loops.
6. Switch to another Sorairo theme with wallpaper-follow enabled. Confirm that the system wallpaper changes too.
7. Turn home & lock sync off and confirm automatic theme changes only update the home-screen target.
8. Reboot or leave and return to the home screen; confirm the live wallpaper resumes.

## Notes

- Android's own wallpaper picker controls initial live-wallpaper confirmation and placement. Sorairo cannot silently override the system's choice.
- Test GIF and video files from local storage first; online theme wallpaper downloads are currently static-image sources.
- Record device model, Android version, media format, and any relevant logcat errors for failures.
