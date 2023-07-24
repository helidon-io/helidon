/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.tracing;

import io.helidon.tracing.jersey.client.ClientTracingFilter;

import jakarta.ws.rs.Priorities;
import org.eclipse.microprofile.opentracing.Traced;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.spi.RestClientListener;

/**
 * Tracing extension for Rest Client.
 * Registers a filter that reads {@link org.eclipse.microprofile.opentracing.Traced} from methods to configure (or reconfigure)
 * tracing.
 */
public class MpTracingRestClientListener implements RestClientListener {
    private static final ClientTracingFilter FILTER = new ClientTracingFilter();
    private static final MpTracingRestClientFilter REST_CLIENT_FILTER = new MpTracingRestClientFilter();

    @Override
    public void onNewClient(Class<?> serviceInterface, RestClientBuilder builder) {
        Traced traced = serviceInterface.getAnnotation(Traced.class);

        boolean enabled;
        String opName;

        if (null != traced) {
            enabled = traced.value();
            opName = traced.operationName();
        } else {
            enabled = true;
            opName = "";
        }

        builder.register(REST_CLIENT_FILTER, Priorities.AUTHENTICATION - 300);
        builder.register(FILTER, Priorities.AUTHENTICATION - 250);

        if (!opName.isEmpty()) {
            builder.property(ClientTracingFilter.SPAN_NAME_PROPERTY_NAME, opName);
        }

        builder.property(ClientTracingFilter.ENABLED_PROPERTY_NAME, enabled);
    }
}
