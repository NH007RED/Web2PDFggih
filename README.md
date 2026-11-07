# Web2PDF – Website to PDF Converter

Native Android app (Kotlin, Jetpack Compose, Material 3, MVVM) that converts any web page into a **real PDF** using
Android's WebView and print framework. No Firebase, no account, no ads.

## Setup
1. Open the folder in Android Studio (or Code on the Go). Gradle wrapper: **8.14.3**. JDK 17.
2. Sync, then run on a device/emulator (minSdk 26, targetSdk/compileSdk 34).
   If your SDK lacks platform 34, change `compileSdk`/`targetSdk` in `app/build.gradle.kts`.
3. Debug: `./gradlew assembleDebug`. Release (signed with the debug key so it installs): `./gradlew assembleRelease`.

Stack: AGP 8.7.3 · Kotlin 2.0.21 · KSP 2.0.21-1.0.28 · Compose BOM 2024.06.00 · Room 2.6.1 · DataStore 1.1.1 · Navigation 2.7.7.

## Flow
Home → (URL Input) → Webpage Preview → PDF Settings → Conversion → Success → PDF Preview / History / Settings.
PDF Settings and Conversion are full-screen layers over the preview so the loaded WebView stays alive for printing.

## How the PDF is made
`WebView.createPrintDocumentAdapter()` is driven directly (no system dialog) by `android/print/PdfPrinter.kt`
(placed in the `android.print` package because the callback constructors are package-private). The PDF is written to a
temp file, verified (`%PDF` header + opened with `PdfRenderer`), then saved. If a device blocks the silent engine, the
conversion screen offers the system print dialog ("Save as PDF") instead of failing silently.

## Storage (no storage permissions)
Folder you pick in Settings (SAF) → otherwise `Downloads/Web2PDF` via MediaStore (Android 10+) → otherwise the app's
Documents folder (Android 8–9). Rename, delete, share, open-in-app and "Save a copy" (system file picker) are supported.
Share/open use a cached copy through FileProvider.

## Honest limits
* Headers/footers: Android's print engine has no API for them, so no fake toggle is shown.
* "Background graphics" injects print CSS; some sites override it.
* Pages that need a login, block automated rendering or are mostly lazy-loaded may produce partial/empty PDFs; errors are explained.
* SSL errors are never bypassed. Plain http pages load (cleartext allowed) and show a "not secure" icon.
* Page range uses printed pages (e.g. `1-3, 5`).

## v1.2 – PDF viewer additions
* **Full screen:** the full-screen button (top strip of the PDF viewer) hides the status/navigation bars and all app bars so the
  PDF fills the whole screen. A small floating bar keeps page number, zoom, voice and "exit full screen". Back button also exits.
* **Tap to listen:** tap any Hindi (or English) text/speech bubble in a PDF page and the phone reads it aloud. The page image is
  read with ML Kit offline OCR (Devanagari model bundled in the app, no internet needed) and spoken with Android Text-to-Speech
  (hi-IN). The tapped text is highlighted; tap it again or press Stop to stop. The speaker icon turns the feature on/off.
* Needs a Hindi voice in Android Settings → Text-to-speech (the app offers an "Install" shortcut if it is missing).
* New dependency: `com.google.mlkit:text-recognition-devanagari:16.0.1`. Gradle needs internet once to download it.
