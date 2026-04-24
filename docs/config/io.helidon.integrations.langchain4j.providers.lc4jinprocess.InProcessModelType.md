# io.helidon.integrations.langchain4j.providers.lc4jinprocess.InProcessModelType

## Description

This type is an enumeration.

## Allowed Values

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Value</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>ALL_MINILM_L6_V2</code></td>
<td>The default "all-minilm-l6-v2" in-process embedding model</td>
</tr>
<tr>
<td><code>ALL_MINILM_L6_V2_Q</code></td>
<td>The quantized variant of the "all-minilm-l6-v2" in-process embedding model, typically offering reduced memory footprint and potentially faster inference at some quality cost</td>
</tr>
<tr>
<td><code>CUSTOM</code></td>
<td>A custom, user-provided in-process ONNX embedding model, when selected `path-to-model` and `path-to-tokenizer` needs to be provided</td>
</tr>
</tbody>
</table>

## Usages

- [`langchain4j.providers.lc4j-in-process.type`](io.helidon.integrations.langchain4j.providers.lc4jinprocess.InProcessEmbeddingModelConfig.md#type)

---

See the [manifest](manifest.md) for all available types.
