# Research protocol

## Separate the submitted prototype from the research claim

The executable demonstrates local OCR, evidence inspection and resource-conscious video processing. Apple Vision is an existing pretrained recognizer; the implementation is not a novel occlusion model. Its internal learned model is not specified here. Apple documents the text recognition interface at https://developer.apple.com/documentation/vision/vnrecognizetextrequest .

## Planned mathematical baseline

For a labelled plate crop x, a CNN and bidirectional recurrent sequence encoder produce per-step character distributions p_t(k|x). CTC marginalizes over alignments:

`P(y|x) = sum_{pi : B(pi)=y} product_t p_t(pi_t|x)`

Train with negative log likelihood `L_CTC = -log P(y|x)`. B merges repeated characters and removes blank symbols. This is a planned implementation, not code or training already completed. Primary references: https://arxiv.org/abs/1507.05717 and https://www.cs.toronto.edu/~graves/icml_2006.pdf .

A later occlusion-aware method may condition on an estimated visibility mask and aggregate independent observations of the same tracked vehicle. The exact contribution must follow literature review and a reproducible baseline. Multi-frame scores cannot simply be multiplied when errors and frames are correlated. Fully absent character information cannot be reconstructed with certainty from the remaining pixels; a format prior can suggest possibilities but is not visual evidence.

## Evaluation commitments

1. Split by vehicle/plate identity, grouping duplicates and all derived images before splitting. Record identity assignments and frozen split hashes locally.
2. Use training data only for optimization; validation only for configuration and confidence calibration; hold the test set untouched until decisions are frozen.
3. Report plate exact match, character error rate (edit distance / reference characters), detection quality, false accepted readings versus coverage, and calibration measures with definitions and confidence intervals.
4. Separate natural occlusions from synthetic masks. Define severity by covered plate/character area. Analyse blur, resolution, angle and lighting independently.
5. Compare full-image recognition, plate crops, CRNN–CTC baseline and proposed improvements. Include parameter count, model storage, latency distributions and complete process memory on named hardware, with warmup and measurement protocol.
6. For video claims collect real labelled highway footage and identity tracks. Artificial motion videos are interface tests only; never mix transformed versions of the same photograph across train and test.
7. Report failed detections and abstentions, not only successful examples. Publish scripts and aggregate results without publishing this dataset or derived media.

## Current gaps

No training labels, frozen identity split, calibrated probabilities, highway videos, research model weights, or controlled accuracy comparison exist. No scientific novelty or journal-ready results are claimed. These remain requirements for the intended detailed journal article.
