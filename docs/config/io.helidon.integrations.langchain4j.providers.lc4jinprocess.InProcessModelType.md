# io.<wbr>helidon.<wbr>integrations.<wbr>langchain4j.<wbr>providers.<wbr>lc4jinprocess.<wbr>InProcess<wbr>Model<wbr>Type

## Description

This type is an enumeration.

## Allowed Values

<table>
<thead>
<tr>
<th>Value</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>ALL_<wbr>MINILM_<wbr>L6_V2</code></td>
<td>The default "all-minilm-l6-v2" in-process embedding model</td>
</tr>
<tr>
<td><code>ALL_<wbr>MINILM_<wbr>L6_V2_<wbr>Q</code></td>
<td>The quantized variant of the "all-minilm-l6-v2" in-process embedding model, typically offering reduced memory footprint and potentially faster inference at some quality cost</td>
</tr>
<tr>
<td><code>CUSTOM</code></td>
<td>A custom, user-provided in-process ONNX embedding model, when selected `path-to-model` and `path-to-tokenizer` needs to be provided</td>
</tr>
</tbody>
</table>

## Usages

- <a href="io.helidon.integrations.langchain4j.providers.lc4jinprocess.InProcessEmbeddingModelConfig.md#type"><code>langchain4j.<wbr>providers.<wbr>lc4j-<wbr>in-process.<wbr>type</code></a>

---

See the [manifest](manifest.md) for all available types.
