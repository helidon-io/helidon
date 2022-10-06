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
 * GraphQL server integration with Helidon Reactive WebServer.
 */
module io.helidon.reactive.graphql.server {
    requires java.logging;
    requires io.helidon.common;
    requires io.helidon.common.uri;
    requires io.helidon.common.configurable;
    requires io.helidon.config;
    requires io.helidon.cors;
    requires io.helidon.graphql.server;
    requires io.helidon.reactive.media.common;
    requires io.helidon.reactive.media.jsonb;
    requires io.helidon.reactive.webserver;
    requires org.eclipse.yasson;
    requires io.helidon.reactive.webserver.cors;

    exports io.helidon.reactive.graphql.server;
}