/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.vault.auths.token;

import java.util.LinkedList;
import java.util.List;

import io.helidon.common.config.Config;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.AuthMethod;
import io.helidon.integrations.vault.spi.AuthMethodProvider;
import io.helidon.integrations.vault.spi.InjectionProvider;

/**
 * Java Service Loader service for Token Authentication method support.
 */
public class TokenAuthProvider implements AuthMethodProvider<TokenAuth>,
                                          InjectionProvider {

    private static final List<InjectionType<?>> INJECTABLES;

    static {
        List<InjectionType<?>> injectables = new LinkedList<>();

        injectables.add(InjectionType.create(TokenAuth.class,
                                             (vault, config, instanceConfig) -> instanceConfig.vaultPath()
                                                     .map(it -> vault.auth(TokenAuth.AUTH_METHOD, it))
                                                     .orElseGet(() -> vault.auth(TokenAuth.AUTH_METHOD))));

        INJECTABLES = List.copyOf(injectables);
    }

    /**
     * Create a new instance.
     */
    public TokenAuthProvider() {
    }

    @Override
    public AuthMethod<TokenAuth> supportedMethod() {
        return TokenAuth.AUTH_METHOD;
    }

    @Override
    public TokenAuth createAuth(Config config, RestApi restApi, String path) {
        return new TokenAuthImpl(restApi, path);
    }

    @Override
    public List<InjectionType<?>> injectables() {
        return INJECTABLES;
    }
}
