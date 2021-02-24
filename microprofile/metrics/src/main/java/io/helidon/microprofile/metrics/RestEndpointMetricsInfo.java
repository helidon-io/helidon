/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.lang.reflect.Method;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.interceptor.InvocationContext;
import javax.ws.rs.container.AsyncResponse;

import io.helidon.microprofile.metrics.MetricsCdiExtension.AsyncResponseInfo;

/**
 * Captures information about REST endpoint synthetic annotations so interceptors can be quicker. Includes:
 * <ul>
 *     <li>whether configuration enables or disables synthetic {@code SimplyMetric} annotation behavior</li>
 *     <li>which JAX-RS endpoint methods (if any) are asynchronous</li>
 * </ul>
 */
@ApplicationScoped
class RestEndpointMetricsInfo {

    private boolean isEnabled;
    private Map<Method, AsyncResponseInfo> asyncResponseInfo;

    @Inject
    RestEndpointMetricsInfo(BeanManager beanManager) {
        MetricsCdiExtension metricsCdiExtension = beanManager.getExtension(MetricsCdiExtension.class);
        isEnabled = metricsCdiExtension.restEndpointsMetricEnabledFromConfig();
        asyncResponseInfo = metricsCdiExtension.asyncResponseInfo();
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public AsyncResponse asyncResponse(InvocationContext context) {
        AsyncResponseInfo info = asyncResponseInfo.get(context.getMethod());
        return info == null ? null : info.asyncResponse(context);
    }
}
