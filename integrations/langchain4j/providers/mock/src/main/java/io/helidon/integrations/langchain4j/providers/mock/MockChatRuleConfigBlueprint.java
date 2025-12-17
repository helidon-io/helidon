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

package io.helidon.integrations.langchain4j.providers.mock;

import java.util.Optional;
import java.util.regex.Pattern;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration blueprint for {@link MockChatRule}.
 *
 * <p>Defines a regular expression pattern and optional response or template used by the mock chat model.
 */

@Prototype.Blueprint
@Prototype.Configured
interface MockChatRuleConfigBlueprint extends Prototype.Factory<MockChatRule> {

    /**
     * The regular expression pattern that this rule matches.
     *
     * @return regular expression pattern
     */
    @Option.Configured
    Pattern pattern();

    /**
     * Static text response that will be returned when the pattern matches.
     *
     * @return static text response
     */
    @Option.Configured
    Optional<String> response();

    /**
     * Response template (e.g., using placeholders ex.: '$1' for regex pattern group 1) used when the pattern matches.
     *
     * @return optional template string
     */
    @Option.Configured
    Optional<String> template();
}
