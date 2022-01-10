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
package io.helidon.microprofile.metrics;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.SimpleTimer;

/**
 * Represents metrics-related work done before and/or after Helidon processes REST requests.
 * <p>
 *     An optional MP metrics feature (which Helidon implements) updates a {@link SimpleTimer} for each REST request
 *     successfully processed or throwing a mapped exception and updates a {@link Counter} for each unmapped exception thrown
 *     during the handling of a REST request.
 * </p>
 */
record SyntheticRestRequestWorkItem(MetricID successfulSimpleTimerMetricID,
                                    SimpleTimer successfulSimpleTimer,
                                    MetricID unmappedExceptionCounterMetricID,
                                    Counter unmappedExceptionCounter) implements MetricWorkItem {

    static SyntheticRestRequestWorkItem create(MetricID successfulSimpleTimerMetricID,
                                               SimpleTimer successfulSimpleTimer,
                                               MetricID unmappedExceptionCounterMetricID,
                                               Counter unmappedExceptionCounter) {
        return new SyntheticRestRequestWorkItem(successfulSimpleTimerMetricID,
                                                successfulSimpleTimer,
                                                unmappedExceptionCounterMetricID,
                                                unmappedExceptionCounter);
    }
}
