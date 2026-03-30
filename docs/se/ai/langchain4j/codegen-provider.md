# Model Provider Generator

## Contents

- [Overview](#overview)
- [Maven Coordinates](#maven-coordinates)
- [Lc4j Provider](#lc4j-provider)
- [Configuration](#configuration)
- [Injectable Properties](#injectable-properties)
- [Default Injectable Properties](#default-injectable-properties)

## Overview

The number of available AI providers and their models in the LangChain4j project is impressive and is growing every day. To maintain binding with all of them in Helidon is very challenging, and it may happen that the provider you are looking for is not one of those we are providing out of the box. But you can generate integration yourself the same way we are generating official LangChain4j binding modules, in build time, with our codegen tooling.

## Maven Coordinates

In addition to the [Helidon integration with LangChain4J core dependencies](langchain4j.md#maven-coordinates), you must add the following:

Extra dependency for LangChain4j provider you want to generate binding for, here we are using Google AI Gemini LangChain4j provider as an example:

``` xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-google-ai-gemini</artifactId>
    <version>1.1.0-rc1</version>
</dependency>
```

## Lc4j Provider

For instructing Helidon code generator what it should generate, you need to create a special Lc4jProvider interface, as it is used for code generation, it can be package private. Name of the provider interface needs to comply with convention and end with `Lc4jProvider`, prefix is by default used for deriving a config key and prefixes for generated classes. Interface needs to be annotated with `@AiProvider.ModelConfig` annotation, which takes as an argument LangChain4j model class we want to generate binding for. `@AiProvider.ModelConfig` is repeatable and you can configure multiple models for the same provider interface.

Example of custom generating LangChain4j GoogleAiGeminiChatModel integration:

``` java
import io.helidon.builder.api.Option;
import io.helidon.integrations.langchain4j.AiProvider;

import dev.langchain4j.model.googleai.GeminiSafetySetting;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;

@AiProvider.ModelConfig(GoogleAiGeminiChatModel.class) 
interface GoogleAiGeminiLc4jProvider { 

    @Option.Configured
    @Option.RegistryService 
    List<GeminiSafetySetting> safetySettings();
}
```

- Provide actual LangChain4j model we want to generate binding for.
- Name of the provider needs comply with convention and end with `Lc4jProvider`, prefix is used for deriving a config key
- Some properties can be too complex for configuration, we can supply them via injection instead

You may notice that the **safetySettings** property is manually configured in the provider interface, you can do that for the properties that are too complex for setting via configuration. By adding annotation `@Option.RegistryService` you make it injectable from Helidon’s service registry.

## Configuration

LangChain4j provider config key is by default derived from the provider interface name, example: `NameOfTheProviderLc4jProvider` → `name-of-the-provider`.

``` yaml
langchain4j:
  providers:
    google-ai-gemini: 
      api-key: ${GEMINI_AI_KEY}

  models:
    gemini-chat-model:
      provider: google-ai-gemini
      model-name: gemini-2.5-flash
```

- Config key derived from `GoogleAiGeminiLc4jProvider` interface name

## Injectable Properties

Injectable properties can be specified in the Lc4j Provider, when such property exists no properties with the same name are code generated. `@Option.RegistryService` annotation enables the property to be injectable. By default, beans of the same type are being looked up, when property has the type `java.util.List` all the beans of its generic type are injected.

``` java
@Option.Configured
@Option.RegistryService
List<GeminiSafetySetting> safetySettings();
```

It is possible to configure named qualifiers for injected beans, config property `service-registry.named` prefixed with the key of desired property is used as named qualifier for lookup when such property exists.

``` yaml
langchain4j:
  providers:
    google-ai-gemini:
      api-key: ${GEMINI_AI_KEY}

  models:
    gemini-chat-model:
      provider: google-ai-gemini
      model-name: gemini-2.5-flash
      safety-settings:
        service-registry.named: custom-named-settings 
```

- Named qualifier can be a string value used for looking up desired beans

Example of setting up a bean for injectable property `safety-settings`:

``` java
@Service.Singleton
@Service.Named("custom-named-setting")
public class CustomNamedSafetySettingFactory implements Supplier<GeminiSafetySetting> {
    @Override
    public GeminiSafetySetting get() {
        return new GeminiSafetySetting(HARM_CATEGORY_HATE_SPEECH, BLOCK_MEDIUM_AND_ABOVE);
    }
}
```

### Default Injectable Properties

Some properties usual for LangChain4j models are injectable by default to allow easy customization.

Types of properties injectable by default:

- `dev.langchain4j.model.chat.request.ChatRequestParameters` default parameters for all the models
- `dev.langchain4j.http.client.HttpClientBuilder` Custom http client
- `dev.langchain4j.model.chat.listener.ChatModelListener` Chat model listener for observability
