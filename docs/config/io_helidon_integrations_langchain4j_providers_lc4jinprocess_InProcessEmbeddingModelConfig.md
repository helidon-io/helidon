# io.helidon.integrations.langchain4j.providers.lc4jinprocess.InProcessEmbeddingModelConfig

## Description

Configuration blueprint for LangChain4j in-process models.

## Usages

- [`langchain4j.providers.lc4j-in-process`](../config/config_reference.md#a3ca3d-langchain4j-providers-lc4j-in-process)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="adebfb-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether the embedding model is enabled |
| <span id="a66290-executor"></span> `executor` | `VALUE` | `i.h.c.c.ThreadPoolConfig` |   | Executor configuration used by the embedding model |
| <span id="abd52a-path-to-model"></span> `path-to-model` | `VALUE` | `Path` |   | The path to the modelPath file (e.g., "/path/to/model.onnx") |
| <span id="a6fca5-path-to-tokenizer"></span> `path-to-tokenizer` | `VALUE` | `Path` |   | The path to the tokenizer file (e.g., "/path/to/tokenizer.json") |
| <span id="a20f0e-pooling-mode"></span> [`pooling-mode`](../config/dev_langchain4j_model_embedding_onnx_PoolingMode.md) | `VALUE` | `d.l.m.e.o.PoolingMode` |   | The pooling model to use |
| <span id="ab207e-type"></span> [`type`](../config/io_helidon_integrations_langchain4j_providers_lc4jinprocess_InProcessModelType.md) | `VALUE` | `i.h.i.l.p.l.InProcessModelType` |   | Which in-process ONNX model variant should be used |

See the [manifest](../config/manifest.md) for all available types.
