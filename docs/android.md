# Android port

The Android app is an arm64 SDL2 host for the same statically recompiled game.
It does **not** contain or download a Super Metroid ROM.

## Player flow

1. Open the app and tap **Añadir ROM**.
2. Select an unheadered, unmodified `Super Metroid (Japan, USA) (En,Ja)` image.
3. The app checks SHA-256 locally and copies an accepted image into its private
   external-files directory. Android's Storage Access Framework means no broad
   storage permission is requested.
4. Tap **Jugar**. The ROM path is passed to the native host with
   `--no-launcher --rom`; the normal runtime then loads graphics/audio data and
   applies the same final identity check as desktop.

The gear button over the game changes touch-control opacity and size. **Editar
posición** makes every SNES control draggable. Positions are stored as normalized
coordinates, so a layout remains useful across device resolutions. The overlay
supports simultaneous touches and emits the runtime's normal keyboard bindings.
USB/Bluetooth gamepads continue to work through SDL.

The library places imported `.snesmod` packages in `mods/inbox`. On the first
game boot, the shared hardened installer validates and extracts them into
`mods/preloaded`; they are available on the following launch. Texture archives
are retained in `textures/` for texture-aware `.snesmod` packages. Raw image/ZIP replacement is
not applied blindly: a pack needs a compatible mod manifest/runtime hook, which
prevents an unrelated pack from corrupting the game's graphics.

Saves, settings, mods, texture packs and the selected ROM are under Android's
app-specific external-files directory. Uninstalling the app may remove this data.

## Build an APK locally

Prerequisites are JDK 17, Android SDK 34, NDK `26.3.11579264`, CMake/Ninja and the
private generated C output in `src/gen` plus `recomp/funcs.h` (generated from a
legally owned matching ROM).

```sh
bash android/fetch_sdl.sh
cd android
./gradlew assembleRelease
```

The APK is written under `android/app/build/outputs/apk/release/`. The app is
currently arm64-v8a and requires Android 9 (API 28) or newer.

## GitHub Actions

Android/runtime changes trigger **Android APK** automatically; it can also be
started manually with the SHA-256 of the generated-code archive. As with desktop
releases, maintainers must set the `SM_GENERATED_CODE_URL` repository secret to a
short-lived URL for that exact archive. Manual runs verify the supplied digest;
automatic runs calculate and log the downloaded archive digest. The workflow
fetches pinned SDL 2.32.8, builds the APK, and uploads the APK plus `SHA256SUMS`
as a 14-day artifact. Neither the ROM nor the
generated archive is uploaded as an artifact.
