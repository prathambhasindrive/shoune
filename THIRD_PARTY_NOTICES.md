# Third-party components

The bundled speech model is `vosk-model-small-en-us-0.15`, Copyright 2020 Alpha Cephei Inc. Its original README is preserved under `app/src/main/assets/speech-model`. Published model and license information: https://alphacephei.com/vosk/models (Apache 2.0).

Vosk Android 0.3.47: https://github.com/alphacep/vosk-api (Apache 2.0).

Llamatik Android 1.7.0: https://github.com/ferranpons/Llamatik (Apache 2.0). It packages native components including llama.cpp; upstream notices and native component licenses also apply to built APKs.

Qwen model weights are downloaded separately and are not committed to this repository. Each publisher model page linked in the app carries its applicable license.

sherpa-onnx 1.13.8 is bundled for local speaker embeddings: https://github.com/k2-fsa/sherpa-onnx (Apache 2.0). The bundled CAMPPlus English VoxCeleb speaker model comes from sherpa-onnx's official speaker-recognition release and the Apache-2.0 3D-Speaker project: https://github.com/modelscope/3D-Speaker. Shoune stores a derived voice embedding, not enrollment audio.

The Gradle wrapper is distributed under Apache 2.0. A copy is in `licenses/Apache-2.0.txt`. These notices do not assign a license to the project's original application source.
