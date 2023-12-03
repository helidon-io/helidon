/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.common.processor;

/**
 * Tools for generating code.
 *
 * @deprecated use {@code helidon-codegen} instead.
 */
@Deprecated(forRemoval = true, since = "4.1.0")
public final class GeneratorTools {
    private GeneratorTools() {

    }

    /**
     * Capitalize the first letter of the provided string.
     *
     * @param name string to capitalize
     * @return name with the first character as capital letter
     */
    public static String capitalize(String name) {
        if (name.isBlank() || name.isEmpty()) {
            return name;
        }
        char first = name.charAt(0);
        first = Character.toUpperCase(first);
        return first + name.substring(1);
    }
}
