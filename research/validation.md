# Validation record — 13 October 2026

## Executed checks

- Built the native Apple Silicon macOS app and launched it through the desktop UI.
- Verified a dataset image produces actual OCR output with a text-region overlay, a recognizer score and explicit review status.
- Fixed a file-origin canvas restriction for bundled images by embedding the local sample bytes only in ignored build resources.
- Fixed native file import using a native picker. Native video frames are decoded with AVFoundation one frame at a time, avoiding full video upload or conversion to base64.
- Verified the labelled H.264 demo opens in the native app, decodes frames and runs a sequential 12-frame scan.
- Browser preview completed a 12-frame video scan without JavaScript errors. In the first small-scale simulation, several readings were incorrect despite a raw OCR score of 100%. This directly illustrates why the score is not calibrated plate accuracy. The simulation was subsequently adjusted to preserve more source-image resolution; it remains a demonstration, not a benchmark.
- Verified native JSON export writes a parseable report under ignored local outputs, preserving original observations and scores. A review and manual transcription selected at 3 seconds affected exactly that earlier record in the 12-frame export; the original score remained unchanged.
- Three Node tests passed: glyph preservation and format filtering; ranked candidates with provenance and per-box deduplication; empty/no-plate inputs yielding no fabricated candidate.
- Python scripts pass compilation checks. The local `.app` passes ad-hoc code-signature verification.
- Confirmed dataset, generated videos, build resources and outputs are ignored by Git. Remote publication is restricted to a reviewed source/documentation allowlist.

## Descriptive baseline timing

Hardware reported by the user: MacBook with Apple M5 and 24 GB RAM. Each of the 154 available 699 × 466 JPEGs was passed through a newly launched compiled OCR process. All invocations returned without process errors; this does not mean all text was correct.

| Measure | Result |
|---|---:|
| Images | 154 |
| Median elapsed time including process launch | 142.9 ms |
| 95th percentile elapsed time | 155.8 ms |
| Peak child-process RSS | 54.84 MiB |
| Desktop host RSS at one post-image snapshot | 114.27 MiB |

CLI memory was measured with macOS `getrusage(RUSAGE_CHILDREN).ru_maxrss` and converted from bytes to MiB. The desktop host snapshot was measured with `ps`, converting KiB to MiB. Neither figure is the complete desktop application's memory: WebKit and OS-managed acceleration services are separate. No complete application peak, GPU memory, long-video soak test or Windows test has been measured. Timing of native video decoding was not included in the CLI benchmark.

Raw benchmark outputs and predictions are local-only. Dataset subsampling was by available download order, so these figures are not representative performance guarantees. Ground-truth accuracy, calibration and robustness remain unmeasured.
