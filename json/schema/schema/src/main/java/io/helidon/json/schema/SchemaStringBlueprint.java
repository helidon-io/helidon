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

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonValue;

/**
 * Json schema related to the strings.
 */
@Prototype.Blueprint
@Prototype.CustomMethods(SchemaSupport.SchemaStringCustomMethods.class)
interface SchemaStringBlueprint extends SchemaItemBlueprint {

    /**
     * Maximum length of the string.
     *
     * @return maximum string length
     */
    Optional<Long> maxLength();

    /**
     * Minimum length of the string.
     *
     * @return minimum string length
     */
    Optional<Long> minLength();

    /**
     * String pattern to follow.
     *
     * @return string pattern
     */
    Optional<String> pattern();

    /**
     * String format, such as {@code date}, {@code time}, or {@code date-time}.
     *
     * @return string format
     * @since 4.5.7
     */
    Optional<String> format();

    /**
     * Values of the enum keyword, in declaration order.
     * Entries of any JSON type are preserved, including {@link io.helidon.json.JsonNull}.
     * The string type restriction applies independently of these values.
     * When absent, no enum keyword restricts the string values.
     * A present empty list generates an empty enum, which allows no values.
     *
     * @return enum values
     * @since 4.5.7
     */
    @Option.Singular("enumValue")
    Optional<List<JsonValue>> enumValues();

    @Option.Access("")
    @Option.Default("STRING")
    @Override
    SchemaType schemaType();

    @Override
    default void generate(JsonObject.Builder builder) {
        SchemaItemBlueprint.super.generate(builder);
        maxLength().ifPresent(maxLength -> builder.set("maxLength", maxLength));
        minLength().ifPresent(minLength -> builder.set("minLength", minLength));
        pattern().ifPresent(pattern -> builder.set("pattern", pattern));
        format().ifPresent(format -> builder.set("format", format));
        enumValues().ifPresent(values -> builder.set("enum", JsonArray.create(values)));
    }

}
