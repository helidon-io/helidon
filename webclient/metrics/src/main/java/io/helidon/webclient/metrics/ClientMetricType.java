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
package io.helidon.webclient.metrics;

import java.util.function.Function;

/**
 * Supported client metric types.
 */
public enum ClientMetricType {

    /**
     * Client counter metric.
     */
    COUNTER(ClientCounter::new),
    /**
     * Client timer metric.
     */
    TIMER(ClientTimer::new),
    /**
     * Client gauge in progress metric.
     */
    GAUGE_IN_PROGRESS(ClientGaugeInProgress::new),
    /**
     * Client meter metric.
     */
    METER(ClientMeter::new);

    private final Function<ClientMetric.Builder, ClientMetric> function;

    ClientMetricType(Function<ClientMetric.Builder, ClientMetric> function) {
        this.function = function;
    }

    ClientMetric createInstance(ClientMetric.Builder builder) {
        return function.apply(builder);
    }

}
