# Jlama

## Contents

- [Overview](#overview)
- [Maven Coordinates](#maven-coordinates)
- [Components](#components)
  - [JlamaChatModel](#jlamachatmodel)
  - [JlamaEmbeddingModel](#jlamaembeddingmodel)
  - [JlamaLanguageModel](#jlamalanguagemodel)
  - [JlamaStreamingChatModel](#jlamastreamingchatmodel)
- [Additional Information](#additional-information)

## Overview

This module adds support for selected [Jlama](https://github.com/tjake/Jlama) models.

## Maven Coordinates

In addition to the [Helidon integration with LangChain4J core dependencies](langchain4j.md#maven-coordinates), you must add the following:

``` xml
<dependency>
    <groupId>io.helidon.integrations.langchain4j.providers</groupId>
    <artifactId>helidon-integrations-langchain4j-providers-jlama</artifactId>
</dependency>
```

## Components

### JlamaChatModel

To automatically create and add `JlamaChatModel` to the service registry add the following lines to `application.yaml`:

``` yaml
langchain4j:
  providers:
    jlama:
      temperature: 1.2

  models:
    jlama-chat-model:
      provider: jlama
      model-name: "tjake/Qwen2.5-0.5B-Instruct-JQ4"
```

If `enabled` is set to `false`, the configuration is ignored, and the component is not created.

Full list of configuration properties:

| Key | Type | Description |
|----|----|----|
| `enabled` | boolean | If set to false, the component will not be available even if configured. |
| `model-name` | string | The model name to use. |
| `temperature` | double | Sampling temperature to use, between 0 and 2. Higher values make the output more random, while lower values make it more focused and deterministic. |
| `working-quantized-type` | enum | Quantize the model at runtime. Default quantization is Q4. |
| `model-cache-path` | Path | Path to a directory where the model will be cached once downloaded. |
| `working-directory` | Path | Path to a directory where persistent ChatMemory can be stored on disk for a given model instance. |
| `auth-token` | string | Token to use when fetching private models from [Hugging Face](https://huggingface.co/) |
| `max-tokens` | integer | Maximum number of tokens to generate. |
| `thread-count` | integer | Number of threads to use. |
| `quantize-model-at-runtime` | boolean | Whether quantize the model at runtime. |

### JlamaEmbeddingModel

To automatically create and add `JlamaEmbeddingModel` to the service registry add the following lines to `application.yaml`:

``` yaml
langchain4j:
  providers:
    jlama:
      temperature: 1.2

  models:
    jlama-embedding-model:
      provider: jlama
      model-name: "tjake/Qwen2.5-0.5B-Instruct-JQ4"
```

If `enabled` is set to `false`, the configuration is ignored, and the component is not created.

Full list of configuration properties:

| Key | Type | Description |
|----|----|----|
| `enabled` | boolean | If set to false, the component will not be available even if configured. |
| `model-name` | string | The model name to use. |
| `model-cache-path` | Path | Path to a directory where the model will be cached once downloaded. |
| `working-directory` | Path | Path to a directory where persistent ChatMemory can be stored on disk for a given model instance. |
| `auth-token` | string | Token to use when fetching private models from [Hugging Face](https://huggingface.co/) |
| `thread-count` | integer | Number of threads to use. |
| `pooling-type` | enum | Method of embedding pooling. |

### JlamaLanguageModel

To automatically create and add `JlamaLanguageModel` to the service registry add the following lines to `application.yaml`:

``` yaml
langchain4j:
  providers:
    jlama:
      temperature: 1.2

  models:
    jlama-language-model:
      provider: jlama
      model-name: "tjake/Qwen2.5-0.5B-Instruct-JQ4"
```

If `enabled` is set to `false`, the configuration is ignored, and the component is not created.

Full list of configuration properties:

| Key | Type | Description |
|----|----|----|
| `enabled` | boolean | If set to false, the component will not be available even if configured. |
| `model-name` | string | The model name to use. |
| `temperature` | double | Sampling temperature to use, between 0 and 2. Higher values make the output more random, while lower values make it more focused and deterministic. |
| `working-quantized-type` | enum | Quantize the model at runtime. Default quantization is Q4. |
| `model-cache-path` | Path | Path to a directory where the model will be cached once downloaded. |
| `working-directory` | Path | Path to a directory where persistent ChatMemory can be stored on disk for a given model instance. |
| `auth-token` | string | Token to use when fetching private models from [Hugging Face](https://huggingface.co/) |
| `max-tokens` | integer | Maximum number of tokens to generate. |
| `thread-count` | integer | Number of threads to use. |
| `quantize-model-at-runtime` | boolean | Whether quantize the model at runtime. |

### JlamaStreamingChatModel

To automatically create and add `JlamaStreamingChatModel` to the service registry add the following lines to `application.yaml`:

``` yaml
langchain4j:
  providers:
    jlama:
      temperature: 1.2

  models:
    jlama-streaming-chat-model:
      provider: jlama
      model-name: "tjake/Qwen2.5-0.5B-Instruct-JQ4"
```

If `enabled` is set to `false`, the configuration is ignored, and the component is not created.

Full list of configuration properties:

| Key | Type | Description |
|----|----|----|
| `enabled` | boolean | If set to false, the component will not be available even if configured. |
| `model-name` | string | The model name to use. |
| `temperature` | double | Sampling temperature to use, between 0 and 2. Higher values make the output more random, while lower values make it more focused and deterministic. |
| `working-quantized-type` | enum | Quantize the model at runtime. Default quantization is Q4. |
| `model-cache-path` | Path | Path to a directory where the model will be cached once downloaded. |
| `working-directory` | Path | Path to a directory where persistent ChatMemory can be stored on disk for a given model instance. |
| `auth-token` | string | Token to use when fetching private models from [Hugging Face](https://huggingface.co/) |
| `max-tokens` | integer | Maximum number of tokens to generate. |
| `thread-count` | integer | Number of threads to use. |
| `quantize-model-at-runtime` | boolean | Whether quantize the model at runtime. |

## Additional Information

- [LangChain4J Integration](langchain4j.md)
- [LangChain4J Jlama Documentation](https://docs.langchain4j.dev/integrations/language-models/jlama/)
- [Jlama Website](https://github.com/tjake/Jlama)
