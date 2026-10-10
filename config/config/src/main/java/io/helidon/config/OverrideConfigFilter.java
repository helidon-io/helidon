/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import io.helidon.config.spi.ConfigFilter;

/**
 * A config filter that replaces values with a new ones of keys that matching with {@link Pattern}.
 *
 * @deprecated Since 28.0.0, this legacy core implementation is deprecated for removal in favor of the optional
 *             overrides filter module. Overrides functionality is being replaced, not discontinued.
 *             If you use config overrides, please report your usage in
 *             <a href="https://github.com/helidon-io/helidon/issues/10415">issue 10415</a>.
 *             Removal timing has not been decided. See {@link io.helidon.config.spi.OverrideSource}
 *             for migration considerations.
 */
@Deprecated(since = "28.0.0", forRemoval = true)
public class OverrideConfigFilter implements ConfigFilter {

    private static final System.Logger LOGGER = System.getLogger(OverrideConfigFilter.class.getName());
    private static final AtomicBoolean DEPRECATION_LOGGED = new AtomicBoolean();

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
                    if (!DEPRECATION_LOGGED.get() && DEPRECATION_LOGGED.compareAndSet(false, true)) {
                        LOGGER.log(Level.WARNING,
                                   "Legacy Helidon Config overrides APIs are deprecated in favor of the optional overrides "
                                           + "filter module. If you use this feature, "
                                           + "please report your usage at https://github.com/helidon-io/helidon/issues/10415. "
                                           + "Removal timing has not been decided.");
                    }
                    return entry.getValue();
                }
            }
        }
        return stringValue;
    }
}
