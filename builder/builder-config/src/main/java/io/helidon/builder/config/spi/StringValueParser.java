/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.config.spi;

import java.util.Optional;

/**
 * String value parser.
 */
@FunctionalInterface
public interface StringValueParser {

    /**
     * Parse the string into a type R instance.
     *
     * @param val the string value to parse
     * @param type the type of the result expected
     * @param <R> the return type
     * @return the optional nullable parsed value
     * @throws java.lang.IllegalArgumentException if the format is not parsable or the return type is not supported
     */
    <R> Optional<R> parse(
            String val,
            Class<R> type);

}
