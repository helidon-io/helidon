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

package io.helidon.inject.tests.inject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.helidon.codegen.CodegenException;

/**
 * Testing utilities.
 */
public final class TestUtils {

    private TestUtils() {
    }

    /**
     * Load string from resource.
     *
     * @param resourceNamePath the resource path
     * @return the loaded string
     */
    // same as from CommonUtils.
    public static String loadStringFromResource(String resourceNamePath) {
        try {
            try (InputStream in = TestUtils.class.getClassLoader().getResourceAsStream(resourceNamePath)) {
                if (in == null) {
                    throw new CodegenException("Could not find resource: " + resourceNamePath);
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        } catch (CodegenException e) {
            throw e;
        } catch (Exception e) {
            throw new CodegenException("Failed to load: " + resourceNamePath, e);
        }
    }

    /**
     * Loads a String from a file, wrapping any exception encountered.
     *
     * @param fileName the file name to load
     * @return the contents of the file
     * @throws io.helidon.codegen.CodegenException if there were any exceptions encountered
     */
     // same as from CommonUtils.
     public static String loadStringFromFile(String fileName) {
        try {
            Path filePath = Path.of(fileName);
            String content = Files.readString(filePath);
            return content.trim();
        } catch (IOException e) {
            throw new CodegenException("Unable to load from file: " + fileName, e);
        }
    }

}
