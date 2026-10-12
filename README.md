# Nigerian Licence Plate Recognition Under Partial Occlusion

A Python research project investigating accurate and computationally efficient recognition of Nigerian vehicle licence plates when parts of the plate are obscured.

## Status

Planning stage. No recognition model, trained weights, or experimental results are available yet. The proposed research contribution will be selected after a literature review and dataset assessment.

## Planned approach

- Detect and crop licence plates from vehicle images.
- Establish a CRNN–CTC recognition baseline.
- Investigate an improvement for partial occlusion and compare it with credible baselines.
- Evaluate character error rate, complete-plate accuracy, rejection behaviour, inference latency, memory use, and model size.
- Report uncertainty when the available image does not support a reliable reading.

## Research practice

Keep plate identities separate across training, validation, and test sets. Evaluate both simulated and real occlusions, document data provenance and usage rights, and record experiment configurations, random seeds, code versions, and hardware.

Datasets, model weights, and generated results are stored outside version control by default. A future manuscript will report measured findings and limitations; no novelty or performance claims have been established.

## Implementation roadmap

1. Literature review and dataset assessment.
2. Dataset preparation and baseline implementation.
3. Evaluation and error analysis.
4. Proposed method and ablation experiments.
5. Demonstration application and research manuscript.
