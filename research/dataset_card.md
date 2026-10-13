# Dataset card — partial audit, 13 October 2026

**Author:** Akinwale Awoyele, as confirmed by the author in this project conversation. Collection consent, collection protocol, date and redistribution licence have not been independently documented. Project use was authorized by the author. The author expressly instructed that the dataset must not be uploaded to GitHub.

The supplied Drive folder inventory contains **564 JPEG files** (551 `.JPG`, 13 `.jpg`) and no annotation files or videos. Inventory is not evidence that all files are downloadable.

## Downloaded subset

- 154 valid decoded images, all 699 × 466 pixels.
- 17,099,524 bytes total, about 16.31 MiB.
- 225 recorded download attempts: 154 successful and 71 failed; the remainder were not recorded as attempted when downloading stopped.
- Six exact-byte duplicate pairs in the downloaded subset; 148 unique byte hashes. The same six pairs also have identical RGB pixels.
- Eleven case-folded filename collision groups in the full inventory. Some are identical names with different Drive IDs; others differ only by letter case. Downloading by unique ID prevents overwrites.
- No ground-truth transcriptions, plate polygons, identity groups or occlusion labels supplied.

Google Drive began rejecting public download requests. The message can indicate permissions or excessive accesses; the cause was not established. This audit is incomplete. The downloaded subset follows inventory order and is not a random or representative sample.

## Visual inspection

A 24-image contact sheet from an early downloaded subset showed mainly parked vehicles, front and rear views, varied lighting, small plates, occasional people, and old and newer plate layouts. It is not a systematic labelling study. Identical vehicle identities and near-duplicates still require review, including images with recovered or edited filenames. No claims about the full dataset's occlusion distribution can be made.

## Local artifacts

`outputs/audit/drive_inventory.json`, `download_manifest.json`, `image_manifest.json`, `summary.json`, `contact_sheet.jpg`, and `baseline_predictions.json` remain local and are excluded from Git. Predictions are unverified machine outputs and must not be relabelled as ground truth.

## Required before training

Obtain the remaining files; establish the dataset's intended licence and collection documentation; annotate plate corners, exact visible transcription, uncertain characters, full reference transcription only where independently established, vehicle identity, and real occlusion severity. Group exact and near duplicates and repeated vehicles before splitting. Keep all synthetic variants of a source in its source split.
