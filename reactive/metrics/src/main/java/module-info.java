/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
 * Metrics endpoint for reactive WebServer.
 */
module io.helidon.reactive.metrics {
    requires io.helidon.metrics.api;
    requires io.helidon.reactive.webserver;
    requires static io.helidon.config.metadata;
    requires io.helidon.metrics.serviceapi;
    requires io.helidon.reactive.media.jsonp;
    requires io.helidon.reactive.servicecommon;
    requires java.logging;

    exports io.helidon.reactive.metrics;
}