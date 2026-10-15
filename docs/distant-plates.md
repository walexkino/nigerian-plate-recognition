# Distant plate capture — 0.5.0

PlateLab can preserve more of the camera or source image detail before reading a selected plate. This is a capture aid; reliable distance in metres and recognition accuracy have not been measured.

## Android

1. Enable **Distant plates · capture more detail**.
2. Start the camera, use the zoom slider, and tap the plate to focus. Hold the phone steady.
3. Choose **Capture detailed photo**, then drag around the plate and run recognition.

This mode uses CameraX still capture instead of continuous analysis. The requested capture size is 3840 × 2160; the camera chooses a supported resolution. Imported and captured photos are limited to a 4096-pixel longest edge. Standard photo mode retains its 1280-pixel limit. Zoom support and quality depend on the phone; digital zoom does not add missing detail.

## macOS

Enable **Distant plates** and select the plate area. The native app reads the region from the original image, or regenerates the selected video frame, with a 4096-pixel maximum source edge. The preview stays smaller to limit memory use. Live camera connections started in this mode use frames up to 2560 pixels and pause initially for area selection. Recognition uses the exact displayed live frame, including when the decoder has received a newer frame.

The standalone browser preview retains its existing preview-based crop. Original-file detailed recognition requires the native Mac app.

## Evidence and limits

Crop observations map back to the source preview coordinates. Reports retain capture mode and available source plate dimensions, without exporting image bytes or camera credentials. Images remain in the local recognition workflow; selecting a region does not upload it to a recognition service.

Small or blurred characters, glare, motion, and occlusion still limit recognition. The source-width/height prompt is a practical capture hint, not a validated accuracy threshold. PlateLab does not recover hidden characters or treat enlargement as new evidence. Use optical zoom or a closer camera position where available, and review the original evidence before accepting a reading.

## Verification

Tests cover selected-region geometry, mapping OCR boxes back to the source, original-file native recognition using an invented 4096-pixel fixture, and exact-frame live recognition. An Android emulator exercises camera binding, zoom availability, detailed still capture, and region selection. Physical-phone optical zoom, real long-distance performance, and accuracy across camera models remain unmeasured.

Implementation references: [CameraX camera controls](https://developer.android.com/media/camera/camerax/configuration), [ML Kit text recognition image guidance](https://developers.google.com/ml-kit/vision/text-recognition/v2/android#input-image-guidelines).
