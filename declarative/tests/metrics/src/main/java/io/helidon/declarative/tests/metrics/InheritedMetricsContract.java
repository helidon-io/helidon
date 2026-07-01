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

package io.helidon.declarative.tests.metrics;

import io.helidon.http.Http;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.Metrics;
import io.helidon.service.registry.Service;

@SuppressWarnings("deprecation")
@Service.Contract
interface InheritedMetricsContract {
    @Http.GET
    @Http.Path("/inherited-counted")
    @Metrics.Counted(value = "inherited-counted", absoluteName = true)
    @Metrics.Tag(key = "contract", value = "counted")
    String inheritedCounted();

    @Metrics.Gauge(value = "inherited-gauge", absoluteName = true, unit = Meter.BaseUnits.BYTES)
    @Metrics.Tag(key = "contract", value = "gauge")
    int inheritedGaugeValue();

    @Http.GET
    @Http.Path("/split-counted")
    @Metrics.Tag(key = "contract", value = "split-counted")
    String splitCounted();

    @Http.GET
    @Http.Path("/split-timed")
    @Metrics.Tag(key = "contract", value = "split-timed")
    @Metrics.Tag(key = "declaration", value = "contract")
    String splitTimed();
}
