/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
 * GraphQL microprofile server module.
 */
module helidon.microprofile.graphql.server {
    requires cdi.api;

    requires com.fasterxml.jackson.databind;
    requires io.helidon.microprofile.cdi;

    requires jandex;
    requires java.ws.rs;
    requires java.desktop;
    requires java.json.bind;
    requires java.annotation;
    requires java.logging;
    requires graphql.java;
    requires graphql.java.extended.scalars;
    requires microprofile.graphql.api;

    exports io.helidon.microprofile.graphql.server;

    provides javax.enterprise.inject.spi.Extension with
            io.helidon.microprofile.graphql.server.GraphQLCdiExtension;

    opens io.helidon.microprofile.graphql.server to weld.core.impl;
}