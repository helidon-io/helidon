/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * General utils.
 */
final class CommonUtils {

    private CommonUtils() {
    }

    /**
     * Loads a string from a resource using this loader that loaded this class.
     *
     * @param resourceNamePath the resource path
     * @return the loaded string resource
     * @throws io.helidon.pico.tools.ToolsException if there were any exceptions encountered
     */
    static String loadStringFromResource(String resourceNamePath) {
        try {
            try (InputStream in = CommonUtils.class.getClassLoader().getResourceAsStream(resourceNamePath)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
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
    static String loadStringFromFile(String fileName) {
        try {
            Path filePath = Path.of(fileName);
            String content = Files.readString(filePath);
            return content;
        } catch (IOException e) {
            throw new ToolsException("unable to load from file: " + fileName, e);
        }
    }

    /**
     * Converts a collection using a {@code path.separator} delimited string.
     *
     * @param coll the collection
     * @return the concatenated, delimited string value
     */
    static String toPathString(Iterable<String> coll) {
        return String.join(System.getProperty("path.separator"), coll);
    }

    /**
     * Determines the root throwable stack trace element from a chain of throwable causes.
     *
     * @param t the throwable
     * @return the root throwable error stack trace element
     */
     static StackTraceElement rootStackTraceElementOf(Throwable t) {
        while (Objects.nonNull(t.getCause()) && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getStackTrace()[0];
    }

    /**
     * Splits given using a comma-delimiter, and returns a trimmed list of string for each item.
     *
     * @param str the string to split
     * @return the list of string values
     */
    static List<String> toList(String str) {
        return toList(str, ",");
    }

    /**
     * Splits a string given a delimiter, and returns a trimmed list of string for each item.
     *
     * @param str the string to split
     * @param delim the delimiter
     * @return the list of string values
     */
    static List<String> toList(String str,
                               String delim) {
        String[] split = str.split(delim);
        return Arrays.stream(split).map(String::trim).collect(Collectors.toList());
    }

    /**
     * Trims each line of a multi-line string.
     *
     * @param multiLineStr the string
     * @return the trimmed content
     */
    static String trimLines(String multiLineStr) {
        BufferedReader reader = new BufferedReader(new StringReader(multiLineStr));
        String line;
        StringBuilder builder = new StringBuilder();
        try {
            while (null != (line = reader.readLine())) {
                if (line.isBlank()) {
                    builder.append("\n");
                } else {
                    builder.append(line.stripTrailing()).append("\n");
                }
            }
        } catch (IOException e) {
            throw new ToolsException("failed to read", e);
        }
        return builder.toString().trim();
    }

}
