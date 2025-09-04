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
 * Common interface for all Json Schema items.
 */
@Prototype.Blueprint
interface SchemaItemBlueprint {

    /**
     * Title of the item.
     *
     * @return item title
     */
    Optional<String> title();

    /**
     * Description of the item.
     *
     * @return item description
     */
    Optional<String> description();

    /**
     * Used in the object properties to mark required property.
     *
     * @return whether object property is required
     */
    boolean required();

    /**
     * Schema type of the item.
     *
     * @return schema type
     */
    @Option.Access("")
    SchemaType schemaType();

    /**
     * Generated the Json schema item to the Json.
     * This method servers mainly as a helpful tool for the Json generation.
     * It should not be used.
     *
     * @param builder hson struct builder
     * @deprecated this method is not intended to be broadly used. Will be removed later.
     */
    @Deprecated(forRemoval = true, since = "4.3.0")
    default void generate(Hson.Struct.Builder builder) {
        title().ifPresent(title -> builder.set("title", title));
        description().ifPresent(description -> builder.set("description", description));
        builder.set("type", schemaType().name().toLowerCase());
    }

}
