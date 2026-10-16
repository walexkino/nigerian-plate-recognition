# Local memory observations

Measured on 2026-10-16 during development; these are observations, not maximum requirements or a guarantee for every device.

| Scenario | Observation | Measurement |
| --- | --- | --- |
| Mac empty workspace after sign-in | About 110 MiB | macOS footprint, including the app's WebKit renderer, graphics and network helpers |
| Android settled workspace after workflow tests | About 64 MiB | Android proportional memory (PSS), Android 15 AOSP emulator with 2 GiB RAM, instrumentation build |
| Android camera recognition and pause | Highest sampled PSS about 143 MiB | 16 samples during a roughly nine-second synthetic camera test; highest sampled RSS about 269 MiB |

PSS apportions shared pages; RSS includes the full resident shared mappings. macOS footprint uses a different accounting method, so the rows are not a direct platform comparison. Android tests include instrumentation overhead. Physical-phone use has not been benchmarked, and brief peaks between samples may be missed.

Memory controls already in the app:

- Process the latest camera frame and finish its recognition before starting another; do not queue old frames.
- Android retains the newest 200 text records, decodes ordinary photos to a 1280-pixel working edge, and stops the camera when leaving the foreground.
- Mac retains at most 12 frame previews and 1,000 text results. Live decoding keeps only the newest frame and bounds its encoded buffer.
- Distant mode decodes at most 4096 pixels for original-photo detail; the high-detail Mac live feed uses a bounded 2560-pixel frame.
- The connection indicator uses system callbacks on Android and a short empty HTTPS check every 30 seconds/path change on Mac. It does not load a map, stream, or recognition model.

Large/detailed photos, video decoding, and live feeds can use substantially more RAM than an idle workspace. A 4096 × 4096 RGBA bitmap alone takes about 64 MiB before OCR, crop copies or decoder buffers. Standard mode and pausing live recognition reduce that work. Disk space occupied by the APK/app is separate from RAM use.
