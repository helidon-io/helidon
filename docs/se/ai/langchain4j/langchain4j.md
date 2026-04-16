# LangChain4j

## Overview

[LangChain4j](https://github.com/langchain4j/langchain4j) is a Java framework for building AI-powered applications using Large Language Models (LLMs). It provides seamless integration with multiple LLM providers, including OpenAI, Cohere, Hugging Face, and others. Key features include AI Services and Agents for model interaction, support for Retrieval-Augmented Generation (RAG) to enhance responses with external data, and tools for working with embeddings and knowledge retrieval.

Helidon provides a LangChain4j integration module that simplifies the use of LangChain4j in Helidon applications.

> [!NOTE]
> LangChain4j integration is a preview feature. The APIs shown here are subject to change. These APIs will be finalized in a future release of Helidon.

## Features

- **Integration with Helidon Inject**

  Automatically creates and registers selected LangChain4j components in the Helidon service registry based on configuration.

- **Integration with CDI**

  Thanks to the **Helidon Inject to CDI Bridge**, LangChain4j components can be used in CDI environments, including Helidon MP applications.

- **Declarative AI Services and Agents**

  Supports [LangChain4j’s AI Services](https://docs.langchain4j.dev/tutorials/ai-services/) and [Agents](https://docs.langchain4j.dev/tutorials/agents/) within the declarative programming model, allowing for clean, easy-to-manage code structures.

## Maven Coordinates

To enable LangChain4j Integration, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.integrations.langchain4j</groupId>
    <artifactId>helidon-integrations-langchain4j</artifactId>
</dependency>
```

Include the following annotation processor in the `<build><plugins>` section of `pom.xml`:

``` xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>io.helidon.bundles</groupId>
                <artifactId>helidon-bundles-apt</artifactId>
                <version>${helidon.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

Some features of the integration may require adding other dependencies. Check the corresponding sections for additional information.

## General Concepts

LangChain4j integration in Helidon is orchestrating LangChain4j AiServices and Agents as named singleton declarative service beans. Singletons can be referenced by their names and created either by configuration-driven [providers](#providers) or manually as any other declarative Helidon service bean, with [supplier factory](#supplier-factory), for example.

### Providers

Helidon LangChain4j providers are extensions that enable integration with a range of AI vendors. Each provider is identified and configured using a unique key. You can define multiple named models, reuse provider-level configuration, or override it with model-specific properties.

![Provider and model configuration merge](../../../images/lc4j/model-provider-config.svg)

Once you have configured named model, like `cheaper-model` on the example above, you can reference it from AiServices and Agents via `@Ai.ChatModel` or `@Ai.StreamingChatModel` annotations by its name:

``` java
@Ai.Service
@Ai.ChatModel("cheaper-model") //<1>
public interface ChefAiService {
    @SystemMessage("You are an expert in the food preparation.")
    @UserMessage("""
            Provide a short step-by-step instructions how to prepare: {{foodName}}
            """)
    String cookingInstructions(@V("foodName") String foodName);
}
```

1.  Custom name selected in the model configuration above

Providers available out of the box:

|  |  |  |
|----|----|----|
| Provider | Provider Key | Description |
| [**LangChain4j content retriever**](lc4j-providers.md#Lc4jContentRetrieverProvider) | `lc4j-content-retriever` | Built-in content-retriever for RAG |
| [**LangChain4j in-memory embedding store**](lc4j-providers.md#Lc4jInMemoryEmbeddingStoreProvider) | `lc4j-in-memory` | Built-in in-memory embedding store |
| [**LangChain4j in-process models**](lc4j-in-process.md) | `lc4j-in-process` | Local [in-process models](https://docs.langchain4j.dev/integrations/embedding-models/in-process) |
| [**Open AI**](open-ai.md) | `open-ai` | OpenAI and OpenAI compatible models providers |
| [**OCI GenAI**](oci-genai.md) | `oci-gen-ai`, `oci-gen-ai-cohere` | Oracle Cloud Infrastructure GenAI models |
| [**Jlama**](jlama.md) | `jlama` | Local inference with selected [Jlama](https://github.com/tjake/Jlama) models |
| [**Google Gemini**](gemini.md) | `google-gemini` | Google Gemini hosted models |
| [**Ollama**](ollama.md) | `ollama` | Ollama hosted models support |
| [**Cohere**](cohere.md) | `cohere` | Cohere hosted models |
| [**Oracle Embedding Store**](oracle.md) | `oracle` | Oracle Database as an embedding store |
| [**Coherence Embedding Store**](coherence.md) | `coherence` | Coherence as embedding and chat memory store |
| [**Mock**](mock.md) | `helidon-mock` | Mockable chat model for deterministic testing |

> [!NOTE]
> Missing your favorite AI vendor already supported by LangChain4j? You can generate your own Helidon integration with our [**LangChain4j Model Provider Generator**](codegen-provider.md) or use the supplier factory!

### Supplier Factory

Supplier Factory provides another way to create and register LangChain4j components. It is useful for creating components that are not yet natively supported by the integration, such as ChatModels, Embedding Models, Embedding Stores or Content Retrievers. This method is not limited to LangChain4j and suitable for creating and registering other classes.

The example below demonstrates a supplier factory for `MistralAiChatModel`.

``` java
@Service.Singleton
@Service.Named("custom-chat-model") //(1)
class ChatModelFactory implements Supplier<ChatModel> {
    @Override
    public ChatModel get() {
        return MistralAiChatModel.builder()
                .apiKey(ApiKeys.MISTRALAI_API_KEY)
                .modelName(MistralAiChatModelName.MISTRAL_SMALL_LATEST)
                .build();
    }
}
```

1.  Custom name of the resulting declarative service bean referencable from Ai Services or Agents

> [!NOTE]
> Supplier factories can be **standalone** or **static inner** classes.

To use such a manually created model, reference it by name.

``` java
@Ai.Service
@Ai.ChatModel("custom-chat-model") //<1>
public interface ChefAiService {
    @SystemMessage("You are an expert in the food preparation.")
    @UserMessage("""
            Provide a short step-by-step instructions how to prepare: {{foodName}}
            """)
    String cookingInstructions(@V("foodName") String foodName);
}
```

1.  Custom name selected in the supplier factory above

## Configuration

Helidon LangChain4j uses a unified configuration that separates **providers** (shared configuration) from **named components** such as models, embedding stores, content retrievers, services, and agents. Components are enabled by default; add `enabled: false` to disable a component entry explicitly.

Key concepts:

- **Providers**: Shared configuration per provider key, with optional defaults for model/embedding store creation.
- **Named components**: Models, embedding stores, content retrievers, services, and agents are named entries and become named singleton beans.
- **Multiple models per provider**: You can configure multiple models for a single provider by adding multiple entries under `langchain4j.models`.
- **Overrides**: Component configuration overrides provider defaults during merge.

``` yaml
langchain4j:
  providers:
    foo-bar-provider-name:
      # config common for all models/embedding stores referencing this provider

  models:
    foo-bar-model-name:
      # model-specific config is merged with provider config; model is named singleton bean 'foo-bar-model-name'
      provider: foo-bar-provider-name

  services:
    foo-bar-service-name:
      # overrides service annotation setup
      streaming-chat-model: foo-bar-model-name

  agents:
    foo-bar-agent-name:
      # overrides agent annotation setup
      chat-model: foo-bar-model-name

  embedding-stores:
    foo-bar-embedding-store-name:
      provider: foo-bar-provider-name

  content-retrievers:
    foo-bar-content-retriever-name:
      provider: helidon
      type: embedding-store-content-retriever

  mcp-clients:
    foo-bar-mcp-server:
      uri: http://foo-bar-mcp-server:1234/foo
```

### Configuration Migration Guide

> [!WARNING]
> The configuration format has changed in version 4.4 in a backward-incompatible way.

Changes in 4.4:

- Provider configuration now lives under `langchain4j.providers`.
- Models and embedding stores are named entries under `langchain4j.models` and `langchain4j.embedding-stores` etc.
- Providers and components are enabled by default (set `enabled: false` to disable).
- You can configure multiple models of the same type by adding multiple entries under `langchain4j.models`.

#### Example Migration

Pre 4.4 configuration:

``` yaml
langchain4j:
  open-ai:
    # Models were referenced by the provider name, in this case 'open-ai'
    chat-model:
      enabled: true
      api-key: "${OPEN_AI_TOKEN}"
      model-name: "gpt-4o-mini"
```

New configuration as documented in [Configuration](#configuration):

``` yaml
langchain4j:
  models:
    # Custom model names are required, to be referencable, in this case 'cheaper-model'
    cheaper-model:
      provider: open-ai
      api-key: "${OPEN_AI_TOKEN}"
      model-name: "gpt-4o-mini"
```

## Declarative AI

LangChain4j AI Services provide a declarative and type-safe way to define AI-powered functionality. It allows combining chat models, retrieval-augmented generation (RAG), chat memory, and other building blocks to create sophisticated AI-driven workflows. Read more about it in [LangChain4j documentation](https://docs.langchain4j.dev/tutorials/ai-services).

Helidon LangChain4j integration provides two declarative approaches for using AI:

- AI Services
- Agents and Agentic Workflows

Services and Agents are typically Java interfaces annotated with LangChain4j and Helidon annotations, the resulting implementation is created by LangChain4j runtime and wired together by [Helidon as singleton declarative service beans](../../../se/injection/injection.md). You can access those anywhere in Helidon with `Services.get(FooBarAiService.class)` or inject it in another service bean as constructor parameter. Thanks to the CDI bridge, you can inject AI Services and Agents even to Helidon MP CDI beans.

Both AI Services and Agents can be configured with the following Helidon annotations:

|  |  |
|----|----|
| Annotation | Description |
| `Ai.ChatModel` | Specifies the name of a service in the service registry that implements `ChatModel` to be used in the annotated AI Service. Mutually exclusive with `Ai.StreamingChatModel`. |
| `Ai.StreamingChatModel` | Specifies the name of a service in the service registry that implements `StreamingChatModel` to use in the annotated Ai Service. Mutually exclusive with `Ai.ChatModel`. |
| `Ai.ChatMemoryProvider` | Specifies the name of a service in the service registry that implements `ChatMemoryProvider` to use in the annotated Ai Service. |
| `Ai.ModerationModel` | Specifies the name of a service in the service registry that implements `ModerationModel` to use in the annotated Ai Service. |
| `Ai.ContentRetriever` | Specifies the name of a service in the service registry that implements `ContentRetriever` to use in the annotated Ai Service. Mutually exclusive with `Ai.RetrievalAugmentor`. |
| `Ai.RetrievalAugmentor` | Specifies the name of a service in the service registry that implements `RetrievalAugmentor` to use in the annotated Ai Service. Mutually exclusive with `Ai.ContentRetriever`. |
| `Ai.ToolProvider` | Specifies the name of a service in the service registry that implements `ToolProvider` to use in the annotated Ai Service. Mutually exclusive with `Ai.McpClients`. |
| `Ai.Tools` | Specifies the classes with tools. In case a singleton service bean of the same type exists, its instance is supplied. |
| `Ai.McpClients` | Specifies the name/s of a `McpClient` in the service registry that implements `ToolProvider` to use in the annotated Ai Service. `McpToolProvider` is created from these clients. Mutually exclusive with `Ai.ToolProvider`. |

### AI Services

AI Service is defined by a Java interface. It’s a pure LangChain4j component. Refer to [LangChain4j documentation](https://docs.langchain4j.dev/tutorials/ai-services) to read more details about it.

Helidon’s LangChain4j integration provides a specialized set of annotations for creating, configuring, and using LangChain4j AI Services in Helidon applications.

To create an AI Service define an interface and annotate it with `@Ai.Service`.

``` java
@Ai.Service
public interface ChatAiService {
    String chat(String question);
}
```

In Helidon, AI Service implementations are created as singleton declarative service beans. Unlike agents, AI Service names are optional. If you do not provide a name, the service is typically consumed by its interface type. If you provide a name (`@Ai.Service("name")`), that name becomes a declarative bean name for config, qualified injection, and lookup.

``` java
@Ai.Service("chat-assistant")
public interface NamedChatAiService {
    String chat(String question);
}
```

``` java
@Service.Singleton
public class ChatEndpoint {
    private final ChatAiService defaultChatService;
    private final NamedChatAiService namedChatService;

    ChatEndpoint(ChatAiService defaultChatService, //(1)
                 @Service.Named("chat-assistant") NamedChatAiService namedChatService) { //(2)
        this.defaultChatService = defaultChatService;
        this.namedChatService = namedChatService;
    }
}
```

1.  Injection by type for unnamed/default AI Service bean.
2.  Qualified injection for explicitly named AI Service bean.

``` java
ChatAiService defaultService = Services.get(ChatAiService.class); //(1)
NamedChatAiService namedService = Services.getNamed(NamedChatAiService.class, "chat-assistant"); //(2)
```

1.  Programmatic lookup by type.
2.  Programmatic lookup by declarative bean name.

Named AI services can be configured under `langchain4j.services`, where values in configuration override annotation values.

### Agents

LangChain4j agents are AI services enhanced for agentic workflows. In Helidon, each agent is a named declarative singleton service. Agent configuration can be set using annotations and overridden by Helidon config under `langchain4j.agents`. Compared to [AI Services](https://docs.langchain4j.dev/tutorials/ai-services), which are typically used as typed service-layer entry points, agents are designed to collaborate inside workflows and pure agentic systems ([Agents documentation](https://docs.langchain4j.dev/tutorials/agents)). In practice, agents keep AI Service capabilities but add agentic composition concerns such as explicit agent identity and workflow state exchange. The key difference is that agent results are commonly written into shared agentic context (for example via `outputKey`) so that other agents can consume them in subsequent workflow steps.

To define a named agent create an interface using `@Ai.Agent` and annotate the method with LangChain4j `@Agent`:

``` java
@Ai.Agent("cli-expert")
@Ai.ChatModel("custom-model-name")
@Ai.McpClients("cli-tools-mcp-server")
public interface CliExpert {

    @UserMessage("""
            You are a command line expert helping users with Helidon CLI.
            Provide a short step-by-step answer to the request: {{request}}
            and always include the exact CLI command on its own line.
            """)
    @Agent(value = "Helidon CLI specialist", outputKey = "response")
    String answer(@V("request") String request);
}
```

Agent names are required (`@Ai.Agent("…​")`), can be arbitrary, and become declarative service bean names in Helidon. Use stable, descriptive names because these names are used for configuration (`langchain4j.agents.<name>`), injection, and programmatic lookup.

``` java
@Service.Singleton
public class CliCoordinator {
    private final CliExpert cliExpert;

    CliCoordinator(@Service.Named("cli-expert") CliExpert cliExpert) { //(1)
        this.cliExpert = cliExpert;
    }
}
```

1.  The qualifier value must match the agent name from `@Ai.Agent("cli-expert")`. This is the declarative style: `CliCoordinator` is a Helidon declarative bean and the named agent is injected by the service registry.

``` java
CliExpert cliExpert = Services.getNamed(CliExpert.class, "cli-expert"); //(1)
String answer = cliExpert.answer("How do I generate a Helidon SE project?");
```

1.  This is programmatic lookup: fetch the named agent directly from Helidon’s service registry at runtime.

Agents can be configured or overridden using `langchain4j.agents.<agent-name>` entries, for example to replace a chat model or adjust an output key.

![LangChain4j agents in Helidon](../../../images/lc4j/agents.svg)

#### Agentic Workflow

Helidon supports LangChain4j declarative agentic workflows such as sequence and conditional agents. Each subagent remains a named Helidon service, and agentic systems can be composed using declarative annotations like `@SequenceAgent` and `@ConditionalAgent`. Helidon follows the LangChain4j declarative agent API, so many other agent types from LangChain4j can be used in the same way. For the full set of patterns and annotations, see the [LangChain4j Agents documentation](https://docs.langchain4j.dev/tutorials/agents).

The following workflow example illustrates how declarative composition works in practice. Agentic workflow allows composing more complicated behavior in a declarative way while keeping each agent focused on a single task. Instead of wiring orchestration code manually, you define sequence and conditional flow with annotations and let LangChain4j execute the workflow using shared agentic context. This makes it easy to connect specialized agents with guardrails, tools, MCP clients/servers, retrieval components, model selection, memory, and other LangChain4j capabilities. In Helidon, all of these remain named declarative service beans, so workflow topology and capabilities can be evolved with minimal boilerplate using annotations and configuration.

![Agentic workflow with sequence and conditional agents](../../../images/lc4j/agentic-workflow.svg)

For example, `@SequenceAgent` runs listed subagents in order and shares intermediate outputs through workflow variables in the agentic context - a shared execution state where one agent writes values (for example via `outputKey`) and later agents read them (for example through `@V` parameters). In this workflow, `FlavorClassifierAgent` runs first and stores `flavor`, then `FlavorRouterAgent` uses that value to select the appropriate expert and produce the final `response` output key. For more details about agentic context and workflow state, see [LangChain4j Agents documentation](https://docs.langchain4j.dev/tutorials/agents).

``` java
@Ai.Agent("helidon-expert")
public interface HelidonExpertAgent {

    @SequenceAgent(outputKey = "response", subAgents = {
            FlavorClassifierAgent.class,
            FlavorRouterAgent.class
    })
    String ask(@V("request") String request);
}
```

`FlavorClassifierAgent` classifies the incoming request and writes `flavor` used for routing. This also demonstrates a specialized agent pattern: a focused subagent adds structured information to the agentic context, so later subagents can make better decisions without repeating the same analysis:

``` java
@Ai.Agent("flavor-classifier")
@Ai.ChatModel("cheap-model")
public interface FlavorClassifierAgent {

    @Agent(value = "Categorize a user request", outputKey = "flavor")
    HelidonFlavor classify(@V("question") String question);
}
```

Example of conditional agent is `FlavorRouterAgent`, which conditionally activates one expert subagent based on the classified flavor. `@ConditionalAgent` evaluates activation conditions for configured subagents using values already present in agentic context. In this example, `activateSeExpert` and `activateMpExpert` both read `flavor` from context (`@V("flavor")`) and enable only the matching expert. The active subagent then runs and its result is used as the router output, so the workflow can continue with the response from the selected specialized agent:

``` java
@Ai.Agent("flavor-router")
public interface FlavorRouterAgent {

    @ConditionalAgent(subAgents = {
            HelidonMpExpert.class,
            HelidonSeExpert.class
    })
    String askExpert(@V("question") String question);

    @ActivationCondition(HelidonSeExpert.class)
    static boolean activateSeExpert(@V("flavor") HelidonFlavor flavor) {
        return flavor == HelidonFlavor.SE;
    }

    @ActivationCondition(HelidonMpExpert.class)
    static boolean activateMpExpert(@V("flavor") HelidonFlavor flavor) {
        return flavor == HelidonFlavor.MP;
    }
}
```

Last example is specialized agent `HelidonSeExpert`, focused on Helidon SE questions. As described in LangChain4j agents concepts, agents are most effective when they have a clear, narrow responsibility and the exact capabilities needed for that responsibility. This agent reads workflow input from agentic context via `@V("question")`, uses an SE-focused prompt with a selected chat model, content retriever, and tools, and writes its result back to agentic context through `outputKey = "lastResponse"` for downstream workflow steps (see [LangChain4j Agents documentation](https://docs.langchain4j.dev/tutorials/agents)):

``` java
@Ai.Agent("helidon-se-expert")
@Ai.ChatModel("expensive-model")
@Ai.ContentRetriever("se-content-retriever")
@Ai.Tools(CliTools.class)
public interface HelidonSeExpert {

    @Agent(value = "A Helidon SE expert", outputKey = "lastResponse")
    String askExpert(@V("question") String question);
}
```

## Tools (Callback Functions)

In LangChain4j, tools are callback functions that the language model can invoke during a conversation to perform specific tasks, retrieve information, or execute external logic. These tools extend the model’s capabilities beyond simple text generation, allowing it to dynamically interact with external systems. For instance, a tool might query a database, call an external API, or perform calculations. Based on user input, the model can decide to call a tool, interpret its response, and incorporate it into the conversation for a more context-aware and multi-step interaction.

To expose a method in a Helidon service as a tool, annotate it with the `@Tool` annotation from `dev.langchain4j.agent.tool.Tool`, as shown in the example below:

``` java
@Service.Singleton
public class OrderService {

    @Tool("Get order details for specified order number")
    public Order getOrderDetails(String orderNumber) {
        // ...
    }
}
```

> [!NOTE]
> If you are using Helidon MP, to enable `@Tool`-annotated methods in CDI beans, you must annotate the CDI bean with the `@Ai.Tool` qualifier.

For more details, read the [LangChain4j Documentation on Tools](https://docs.langchain4j.dev/tutorials/tools#high-level-tool-api).

## Guardrails

LangChain4j guardrails validate input and output around model calls and help enforce application-level policies, for example: scope checks, prompt-injection protection, output validation, retries, or reprompts. For guardrail concepts and behavior details, see the [LangChain4j Guardrails documentation](https://docs.langchain4j.dev/tutorials/guardrails).

In Helidon, guardrails can be regular singleton declarative service beans. When a guardrail class is annotated with `@Service.Singleton`, Helidon can create and inject dependencies into it, and LangChain4j resolves that class instance from the Helidon service registry.

The following snippet shows an input guardrail implemented as a Helidon singleton bean:

``` java
@Service.Singleton
public class ForbiddenWordsInputGuardrail implements InputGuardrail {

    private final List<String> forbidden;

    @Service.Inject
    ForbiddenWordsInputGuardrail(Config config) {
        this.forbidden = config.get("app.guardrails.forbidden").asList(String.class).orElse(List.of());
    }

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        for (String forbiddenWord : forbidden) {
            if (userMessage.singleText().contains(forbiddenWord)) {
                return this.fatal("Inappropriate question containing: " + forbiddenWord);
            }
        }
        return this.success();
    }
}
```

Guardrails can be attached declaratively on an agent method using `@InputGuardrails` and `@OutputGuardrails` annotations:

``` java
@Ai.Agent("helidon-se-expert")
@Ai.ChatModel("expensive-model")
public interface HelidonSeExpert {

    @Agent("A Helidon SE expert")
    @InputGuardrails(ForbiddenWordsInputGuardrail.class)
    @OutputGuardrails(FooBarOutputGuardrail.class)
    String askExpert(@V("question") String question);
}
```

Guardrails on Agents can be overridden by a named agent configuration:

``` yaml
langchain4j:
  agents:
    helidon-se-expert:
      input-guardrails:
        - com.example.ForbiddenWordsInputGuardrail
      output-guardrails:
        - com.example.FooBarOutputGuardrail
```

## Observability (ChatModelListeners)

While LangChain4j doesn’t provide Observability out-of-box, it provides for user to supplement it using `ChatModelListener`. For more details, read the [LangChain4j Documentation on Observability](https://docs.langchain4j.dev/tutorials/observability/).

Helidon provides `MetricsChatModelListener` which generates metrics that follow the [OpenTelemetry Semantic Conventions for GenAI Metrics v1.36.0](https://github.com/open-telemetry/semantic-conventions/blob/v1.36.0/docs/gen-ai/gen-ai-metrics.md). This is done out-of-box for Chat API calls.

## Additional Information

- [LangChain4j documentation](https://docs.langchain4j.dev/)
- Components Reference
  - [Code generated Lc4j Provider](codegen-provider.md)
