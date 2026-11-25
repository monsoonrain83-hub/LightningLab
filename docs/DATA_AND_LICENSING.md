# Source code, data and third-party licensing

GPLv3 applies to original LightningLab program source. Preserve the notices in [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md). AndroidX/Media3, TensorFlow Lite and other libraries retain applicable licenses. Google ML Kit is subject to its own terms; review distribution compatibility before publishing a combined binary, and isolate or omit optional components if required. Training data, model weights, cloud reference content, fonts, images and bundled videos require separate provenance and redistribution review. No trained model accuracy or rights to third-party datasets are established by adding GPLv3.

## Distribution checklist

- Preserve copyright and license notices for third-party components; do not claim rights the project does not own.
- Record source, version, license, modifications and attribution for redistributed material.
- Verify compatibility of the actual dependency graph and bundled assets; this document is not a completed legal audit.
- Provide the corresponding source and build instructions required by GPLv3 when distributing covered binaries.
- Keep private data and secrets separate from source; do not add non-GPL restrictions to GPL-covered code.
- Do not redistribute uncertain material until permission is established.
