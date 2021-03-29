/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.vault.auths.approle;

import io.helidon.config.Config;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.AuthMethod;
import io.helidon.integrations.vault.spi.AuthMethodProvider;

/**
 * Java Service Loader implementation for AppRole authentication method.
 */
public class AppRoleAuthProvider implements AuthMethodProvider<AppRoleAuth> {
    @Override
    public AuthMethod<AppRoleAuth> supportedMethod() {
        return AppRoleAuth.AUTH_METHOD;
    }

    @Override
    public AppRoleAuth createAuth(Config config, RestApi restApi, String path) {
        return new AppRoleAuthImpl(restApi, path);
    }
}
