/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.jwt.auth;

import io.helidon.config.Config;
import io.helidon.security.spi.SecurityProvider;
import io.helidon.security.spi.SecurityProviderService;

/**
 * {@link java.util.ServiceLoader} service to load {@link JwtAuthProvider}.
 */
public class JwtAuthProviderService implements SecurityProviderService {
    static final String PROVIDER_NAME = "mp-jwt-auth";
    @Override
    public String providerConfigKey() {
        return PROVIDER_NAME;
    }

    @Override
    public Class<? extends SecurityProvider> providerClass() {
        return JwtAuthProvider.class;
    }

    @Override
    public SecurityProvider providerInstance(Config config) {
        return JwtAuthProvider.create(config);
    }
}
