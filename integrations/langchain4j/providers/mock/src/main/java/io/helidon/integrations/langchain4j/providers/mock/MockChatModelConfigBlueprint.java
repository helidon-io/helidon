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

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

@Prototype.Blueprint
@Prototype.RegistrySupport
@Prototype.Configured(MockChatModelConfigBlueprint.CONFIG_ROOT)
interface MockChatModelConfigBlueprint extends Prototype.Factory<MockChatModel> {

    /**
     * The root configuration key for this builder.
     */
    String CONFIG_ROOT = "langchain4j.mock.chat-model";

    /**
     * If set to {@code false} (default), MockChatModel will not be available even if configured.
     *
     * @return whether MockChatModel is enabled, defaults to {@code false}
     */
    @Option.Configured
    boolean enabled();

    /**
     * The list of {@link MockChatRule}s that the mock chat model evaluates.
     * <p>
     * Rules can be defined in configuration under {@code langchain4j.mock.chat-model.rules}
     * and may also be provided as {@link io.helidon.service.registry.Service} singletons.
     *
     * @return an immutable list of mock chat rules
     */
    @Option.Configured
    @Option.RegistryService
    List<MockChatRule> rules();
}
