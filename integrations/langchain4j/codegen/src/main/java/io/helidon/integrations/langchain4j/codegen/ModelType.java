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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.codegen.CodegenException;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_CHAT_MODEL;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_EMBEDDING_MODEL;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_EMBEDDING_STORE;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_IMAGE_MODEL;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_LANGUAGE_MODEL;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_MODERATION_MODEL;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_SCORING_MODEL;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_STREAMING_CHAT_MODEL;

enum ModelType {
    CHAT_MODEL("chat-model"),
    STREAMING_CHAT_MODEL("streaming-chat-model"),
    LANGUAGE_MODEL("language-model"),
    EMBEDDING_MODEL("embedding-model"),
    EMBEDDING_STORE("embedding-store"),
    MODERATION_MODEL("moderation-model"),
    IMAGE_MODEL("image-model"),
    SCORING_MODEL("scoring-model");

    private final String key;

    ModelType(String configKey) {
        key = configKey;
    }

    String configKey() {
        return key;
    }

    private static final Map<TypeName, ModelType> MAP = Map.of(
            LC_LANGUAGE_MODEL, LANGUAGE_MODEL,
            LC_CHAT_MODEL, CHAT_MODEL,
            LC_STREAMING_CHAT_MODEL, STREAMING_CHAT_MODEL,
            LC_EMBEDDING_MODEL, EMBEDDING_MODEL,
            LC_SCORING_MODEL, SCORING_MODEL,
            LC_MODERATION_MODEL, MODERATION_MODEL,
            LC_IMAGE_MODEL, IMAGE_MODEL,
            LC_EMBEDDING_STORE, EMBEDDING_STORE
    );

    static ModelType forType(TypeName type) {
        return MAP.get(type);
    }

    static ModelType forTypeInfo(TypeInfo typeInfo) {
        List<TypeInfo> lineage = new ArrayList<>();
        allParents(typeInfo, lineage);
        for (var t : lineage) {
            var type = forType(t.typeName());
            if (type != null) {
                return type;
            }
        }
        throw new CodegenException("Type " + typeInfo + " is not recognized as supported model type.");
    }

    static void allParents(TypeInfo ti, List<TypeInfo> lineage) {
        lineage.add(ti);
        for (Optional<TypeInfo> t = ti.superTypeInfo();
                t.isPresent();
                t = t.get().superTypeInfo()) {
            allParents(t.get(), lineage);
        }
        for (TypeInfo i : ti.interfaceTypeInfo()) {
            allParents(i, lineage);
        }
    }
}
