/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.test.testsubjects;

import java.util.HashSet;
import java.util.Set;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.ConfiguredOption;

@Prototype.Blueprint
interface HelidonOpenApiConfigBlueprint extends FakeOpenApiConfig {

    /**
     * Void methods will not be processed - see {@link FakeOpenApiConfig#doAllowNakedPathParameter()}.
     */
    @Override
    default void doAllowNakedPathParameter() {
    }

    /**
     * Override to mix-in the {@link io.helidon.builder.api.Prototype.Singular} annotation.
     */
    @Override
    @Prototype.Singular
    default Set<String> servers() {
        return new HashSet<>();
    }

    /**
     * Override to mix-in a default value.
     */
    @ConfiguredOption("@default")
    default String modelReader() {
        return null;
    }

}
