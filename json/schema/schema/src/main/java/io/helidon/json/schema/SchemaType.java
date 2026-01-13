/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.json.schema;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Json schema type.
 */
public enum SchemaType {

    /**
     * Json schema object type.
     */
    OBJECT("object"),

    /**
     * Json schema array type.
     */
    ARRAY("array"),

    /**
     * Json schema string type.
     */
    STRING("string"),

    /**
     * Json schema number type.
     */
    NUMBER("number"),

    /**
     * Json schema integer type.
     */
    INTEGER("integer"),

    /**
     * Json schema boolean type.
     */
    BOOLEAN("boolean"),

    /**
     * Json schema null type.
     */
    NULL("null");

    private static final Map<String, SchemaType> TYPE_TO_ENUM;

    static {
        Map<String, SchemaType> map = new HashMap<>();
        for (SchemaType type : SchemaType.values()) {
            map.put(type.type(), type);
        }
        TYPE_TO_ENUM = Map.copyOf(map);
    }

    private final String type;

    SchemaType(String type) {
        this.type = type;
    }

    /**
     * Type as used in JSON Schema.
     *
     * @return type string
     */
    String type() {
        return type;
    }

    /**
     * Create a  {@link io.helidon.json.schema.SchemaType} from
     * the schema type string.
     *
     * @param type as used in JSON Schema
     * @return a schema type instance
     * @throws io.helidon.json.schema.JsonSchemaException in case the type is not valid
     */
    static SchemaType fromType(String type) {
        return Optional.ofNullable(TYPE_TO_ENUM.get(type))
                .orElseThrow(() -> new JsonSchemaException("Unsupported type in schema: " + type));
    }
}
