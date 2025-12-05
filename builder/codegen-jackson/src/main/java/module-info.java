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

/**
 * Adds support for Jackson annotations for serialization and deserialization to the generated prototype.
 * <p>
 * Supports builder codegen extension for the following types:
 * <ul>
 *     <li>{@code com.fasterxml.jackson.databind.annotation.JsonSerialize}</li>
 *     <li>{@code com.fasterxml.jackson.databind.annotation.JsonDeserialize}</li>
 * </ul>
 */
module io.helidon.builder.codegen.jackson {
    requires io.helidon.common.types;
    requires io.helidon.builder.codegen;
    requires io.helidon.codegen.classmodel;

    exports io.helidon.builder.codegen.jackson;

    provides io.helidon.builder.codegen.spi.BuilderCodegenExtensionProvider
            with io.helidon.builder.codegen.jackson.JacksonBuilderExtensionProvider;
}