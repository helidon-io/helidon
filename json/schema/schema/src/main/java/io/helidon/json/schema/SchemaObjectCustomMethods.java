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

package io.helidon.json.schema;

import io.helidon.builder.api.Prototype;

class SchemaObjectCustomMethods {

    private SchemaObjectCustomMethods() {
    }

    /**
     * Add JSON schema property based on the provided schema root type.
     *
     * @param target builder
     * @param name   property name
     * @param schema schema
     */
    @Prototype.BuilderMethod
    static void addSchema(SchemaObject.BuilderBase<?, ?> target, String name, Schema schema) {
        SchemaItem root = schema.root();
        switch (root.schemaType()) {
        case OBJECT -> target.addObjectProperty(name, (SchemaObject) root);
        case ARRAY -> target.addArrayProperty(name, (SchemaArray) root);
        case NUMBER -> target.addNumberProperty(name, (SchemaNumber) root);
        case INTEGER -> target.addIntegerProperty(name, (SchemaInteger) root);
        case BOOLEAN -> target.addBooleanProperty(name, (SchemaBoolean) root);
        case STRING -> target.addStringProperty(name, (SchemaString) root);
        case NULL -> target.addNullProperty(name, (SchemaNull) root);
        default -> throw new JsonSchemaException("Unsupported schema type: " + root.schemaType());
        }
    }

}
