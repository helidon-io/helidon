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
 *
 */
package io.helidon.microprofile.metrics;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;

/**
 * Captures whether configuration enables or disables synthetic {@code SimplyMetric} annotation
 * behavior efficiently so interceptor instances know efficiently whether to find and update
 * the corresponding metrics or not.
 */
@ApplicationScoped
class RestEndpointMetricsInfo {

    private boolean isEnabled;

    @PostConstruct
    void setup() {
        isEnabled = MetricsCdiExtension.restEndpointsMetricEnabledFromConfig();
    }

    public boolean isEnabled() {
        return isEnabled;
    }
}
