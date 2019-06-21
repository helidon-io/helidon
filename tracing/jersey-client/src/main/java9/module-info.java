/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.jersey.internal.spi.AutoDiscoverable;

/**
 * Tracing integration with jersey (JAX-RS) client.
 */
module io.helidon.tracing.jersey.client {
    requires java.logging;
    requires java.annotation;

    requires java.ws.rs;
    requires javax.inject;
    requires jersey.client;
    requires jersey.common;

    requires opentracing.api;
    requires opentracing.util;

    requires io.helidon.tracing;
    requires io.helidon.tracing.config;
    requires io.helidon.common;
    requires io.helidon.common.context;

    exports io.helidon.tracing.jersey.client;

    // needed to propagate tracing context from server to client
    opens io.helidon.tracing.jersey.client.internal to io.helidon.tracing.jersey,io.helidon.microprofile.tracing;
    exports io.helidon.tracing.jersey.client.internal;

    provides AutoDiscoverable with io.helidon.tracing.jersey.client.ClientTracingAutoDiscoverable;
}
