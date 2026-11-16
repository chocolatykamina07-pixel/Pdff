# ScanNest — Offline-First Document Scanner

ScanNest is an original, 100% offline Android document scanner built with **Kotlin**, **Jetpack Compose**, **Material 3**, **CameraX**, **Room**, **DataStore**, **ML Kit (On-Device Text & Barcode)**, and **Android PdfDocument / PDFBox-Android**.

## Privacy & Permissions
- **Zero network permission** (`INTERNET` is not declared in `AndroidManifest.xml`).
- **Zero analytics, ads, or accounts**. All edge detection, perspective warping, filters, signatures, OCR, and PDF generation run strictly on-device.
- Uses **Camera** permission only for live scanning, and zero-permission **Photo Picker** / **Storage Access Framework (SAF)** for importing and saving files.

## Project Structure
- `com.example.ui.theme` — Deep Teal (`#0F766E`) + Amber (`#F59E0B`) color tokens, Plus Jakarta Sans + Inter typography, 16dp/28dp M3 shapes.
- `com.example.data` — Room database (`DocumentEntity`, `PageEntity`, `FolderEntity`, `SignatureEntity`), DataStore `PreferencesManager`, and `ScanNestRepository`.
- `com.example.camera` — Real-time Sobel/gradient quadrilateral `EdgeDetector` and `QuadStabilityTracker` for auto-capture.
- `com.example.processing` — `ImageProcessor` (`Matrix.setPolyToPoly` 4-point perspective crop, adaptive B&W, Clean Doc, shadow/blemish removal, ID front+back combiner, ID Photo Pack, diagonal watermarks).
- `com.example.ocr` — On-device ML Kit Latin + Devanagari text recognition and QR/Barcode scanning.
- `com.example.pdf` — Streaming `PdfDocument` generator (with optional invisible searchable OCR text layer), `PdfRenderer`, PDFBox password protection/unlock, merge/split/compress, and TXT/DOCX export.
- `com.example.ui` — Onboarding, Home/Files (with tablet two-pane layout), CameraX Scanner, Crop & Adjust (with magnifier loupe), Filters & Enhance, Multi-page Document Editor, Sign & Annotate, OCR, Tools (11 tools), Settings, Trash (30-day retention), and App Lock.

## How to Build & Run
1. Open the project in Android Studio (JDK 11+, Android Gradle Plugin 9.x, Compile SDK 36, Min SDK 24).
2. Run `gradle :app:assembleDebug` or launch directly on an Android device/emulator.
3. For unit / Robolectric tests, run `gradle :app:testDebugUnitTest`.

## Testing Checklist
- [ ] First-launch Onboarding: swipe 3 cards, verify permission rationale, tap **Allow Camera & Continue** or **Continue with Import Only**.
- [ ] Scan Flow: tap amber Scan FAB → capture document (or import photo) → adjust 4-corner handles with loupe → apply filter & hold **Compare** → save as multi-page PDF.
- [ ] Batch Scan: toggle **Batch**, capture multiple pages, verify counter badge, export multi-page PDF smoothly.
- [ ] ID Card Mode: capture front then back → verify combined A4 ID page.
- [ ] Sign & Annotate: create a smooth Bezier signature, place & resize/rotate it, add date/highlight, tap **Flatten & Save**.
- [ ] OCR & Search: run OCR (Latin or Devanagari), verify searchable PDF export, and search Home by extracted OCR text.
- [ ] Tools Tab: test Image to PDF, Merge PDFs, Split PDF, Compress PDF, Protect/Unlock PDF, PDF to Images, ID Photo Pack, and QR Scanner.
- [ ] Themes & Adaptive Layout: switch Light/Dark theme in Settings and verify tablet NavigationRail + two-pane Home layout.
