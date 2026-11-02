# Photo Compressor Lite

Native Kotlin / Jetpack Compose Android application. Minimum Android 8 (API 26), target API 36. No backend, accounts, network permission, ads, or analytics.

## Build

Open this directory in Android Studio with an Android SDK 36 installation and JDK 21 (JDK 17 also supported). The bundled Android Studio JBR 25 is too new for Gradle 8.13. Let Android Studio set `local.properties` for your SDK.

```powershell
$env:JAVA_HOME = 'C:\Users\User\.jdks\jbr-21.0.11'
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug bundleRelease
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
Release bundle: `app/build/outputs/bundle/release/app-release.aab` (unsigned). Configure your own private upload key through Android Studio's Generate Signed Bundle wizard; no signing secrets are committed.

## Implementation

`MainActivity.kt`: adaptive-width Compose UI, native image picker, permission flow for Android 8/9 saves, secure sharing, persisted theme/settings, recent summaries. `MainViewModel.kt`: StateFlow screen state, selection, background batch jobs, cancellation, partial results and retryable saves. `PhotoRepository.kt`: ContentResolver input, bounded decode, all EXIF orientation transforms, explicit transparency treatment, quality search and iterative dimension reduction, MediaStore scoped saves, FileProvider sharing, private local preferences/history. No database is necessary for 20 summary records.

Images are processed sequentially, capped at 2560 pixels on the longest decoded edge. Outputs deliberately omit original EXIF and location metadata. Transparency is preserved in WebP; JPEG requires explicit white-background consent when transparent pixels exist. Existing originals are never modified. Targets apply per image and may require lower detail. A result always reports its actual size and whether the target was met; output may exceed a tiny original. Partially completed batches remain saveable after cancellation.

Private compressed outputs remain in app files for sharing during the app session and until app data is removed. Gallery copies remain after uninstall. History stores counts, timestamps and aggregate byte sizes, not original photos. Clearing history removes summaries only. No fake photos or seeded history are present.

## Release checklist and device verification

Before publication, supply a public privacy-policy URL and developer contact, complete Play Data Safety based on the shipped binary, generate and securely store your release signing key, and test a signed bundle with Play internal testing. The repository's unsigned release configuration is not a signed store upload.

Device checks still required: API 26/28 storage-permission denial and save; API 29 scoped save; API 33/36 Photo Picker; rotated/mirrored JPEGs; PNG transparency in both formats; WebP; revoked/unavailable URIs; low-storage save failure; large batches; cancellation; rotation; dark mode; TalkBack; enlarged text; gesture navigation; share recipient access. Unit tests cover byte savings and decode/downscale calculations. Build checks cannot establish device compatibility by themselves.

The brief referenced a screenshot, but only its written description was available. The UI follows the specified blue/navy palette and three-column selection layout.

## Verified in this workspace

On October 30, 2026: debug APK compilation, release AAB generation, five JVM unit tests, and eight instrumented tests on the installed API 37 emulator succeeded. Instrumented coverage includes selection and empty-state navigation, persisted theme selection, compression through results and MediaStore save, valid output decoding, secure FileProvider URIs, EXIF rotation and metadata removal, transparent PNG handling for JPEG and WebP, cancellation cleanup, honest impossible-target reporting, and unavailable input errors. The reference screenshot itself was not attached.

The first emulator run encountered a System UI ANR while release optimization was running; the app assertions still passed. The final APK subsequently passed all eight tests again in an isolated 42.5-second run. Clean selection and results captures were visually inspected and are available in `artifacts/selected.png` and `artifacts/results.png`; the blue images in these captures are generated test fixtures, not seeded app data. Legacy Android save-permission behavior and physical-device coverage remain unverified; use the release checklist above before publishing.

