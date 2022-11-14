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

package io.helidon.pico.tools.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.pico.PicoServices;
import io.helidon.pico.spi.impl.DefaultPicoServices;
import io.helidon.pico.tools.ToolsException;

/**
 * General utils.
 */
public class CommonUtils {

    private CommonUtils() {
    }

    /**
     * Will ensure that the reference implementation is being returned. If not found then will throw a
     * {@link io.helidon.pico.tools.ToolsException}.
     *
     * @param reset used to reset the registry and other internal caches
     *
     * @return the reference implementation for services
     */
    public static DefaultPicoServices safeGetPicoRefServices(boolean reset) {
        PicoServices picoServices = PicoServices.picoServices().get();
        if (Objects.isNull(picoServices)) {
            throw new ToolsException("no pico services found in classpath");
        }

        if (picoServices instanceof DefaultPicoServices) {
            DefaultPicoServices refPicoServices = (DefaultPicoServices) picoServices;

            if (reset) {
                refPicoServices.reset();
            }

            return refPicoServices;
        }

        throw new ToolsException("inappropriate use of pico services provider implementation: "
                                         + picoServices.getClass() + ":" + picoServices);
    }

    /**
     * Loads a string from a resource using this loader that loaded this class.
     *
     * @param resourceNamePath the resource path
     * @return the string, or null if the resource was not found
     * @throws io.helidon.pico.tools.ToolsException if there were any exceptions encountered
     */
    public static String loadStringFromResource(String resourceNamePath) {
        try {
            InputStream in = CommonUtils.class.getClassLoader().getResourceAsStream(resourceNamePath);
            if (Objects.isNull(in)) {
                return null;
            }
            try (in) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new ToolsException("unable to load resource: " + resourceNamePath, e);
        }
    }

    /**
     * Loads a String from a file, wrapping any exception encountered to a {@link io.helidon.pico.tools.ToolsException}.
     *
     * @param fileName the file name to load
     * @return the contents of the file
     * @throws io.helidon.pico.tools.ToolsException if there were any exceptions encountered
     */
    public static String loadStringFromFile(String fileName) {
        try {
            Path filePath = Path.of(fileName);
            String content = Files.readString(filePath);
            return content;
        } catch (IOException e) {
            throw new ToolsException("unable to load from file: " + fileName, e);
        }
    }

    /**
     * Searches system property, followed by env for a key value.
     *
     * @param name the property name
     * @param defaultVal the default value to use
     * @return the property value or else the default value
     */
    public static String getProp(String name, String defaultVal) {
        String val = System.getProperty(name);
        if (val == null) {
            val = System.getenv(name);
        }
        if (Objects.isNull(val)) {
            val = defaultVal;
        }
        if (Objects.nonNull(val)) {
            val = val.replaceFirst("^~", System.getProperty("user.home"));
        }
        return val;
    }

    /**
     * Searches system property, followed by env for a key value.
     *
     * @param name the property name
     * @param defaultVal the default value to use
     * @return the property value or null
     */
    public static boolean getProp(String name, boolean defaultVal) {
        return Boolean.parseBoolean(getProp(name, String.valueOf(defaultVal)));
    }

    /**
     * Searches system property, followed by env for a key value.
     *
     * @param name the property name
     * @param defaultVal the default value to use
     * @return the property value or defaultValue
     */
    public static int getProp(String name, int defaultVal) {
        return Integer.parseInt(getProp(name, String.valueOf(defaultVal)));
    }

    /**
     * Converts a collection to a comma delimited string.
     *
     * @param coll the collection
     * @return the concatenated, delimited string value
     */
    public static String toString(Collection<?> coll) {
        return toString(coll, null, null);
    }

    /**
     * Converts a collection using a path.separator delimited string.
     *
     * @param coll the collection
     * @return the concatenated, delimited string value
     */
    public static String toPathString(Collection<?> coll) {
        if (Objects.isNull(coll)) {
            return null;
        }

        return toString(coll, null, System.getProperty("path.separator"));
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
    public static <T> String toString(Collection<T> coll, Function<T, String> fnc, String separator) {
        Function<T, String> fn = Objects.isNull(fnc) ? String::valueOf : fnc;
        separator = Objects.isNull(separator) ? ", " : separator;
        return coll.stream().map(val -> fn.apply(val)).collect(Collectors.joining(separator));
    }

    /**
     * Converts the collection of type T to a set of strings, handling the null case.
     *
     * @param coll  the collection or null
     * @param fn    the mapper function
     * @param <T>   the type of the items in the collection
     * @return the set of mapped strings from the collection
     */
    public static <T> Set<String> toStringSet(Collection<T> coll, Function<T, String> fn) {
        if (Objects.isNull(coll)) {
            return Collections.emptySet();
        }

        return coll.stream().map(fn).collect(Collectors.toSet());
    }

    /**
     * Replaces the provided string's usage of '.' with '$'.
     *
     * @param className the classname
     * @return the converted string
     */
    public static String toFlatName(String className) {
        return className.replace('.', '$');
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
    public static <T> T first(Collection<T> coll, boolean allowEmptyCollection) {
        assert (Objects.nonNull(coll));

        if (coll.isEmpty()) {
            if (allowEmptyCollection) {
                return null;
            } else {
                throw new ToolsException("expected a non-empty collection");
            }
        }

        return coll.iterator().next();
    }

    /**
     * Returns the first element of a collection.
     *
     * @param coll                  the collection
     * @param <T> the type of the collection
     * @return the first element
     * @throws io.helidon.pico.tools.ToolsException if the collection is empty
     */
    public static <T> T first(Collection<T> coll) {
        return first(coll, false);
    }

    /**
     * Determines the root throwable from a chain of error causes.
     *
     * @param t the throwable
     * @return the root throwable
     */
    public static String rootErrorCoordinateOf(Throwable t) {
        if (Objects.isNull(t)) {
            return null;
        }

        while (Objects.nonNull(t.getCause()) && t.getCause() != t) {
            t = t.getCause();
        }

        return t.getStackTrace()[0].toString();
    }

    /**
     * Splits given using a comma-delimiter, and returns a trimmed list of string for each item.
     *
     * @param str the string to split
     * @return the list of string values
     */
    public static List<String> toList(String str) {
        return toList(str, ",");
    }

    /**
     * Splits given a delimiter, and returns a trimmed list of string for each item.
     *
     * @param str the string to split
     * @param delim the delimiter
     * @return the list of string values
     */
    public static List<String> toList(String str, String delim) {
        String[] split = str.split(delim);
        return Arrays.stream(split).map(String::trim).collect(Collectors.toList());
    }

}
