/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

import javax.enterprise.inject.spi.Extension;

/**
 * Eclipse Microprofile Tracing implementation for helidon microprofile.
 */
module io.helidon.microprofile.tracing {
    requires java.logging;
    requires java.annotation;

    requires java.ws.rs;
    requires jersey.common;
    requires io.opentracing.api;

    requires static jakarta.enterprise.cdi.api;
    requires static jakarta.inject.api;
    requires static jakarta.interceptor.api;

    requires io.helidon.microprofile.server;
    requires transitive io.helidon.microprofile.config;
    requires io.helidon.common;
    requires io.helidon.webserver;
    requires io.helidon.jersey.common;
    requires transitive io.helidon.tracing;
    requires transitive io.helidon.tracing.jersey;
    requires io.helidon.tracing.tracerresolver;

    requires transitive microprofile.opentracing.api;
    requires microprofile.rest.client.api;


    exports io.helidon.microprofile.tracing;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.microprofile.tracing to weld.core.impl,hk2.utils;

    provides Extension with io.helidon.microprofile.tracing.TracingCdiExtension;
    provides org.glassfish.jersey.internal.spi.AutoDiscoverable with io.helidon.microprofile.tracing.MpTracingAutoDiscoverable;
}
