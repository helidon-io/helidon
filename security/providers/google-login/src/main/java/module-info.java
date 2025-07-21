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

import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Google login authentication provider.
 *
 * @deprecated use our OpenID Connect security provider instead
 */
@Features.Name("Google Login")
@Features.Description("Security provider for Google login button authentication and outbound")
@Features.Flavor({HelidonFlavor.SE, HelidonFlavor.MP})
@Features.Path({"Security", "Provider", "Google-Login"})
@Features.Aot(false)
@Deprecated(forRemoval = true, since = "4.3.0")
module io.helidon.security.providers.google.login {

    requires com.google.api.client.auth;
    requires com.google.api.client.json.gson;
    requires com.google.api.client;
    requires google.api.client;
    requires io.helidon.common;
    requires io.helidon.security.util;
    requires io.helidon.tracing;

    requires static io.helidon.common.features.api;
    requires static io.helidon.config.metadata;

    requires transitive io.helidon.config;
    requires transitive io.helidon.security.providers.common;
    requires transitive io.helidon.security;

    exports io.helidon.security.providers.google.login;

    provides io.helidon.security.spi.SecurityProviderService with io.helidon.security.providers.google.login.GoogleTokenService;

}
