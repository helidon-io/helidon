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

class LangchainTypes {
    static final TypeName AI_SERVICE = TypeName.create("io.helidon.integrations.langchain4j.Ai.Service");
    static final TypeName AI_CHAT_MODEL = TypeName.create("io.helidon.integrations.langchain4j.Ai.ChatModel");
    static final TypeName AI_STREAMING_CHAT_MODEL = TypeName.create("io.helidon.integrations.langchain4j.Ai.StreamingChatModel");
    static final TypeName AI_CHAT_MEMORY = TypeName.create("io.helidon.integrations.langchain4j.Ai.ChatMemory");
    static final TypeName AI_CHAT_MEMORY_PROVIDER = TypeName.create("io.helidon.integrations.langchain4j.Ai.ChatMemoryProvider");
    static final TypeName AI_MODERATION_MODEL = TypeName.create("io.helidon.integrations.langchain4j.Ai.ModerationModel");
    static final TypeName AI_CONTENT_RETRIEVER = TypeName.create("io.helidon.integrations.langchain4j.Ai.ContentRetriever");
    static final TypeName AI_RETRIEVER_AUGMENTOR = TypeName.create("io.helidon.integrations.langchain4j.Ai.RetrievalAugmentor");
    static final TypeName AI_TOOLS = TypeName.create("io.helidon.integrations.langchain4j.Ai.Tools");
    static final TypeName AI_TOOL = TypeName.create("io.helidon.integrations.langchain4j.Ai.Tool");
    static final Annotation TOOL_QUALIFIER_ANNOTATION = Annotation.create(AI_TOOL);

    static final TypeName LC_AI_SERVICES = TypeName.create("dev.langchain4j.service.AiServices");
    static final TypeName LC_TOOL = TypeName.create("dev.langchain4j.agent.tool.Tool");
    static final TypeName LC_CHAT_MODEL = TypeName.create("dev.langchain4j.model.chat.ChatLanguageModel");
    static final TypeName LC_STREAMING_CHAT_MODEL = TypeName.create("dev.langchain4j.model.chat.StreamingChatLanguageModel");
    static final TypeName LC_CHAT_MEMORY = TypeName.create("dev.langchain4j.memory.ChatMemory");
    static final TypeName LC_CHAT_MEMORY_PROVIDER = TypeName.create("dev.langchain4j.memory.chat.ChatMemoryProvider");
    static final TypeName LC_MODERATION_MODEL = TypeName.create("dev.langchain4j.model.moderation.ModerationModel");
    static final TypeName LC_RETRIEVAL_AUGMENTOR = TypeName.create("dev.langchain4j.rag.RetrievalAugmentor");
    static final TypeName LC_CONTENT_RETRIEVER = TypeName.create("dev.langchain4j.rag.content.retriever.ContentRetriever");
}
