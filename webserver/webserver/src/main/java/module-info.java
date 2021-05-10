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

/**
 * Reactive web server.
 */
module io.helidon.webserver {
    requires io.helidon.common;
    requires transitive io.helidon.media.common;
    requires transitive io.helidon.common.http;
    requires io.helidon.common.mapper;
    requires transitive io.helidon.common.pki;
    requires transitive io.helidon.common.reactive;
    requires transitive io.helidon.common.context;
    requires transitive io.helidon.config;
    requires transitive io.helidon.tracing.config;
    requires transitive io.opentracing.util;

    requires java.logging;
    requires io.opentracing.api;
    requires io.opentracing.noop;
    requires io.netty.handler;
    requires io.netty.codec.http;
    requires io.netty.codec;
    requires io.netty.transport;
    requires io.netty.common;
    requires io.netty.buffer;
    requires io.netty.codec.http2;

    uses io.helidon.webserver.KeyPerformanceIndicatorMetricsServiceFactory;

    exports io.helidon.webserver;
}
