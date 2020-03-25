/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import io.helidon.config.spi.ConfigFilter;

/**
 * A config filter that replaces values with a new ones of keys that matching with {@link Pattern}.
 */
public class OverrideConfigFilter implements ConfigFilter {

    private final Supplier<List<Map.Entry<Predicate<Config.Key>, String>>> overrideValuesSupplier;

    /**
     * Creates a filter with a given supplier of a map of key patterns to a override values.
     *
     * @param overrideValuesSupplier a supplier of a map of key patterns to a override values
     */
    public OverrideConfigFilter(Supplier<List<Map.Entry<Predicate<Config.Key>, String>>> overrideValuesSupplier) {
        this.overrideValuesSupplier = overrideValuesSupplier;
    }

    @Override
    public String apply(Config.Key key, String stringValue) {
        List<Map.Entry<Predicate<Config.Key>, String>> overrideValues = overrideValuesSupplier.get();
        if (overrideValues != null) {
            for (Map.Entry<Predicate<Config.Key>, String> entry : overrideValues) {
                if (entry.getKey().test(key)) {
                    return entry.getValue();
                }
            }
        }
        return stringValue;
    }
}
