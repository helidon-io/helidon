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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.metadata.hson.Hson;

/**
 * Json schema related to the objects.
 */
@Prototype.Blueprint(decorator = SchemaSupport.SchemaObjectDecorator.class)
@Prototype.CustomMethods(SchemaSupport.SchemaObjectCustomMethods.class)
interface SchemaObjectBlueprint extends SchemaItemBlueprint {

    /**
     * Maximum number of the object properties.
     *
     * @return maximum number of the properties
     */
    Optional<Integer> maxProperties();

    /**
     * Minimum number of the object properties.
     *
     * @return minimum number of the properties
     */
    Optional<Integer> minProperties();

    /**
     * Whether any additional properties are allowed.
     * If set to true, all the available properties have to be added via corresponding property methods.
     *
     * @return whether are additional properties allowed
     */
    Optional<Boolean> additionalProperties();

    /**
     * Map of all registered properties.
     *
     * @return all registered properties
     */
    @Option.Singular("property")
    @Option.Access("")
    Map<String, SchemaItem> properties();

    /**
     * Map of all string properties.
     *
     * @return all string properties
     */
    @Option.Singular(value = "addStringProperty", withPrefix = false)
    Map<String, SchemaString> stringProperties();

    /**
     * Map of all object properties.
     *
     * @return all object properties
     */
    @Option.Singular(value = "addObjectProperty", withPrefix = false)
    Map<String, SchemaObject> objectProperties();

    /**
     * Map of all array properties.
     *
     * @return all array properties
     */
    @Option.Singular(value = "addArrayProperty", withPrefix = false)
    Map<String, SchemaArray> arrayProperties();

    /**
     * Map of all number properties.
     *
     * @return all number properties
     */
    @Option.Singular(value = "addNumberProperty", withPrefix = false)
    Map<String, SchemaNumber> numberProperties();

    /**
     * Map of all integer properties.
     *
     * @return all integer properties
     */
    @Option.Singular(value = "addIntegerProperty", withPrefix = false)
    Map<String, SchemaInteger> integerProperties();

    /**
     * Map of all boolean properties.
     *
     * @return all boolean properties
     */
    @Option.Singular(value = "addBooleanProperty", withPrefix = false)
    Map<String, SchemaBoolean> booleanProperties();

    /**
     * Map of all null properties.
     *
     * @return all null properties
     */
    @Option.Singular(value = "addNullProperty", withPrefix = false)
    Map<String, SchemaNull> nullProperties();

    @Option.Access("")
    @Option.Default("OBJECT")
    @Override
    SchemaType schemaType();

    @Override
    default void generate(Hson.Struct.Builder builder) {
        SchemaItemBlueprint.super.generate(builder);
        maxProperties().ifPresent(maxProperties -> builder.set("maxProperties", maxProperties));
        minProperties().ifPresent(minProperties -> builder.set("minProperties", minProperties));
        additionalProperties()
                .ifPresent(additionalProperties -> builder.set("additionalProperties", additionalProperties));
        Set<String> requiredProperties = new HashSet<>();
        Map<String, SchemaItem> properties = properties();
        if (!properties.isEmpty()) {
            Hson.Struct.Builder objectBuilder = Hson.structBuilder();
            for (Map.Entry<String, SchemaItem> entry : properties.entrySet()) {
                SchemaItem schemaItem = entry.getValue();
                Hson.Struct.Builder itemBuilder = Hson.structBuilder();
                schemaItem.generate(itemBuilder);
                objectBuilder.set(entry.getKey(), itemBuilder.build());
                if (schemaItem.required()) {
                    requiredProperties.add(entry.getKey());
                }
            }
            builder.set("properties", objectBuilder.build());
        }
        if (!requiredProperties.isEmpty()) {
            builder.setStrings("required", List.copyOf(requiredProperties));
        }
    }
}
