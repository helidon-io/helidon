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
import io.micrometer.core.instrument.config.NamingConvention;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;

final class PrometheusNameSupport {

    private PrometheusNameSupport() {
    }

    static String expositionName(Meter.Id meterId, NamingConvention namingConvention) {
        // Apply the writer's final character escaping after the convention has appended any base unit and type suffix.
        // Do not use sanitizeMetricName here because compatibility naming deliberately preserves reserved suffixes.
        return PrometheusNaming.prometheusName(meterId.getConventionName(namingConvention));
    }
}
