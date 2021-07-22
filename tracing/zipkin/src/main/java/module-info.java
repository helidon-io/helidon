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
 * Zipkin tracing support.
 */
module io.helidon.tracing.zipkin {
    requires io.helidon.common;
    requires io.helidon.config;
    requires io.helidon.tracing;

    requires java.logging;
    requires io.opentracing.util;
    requires brave.opentracing;
    requires zipkin2.reporter;
    requires zipkin2.reporter.urlconnection;
    requires zipkin2;
    requires brave;
    requires io.opentracing.noop;

    exports io.helidon.tracing.zipkin;

    provides io.helidon.tracing.spi.TracerProvider with io.helidon.tracing.zipkin.ZipkinTracerProvider;
}
