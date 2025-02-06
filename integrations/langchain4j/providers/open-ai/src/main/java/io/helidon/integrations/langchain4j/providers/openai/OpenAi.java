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

package io.helidon.integrations.langchain4j.providers.openai;

import io.helidon.common.Weighted;
import io.helidon.integrations.langchain4j.Ai;
import io.helidon.service.registry.Qualifier;

final class OpenAi {
    static final String OPEN_AI = "open-ai";
    static final String CHAT_MODEL = OPEN_AI + "." + Ai.CHAT_MODEL_NAME;
    static final String EMBEDDING_MODEL = OPEN_AI + "." + Ai.EMBEDDING_MODEL_NAME;
    static final String IMAGE_MODEL = OPEN_AI + "." + Ai.IMAGE_MODEL_NAME;
    static final String LANGUAGE_MODEL = OPEN_AI + "." + Ai.LANGUAGE_MODEL_NAME;
    static final String MODERATION_MODEL = OPEN_AI + "." + Ai.MODERATION_MODEL_NAME;
    static final String STREAM_CHAT_MODEL = OPEN_AI + "." + Ai.STREAMING_CHAT_MODEL_NAME;

    static final Qualifier OPEN_AI_QUALIFIER = Qualifier.createNamed(OPEN_AI);
    static final double WEIGHT = Weighted.DEFAULT_WEIGHT - 10;

    private OpenAi() {
    }
}
