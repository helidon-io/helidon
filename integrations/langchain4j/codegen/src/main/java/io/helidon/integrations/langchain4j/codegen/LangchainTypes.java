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

package io.helidon.integrations.langchain4j.codegen;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

final class LangchainTypes {
    static final TypeName AI_SERVICE = TypeName.create("io.helidon.integrations.langchain4j.Ai.Service");
    static final TypeName AI_CHAT_MODEL = TypeName.create("io.helidon.integrations.langchain4j.Ai.ChatModel");
    static final TypeName AI_STREAMING_CHAT_MODEL = TypeName.create("io.helidon.integrations.langchain4j.Ai.StreamingChatModel");
    static final TypeName AI_CHAT_MEMORY = TypeName.create("io.helidon.integrations.langchain4j.Ai.ChatMemory");
    static final TypeName AI_CHAT_MEMORY_WINDOW = TypeName.create("io.helidon.integrations.langchain4j.Ai.ChatMemoryWindow");
    static final TypeName AI_CHAT_MEMORY_PROVIDER = TypeName.create("io.helidon.integrations.langchain4j.Ai.ChatMemoryProvider");
    static final TypeName AI_MODERATION_MODEL = TypeName.create("io.helidon.integrations.langchain4j.Ai.ModerationModel");
    static final TypeName AI_CONTENT_RETRIEVER = TypeName.create("io.helidon.integrations.langchain4j.Ai.ContentRetriever");
    static final TypeName AI_RETRIEVER_AUGMENTOR = TypeName.create("io.helidon.integrations.langchain4j.Ai.RetrievalAugmentor");
    static final TypeName AI_TOOLS = TypeName.create("io.helidon.integrations.langchain4j.Ai.Tools");
    static final TypeName AI_TOOL = TypeName.create("io.helidon.integrations.langchain4j.Ai.Tool");
    static final Annotation TOOL_QUALIFIER_ANNOTATION = Annotation.create(AI_TOOL);
    static final TypeName MODEL_CONFIG_TYPE = TypeName.create("io.helidon.integrations.langchain4j.AiProvider.ModelConfig");
    static final TypeName MODEL_CONFIGS_TYPE = TypeName.create("io.helidon.integrations.langchain4j.AiProvider.ModelConfigs");
    static final TypeName MODEL_DEFAULT_WEIGHT = TypeName.create("io.helidon.integrations.langchain4j.AiProvider.DefaultWeight");
    static final TypeName MODEL_NESTED_CONFIG = TypeName.create("io.helidon.integrations.langchain4j.AiProvider.NestedConfig");
    static final TypeName MODEL_CUSTOM_BUILDER_MAPPING =
            TypeName.create("io.helidon.integrations.langchain4j.AiProvider.CustomBuilderMapping");

    static final TypeName LC_AI_SERVICES = TypeName.create("dev.langchain4j.service.AiServices");
    static final TypeName LC_TOOL = TypeName.create("dev.langchain4j.agent.tool.Tool");
    static final TypeName LC_CHAT_MODEL = TypeName.create("dev.langchain4j.model.chat.ChatModel");
    static final TypeName LC_LANGUAGE_MODEL = TypeName.create("dev.langchain4j.model.language.LanguageModel");
    static final TypeName LC_STREAMING_CHAT_MODEL = TypeName.create("dev.langchain4j.model.chat.StreamingChatModel");
    static final TypeName LC_EMBEDDING_MODEL = TypeName.create("dev.langchain4j.model.embedding.EmbeddingModel");
    static final TypeName LC_EMBEDDING_STORE = TypeName.create("dev.langchain4j.store.embedding.EmbeddingStore");
    static final TypeName LC_SCORING_MODEL = TypeName.create("dev.langchain4j.model.scoring.ScoringModel");
    static final TypeName LC_CHAT_MEMORY = TypeName.create("dev.langchain4j.memory.ChatMemory");
    static final TypeName LC_CHAT_MEMORY_STORE = TypeName.create("dev.langchain4j.store.memory.chat.ChatMemoryStore");
    static final TypeName LC_CHAT_MEMORY_PROVIDER = TypeName.create("dev.langchain4j.memory.chat.ChatMemoryProvider");
    static final TypeName LC_MODERATION_MODEL = TypeName.create("dev.langchain4j.model.moderation.ModerationModel");
    static final TypeName LC_IMAGE_MODEL = TypeName.create("dev.langchain4j.model.image.ImageModel");
    static final TypeName LC_RETRIEVAL_AUGMENTOR = TypeName.create("dev.langchain4j.rag.RetrievalAugmentor");
    static final TypeName LC_CONTENT_RETRIEVER = TypeName.create("dev.langchain4j.rag.content.retriever.ContentRetriever");
    static final TypeName LC_CHAT_MEMORY_WINDOW = TypeName.create("dev.langchain4j.memory.chat.MessageWindowChatMemory");
    static final TypeName LC_HTTP_CLIENT_BUILDER = TypeName.create("dev.langchain4j.http.client.HttpClientBuilder");
    static final TypeName LC_CHAT_MODEL_LISTENER = TypeName.create("dev.langchain4j.model.chat.listener.ChatModelListener");
    static final TypeName LC_DEF_REQUEST_PARAMS = TypeName.create("dev.langchain4j.model.chat.request.ChatRequestParameters");

    static final TypeName SVC_QUALIFIED_INSTANCE = TypeName.create("io.helidon.service.registry.Service.QualifiedInstance");
    static final TypeName SVC_SERVICES_FACTORY = TypeName.create("io.helidon.service.registry.Service.ServicesFactory");
    static final TypeName SVC_QUALIFIER = TypeName.create("io.helidon.service.registry.Qualifier");
    static final TypeName COMMON_WEIGHT = TypeName.create("io.helidon.common.Weight");
    static final TypeName COMMON_CONFIG = TypeName.create("io.helidon.common.config.Config");
    static final TypeName BLDR_PROTOTYPE_REGISTRY_SUPPORT = TypeName.create("io.helidon.builder.api.Prototype.RegistrySupport");
    static final Annotation BLDR_REGISTRY_SUPPORT_ANNOTATION = Annotation.create(BLDR_PROTOTYPE_REGISTRY_SUPPORT);
    static final TypeName OPT_SINGULAR = TypeName.create("io.helidon.builder.api.Option.Singular");
    static final TypeName OPT_CONFIGURED = TypeName.create("io.helidon.builder.api.Option.Configured");
    static final TypeName OPT_REGISTRY_SERVICE = TypeName.create("io.helidon.builder.api.Option.RegistryService");
    static final Annotation BLDR_SINGULAR_ANNOTATION = Annotation.create(OPT_SINGULAR);
    static final TypeName BLDR_PROTOTYPE_CONFIGURED = TypeName.create("io.helidon.builder.api.Prototype.Configured");

    private LangchainTypes() {
    }
}
