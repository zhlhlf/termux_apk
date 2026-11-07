# zhlhlf/termux_apk

Personal build fork of [termux/termux-app](https://github.com/termux/termux-app).

- Synced with: `termux/termux-app` `master` @ `8629e632fcb95da272221be327db653fb24befe9` (2026-09-26)
- Sync date: 2026-11-07

## Customizations kept on top of upstream

1. **Custom background** — `app/src/main/res/drawable/bg.png` is set as the root background of
   `activity_termux.xml` (`android:background="@drawable/bg"`).
2. **Translucent panels** — the session drawer and the extra-keys toolbar use `#3056CFF7`
   (ARGB, alpha `0x30`) instead of the theme backgrounds, so the terminal wallpaper shows through.
3. **arm64 only** — `app/build.gradle` only packages `arm64-v8a`, no universal APK.
4. **APK naming** — output APKs get the `-by_zhlhlf` suffix.
5. **Custom bootstrap** — `downloadBootstraps` pulls `bootstrap-aarch64.zip` from this repo's
   release `421` (prebuilt, no checksum verification).
6. **No `REQUEST_INSTALL_PACKAGES`** — that permission is removed from `AndroidManifest.xml`.

To sync again later, diff these files against the new upstream tag and re-apply.
