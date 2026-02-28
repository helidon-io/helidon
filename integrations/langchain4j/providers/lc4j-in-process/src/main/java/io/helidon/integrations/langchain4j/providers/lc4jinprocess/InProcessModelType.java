/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.integrations.langchain4j.providers.lc4jinprocess;

/**
 * Supported LangChain4j in-process embedding model types.
 * This enum provides well-known model identifiers for LangChain4j's ONNX-based "in-process" embedding models.
 *
 * @see <a href="https://docs.langchain4j.dev/integrations/embedding-models/in-process">LangChain4j in-process embedding
 *         models</a>
 */
public enum InProcessModelType {

    /**
     * The default "all-minilm-l6-v2" in-process embedding model.
     */
    ALL_MINILM_L6_V2("all-minilm-l6-v2"),

    /**
     * The quantized variant of the "all-minilm-l6-v2" in-process embedding model, typically offering reduced memory
     * footprint and potentially faster inference at some quality cost.
     */
    ALL_MINILM_L6_V2_Q("all-minilm-l6-v2-q"),

    /**
     * A custom, user-provided in-process ONNX embedding model,
     * when selected `path-to-model` and `path-to-tokenizer` needs to be provided.
     *
     */
    CUSTOM("custom");

    private final String name;

    InProcessModelType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
