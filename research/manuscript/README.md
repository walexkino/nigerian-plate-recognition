# Journal manuscript

**Title:** Nigerian License Plate Recognition Under Partial Occlusion: A Local Prototype and Mathematical Framework  
**Author:** Akinwale Awoyele  
**Status:** Research draft, 15 October 2026. Not submitted, peer reviewed, or accepted.

The author requested that this manuscript be saved locally and published in this repository. Dataset photographs, individual predictions, private manifests, and derived videos are not included.

## Read and edit

- [Article source](platelab_partial_occlusion.tex): standalone LaTeX using the standard `IEEEtran` journal class, with an inline numbered bibliography and numbered equations.
- [Article PDF](platelab_partial_occlusion.pdf): compiled reading copy.
- [Evidence record](evidence.json): aggregate counts and historical timing values used in the article, with their limitations and local source paths.

The mathematical sections cover image formation and visibility, an ambiguity limit, the implemented candidate ranking, a proposed CRNN–CTC recognizer, occlusion-aware training, proposed tracked-frame fusion, abstention, and evaluation metrics. Proposed components have **not** been trained or evaluated. The implemented apps use Apple Vision and bundled ML Kit.

## Build

From the repository root, with Tectonic installed:

```sh
tectonic research/manuscript/platelab_partial_occlusion.tex
```

Alternatively, use a LaTeX installation containing `IEEEtran`, `fontenc`, `amsmath`, `amssymb`, `array`, `booktabs`, `cite`, and `hyperref`, and run `pdflatex` twice on the source. No dataset images, external figure files, BibTeX run, API keys, or private files are needed to compile it. Only the `.tex`, final `.pdf`, this guide, and the aggregate evidence record belong in this folder; build intermediates are ignored.

## Before submission

1. Choose the IEEE journal and apply its specific template, length, reference, and submission requirements. Generic IEEE journal formatting alone does not establish suitability or acceptance.
2. Confirm the author's preferred spelling, affiliation, correspondence details, funding and any required ethics or data-use declarations. No institutional affiliation or approval has been invented.
3. Complete independent annotations, identity grouping, frozen splits, model implementation and training, calibration, and held-out accuracy/occlusion experiments. The existing 154-image timing run cannot substitute for these.
4. Record exact hardware, operating system, recognizer versions, code revision, training settings, dependencies, seeds, and benchmark configuration for new results. The October 13 timing archive lacks a complete version manifest and is described as historical.
5. Verify all claims, equations and citations. The acknowledgment discloses substantive assistance from OpenAI Codex; the author still needs to review and approve the manuscript before submission.

The article distinguishes measurements already recorded from a proposed evaluation protocol. It makes no numerical accuracy, novelty, or occlusion-recovery improvement claim. References are selected primary research and platform documentation, not an exhaustive systematic review.

© 2026 Akinwale.
