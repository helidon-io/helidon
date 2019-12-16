/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
 * Jaeger tracing support.
 */
module io.helidon.tracing.jaeger {
    requires io.helidon.common;
    requires io.helidon.config;
    requires io.helidon.tracing;

    requires java.logging;
    requires io.opentracing.util;
    requires jaeger.client;
    requires jaeger.core;
    requires io.opentracing.noop;

    exports io.helidon.tracing.jaeger;

    provides io.helidon.tracing.spi.TracerProvider with io.helidon.tracing.jaeger.JaegerTracerProvider;
}
