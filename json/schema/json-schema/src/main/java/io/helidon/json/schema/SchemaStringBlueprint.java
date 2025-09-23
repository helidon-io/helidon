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
 * Json schema related to the strings.
 */
@Prototype.Blueprint
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

    @Option.Access("")
    @Option.Default("STRING")
    @Override
    SchemaType schemaType();

    @Override
    default void generate(Hson.Struct.Builder builder) {
        SchemaItemBlueprint.super.generate(builder);
        maxLength().ifPresent(maxLength -> builder.set("maxLength", maxLength));
        minLength().ifPresent(minLength -> builder.set("minLength", minLength));
        pattern().ifPresent(pattern -> builder.set("pattern", pattern));
    }

}
