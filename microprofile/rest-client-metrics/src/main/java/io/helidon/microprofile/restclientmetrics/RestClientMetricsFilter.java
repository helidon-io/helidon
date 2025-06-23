/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.restclientmetrics;

import java.lang.reflect.Method;

import io.helidon.common.LazyValue;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Filter which automatically registers and updates metrics for outgoing REST client requests.
 * <p>
 * An instance of this filter is added explicitly to the filter chain for each REST client interface
 * </p>
 */
@Priority(Priorities.USER - 100)
@Provider
class RestClientMetricsFilter implements ClientRequestFilter, ClientResponseFilter {

    static final String REST_CLIENT_METRICS_CONFIG_KEY = "rest-client.metrics";

    private static final String INVOKED_METHOD = "org.eclipse.microprofile.rest.client.invokedMethod";

    /*
    In some cases, the system will invoke REST client filters before CDI has completed its startup. The REST client metrics
     CDI extension registers the meters to be updated for each REST client method, so this filter has nothing to do if
     it runs before CDI has initialized the extension. That said, it needs to keep retrying to locate the CDI extension
     because the extension might (should) become available later. The code below uses a utility method to access this
     lazy value to allow that retry behavior.
     */
    private final LazyValue<RestClientMetricsCdiExtension> ext =
            LazyValue.create(() -> CDI.current().getBeanManager().getExtension(RestClientMetricsCdiExtension.class));;

    private RestClientMetricsFilter() {
    }

    static RestClientMetricsFilter create() {
        return new RestClientMetricsFilter();
    }

    @Override
    public void filter(ClientRequestContext requestContext) {
        Method javaMethod = (Method) requestContext.getProperty(INVOKED_METHOD);
        if (javaMethod != null) {
            Utils.optOf(ext).ifPresent(e -> e.doPreWork(javaMethod, requestContext));
        }
    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) {
        Method javaMethod = (Method) requestContext.getProperty(INVOKED_METHOD);
        if (javaMethod != null) {
            Utils.optOf(ext).ifPresent(e -> e.doPostWork(javaMethod, requestContext));
        }
    }
}
