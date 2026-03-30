# io.helidon.integrations.langchain4j.providers.lc4jinprocess.InProcessModelType

## Description

This type is an enumeration.

## Usages

- [`langchain4j.providers.lc4j-in-process.type`](io_helidon_integrations_langchain4j_providers_lc4jinprocess_InProcessEmbeddingModelConfig.md#ab207e-type)

## Allowed Values

| Value | Description |
|----|----|
| `ALL_MINILM_L6_V2` | The default "all-minilm-l6-v2" in-process embedding model |
| `ALL_MINILM_L6_V2_Q` | The quantized variant of the "all-minilm-l6-v2" in-process embedding model, typically offering reduced memory footprint and potentially faster inference at some quality cost |
| `CUSTOM` | A custom, user-provided in-process ONNX embedding model, when selected \`path-to-model\` and \`path-to-tokenizer\` needs to be provided |

See the [manifest](../config/manifest.md) for all available types.
