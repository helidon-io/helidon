/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.integrations.langchain4j;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * This interface contains a set of annotations for defining langChain4J declarative services.
 */
public final class Ai {
    /**
     * Name that is used to qualify chat models.
     */
    public static final String CHAT_MODEL_NAME = "chat-model";
    /**
     * Name that is used to qualify chat stream chat models.
     */
    public static final String STREAMING_CHAT_MODEL_NAME = "streaming-chat-model";
    /**
     * Name that is used to qualify embedding models.
     */
    public static final String EMBEDDING_MODEL_NAME = "embedding-model";
    /**
     * Name that is used to qualify image models.
     */
    public static final String IMAGE_MODEL_NAME = "image-model";
    /**
     * Name that is used to qualify language models.
     */
    public static final String LANGUAGE_MODEL_NAME = "language-model";
    /**
     * Name that is used to qualify moderation models.
     */
    public static final String MODERATION_MODEL_NAME = "moderation-model";

    private Ai() {
    }

    /**
     * Annotation to define a langChain4J service. A langChain4J service aggregates various components
     * that support functionalities like chat, memory management, moderation, content retrieval, and
     * tool-based augmentations.
     *
     * <p>The primary components include:</p>
     * <ul>
     *   <li>{@code dev.langchain4j.model.chat.ChatLanguageModel} or
     *   {@code dev.langchain4j.model.chat.StreamingChatLanguageModel} -
     *       Models that handle chat-based language interactions.</li>
     *   <li>{@code dev.langchain4j.memory.ChatMemory} or {@code dev.langchain4j.memory.chat.ChatMemoryProvider} -
     *       Components for storing and managing chat memory.</li>
     *   <li>{@code dev.langchain4j.model.moderation.ModerationModel} - Model for moderating chat content.</li>
     *   <li>{@code dev.langchain4j.rag.content.retriever.ContentRetriever} - Retrieves relevant content to support responses
     *   .</li>
     *   <li>{@code dev.langchain4j.rag.RetrievalAugmentor} - Enhances retrieval processes with additional context.</li>
     *   <li>CDI bean methods annotated with {@code dev.langchain4j.agent.tool.Tool} -
     *       Tool methods that further extend service capabilities.</li>
     * </ul>
     *
     * <p>If the {@code autoDiscovery} parameter is set to {@code true} (the default value), components are
     * automatically added from the CDI registry. Components explicitly specified using corresponding annotations
     * are prioritized over automatically discovered ones.</p>
     *
     * <p>If {@code autoDiscovery} is set to {@code false}, only components explicitly specified using annotations
     * are included in the service, allowing manual control over the service composition.</p>
     *
     * <p>At a minimum, either a {@code dev.langchain4j.model.chat.ChatLanguageModel} or
     * {@code dev.langchain4j.model.chat.StreamingChatLanguageModel} is required for the service to function
     * effectively.</p>
     */
    @Target(TYPE)
    @Retention(RUNTIME)
    public @interface Service {

        /**
         * Specifies whether to use auto-discovery mode to locate service components or to rely on manual
         * component specification.
         *
         * @return {@code true} if auto-discovery is enabled, {@code false} if manual discovery mode is used.
         */
        boolean autoDiscovery() default true;
    }

    /**
     * Annotation to specify a ChatModel for the service.
     */
    @Target(TYPE)
    @Retention(RUNTIME)
    public @interface ChatModel {
        /**
         * Name of the chat model to be used.
         *
         * @return name of the chat model
         */
        String value();
    }

    /**
     * Annotation to specify a StreamingChatModel for the service.
     */
    @Target(TYPE)
    @Retention(RUNTIME)
    public @interface StreamingChatModel {
        /**
         * Name of the streaming chat model to be used.
         *
         * @return name of the streaming chat model
         */
        String value();
    }

    /**
     * Annotation to specify a ChatMemory for the service.
     */
    @Target(TYPE)
    @Retention(RUNTIME)
    public @interface ChatMemory {
        /**
         * Name of the chat memory to be used.
         *
         * @return name of the chat memory
         */
        String value();
    }

    /**
     * Annotation to specify a {@link dev.langchain4j.memory.chat.MessageWindowChatMemory} for the service.
     * This annotation is mutually exclusive with {@link io.helidon.integrations.langchain4j.Ai.ChatMemory}.
     */
    @Target(TYPE)
    @Retention(RUNTIME)
    public @interface ChatMemoryWindow {
        /**
         * The window of the chat memory.
         *
         * @return number of messages to keep in the chat window
         */
        int value();

        /**
         * Id of the chat memory window, defaults to {@link io.helidon.service.registry.Service.Named#DEFAULT_NAME}.
         *
         * @return id of the chat memory window
         */
        String id() default io.helidon.service.registry.Service.Named.DEFAULT_NAME;

        /**
         * Name qualifier of {@link dev.langchain4j.store.memory.chat.ChatMemoryStore},
         * defaults to default of Lanchain4j (in-memory store).
         *
         * @return name qualifier of chat memory store
         */
        String store() default io.helidon.service.registry.Service.Named.DEFAULT_NAME;
    }

    /**
     * Annotation to specify a ChatMemoryProvider for the service.
     */
    @Target(TYPE)
    @Retention(RUNTIME)
    public @interface ChatMemoryProvider {
        /**
         * Name of the chat memory provider to be used.
         *
         * @return name of the chat memory provider
         */
        String value();
    }

    /**
     * Annotation to specify a ModerationModel for the service.
     */
    @Target(TYPE)
    @Retention(RUNTIME)
    public @interface ModerationModel {
        /**
         * Name of the moderation model to be used.
         *
         * @return name of the moderation model
         */
        String value();
    }

    /**
     * Annotation to specify a ContentRetriever for the service.
     */
    @Target(TYPE)
    @Retention(RUNTIME)
    public @interface ContentRetriever {
        /**
         * Name of the content retriever to be used.
         *
         * @return name of the content retriever
         */
        String value();
    }

    /**
     * Annotation to specify a RetrievalAugmentor for the service.
     */
    @Target(TYPE)
    @Retention(RUNTIME)
    public @interface RetrievalAugmentor {
        /**
         * Name of the retrieval augmentor to be used.
         *
         * @return name of the retrieval augmentor
         */
        String value();
    }

    /**
     * Annotation to manually specify classes containing tools for the service.
     */
    @Target(TYPE)
    @Retention(RUNTIME)
    public @interface Tools {
        /**
         * Classes with tool methods (methods annotated with lc4j annotation {@code Tool}).
         *
         * @return an array of classes containing tool methods
         */
        Class<?>[] value();
    }

    /**
     * A qualifier for a tool class (a class that has at least one method annotated
     * with lc4j annotation {@code Tool}.
     */
    @Target({TYPE, PARAMETER})
    @Retention(RUNTIME)
    @io.helidon.service.registry.Service.Qualifier
    public @interface Tool {
    }
}
