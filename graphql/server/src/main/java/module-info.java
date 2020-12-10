/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 * GraphQl server implementation.
 */
module io.helidon.graphql.server {
    requires java.logging;

    requires java.json.bind;
    requires org.eclipse.yasson;

    requires io.helidon.common.configurable;
    requires io.helidon.common.http;
    requires io.helidon.media.common;
    requires io.helidon.media.jsonb;
    requires io.helidon.webserver;

    requires transitive io.helidon.webserver.cors;
    requires transitive io.helidon.config;
    requires transitive graphql.java;

    exports io.helidon.graphql.server;
}