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

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.metadata.hson.Hson;

/**
 * Json schema related to the array.
 */
@Prototype.Blueprint(decorator = SchemaSupport.SchemaArrayDecorator.class)
interface SchemaArrayBlueprint extends SchemaItemBlueprint {

    /**
     * Max number of items an array can have.
     *
     * @return max array size
     */
    Optional<Integer> maxItems();

    /**
     * Min number of items an array can have.
     *
     * @return min array size
     */
    Optional<Integer> minItems();

    /**
     * Whether the array can contain duplicate values.
     *
     * @return duplicate values allowed
     */
    Optional<Boolean> uniqueItems();

    /**
     * Json type used to validate the content of the array.
     * It is filled by the more specific configured schema item type.
     *
     * @return configured items schema type
     */
    @Option.Access("")
    Optional<SchemaItem> items();

    /**
     * Array items should be validated as an objects.
     *
     * @return json object schema
     */
    Optional<SchemaObject> itemsObject();

    /**
     * Array items should be validated as an arrays.
     *
     * @return json array schema
     */
    Optional<SchemaArray> itemsArray();

    /**
     * Array items should be validated as a number.
     *
     * @return json number schema
     */
    Optional<SchemaNumber> itemsNumber();

    /**
     * Array items should be validated as an integer.
     *
     * @return json integer schema
     */
    Optional<SchemaInteger> itemsInteger();

    /**
     * Array items should be validated as a string.
     *
     * @return json string schema
     */
    Optional<SchemaString> itemsString();

    /**
     * Array items should be validated as a boolean.
     *
     * @return json boolean schema
     */
    Optional<SchemaBoolean> itemsBoolean();

    /**
     * Array items should be validated as an null.
     *
     * @return json array schema
     */
    Optional<SchemaNull> itemsNull();

    @Option.Access("")
    @Option.Default("ARRAY")
    @Override
    SchemaType schemaType();

    @Override
    default void generate(Hson.Struct.Builder builder) {
        SchemaItemBlueprint.super.generate(builder);
        maxItems().ifPresent(maxItems -> builder.set("maxItems", maxItems));
        minItems().ifPresent(minItems -> builder.set("minItems", minItems));
        uniqueItems().ifPresent(uniqueItems -> builder.set("uniqueItems", uniqueItems));
        items().ifPresent(items -> {
            Hson.Struct.Builder objectBuilder = Hson.structBuilder();
            items.generate(objectBuilder);
            builder.set("items", objectBuilder.build());
        });
    }
}
