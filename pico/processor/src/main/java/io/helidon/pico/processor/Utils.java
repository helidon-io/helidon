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

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

final class Utils {
    private Utils() {
    }

    /**
     * Determines the root throwable stack trace element from a chain of throwable causes.
     *
     * @param t the throwable
     * @return the root throwable error stack trace element
     */
    static StackTraceElement rootStackTraceElementOf(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getStackTrace()[0];
    }

    /**
     * Converts a collection to a comma delimited string.
     *
     * @param coll the collection
     * @return the concatenated, delimited string value
     */
    static String toString(Collection<?> coll) {
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
    static <T> String toString(Collection<T> coll,
                               Function<T, String> fnc,
                               String separator) {
        Function<T, String> fn = (fnc == null) ? String::valueOf : fnc;
        separator = (separator == null) ? ", " : separator;
        return coll.stream().map(fn).collect(Collectors.joining(separator));
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
     * Will return non-empty File if the uri represents a local file on the fs.
     *
     * @param uri the uri of the artifact
     * @return the file instance, or empty if not local
     */
    static Optional<Path> toPath(URI uri) {
        if (uri.getHost() != null) {
            return Optional.empty();
        }
        return Optional.of(Paths.get(uri));
    }

}
