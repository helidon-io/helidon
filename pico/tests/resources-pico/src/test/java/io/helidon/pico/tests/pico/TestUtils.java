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

package io.helidon.pico.tests.pico;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.helidon.pico.tools.CommonUtils;
import io.helidon.pico.tools.ToolsException;

/**
 * Test utilities.
 *
 * @deprecated
 */
public class TestUtils {

    /**
     * Load string from resource.
     *
     * @param resourceNamePath the resource path
     * @return the loaded string.
     */
    // same as from CommonUtils.
    public static String loadStringFromResource(
            String resourceNamePath) {
        try {
            try (InputStream in = CommonUtils.class.getClassLoader().getResourceAsStream(resourceNamePath)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            throw new ToolsException("failed to load: " + resourceNamePath, e);
        }
    }

    /**
     * Loads a String from a file, wrapping any exception encountered to a {@link io.helidon.pico.tools.ToolsException}.
     *
     * @param fileName the file name to load
     * @return the contents of the file
     * @throws io.helidon.pico.tools.ToolsException if there were any exceptions encountered
     */
    // same as from CommonUtils.
        public static String loadStringFromFile(
            String fileName) {
        try {
            Path filePath = Path.of(fileName);
            String content = Files.readString(filePath);
            return content;
        } catch (IOException e) {
            throw new ToolsException("unable to load from file: " + fileName, e);
        }
    }

}
