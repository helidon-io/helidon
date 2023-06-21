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

package io.helidon.config;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Filter properties with provided and default filter pattern.
 */
public class PropertiesFilter {
    /**
     * Key filter system property.
     */
    static final String KEY_FILTER_PROPERTY = "io.helidon.config.env-filter.key";
    /**
     * Value filter system property.
     */
    static final String VALUE_FILTER_PROPERTY = "io.helidon.config.env-filter.value";
    /**
     * Use default filter system property.
     */
    static final String USE_DEFAULT_FILTER_PROPERTY = "io.helidon.config.env-filter.use-default";
    /**
     * Regex separator property.
     */
    static final String SEPARATOR_FILTER_PROPERTY = "io.helidon.config.env-filter.separator";
    private static final String REGEX_BASH_FUNC = "BASH_FUNC_(.*?)%%";
    private static final Pattern PATTERN_BASH_FUNC = Pattern.compile(REGEX_BASH_FUNC);
    private static final List<Pattern> DEFAULT_KEY_PATTERNS = List.of(PATTERN_BASH_FUNC);
    private final List<Pattern> keyFilters;
    private final List<Pattern> valueFilters;

    private PropertiesFilter(List<String> keyFilters, List<String> valueFilters, boolean useDefault) {
        this.keyFilters = convert(keyFilters);
        this.valueFilters = convert(valueFilters);
        if (useDefault) {
            this.keyFilters.addAll(DEFAULT_KEY_PATTERNS);
        }
    }

    /**
     * Create a {@link PropertiesFilter} instance.
     *
     * @param properties System Properties
     * @return a {@link PropertiesFilter} instance
     */
    public static PropertiesFilter create(Properties properties) {
        Objects.requireNonNull(properties, "properties are null");
        List<String> keyFilters = parseFilterProperty(KEY_FILTER_PROPERTY, properties);
        List<String> valueFilters = parseFilterProperty(VALUE_FILTER_PROPERTY, properties);
        boolean useDefault = Boolean.parseBoolean(properties.getProperty(USE_DEFAULT_FILTER_PROPERTY, "true"));
        return new PropertiesFilter(keyFilters, valueFilters, useDefault);
    }

    /**
     * Filter provided properties with this filter.
     *
     * @param properties to be filtered
     * @return  the filtered properties
     */
    public Map<String, String> filter(Map<String, String> properties) {
        return properties.entrySet().stream()
                .filter(entry -> matches(keyFilters, entry.getKey()))
                .filter(entry -> matches(valueFilters, entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static List<String> parseFilterProperty(String property, Properties properties) {
        String separator = properties.getProperty(SEPARATOR_FILTER_PROPERTY, ",");
        String resolved = properties.getProperty(property);
        if (resolved == null) {
            return List.of();
        }
        return Arrays.asList(resolved.split(separator));
    }

    private List<Pattern> convert(List<String> list) {
        return list.stream()
                .map(Pattern::compile)
                .collect(Collectors.toCollection(LinkedList::new));
    }

    private boolean matches(List<Pattern> patterns, String value) {
        return patterns.stream()
                .noneMatch(matcher -> matcher.matcher(value).matches());
    }
}
