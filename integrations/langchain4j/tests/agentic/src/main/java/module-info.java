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

module io.helidon.integrations.langchain4j.tests.agentic {
    requires langchain4j.agentic;
    requires langchain4j;
    requires io.helidon.integrations.langchain4j;
    requires langchain4j.core;
    requires io.helidon.metrics.api;
    requires io.helidon.integrations.langchain4j.providers.openai;
    requires io.helidon.integrations.langchain4j.providers.google.gemini;
    requires io.helidon.integrations.langchain4j.providers.mock;
    requires io.helidon.http;
    requires io.helidon.config.yaml;
    requires io.helidon.logging.common;
    requires io.helidon.common.features.api;
    requires java.net.http;
    requires com.fasterxml.jackson.core;

    exports io.helidon.integrations.langchain4j.tests.agentic;
}