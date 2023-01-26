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

package io.helidon.pico.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * General utils.
 */
public final class CommonUtils {

    private CommonUtils() {
    }

    /**
     * Loads a string from a resource using this loader that loaded this class.
     *
     * @param resourceNamePath the resource path
     * @return the loaded string resource
     * @throws io.helidon.pico.tools.ToolsException if there were any exceptions encountered
     */
    static String loadStringFromResource(
            String resourceNamePath) {
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
    static String loadStringFromFile(
            String fileName) {
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
    static String toPathString(
            Collection<?> coll) {
        return toString(coll, null, System.getProperty("path.separator"));
    }

    /**
     * Converts a collection to a comma delimited string.
     *
     * @param coll the collection
     * @return the concatenated, delimited string value
     */
    public static String toString(
            Collection<?> coll) {
        return toString(coll, null, null);
    }

    /**
     * Provides specialization in concatenation, allowing for a function to be called for each element as well as to
     * use special separators.
     *
     * @param coll      the collection
     * @param fnc       the optional function to translate the collection item to a string
     * @param separator the optional separator
     * @param <T> the type held by the collection
     * @return the concatenated, delimited string value
     */
    static <T> String toString(
            Collection<T> coll,
            Function<T, String> fnc,
            String separator) {
        Function<T, String> fn = (fnc == null) ? String::valueOf : fnc;
        separator = (separator == null) ? ", " : separator;
        return coll.stream().map(fn::apply).collect(Collectors.joining(separator));
    }

    /**
     * Splits given using a comma-delimiter, and returns a trimmed list of string for each item.
     *
     * @param str the string to split
     * @return the list of string values
     */
    public static List<String> toList(
            String str) {
        return toList(str, ",");
    }

    /**
     * Splits a string given a delimiter, and returns a trimmed list of string for each item.
     *
     * @param str the string to split
     * @param delim the delimiter
     * @return the list of string values
     */
    static List<String> toList(
            String str,
            String delim) {
        String[] split = str.split(delim);
        return Arrays.stream(split).map(String::trim).collect(Collectors.toList());
    }

    /**
     * Converts the collection of type T to a set of strings, handling the null case.
     *
     * @param coll  the collection or null
     * @param fn    the mapper function
     * @param <T>   the type of the items in the collection
     * @return the set of mapped strings from the collection
     */
    static <T> Set<String> toSet(
            Collection<T> coll,
            Function<T, String> fn) {
        if (coll == null) {
            return Set.of();
        }

        return coll.stream().map(fn).collect(Collectors.toSet());
    }

    /**
     * Trims each line of a multi-line string.
     *
     * @param multiLineStr the string
     * @return the trimmed content
     */
    static String trimLines(
            String multiLineStr) {
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

    /**
     * Returns the first element of a collection.
     *
     * @param coll                  the collection
     * @param allowEmptyCollection  if true, and the collection is empty, will return null instead of throwing
     * @param <T> the type of the collection
     * @return the first element, or null if empty collections are allowed
     * @throws io.helidon.pico.tools.ToolsException if not allowEmptyCollection and the collection is empty
     */
    static <T> T first(
            Collection<T> coll,
            boolean allowEmptyCollection) {
        if (coll.isEmpty()) {
            if (allowEmptyCollection) {
                return null;
            } else {
                throw new ToolsException("expected a non-empty collection");
            }
        }

        return coll.iterator().next();
    }

    static boolean hasValue(
            String str) {
        return (str != null && !str.isBlank());
    }

    /**
     * Replaces the provided string's usage of '.' with '$'.
     *
     * @param className the classname
     * @return the converted string
     */
    static String toFlatName(
            String className) {
        return className.replace('.', '$');
    }

    /**
     * Determines the root throwable stack trace element from a chain of throwable causes.
     *
     * @param t the throwable
     * @return the root throwable error stack trace element
     */
    public static StackTraceElement rootStackTraceElementOf(
            Throwable t) {
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getStackTrace()[0];
    }

}
