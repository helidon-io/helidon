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
 * Json schema related to the integer numbers.
 */
@Prototype.Blueprint(decorator = SchemaSupport.SchemaIntegerDecorator.class)
@Prototype.CustomMethods(SchemaSupport.SchemaIntegerCustomMethods.class)
interface SchemaIntegerBlueprint extends SchemaItemBlueprint {

    /**
     * Value restriction to be a multiple of a given integer number.
     *
     * @return multiple value restriction
     */
    Optional<Long> multipleOf();

    /**
     * Minimal value of the integer number.
     * Cannot be higher than maximal configured value.
     * Mutually exclusive to {@link #exclusiveMinimum()}.
     *
     * @return minimal value
     */
    Optional<Long> minimum();

    /**
     * Maximal value of the integer number.
     * Cannot be lower than minimal configured value.
     * Mutually exclusive to {@link #exclusiveMaximum()}.
     *
     * @return maximal value
     */
    Optional<Long> maximum();

    /**
     * Maximal exclusive value of the integer number.
     * Cannot be lower than minimal configured value.
     * Mutually exclusive to {@link #maximum()}.
     *
     * @return maximal exclusive value
     */
    Optional<Long> exclusiveMaximum();

    /**
     * Minimal exclusive value of the integer number.
     * Cannot be higher than maximal configured value.
     * Mutually exclusive to {@link #minimum()}.
     *
     * @return minimal exclusive value
     */
    Optional<Long> exclusiveMinimum();

    @Option.Access("")
    @Option.Default("INTEGER")
    @Override
    SchemaType schemaType();

    @Override
    default void generate(Hson.Struct.Builder builder) {
        SchemaItemBlueprint.super.generate(builder);
        multipleOf().ifPresent(multipleOf -> builder.set("multipleOf", multipleOf));
        minimum().ifPresent(minimum -> builder.set("minimum", minimum));
        maximum().ifPresent(maximum -> builder.set("maximum", maximum));
        exclusiveMaximum().ifPresent(exclusiveMaximum -> builder.set("exclusiveMaximum", exclusiveMaximum));
        exclusiveMinimum().ifPresent(exclusiveMinimum -> builder.set("exclusiveMinimum", exclusiveMinimum));
    }

}
