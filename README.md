# EZ Sign Writer

Small Java-only Android NFC writer for EZ Sign / FMSC-style passive e-paper tags.

- No internet permission
- No WebView / Capacitor
- No AndroidX
- No runtime dependencies
- Uses NfcAdapter + IsoDep directly
- Auto-detects common 2.9" / 4.2" panels and 2/4 colors
- Center-crops, dithers and packs the image
- First build uses raw F0 D2 transfer, then F0 D4 refresh

Build requirements: Java 17, Gradle 8.9, Android SDK 35.

The GitHub Actions workflow builds an installable debug APK and uploads it as artifact `EZSignWriter-debug-apk`.

Note: this is reverse-engineered interoperability code and has not yet been tested on the physical EZ Sign in this environment.
