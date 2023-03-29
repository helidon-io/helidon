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

package io.helidon.pico.processor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.helidon.pico.tools.ToolsException;

/**
 * Testing utilities.
 */
class TestUtils {

    private TestUtils() {
    }

    /**
     * Load string from resource.
     *
     * @param resourceNamePath the resource path
     * @return the loaded string
     */
    static String loadStringFromResource(String resourceNamePath) {
        try {
            try (InputStream in = TestUtils.class.getClassLoader().getResourceAsStream(resourceNamePath)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new ToolsException("failed to load: " + resourceNamePath, e);
        }
    }

}
