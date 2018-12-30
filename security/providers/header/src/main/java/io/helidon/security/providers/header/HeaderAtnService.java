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

package io.helidon.security.providers.header;

import io.helidon.config.Config;
import io.helidon.security.spi.SecurityProvider;
import io.helidon.security.spi.SecurityProviderService;

/**
 * Service for {@link HeaderAtnProvider} to auto-configure it
 * with {@link io.helidon.security.Security}.
 */
public class HeaderAtnService implements SecurityProviderService {
    @Override
    public String providerConfigKey() {
        return "header-atn";
    }

    @Override
    public Class<? extends SecurityProvider> providerClass() {
        return HeaderAtnProvider.class;
    }

    @Override
    public SecurityProvider providerInstance(Config config) {
        return HeaderAtnProvider.create(config);
    }
}
