/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
 * OpenAPI integration with Helidon Reactive WebServer.
 */
module io.helidon.reactive.openapi {
    requires io.helidon.common;
    requires io.helidon.common.http;
    requires io.helidon.config;
    requires io.helidon.cors;
    requires io.helidon.openapi;
    requires io.helidon.reactive.media.common;
    requires io.helidon.reactive.webserver;
    requires io.helidon.reactive.media.jsonp;
    requires io.helidon.reactive.webserver.cors;
    requires smallrye.open.api.core;
    requires org.jboss.jandex;
    requires org.yaml.snakeyaml;

    requires static io.helidon.config.metadata;

    exports io.helidon.reactive.openapi;
}