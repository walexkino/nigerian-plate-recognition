# Demonstration guide — PlateLab v0.1

## Present the work accurately

“PlateLab is a local desktop prototype for inspecting Nigerian vehicle-plate readings. It accepts still images and video, exposes uncertainty and supports human review. This release uses Apple Vision as an existing OCR baseline. The longer-term research investigates recognition under partial occlusion.”

The originally supplied Andela brief concerns learning access, sustained engagement and pathways to opportunity. Vehicle-plate recognition does not directly satisfy that theme; confirm separate approval or explain an approved connection. Do not claim a connection that was not agreed with the assessors.

## Three-minute demonstration

1. Open `dist/macos/PlateLab.app`. No Python environment or server is needed to run the built app on this Mac.
2. Choose **Dataset**, load a photograph, and click **Recognize frame**. Check the green text-region box and the candidate against the photograph.
3. Explain that **OCR confidence is uncalibrated**, so 100% is not a guarantee. Per-character confidence is marked unavailable, rather than simulated.
4. Use **Select plate region** and drag around a small plate. Run recognition again. Enlarging a crop may help OCR inspect existing signal, but adds no new observed detail.
5. Import `outputs/demo/synthetic-road-demonstration.mp4`. It is visibly labelled synthetic. Click **Scan video**. Select history frames to inspect changing readings as artificial occlusion appears.
6. Review or manually correct a transcription. **Export → Save JSON** preserves raw observations and separates manual transcription from model output.
7. State the measured cold-process timing and its limits: median 142.9 ms over 154 downloaded images; about 54.84 MiB peak OCR child-process RSS, excluding the UI. Accuracy has not been measured.

## What to submit

Source repository and this guide; the locally built `.app` if your submission accepts Mac apps. Keep dataset files and derived media off GitHub. The local app bundle includes a small set of samples for your demonstration; do not publish that bundle to GitHub. The app is locally ad-hoc signed and not notarized. Windows support remains future work.

Do not describe this as a trained novel model, a validated solution for arbitrary focal lengths, a real highway evaluation, or a bug-free production system. The deliverable today is a functional prototype with a documented baseline and research plan.
