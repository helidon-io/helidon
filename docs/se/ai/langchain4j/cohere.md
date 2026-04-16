# Cohere

## Overview

This module adds support for selected Cohere models.

## Maven Coordinates

In addition to the [Helidon integration with LangChain4j core dependencies](langchain4j.md#maven-coordinates), you must add the following:

``` xml
<dependency>
    <groupId>io.helidon.integrations.langchain4j.providers</groupId>
    <artifactId>helidon-integrations-langchain4j-providers-cohere</artifactId>
</dependency>
```

## Components

### CohereEmbeddingModel

To automatically create and add `CohereEmbeddingModel` to the service registry add the following lines to `application.yaml`:

``` yaml
langchain4j:
  providers:
    cohere:
      api-key: "${COHERE_TOKEN}"

  models:
    cohere-embedding-model:
      provider: cohere
      model-name: "embed-english-v3.0"
```

If `enabled` is set to `false`, the configuration is ignored, and the component is not created.

Full list of configuration properties:

| Key | Type | Description |
|----|----|----|
| `api-key` | string | Required. The API key used to authenticate requests to the Cohere API. |
| `base-url` | string | The base URL for the model API. If not present, the default value supplied from LangChain4j is used. |
| `custom-headers` | Map\<string, string\> | A map containing custom headers. |
| `enabled` | boolean | If set to false, this component will not be available even if configured. |
| `input-type` | string | Input type. |
| `log-requests` | boolean | Whether to log API requests. |
| `log-responses` | boolean | Whether to log API responses. |
| `max-segments-per-batch` | int | Maximum number of segments per batch. |
| `model-name` | string | The model name to use. |
| `timeout` | duration | The timeout setting for API requests. See [here](https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-) for the format. |

### CohereScoringModel

To automatically create and add `CohereScoringModel` to the service registry add the following lines to `application.yaml`:

``` yaml
langchain4j:
  providers:
    cohere:
      api-key: "${COHERE_TOKEN}"

  models:
    cohere-scoring-model:
      provider: cohere
      model-name: "rerank-english-v3.0"
```

If `enabled` is set to `false`, the configuration is ignored, and the component is not created.

Full list of configuration properties:

| Key | Type | Description |
|----|----|----|
| `api-key` | string | Required. The API key used to authenticate requests to the Cohere API. |
| `base-url` | string | The base URL for the model API. If not present, the default value supplied from LangChain4j is used. |
| `custom-headers` | Map\<string, string\> | A map containing custom headers. |
| `enabled` | boolean | If set to false, this component will not be available even if configured. |
| `log-requests` | boolean | Whether to log API requests. |
| `log-responses` | boolean | Whether to log API responses. |
| `max-retries` | int | The maximum number of retries for failed API requests. |
| `model-name` | string | The model name to use. |
| `timeout` | duration | The timeout setting for API requests. See [here](https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-) for the format. |

## Additional Information

- [LangChain4j Integration](langchain4j.md)
- [LangChain4j Cohere Documentation](https://docs.langchain4j.dev/integrations/embedding-models/cohere)
