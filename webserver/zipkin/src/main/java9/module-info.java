/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
module io.helidon.webserver.zipkin {
    requires io.helidon.webserver;
    requires io.helidon.common;
    requires transitive opentracing.api;

    requires java.logging;
    requires opentracing.util;
    requires java.annotation;
    requires brave.opentracing;
    requires zipkin2.reporter;
    requires zipkin2.reporter.urlconnection;
    requires zipkin2;
    requires brave;

    // Support for injection and outbound context propagation
    requires static javax.inject;
    requires static jersey.client;
    requires static jersey.server;
    requires static jersey.common;
    requires static java.ws.rs;

    exports io.helidon.webserver.opentracing;
    exports io.helidon.webserver.zipkin;
}
