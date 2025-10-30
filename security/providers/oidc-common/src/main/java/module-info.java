/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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
 * OIDC common classes.
 */
module io.helidon.security.providers.oidc.common {

    requires io.helidon.common.context;
    requires io.helidon.common.crypto;
    requires io.helidon.common.parameters;
    requires io.helidon.common.pki;
    requires io.helidon.cors;
    requires io.helidon.http.media.jsonp;
    requires io.helidon.security.providers.common;
    requires io.helidon.security.providers.httpauth;
    requires io.helidon.webclient.security; // EncryptionProvider.EncryptionSupport is part of API
    requires io.helidon.webclient.tracing;

    requires static io.helidon.config.metadata;

    requires transitive io.helidon.security.jwt;
    requires transitive io.helidon.security.util; // TokenHandler is part of API
    requires transitive io.helidon.security;
    requires transitive io.helidon.webclient;

    exports io.helidon.security.providers.oidc.common;
    exports io.helidon.security.providers.oidc.common.spi;

}
