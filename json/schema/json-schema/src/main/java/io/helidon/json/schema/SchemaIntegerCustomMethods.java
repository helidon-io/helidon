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

class SchemaIntegerCustomMethods {

    /**
     * Value restriction to be a multiple of a given integer number.
     *
     * @param value multiple value restriction
     * @return updated builder instance
     * @see SchemaInteger.BuilderBase#multipleOf()
     */
    @Prototype.BuilderMethod
    static void multipleOf(SchemaInteger.BuilderBase<?,?> builder, int value) {
        builder.multipleOf((long)value);
    }

    /**
     * Minimal value of the integer number.
     * Cannot be higher than maximal configured value.
     * Mutually exclusive to {@link SchemaInteger.BuilderBase##exclusiveMinimum()}.
     *
     * @param value minimal value
     * @return updated builder instance
     * @see SchemaInteger.BuilderBase#minimum()
     */
    @Prototype.BuilderMethod
    static void minimum(SchemaInteger.BuilderBase<?,?> builder, int value) {
        builder.minimum((long)value);
    }

    /**
     * Maximal value of the integer number.
     * Cannot be lower than minimal configured value.
     * Mutually exclusive to {@link SchemaInteger.BuilderBase#exclusiveMaximum()}.
     *
     * @param value maximal value
     * @return updated builder instance
     * @see SchemaInteger.BuilderBase#maximum()
     */
    @Prototype.BuilderMethod
    static void maximum(SchemaInteger.BuilderBase<?,?> builder, int value) {
        builder.maximum((long)value);
    }

    /**
     * Maximal exclusive value of the integer number.
     * Cannot be lower than minimal configured value.
     * Mutually exclusive to {@link SchemaInteger.BuilderBase#maximum()}.
     *
     * @param value maximal exclusive value
     * @return updated builder instance
     * @see SchemaInteger.BuilderBase#exclusiveMaximum()
     */
    @Prototype.BuilderMethod
    static void exclusiveMaximum(SchemaInteger.BuilderBase<?,?> builder, int value) {
        builder.exclusiveMaximum((long)value);
    }

    /**
     * Minimal exclusive value of the integer number.
     * Cannot be lower than maximal configured value.
     * Mutually exclusive to {@link SchemaInteger.BuilderBase#minimum()}.
     *
     * @param value minimal exclusive value
     * @return updated builder instance
     * @see SchemaInteger.BuilderBase#exclusiveMinimum()
     */
    @Prototype.BuilderMethod
    static void exclusiveMinimum(SchemaInteger.BuilderBase<?,?> builder, int value) {
        builder.exclusiveMinimum((long)value);
    }

}
