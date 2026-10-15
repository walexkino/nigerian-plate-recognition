# Research and dataset tools

This folder contains project-authored research guides, dataset auditing and benchmark scripts, and their Python requirements. The [IEEE-style manuscript draft](manuscript/README.md), by Akinwale Awoyele, includes a mathematical framework and a compiled PDF. It is not a peer-reviewed or accepted journal paper. Dataset photographs and model weights are not included.

Run scripts from the repository root so their ignored `data/` and `outputs/` paths resolve correctly:

```sh
.venv/bin/python -m pip install -r research/requirements-audit.txt
.venv/bin/python research/scripts/audit_dataset.py
.venv/bin/python research/scripts/benchmark_baseline.py
```

`benchmark_baseline.py` uses the locally built macOS executable at `dist/macos/PlateLab.app/Contents/MacOS/PlateLab`. The synthetic video tool additionally needs `research/requirements-demo.txt`.

Read `dataset_card.md`, `validation.md`, and `experiment_protocol.md` before interpreting results. OCR predictions are not ground truth, and existing timings do not establish recognition accuracy. Dataset files and derived media remain local.
