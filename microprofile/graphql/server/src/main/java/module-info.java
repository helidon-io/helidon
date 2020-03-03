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
    requires io.helidon.microprofile.cdi;
    requires cdi.api;
    requires java.ws.rs;
    requires jandex;
    requires java.logging;
    requires graphql.java;
    requires microprofile.graphql.api;
    requires graphql.java.extended.scalars;

    exports io.helidon.microprofile.graphql.server.application;

    opens io.helidon.microprofile.graphql.server.application to weld.core.impl;
}