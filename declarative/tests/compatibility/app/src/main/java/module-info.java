/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

@SuppressWarnings({"helidon:api:incubating", "requires-automatic"})
module io.helidon.declarative.tests.compatibility.app {
    requires io.helidon.config.yaml;
    requires io.helidon.declarative.tests.compatibility.v4;
    requires io.helidon.faulttolerance;
    requires io.helidon.http;
    requires io.helidon.http.media.json.binding;
    requires io.helidon.json.binding;
    requires io.helidon.logging.common;
    requires io.helidon.metrics.api;
    requires io.helidon.scheduling;
    requires io.helidon.service.registry;
    requires io.helidon.tracing;
    requires io.helidon.tracing.config;
    requires io.helidon.validation;
    requires io.helidon.webclient.api;
    requires io.helidon.webclient.http1;
    requires io.helidon.webclient.websocket;
    requires io.helidon.webserver;
    requires io.helidon.webserver.concurrency.limits;
    requires io.helidon.webserver.cors;
    requires io.helidon.webserver.observe;
    requires io.helidon.webserver.observe.metrics;
    requires io.helidon.webserver.observe.tracing;
    requires io.helidon.webserver.websocket;

    exports io.helidon.declarative.tests.compatibility.app;
}
