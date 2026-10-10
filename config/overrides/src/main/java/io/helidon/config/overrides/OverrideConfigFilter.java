/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.config.overrides;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import io.helidon.config.Config;
import io.helidon.config.spi.ConfigFilter;

/**
 * An immutable configuration filter that replaces existing values whose keys match configured wildcard expressions
 * or regular expressions. The first matching rule wins. Filters do not add missing configuration nodes.
 */
final class OverrideConfigFilter implements ConfigFilter {
    private final List<OverrideEntry> entries;

    OverrideConfigFilter(OverrideConfig config, Config definitions) {
        List<OverrideEntry> entries = new ArrayList<>();
        config.overridePatterns().forEach(rule -> entries.add(new OverrideEntry(rule.pattern(), rule.value())));
        config.overrideExpressions().forEach((expression, value) -> entries.add(new OverrideEntry(
                OverrideConfigSupport.expressionToPattern(expression), value)));
        definitions.asMap().orElseGet(Map::of)
                .forEach((expression, value) -> entries.add(new OverrideEntry(
                        OverrideConfigSupport.expressionToPattern(expression), value)));
        this.entries = List.copyOf(entries);
    }

    @Override
    public String apply(Config.Key key, String stringValue) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(stringValue);
        for (OverrideEntry entry : entries) {
            if (entry.pattern().matcher(key.toString()).matches()) {
                return entry.value();
            }
        }
        return stringValue;
    }

    private record OverrideEntry(Pattern pattern, String value) {
    }
}
