# Helidon MP LangChain4j Guide

This guide describes how to create a sample AI powered Helidon MP project with LangChain4j integration.

## Introduction

[LangChain4j](https://github.com/langchain4j/langchain4j) is a Java framework for building AI-powered applications using Large Language Models (LLMs). It provides seamless integration with multiple LLM providers, including OpenAI, Cohere, Hugging Face, and others. Key features include AI Services and Agents for model interaction, support for Retrieval-Augmented Generation (RAG) to enhance responses with external data, and tools for working with embeddings and knowledge retrieval.

Helidon provides a LangChain4j integration module that simplifies the use of LangChain4j in Helidon applications.

> [!NOTE]
> LangChain4j integration is a preview feature. The APIs shown here are subject to change. These APIs will be finalized in a future release of Helidon.

## What you need

For this 15 minute tutorial, you will need the following:

|  |  |
|----|----|
| [Java SE 21](https://www.oracle.com/technetwork/java/javase/downloads) ([Open JDK 21](http://jdk.java.net)) | Helidon requires Java 21+ (25+ recommended). |
| [Maven 3.8+](https://maven.apache.org/download.cgi) | Helidon requires Maven 3.8+. |
| [Docker 18.09+](https://docs.docker.com/install/) | If you want to build and run Docker containers. |
| [Kubectl 1.16.5+](https://kubernetes.io/docs/tasks/tools/install-kubectl/) | If you want to deploy to Kubernetes, you need `kubectl` and a Kubernetes cluster (you can [install one on your desktop](../../about/kubernetes.md)). |

Prerequisite product versions for Helidon 4.4.0-SNAPSHOT

*Verify Prerequisites*

```bash
java -version
mvn --version
docker --version
kubectl version
```

*Setting JAVA_HOME*

```bash
# On Mac
export JAVA_HOME=`/usr/libexec/java_home -v 21`

# On Linux
# Use the appropriate path to your JDK
export JAVA_HOME=/usr/lib/jvm/jdk-21
```

## Generate the Project

Generate the project using the Helidon MP Quickstart Maven archetype.

*Run the Maven archetype:*

```bash
mvn -U archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-mp \
    -DarchetypeVersion=4.4.0-SNAPSHOT \
    -DgroupId=io.helidon.examples \
    -DartifactId=helidon-quickstart-lc4j-mp \
    -Dpackage=io.helidon.examples.quickstart.lc4j
```

The archetype generates a Maven project in your current directory, (for example, `helidon-quickstart-lc4j-mp`). Change into this directory and build.

```bash
cd helidon-quickstart-lc4j-mp
```

## Dependencies

Add necessary dependencies for LangChain4j integration and OpenAI provider in the project POM.

```xml
<dependency>
    <groupId>io.helidon.integrations.langchain4j</groupId>
    <artifactId>helidon-integrations-langchain4j</artifactId>
</dependency>
<dependency>
    <groupId>io.helidon.integrations.langchain4j.providers</groupId>
    <artifactId>helidon-integrations-langchain4j-providers-open-ai</artifactId>
</dependency>
```

You will also need extra annotation processors as LangChain4j AI services are handled as superfast build time beans.

Include the following annotation processor in the `<build><plugins>` section of `pom.xml`:

```xml
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

## Configuration

Add to the configuration file `./src/main/resources/META-INF/microprofile-config.properties` following LangChain4j configuration for OpenAI provider.

Model configured under `langchain4j.models` has arbitrary name `pirate-chat-model`, it uses `open-ai` provider defined under `langchain4j.providers`. With a single configured chat model, default auto-discovery resolves it automatically. If you configure multiple chat models, use `@Ai.ChatModel("pirate-chat-model")` to select one explicitly.

```properties
langchain4j.providers.open-ai.base-url=http://langchain4j.dev/demo/openai/v1
# Lc4j demo api key needs to be routed over lc4j proxy
langchain4j.providers.open-ai.api-key=demo
langchain4j.models.pirate-chat-model.provider=open-ai
langchain4j.models.pirate-chat-model.model-name=gpt-4o-mini
```

## Ai Service

Next we need to create LangChain4j [Ai service](https://docs.langchain4j.dev/tutorials/ai-services) and annotate it with `@Ai.Service` so Helidon can make a superfast build time bean from it.

```java
@Ai.Service
public interface PirateService {

    @SystemMessage("""
    You are a pirate who like to tell stories about his time.
    """)
    String chat(String prompt);
}
```

Next step is to add new Http POST JAX-RS resource, create new public class `PirateResource` like following example shows.

```java
@Path("/chat")
public class PirateResource {

    @Inject
    PirateService pirateService; 

    @POST
    public String chat(String message) {
        return pirateService.chat(message);
    }
}
```

- Notice how we can inject the LangChain4j Ai service as Helidon declarative superfast build time bean directly in JAX-RS resource.

When we build and run our Helidon AI-powered quickstart:

    mvn package -DskipTests && java -jar ./target/*.jar

We can test our pirate service with curl:

    echo "Who are you?" | curl -d @- localhost:8080/chat

## Prompt Template Arguments

Ofcourse all the features from LangChain4j Ai services are going to work, let’s try to expand the example with [template arguments](https://docs.langchain4j.dev/tutorials/ai-services#usermessage).

```java
@Ai.Service
public interface PirateService {

    @SystemMessage("""
    You are a pirate who like to tell stories about his time
    at the sea with captain {​{capt-name}​}.
    """)
    String chat(@V("capt-name") String captName,
                @UserMessage String prompt);
}
```

Remember to fix the code calling the service.

```java
@Path("/chat")
public class PirateResource {

    @Inject
    PirateService pirateService;

    @POST
    public String chat(String message) {
        return pirateService.chat("Frank", message);
    }

}
```

When we build and run our Helidon AI-powered quickstart:

    mvn package -DskipTests && java -jar ./target/*.jar

We can test our pirate service with curl:

    echo "Who was your captain?" | curl -d @- localhost:8080/chat

## Custom Memory Provider

We can also extend the pirate example with [conversation memory](https://docs.langchain4j.dev/tutorials/chat-memory). First, we need to create a memory provider so our memory works per conversation ID.

```java
@Service.Singleton
@Service.Named(PirateMemoryProvider.NAME)
public class PirateMemoryProvider implements Supplier<ChatMemoryProvider> {

    static final String NAME = "pirate-memory";

    @Override
    public ChatMemoryProvider get() {
        return memoryId -> MessageWindowChatMemory.builder()
                .maxMessages(10)
                .id(memoryId)
                .chatMemoryStore(new InMemoryChatMemoryStore()).build();
    }
}
```

Now we can extend Ai service with an extra argument so we can supply identifier of our conversation with the pirate.

```java
@Ai.Service
@Ai.ChatMemoryProvider(PirateMemoryProvider.NAME)
public interface PirateService {

    @SystemMessage("""
    You are a pirate who like to tell stories about his time
    at the sea with captain {​{capt-name}​}.
    """)
    String chat(@MemoryId String memoryId,
                @V("capt-name") String captName,
                @UserMessage String prompt);
}
```

We will expect conversation id as a header on the webserver.

```java
@Path("/chat")
public class PirateResource {

    @Inject
    PirateService pirateService;

    @POST
    public String chat(@HeaderParam("conversation-id") String conversationId,
                       String message) {
        return pirateService.chat(conversationId, "Frank", message);
    }
}
```

    mvn package -DskipTests && java -jar ./target/*.jar

We can test our pirate service with curl:

    echo "Hi, I am John."          | curl -d @- -H "conversation-id: 123" localhost:8080/chat
    Ahoy there, John

    echo "Do you remeber my name?" | curl -d @- -H "conversation-id: 123" localhost:8080/chat
    Aye, John! The name be etched in me memory like a ship’s anchor in the sand.
