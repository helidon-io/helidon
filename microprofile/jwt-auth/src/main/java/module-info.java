/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
 * Microprofile jwt module.
 */
module io.helidon.microprofile.jwt.auth {
    requires java.logging;

    requires jakarta.enterprise.cdi.api;
    requires jakarta.inject.api;
    requires jakarta.interceptor.api;
    requires java.ws.rs;
    requires microprofile.config.api;
    requires transitive microprofile.jwt.auth.api;

    requires io.helidon.common;
    requires io.helidon.common.pki;
    requires io.helidon.config;
    requires transitive io.helidon.security;
    requires io.helidon.microprofile.server;
    requires io.helidon.microprofile.security;
    requires io.helidon.security.providers.common;
    requires io.helidon.security.util;
    requires transitive io.helidon.security.jwt;
    requires io.helidon.security.integration.jersey;
    requires java.annotation;

    exports io.helidon.microprofile.jwt.auth;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.microprofile.jwt.auth to weld.core.impl, io.helidon.microprofile.cdi;

    provides io.helidon.security.providers.common.spi.AnnotationAnalyzer with io.helidon.microprofile.jwt.auth.JwtAuthAnnotationAnalyzer;
    provides io.helidon.security.spi.SecurityProviderService with io.helidon.microprofile.jwt.auth.JwtAuthProviderService;
    provides javax.enterprise.inject.spi.Extension with io.helidon.microprofile.jwt.auth.JwtAuthCdiExtension;
}
