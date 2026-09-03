/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.metrics.providers.micrometer;

import java.util.Objects;
import java.util.regex.Pattern;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;

/**
 * Prometheus naming convention compatible with the legacy simpleclient-based Micrometer registry.
 */
class LegacyPrometheusNamingConvention implements NamingConvention {
    private static final Pattern NAME_CHARS = Pattern.compile("[^a-zA-Z0-9_:]");
    private static final Pattern TAG_KEY_CHARS = Pattern.compile("[^a-zA-Z0-9_]");
    private static final Pattern VALID_PREFIX = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private final String timerSuffix;
    private final String nonLetterPrefix;

    LegacyPrometheusNamingConvention(String timerSuffix, String nonLetterPrefix) {
        this.timerSuffix = Objects.requireNonNull(timerSuffix);
        this.nonLetterPrefix = validatePrefix(nonLetterPrefix);
    }

    @Override
    public String name(String name, Meter.Type type, String baseUnit) {
        if (type == Meter.Type.GAUGE) {
            name = LegacyPrometheusMeterFilter.originalGaugeName(name);
        }
        String conventionName = NamingConvention.snakeCase.name(name, type, baseUnit);

        switch (type) {
        case COUNTER, DISTRIBUTION_SUMMARY, GAUGE -> {
            if (baseUnit != null && !conventionName.endsWith("_" + baseUnit)) {
                conventionName += "_" + baseUnit;
            }
        }
        default -> {
        }
        }

        conventionName = switch (type) {
            case COUNTER -> conventionName.endsWith("_total") ? conventionName : conventionName + "_total";
            case TIMER -> timerName(conventionName);
            default -> conventionName;
        };

        return addPrefixIfNeeded(NAME_CHARS.matcher(conventionName).replaceAll("_"));
    }

    @Override
    public String tagKey(String key) {
        String conventionKey = NamingConvention.snakeCase.tagKey(key);
        return addPrefixIfNeeded(TAG_KEY_CHARS.matcher(conventionKey).replaceAll("_"));
    }

    static String validatePrefix(String prefix) {
        Objects.requireNonNull(prefix);
        if (!VALID_PREFIX.matcher(prefix).matches()) {
            throw new IllegalArgumentException("Prometheus non-letter prefix must match " + VALID_PREFIX.pattern()
                                                       + ", but was: " + prefix);
        }
        return prefix;
    }

    private String addPrefixIfNeeded(String value) {
        return Character.isLetter(value.charAt(0)) ? value : nonLetterPrefix + value;
    }

    private String timerName(String conventionName) {
        if (!timerSuffix.isEmpty() && conventionName.endsWith(timerSuffix)) {
            return conventionName + "_seconds";
        }
        return conventionName.endsWith("_seconds") ? conventionName : conventionName + timerSuffix + "_seconds";
    }
}
