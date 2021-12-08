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
 * Microprofile OIDC integration.
 *
 * @see org.eclipse.microprofile.auth
 */
module io.helidon.microprofile.oidc {
    requires java.logging;

    requires io.helidon.microprofile.server;
    requires io.helidon.microprofile.security;
    requires io.helidon.security.providers.oidc;

    exports io.helidon.microprofile.oidc;

    provides jakarta.enterprise.inject.spi.Extension with io.helidon.microprofile.oidc.OidcCdiExtension;
}
