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
 * OIDC authentication and security propagation provider.
 */
module io.helidon.security.oidc {
    requires io.helidon.config;
    requires io.helidon.common;
    requires io.helidon.security;
    requires java.logging;

    requires io.helidon.security.oidc.common;
    requires io.helidon.security.providers;
    requires io.helidon.security.util;
    requires io.helidon.security.abac.scope;
    requires io.helidon.security.jwt;
    requires jersey.client;
    requires java.ws.rs;
    requires io.helidon.webserver;
    requires io.helidon.security.adapter.webserver;

    exports io.helidon.security.oidc;

    provides io.helidon.security.spi.SecurityProviderService with io.helidon.security.oidc.OidcProviderService;
}
