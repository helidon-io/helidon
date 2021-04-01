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

import java.util.LinkedList;
import java.util.List;

import io.helidon.config.Config;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.AuthMethod;
import io.helidon.integrations.vault.spi.AuthMethodProvider;
import io.helidon.integrations.vault.spi.InjectionProvider;

/**
 * Java Service Loader implementation for AppRole authentication method.
 */
public class AppRoleAuthProvider implements AuthMethodProvider<AppRoleAuthRx>,
                                            InjectionProvider {
    private static final List<InjectionType<?>> INJECTABLES;

    static {
        List<InjectionType<?>> injectables = new LinkedList<>();

        injectables.add(InjectionType.create(AppRoleAuthRx.class,
                                             (vault, config, instanceConfig) -> instanceConfig.vaultPath()
                                                     .map(it -> vault.auth(AppRoleAuthRx.AUTH_METHOD, it))
                                                     .orElseGet(() -> vault.auth(AppRoleAuthRx.AUTH_METHOD))));

        injectables.add(InjectionType.create(AppRoleAuth.class,
                                             (vault, config, instanceConfig) -> {
                                                 AppRoleAuthRx rx = instanceConfig.vaultPath()
                                                         .map(it -> vault.auth(AppRoleAuthRx.AUTH_METHOD, it))
                                                         .orElseGet(() -> vault.auth(AppRoleAuthRx.AUTH_METHOD));

                                                 return new AppRoleAuthImpl(rx);
                                             }));

        INJECTABLES = List.copyOf(injectables);
    }

    @Override
    public AuthMethod<AppRoleAuthRx> supportedMethod() {
        return AppRoleAuthRx.AUTH_METHOD;
    }

    @Override
    public AppRoleAuthRx createAuth(Config config, RestApi restApi, String path) {
        return new AppRoleAuthRxImpl(restApi, path);
    }

    @Override
    public List<InjectionType<?>> injectables() {
        return INJECTABLES;
    }
}
