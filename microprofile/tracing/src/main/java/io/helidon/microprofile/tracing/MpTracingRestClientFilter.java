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

import java.lang.reflect.Method;

import io.helidon.tracing.jersey.client.ClientTracingFilter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import org.eclipse.microprofile.opentracing.Traced;

/**
 * Filter to handle REST client specifics.
 */
@Priority(Priorities.AUTHENTICATION - 350)
public class MpTracingRestClientFilter implements ClientRequestFilter {
    private static final String INVOKED_METHOD = "org.eclipse.microprofile.rest.client.invokedMethod";

    @Override
    public void filter(ClientRequestContext requestContext) {
        Method invokedMethod = (Method) requestContext.getProperty(INVOKED_METHOD);

        if (null == invokedMethod) {
            return;
        }

        Traced traced = invokedMethod.getAnnotation(Traced.class);
        if (null == traced) {
            return;
        }

        boolean enabled;
        String opName;

        enabled = traced.value();
        opName = traced.operationName();

        if (!opName.isEmpty()) {
            requestContext.setProperty(ClientTracingFilter.SPAN_NAME_PROPERTY_NAME, opName);
        }

        requestContext.setProperty(ClientTracingFilter.ENABLED_PROPERTY_NAME, enabled);
    }
}
