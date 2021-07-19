/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import io.helidon.rsocket.metrics.MetricsDuplexConnectionInterceptor;
import io.helidon.rsocket.metrics.MetricsRSocketInterceptor;
import io.rsocket.plugins.DuplexConnectionInterceptor;
import io.rsocket.plugins.RSocketInterceptor;

/**
 * RSocket metrics support.
 */
module io.helidon.rsocket.metrics {
    requires java.logging;

    requires rsocket.core;
    requires reactor.core;
    requires io.netty.buffer;
    requires org.reactivestreams;
    requires io.helidon.common.reactive;
    requires io.helidon.common;
    requires io.helidon.config;
    requires microprofile.metrics.api;
    requires io.helidon.rsocket.server;
    requires jakarta.websocket.api;
    requires io.helidon.metrics;

    provides DuplexConnectionInterceptor with MetricsDuplexConnectionInterceptor;
    provides RSocketInterceptor with MetricsRSocketInterceptor;

    exports io.helidon.rsocket.metrics;
}
