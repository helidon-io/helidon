/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.security.integration.jersey.client;

import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.Priorities;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.core.FeatureContext;

import io.helidon.security.providers.common.OutboundConfig;

import org.glassfish.jersey.internal.spi.AutoDiscoverable;

/**
 * Auto discoverable feature to bind into jersey runtime.
 */
@ConstrainedTo(RuntimeType.CLIENT)
public class ClientSecurityAutoDiscoverable implements AutoDiscoverable {
    @Override
    public void configure(FeatureContext context) {
        if (Boolean.TRUE.equals(context.getConfiguration().getProperty(OutboundConfig.PROPERTY_DISABLE_OUTBOUND))) {
            return;
        }
        if (!context.getConfiguration().isRegistered(ClientSecurityFilter.class)) {
            context.register(ClientSecurityFilter.class, Priorities.AUTHENTICATION);
        }
    }
}
