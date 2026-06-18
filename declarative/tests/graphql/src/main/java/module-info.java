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

/**
 * Tests for declarative GraphQL.
 */
@SuppressWarnings("helidon:api:incubating")
module io.helidon.declarative.tests.graphql {
    requires io.helidon.common;
    requires io.helidon.graphql;
    requires io.helidon.graphql.server;
    requires io.helidon.logging.common;
    requires io.helidon.security;
    requires io.helidon.security.abac.role;
    requires io.helidon.security.annotations;
    requires io.helidon.security.providers.abac;
    requires io.helidon.security.providers.httpauth;
    requires io.helidon.service.registry;
    requires io.helidon.validation;
    requires io.helidon.webclient.api;
    requires io.helidon.webserver;
    requires io.helidon.webserver.context;
    requires io.helidon.webserver.graphql;
    requires io.helidon.webserver.security;

    // needed for generated binding
    requires io.helidon.config.yaml;

    exports io.helidon.declarative.tests.graphql;
}
