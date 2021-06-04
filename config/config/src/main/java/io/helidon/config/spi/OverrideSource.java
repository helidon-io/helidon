/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.config.spi;

import java.io.IOException;
import java.io.Reader;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;

/**
 * Source of config override settings.
 * <p>
 * A config override setting provides an alternate, or overriding, value for a
 * config element based on the element's key. Implementations of this interface
 * furnish override settings as {@link OverrideData} objects.
 * <p>
 * The {@link OverrideData#data} method returns a {@code List} of pairs, each of
 * which contains a {@code Predicate} which evaluates the config key and a
 * {@code String} which is the overriding value to be used if the predicate is
 * {@code true}. The config system applies overrides before it applies
 * {@link ConfigFilter filters}, and it applies only the first matching override
 * it finds.
 * <p>
 * The config override mechanism affects existing {@code Config} nodes that come
 * from a {@link ConfigSource}. The override mechanism cannot create additional
 * {@code Config} nodes, only modify existing ones.
 *
 * @see OverrideData
 */
public interface OverrideSource extends Source, Supplier<OverrideSource> {

    @Override
    default OverrideSource get() {
        return this;
    }

    /**
     * Load override data from the underlying source.
     *
     * @return override data if present, empty otherwise
     * @throws ConfigException in case the loading of data failed
     */
    Optional<ConfigContent.OverrideContent> load() throws ConfigException;

    /**
     * Group of config override settings.
     * <p>
     * <a id="wildcardSupport">{@code OverrideData} supports</a> the {@code *}
     * wildcard character which represents one or more regex word characters:
     * [a-zA-Z_0-9]. In particular the {@link #create(java.io.Reader)} and
     * {@link #createFromWildcards} static factory methods deal with pairs of
     * {@code String}s; the first is a possible wildcard expression, and the
     * second is the replacement value the config system will use as it loads
     * any {@code Config} value node with a key that matches the wildcard
     * expression.
     */
    final class OverrideData {

        /**
         * A function to convert wildcards to {@link Predicate}&lt;{@link Config.Key}&gt;.
         */
        static final Function<String, Predicate<Config.Key>> WILDCARDS_TO_PREDICATE =
                (s) -> (Predicate<Config.Key>) key ->
                        Pattern.compile(s.replace("*", "\\w+").replace(".", "\\."))
                                .matcher(key.toString())
                                .matches();

        private List<Map.Entry<Predicate<Config.Key>, String>> data = new ArrayList<>();

        private OverrideData(List<Map.Entry<Predicate<Config.Key>, String>> data) {
            this.data = data;
        }

        /**
         * Creates {@code OverrideData} from a {@code List} of
         * predicate/replacement pairs. This method does not use
         * <a href="#wildcardSupport">wildcarding</a>.
         *
         * @param data the predicate/replacement pairs
         * @return {@code OverrideData} containing the specified pairs
         */
        public static OverrideData create(List<Map.Entry<Predicate<Config.Key>, String>> data) {
            return new OverrideData(data);
        }

        /**
         * Creates {@code OverrideData} from a {@code List} of {@code String}
         * pairs.
         * 
         * @param wildcards {@code List} of pairs of
         * <a href="#wildcardSupport">wildcard expressions</a> and corresponding
         * replacement values
         * @return {@code OverrideData} object containing the
         * {@code Predicate}/{@code String} pairs corresponding to the
         * wildcard/replacement pairs
         */
        public static OverrideData createFromWildcards(List<Map.Entry<String, String>> wildcards) {
            List<Map.Entry<Predicate<Config.Key>, String>> overrides = wildcards
                    .stream()
                    .map((e) -> new AbstractMap.SimpleEntry<>(
                            OverrideData.WILDCARDS_TO_PREDICATE.apply(e.getKey()), e.getValue()))
                    .collect(Collectors.toList());
            return new OverrideData(overrides);
        }

        /**
         * Creates {@code OverrideData} from a {@link Reader}.
         *
         * The {@code Reader} should provide lines in Java
         * {@link java.util.Properties} file format. In each line the
         * {@code String} to the left of the {@code =} sign is either a
         * {@link io.helidon.config.Config.Key} or a
         * <a href="#wildcardSupport">wildcard expressions</a> as described
         * above. The {@code String} to the right of the {@code =} sign is the
         * replacement value.
         *
         * @param reader a source
         * @return a new instance
         * @throws io.helidon.config.ConfigException when an error occurred when reading from the
         * reader
         */
        public static OverrideData create(Reader reader) {
            OrderedProperties properties = new OrderedProperties();
            try (Reader autoCloseableReader = reader) {
                properties.load(autoCloseableReader);
            } catch (IOException e) {
                throw new ConfigException("Cannot load data from reader.", e);
            }
            List<Map.Entry<Predicate<Config.Key>, String>> data = properties.orderedMap().entrySet()
                    .stream()
                    .map((e) -> new AbstractMap.SimpleEntry<>(WILDCARDS_TO_PREDICATE.apply(e.getKey()), e.getValue()))
                    .collect(Collectors.toList());
            return create(data);
        }

        /**
         * Creates an {@code OverrideData} object containing no overrides.
         *
         * @return an empty object
         */
        public static OverrideData empty() {
            return new OverrideData(List.of());
        }

        /**
         * Returns the predicate/replacement value pairs.
         *
         * @return a list of pairs of predicate and replacement value
         */
        public List<Map.Entry<Predicate<Config.Key>, String>> data() {
            return data;
        }
    }

}
