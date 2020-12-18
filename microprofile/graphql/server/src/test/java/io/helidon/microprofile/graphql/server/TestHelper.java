/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server;

/**
 * Helpers for tests.
 */
public class TestHelper {

    /**
     * Helper to create a {@link SchemaArgument}.
     * @param argumentName argument name
     * @param argumentType argument type
     * @param isMandatory  indicates if the argument is mandatory
     * @param defaultValue default value
     * @param originalType original type
     * @return a new {@link SchemaArgument}
     */
    public static SchemaArgument createArgument(String argumentName, String argumentType,
                                          boolean isMandatory, Object defaultValue, Class<?> originalType) {
        return SchemaArgument.builder()
                .argumentName(argumentName)
                .argumentType(argumentType)
                .mandatory(isMandatory)
                .defaultValue(defaultValue)
                .originalType(originalType)
                .build();
    }

    /**
     * Helper to create a {@link SchemaType}.
     * @param name  name of the type
     * @param valueClassName  value class name
     * @return a new {@link SchemaType}
     */
    public static SchemaType createSchemaType(String name, String valueClassName) {
        return SchemaType.builder()
                .name(name)
                .valueClassName(valueClassName)
                .build();
    }
}
