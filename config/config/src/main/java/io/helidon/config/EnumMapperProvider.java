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
package io.helidon.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.config.spi.ConfigMapperProvider;

/**
 * Built-in mapper for {@code enum}s.
 *
 * <p>
 *     This mapper attempts to match strings in the config source to enum values as follows:
 *     <ul>
 *         <li>The mapper treats hyphens ('-') in config strings as underscores when comparing to enum value names.</li>
 *         <li>If the matcher finds a <em>case-sensitive</em> match with an enum value name, then that enum value matches.</li>
 *         <li>If the matcher finds exactly one <em>case-insensitive</em> match, that enum value matches.</li>
 *         <li>If the matcher finds no matches or multiple matches, throw a
 *         {@link io.helidon.config.ConfigEnumMappingException} with a message explaining the problem.</li>
 *     </ul>
 *     These conversions are intended to maximize ease-of-use for authors of config sources so the values need not be
 *     upper-cased nor punctuated with underscores rather than the more conventional (in config at least) hyphen.
 * </p>
 * <p>
 *     The only hardship this imposes is if a confusingly-designed enum has values which differ only in case <em>and</em> the
 *     string in the config source does not exactly match one of the enum value names. In such cases
 *     the mapper will be unable to choose which enum value matches an ambiguous string. A developer faced with this
 *     problem can simply provide her own explicit config mapping for that enum, for instance as a function parameter to
 *     {@code Config#as}.
 * </p>
 *
 */
class EnumMapperProvider implements ConfigMapperProvider {

    private final Map<Class<?>, Function<Config, ?>> mappers = new HashMap<>();

    @Override
    public Map<Class<?>, Function<Config, ?>> mappers() {
        return mappers;
    }

    @Override
    public <T> Optional<Function<Config, T>> mapper(Class<T> type) {
        if (!type.isEnum()) {
            return Optional.empty();
        }

        Function<Config, T> result = (Function<Config, T>) mappers.computeIfAbsent(type, this::enumMapper);
        return Optional.of(result);
    }

    private <T> Function<Config, T> enumMapper(Class<T> type) {
        return config -> {
            // type.isEnum() must be true in the caller, so we know the type is Class<Enum<?>>.
            Class<Enum<?>> enumType = (Class<Enum<?>>) type;
            if (!config.hasValue() || !config.exists()) {
                throw MissingValueException.create(config.key());
            }
            if (!config.isLeaf()) {
                throw new ConfigEnumMappingException(config.key(), "config node must be a leaf but is not", enumType, "unknown");
            }
            String value = config.asString().get();
            String convertedValue = value.replace('-', '_');
            List<Enum<?>> caseInsensitiveMatches = new ArrayList<>();
            for (Enum<?> candidate : enumType.getEnumConstants()) {
                // Check for an exact match first, with or without hyphen conversion.
                if (candidate.name().equals(convertedValue) || candidate.name().equals(value)) {
                    return (T) candidate;
                }
                if (candidate.name().compareToIgnoreCase(convertedValue) == 0
                        || candidate.name().compareToIgnoreCase(value) == 0) {
                    caseInsensitiveMatches.add(candidate);
                }
            }
            if (caseInsensitiveMatches.size() == 1) {
                return (T) caseInsensitiveMatches.get(0);
            }

            String problem;
            if (caseInsensitiveMatches.size() == 0) {
                problem = "no match";
            } else {
                problem = "ambiguous matches with " + caseInsensitiveMatches;
            }

            throw new ConfigEnumMappingException(config.key(), problem, enumType, value);
        };
    }
}
