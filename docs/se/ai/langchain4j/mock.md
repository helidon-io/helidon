# Mock ChatModel

## Contents

- [Overview](#overview)
- [Maven Coordinates](#maven-coordinates)
- [Components](#components)
  - [MockChatModel](#mockchatmodel)
- [Additional Information](#additional-information)

## Overview

The mock chat model enables deterministic testing of LangChain4j features such as agents, tools, and chat memory without invoking an external AI service. By configuring rule patterns, fixed responses, and templated replies, tests remain reproducible and stable across runs, allowing developers to verify interaction logic, component chaining, and error handling in isolation.

## Maven Coordinates

In addition to the [Helidon integration with LangChain4J core dependencies](langchain4j.md#maven-coordinates), you must add the following:

``` xml
<dependency>
    <groupId>io.helidon.integrations.langchain4j.providers</groupId>
    <artifactId>helidon-integrations-langchain4j-providers-mock</artifactId>
</dependency>
```

## Components

### MockChatModel

To automatically create and add `MockChatModel` to the service registry add the following lines to `application.yaml`:

``` java
@Ai.Service("food-service") 
@Ai.ChatModel("production-chatgpt-model") 
public interface FoodExpertAiService {

    @SystemMessage("You are a food expert!")
    String chat(String prompt);
}
```

- Naming your AI service makes its configuration easily overridable from Helidon config.
- Chat model name annotation configuration is overridable by Helidon config

To configure `MockChatModel` to be used, for example, in a test scenario you define your model in `application.yaml` and override a chat model name configured by `@Ai.ChatModel` annotation in FoodExpertAiService:

``` yaml
langchain4j:
  services:
    food-service:
      chat-model: test-mock-model 

  providers:
    helidon-mock: {}

  models:
    test-mock-model:
      provider: helidon-mock
      rules:
        - pattern: .*pizza.*ananas.*
          response: Don't!
        - pattern: .*Return this message:\s+'([^']+)'.*
          template: "The message is: $1"
```

- Override `production-chatgpt-model` chat model in `food-service` named AI service with `test-mock-model`.

The final unit test would look like the following snippet.

``` java
@Testing.Test
class FoodExpertTest {
    @Test
    void customMockResponse(FoodExpertAiService aiService) {
        assertThat(aiService.chat("I can prepare pizza with ananas!"), is("Don't!"));
        assertThat(aiService.chat("Return this message: 'test-message'"), is("The message is: test-message"));
    }
}
```

It is possible to inject a mock model and amend the rule programmatically.

``` java
@Testing.Test
class FoodExpertTest {
    @Test
    void customMockResponse(FoodExpertAiService aiService, @Service.Named("test-mock-model") MockChatModel mockModel) {
        try {
            mockModel.activeRules().add(new MockChatRule() {
                @Override
                public boolean matches(ChatRequest req) {
                    return true;
                }

                @Override
                public String mock(String concatenatedReq) {
                    return "Custom manually added response!";
                }
            });
            assertThat(aiService.chat("I can prepare pizza with ananas!"), is("Custom manually added response!"));
        } finally {
            mockModel.resetRules();
        }
    }
}
```

### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="ac9963-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false` , MockChatModel will not be available even if configured |
| <span id="aa2a29-rules"></span> [`rules`](../../../config/io_helidon_integrations_langchain4j_providers_mock_MockChatRule.md) | `LIST` | `i.h.i.l.p.m.MockChatRule` |   | The list of `MockChatRule`s that the mock chat model evaluates |

## Additional Information

- [LangChain4J Integration](langchain4j.md)
