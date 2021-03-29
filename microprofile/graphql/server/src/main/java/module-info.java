/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.microprofile.graphql.server.GraphQlCdiExtension;

/**
 * GraphQL microprofile server module.
 */
module io.helidon.microprofile.graphql.server {
    requires java.logging;
    requires java.desktop;

    requires java.json.bind;
    requires java.annotation;
    requires jakarta.enterprise.cdi.api;
    requires org.eclipse.yasson;

    requires jandex;

    requires io.helidon.config;
    requires io.helidon.webserver;
    requires io.helidon.graphql.server;
    requires io.helidon.microprofile.cdi;
    requires io.helidon.microprofile.server;

    requires graphql.java;
    requires graphql.java.extended.scalars;
    requires microprofile.graphql.api;
    requires microprofile.config.api;

    exports io.helidon.microprofile.graphql.server;

    provides javax.enterprise.inject.spi.Extension with
            GraphQlCdiExtension;

    opens io.helidon.microprofile.graphql.server to weld.core.impl;
}
