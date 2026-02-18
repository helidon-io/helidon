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

package io.helidon.json.codegen;

import io.helidon.codegen.Option;

/**
 * Code generation options for JSON processing.
 * <p>
 * This interface defines configuration options that control how JSON code generation behaves.
 * </p>
 */
final class JsonCodegenOptions {

    /**
     * Option to control whether generated serializers should write null values.
     * <p>
     * When set to true, null values will be included in the JSON output.
     * When set to false (default), null values will be omitted.
     * </p>
     */
    static final Option<Boolean> CODEGEN_JSON_NULL = Option.create("helidon.codegen.json.nulls",
                                                                   "Sets the default for whether generated type "
                                                                           + "serializers should write nulls or not.",
                                                                   false);
    /**
     * Option to control whether generated deserializers should fail on unknown properties.
     * <p>
     * When set to true, deserialization will fail if unknown properties are encountered.
     * When set to false (default), unknown properties will be ignored.
     * </p>
     */
    static final Option<Boolean> CODEGEN_JSON_UNKNOWN = Option.create("helidon.codegen.json.unknown",
                                                                      "Sets the default for whether generated type "
                                                                              + "deserializers should fail when unknow "
                                                                              + "property is "
                                                                              + "encountered.",
                                                                      false);
    /**
     * Option to control the default ordering of properties in JSON documents.
     * <p>
     * Available values are: ALPHABETICAL, REVERSE_ALPHABETICAL, ANY.
     * Default value is "ALL".
     * </p>
     */
    static final Option<String> CODEGEN_JSON_ORDER = Option.create("helidon.codegen.json.order",
                                                                   "Sets the default for default ordering of the "
                                                                           + "properties in the JSON document. "
                                                                           + "Available values are: ALPHABETICAL, "
                                                                           + "REVERSE_ALPHABETICAL, ANY",
                                                                   "ALL");

    private JsonCodegenOptions() {
    }

}
