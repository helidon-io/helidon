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

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;

/**
 * Prevents the new Prometheus registry from treating a legacy gauge name ending in {@code .info} as an info metric.
 * The marker also preserves the original gauge name through Micrometer's pre-filter ID mapping so the Prometheus
 * registry can detect distinct names which normalize to the same name.
 */
class LegacyPrometheusMeterFilter implements MeterFilter {
    private static final String GAUGE_MARKER = "\uE000helidon-legacy-gauge:";

    @Override
    public Meter.Id map(Meter.Id id) {
        if (id.getType() != Meter.Type.GAUGE) {
            return id;
        }
        return id.withName(GAUGE_MARKER + id.getName() + GAUGE_MARKER);
    }

    static String originalGaugeName(String name) {
        return name.startsWith(GAUGE_MARKER) && name.endsWith(GAUGE_MARKER)
                ? name.substring(GAUGE_MARKER.length(), name.length() - GAUGE_MARKER.length())
                : name;
    }
}
