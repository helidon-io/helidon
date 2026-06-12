# Google Gemini

## Overview

This module adds support for selected [Google Gemini][google-gemini] models.

## Maven Coordinates

In addition to the [Helidon integration with LangChain4j core dependencies](langchain4j.md#maven-coordinates), you must add the following:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.integrations.langchain4j.providers</groupId>
  <artifactId>helidon-integrations-langchain4j-providers-google-gemini</artifactId>
</dependency>
```

## Components

### GoogleAiGeminiChatModel

To automatically create and add `GoogleAiGeminiChatModel` to the service registry add the following lines to `application.yaml`:

```yaml [application.yaml]
langchain4j:
  providers:
    google-gemini:
      api-key: "${GEMINI_TOKEN}"

  models:
    gemini-chat-model:
      provider: google-gemini
      model-name: "gemini-2.0-flash"
```

If `enabled` is set to `false`, the configuration is ignored, and the component is not created.

### Configuration options

<!--@include ../../../config/io.helidon.integrations.langchain4j.providers.gemini.GoogleAiGeminiChatModelConfig.md#configuration-options delim=--- offset=1 collapseTables=10 -->
See [Configuration options](../../../config/io.helidon.integrations.langchain4j.providers.gemini.GoogleAiGeminiChatModelConfig.md#configuration-options).
<!--/include-->


### GoogleAiGeminiStreamingChatModel

To automatically create and add `GoogleAiGeminiStreamingChatModel` to the service registry add the following lines to `application.yaml`:

```yaml [application.yaml]
langchain4j:
  providers:
    google-gemini:
      api-key: "${GEMINI_TOKEN}"

  models:
    gemini-streaming-chat-model:
      provider: google-gemini
      model-name: "gemini-2.0-flash"
```

If `enabled` is set to `false`, the configuration is ignored, and the component is not created.

### Configuration options

<!--@include ../../../config/io.helidon.integrations.langchain4j.providers.gemini.GoogleAiGeminiStreamingChatModelConfig.md#configuration-options delim=--- offset=1 collapseTables=10 -->
See [Configuration options](../../../config/io.helidon.integrations.langchain4j.providers.gemini.GoogleAiGeminiStreamingChatModelConfig.md#configuration-options).
<!--/include-->


## Additional Information

- [LangChain4j Integration](langchain4j.md)
- [LangChain4j Google AI Gemini Documentation][langchain4j-goog]
- [Google AI Gemini Website][google-ai-gemini]

[google-gemini]: https://ai.google.dev/gemini-api/docs/models
[langchain4j-goog]: https://docs.langchain4j.dev/integrations/language-models/google-ai-gemini
[google-ai-gemini]: https://ai.google.dev/gemini-api/docs
