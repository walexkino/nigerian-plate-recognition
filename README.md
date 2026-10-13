# PlateLab: Nigerian licence-plate recognition under partial occlusion

A local desktop research prototype by **Akinwale Awoyele**. The first working release reads visible plate text from images and sampled video frames, shows actual OCR scores, and supports human review.

## Current implementation

- Native macOS `.app`, built with Swift, WKWebView and Apple's on-device Vision OCR.
- Image and video import, user-selected plate region, text bounding boxes and alternative candidates.
- Sequential video processing at one sampled frame per second; stop control and a 120-frame run limit.
- Raw OCR confidence, manual transcription, review status and JSON export.
- Frames capped at 1280 pixels on their longest side; at most 12 previews and 1,000 text records retained.
- Python tools for dataset inventory, duplicate audit, baseline timing and a labelled synthetic video.

The app has **not** been trained on this dataset. It does not yet implement a CRNN–CTC model, occlusion reconstruction, vehicle tracking, temporal fusion, or calibrated per-character confidence. The recognizer score is not the probability that a complete plate is correct; even a score of 100% can accompany a wrong reading. All readings require review. Standard format matching is a heuristic and can exclude valid nonstandard plates.

## Run on macOS

Requires macOS 13 or newer and Apple command-line developer tools to build:

```sh
python3 scripts/build_macos.py
open dist/PlateLab.app
```

The resulting app runs without Python, a web server, cloud inference, or downloaded neural weights. It is locally ad-hoc signed, not notarized for public distribution. The current build targets the host architecture; the tested build is Apple Silicon. A Windows executable is **not implemented**; it needs a different OCR backend and packaging.

For an optional browser development preview after building:

```sh
python3 scripts/dev_server.py
# Visit http://127.0.0.1:58070
```

## Dataset stays local

**Do not commit or upload the dataset, crops, screenshots, predictions, or derived videos to GitHub.** `data/`, `outputs/`, `build/`, `dist/` and media files are ignored. The local build may include a small selection of local samples for demonstration. Those samples are not part of the source repository.

The author has authorized project use of his dataset. No public redistribution licence has been specified. See `research/dataset_card.md` for the partial audit and limitations.

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements-audit.txt
mkdir -p outputs/audit
.venv/bin/gdown 'YOUR_PUBLIC_DRIVE_FOLDER_URL' --json --no-cookies --timeout 30 > outputs/audit/drive_inventory.json
.venv/bin/python scripts/download_dataset.py
.venv/bin/python scripts/audit_dataset.py
.venv/bin/python scripts/benchmark_baseline.py
```

Download names use unique Drive IDs to prevent collisions on case-insensitive filesystems. Repeated failures stop further batches; rerun later to resume existing downloads. Do not interpret a partial download as the complete dataset.

To create the explicitly labelled **synthetic** road-motion demonstration:

```sh
.venv/bin/python -m pip install -r requirements-demo.txt
.venv/bin/python scripts/make_demo_video.py
```

This transforms a source photograph into a moving inset over a road illustration with an artificial occluder. It is not recorded highway footage and must not be used to claim highway accuracy.

## Validation

```sh
node --test tests/core.test.js
python3 -m compileall -q scripts
```

On the 154 downloaded 699×466 images, a local benchmark on 2026-10-13 completed without OCR process failures: median 142.9 ms, p95 155.8 ms, peak child-process RSS 54.84 MiB. Each invocation launched a fresh OCR process. These are descriptive timings, **not recognition accuracy**. The memory figure excludes the desktop UI and WebKit processes. No ground-truth test set exists yet. See `research/validation.md`.

## Research direction

The planned research baseline is CRNN–CTC. Future experiments must use verified transcripts, plate regions, vehicle-identity-disjoint splits, occlusion severity labels, uncertainty calibration and an untouched test set. Any new method must be compared against credible baselines with ablations. Current app functionality establishes an inference and review workflow; it does not establish scientific novelty or solve missing visual information.

See `research/submission_guide.md` for today's demonstration and `research/experiment_protocol.md` for the remaining research work.
