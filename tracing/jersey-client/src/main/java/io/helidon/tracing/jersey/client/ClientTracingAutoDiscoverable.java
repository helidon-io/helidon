/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
package io.helidon.tracing.jersey.client;

import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.core.FeatureContext;
import org.glassfish.jersey.internal.spi.AutoDiscoverable;

/**
 * Auto discoverable feature to bind into jersey runtime.
 */
@ConstrainedTo(RuntimeType.CLIENT)
public class ClientTracingAutoDiscoverable implements AutoDiscoverable {

    static final int CLIENT_TRACING_PRIORITY = 10;

    @Override
    public void configure(FeatureContext context) {
        if (!context.getConfiguration().isRegistered(ClientTracingFilter.class)) {
            context.register(ClientTracingFilter.class, CLIENT_TRACING_PRIORITY);
            context.register(ClientTracingInterceptor.class, CLIENT_TRACING_PRIORITY);
        }
    }
}
