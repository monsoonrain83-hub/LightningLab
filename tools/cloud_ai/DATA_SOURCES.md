# Lightning Lab — cloud image data sources (Phase 2)

## Selected starter source

**CCAiM-CloudsDataset** — https://huggingface.co/datasets/serbekun/CCAiM-CloudsDataset

- Dataset card declares MIT; 916 ground-based images; 10 WMO genera.
- Reported distribution on the dataset card: Cirrus 102, Cirrocumulus 33, Cirrostratus 42, Altocumulus 96, Altostratus 63, Nimbostratus 17, Stratocumulus 94, Stratus 38, Cumulus 398, Cumulonimbus 33.
- This is highly imbalanced and small, especially Nimbostratus and Cumulonimbus. It is a pipeline smoke-test / initial fine-tuning source, not enough by itself to claim operational accuracy.
- The card states MIT. Preserve attribution and re-check the live card and upstream provenance before redistributing a trained model or images.
- Preparation: `python tools/cloud_ai/prepare_ccaim.py --out data/clouds/ccaim`

## Candidate requiring a license check before use

**WEBCAM ground-based cloud image dataset** — https://github.com/MarcusCoteFIT/webcam-ground-based-cloud-image-dataset

- Repository describes more than 15,500 labelled images in nine categories, including Cumulonimbus, Cirrus, fair-weather Cumulus, towering Cumulus, Altocumulus, Stratus, precipitation, obscuration and clear sky.
- This is promising for improving sample count and real-world variability. However, a clear reusable image/data license was not established from the repository information reviewed for this phase. Do not bundle, mirror, or train on it for a distributed product until the dataset terms are checked and permission is clear.
- Labels are not identical to the ten-genus taxonomy; e.g. TCu is a Cumulus subtype and precipitation/obscuration/clear are not cloud genera. Any mapping must preserve original labels and document the mapping.

## Other sources reviewed but not selected as training inputs yet

- CCSN / CloudNet ground-based images: https://github.com/upuil/CCSN-Database — access instructions are provided by the authors; terms must be checked directly before use.
- Cloud-ImVN 1.0: https://data.mendeley.com/datasets/vwdd9grvdp — 2,100 images, six categories, CC BY 4.0. Useful as a supplementary benchmark, but its six labels do not map one-to-one to all ten genera.
- CUMULO: https://github.com/FrontierDevelopmentLab/CUMULO — more than 300k annotated multispectral satellite samples, but it is a satellite/layer-classification dataset, not ordinary phone photographs of the sky. Do not mix it into the ground-photo classifier without a separate modality-specific model.

## Scientific and methodological constraints

1. The initial classifier predicts only the ten WMO cloud genera: Ac, As, Cb, Cc, Ci, Cs, Cu, Ns, Sc, St.
2. Contrails are not a WMO genus. They should be handled as a separate phenomenon/classifier, not inserted as an eleventh genus.
3. Species, varieties, and supplementary features (e.g. incus, mamma, virga) are not trained in this phase because the selected starter source does not provide reliable multi-label annotations for them.
4. Ground photos and satellite products are different domains. Do not combine them in one training split or report a single accuracy across them.
5. Test metrics must be reported per class. Aggregate accuracy alone hides the severe class imbalance.
6. A single photograph cannot establish exact cloud-base altitude, CAPE, vertical wind shear, precipitation rate, or imminent severe-weather risk. The app must label such quantities as unavailable unless supported by independent observations/model data.
