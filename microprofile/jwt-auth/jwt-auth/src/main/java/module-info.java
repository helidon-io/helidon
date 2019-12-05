/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

    requires cdi.api;
    requires microprofile.config.api;
    requires microprofile.jwt.auth.api;

    requires io.helidon.common;
    requires io.helidon.common.pki;
    requires io.helidon.config;
    requires transitive io.helidon.security;
    requires io.helidon.security.providers.common;
    requires io.helidon.security.util;
    requires transitive io.helidon.security.jwt;
    requires io.helidon.security.integration.jersey;

    exports io.helidon.microprofile.jwt.auth;
}
