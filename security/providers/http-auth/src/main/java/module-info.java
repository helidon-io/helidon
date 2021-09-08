/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
 * Basic and digest authentication provider.
 */
module io.helidon.security.providers.httpauth {
    requires io.helidon.config;
    requires io.helidon.common;
    requires io.helidon.common.serviceloader;
    requires io.helidon.security;
    requires io.helidon.security.providers.common;
    requires io.helidon.security.util;
    requires java.logging;
    requires static io.helidon.config.metadata;

    exports io.helidon.security.providers.httpauth;

    provides io.helidon.security.spi.SecurityProviderService with io.helidon.security.providers.httpauth.HttpBasicAuthService,
                io.helidon.security.providers.httpauth.HttpDigestAuthService;

    uses io.helidon.security.providers.httpauth.spi.UserStoreService;
}
