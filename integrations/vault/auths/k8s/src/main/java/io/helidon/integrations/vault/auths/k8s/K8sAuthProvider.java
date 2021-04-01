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

package io.helidon.integrations.vault.auths.k8s;

import java.util.LinkedList;
import java.util.List;

import io.helidon.config.Config;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.AuthMethod;
import io.helidon.integrations.vault.spi.AuthMethodProvider;
import io.helidon.integrations.vault.spi.InjectionProvider;

/**
 * Java Service Loader implementation for Vault authentication method based on Kubernetes.
 */
public class K8sAuthProvider implements AuthMethodProvider<K8sAuthRx>,
                                        InjectionProvider {
    private static final List<InjectionType<?>> INJECTABLES;

    static {
        List<InjectionType<?>> injectables = new LinkedList<>();

        injectables.add(InjectionType.create(K8sAuthRx.class,
                                             (vault, config, instanceConfig) -> instanceConfig.vaultPath()
                                                     .map(it -> vault.auth(K8sAuthRx.AUTH_METHOD, it))
                                                     .orElseGet(() -> vault.auth(K8sAuthRx.AUTH_METHOD))));

        injectables.add(InjectionType.create(K8sAuth.class,
                                             (vault, config, instanceConfig) -> {
                                                 K8sAuthRx rx = instanceConfig.vaultPath()
                                                         .map(it -> vault.auth(K8sAuthRx.AUTH_METHOD, it))
                                                         .orElseGet(() -> vault.auth(K8sAuthRx.AUTH_METHOD));

                                                 return new K8sAuthImpl(rx);
                                             }));

        INJECTABLES = List.copyOf(injectables);
    }
    
    @Override
    public AuthMethod<K8sAuthRx> supportedMethod() {
        return K8sAuthRx.AUTH_METHOD;
    }

    @Override
    public K8sAuthRx createAuth(Config config, RestApi restApi, String path) {
        return new K8sAuthRxImpl(restApi, path);
    }

    @Override
    public List<InjectionType<?>> injectables() {
        return INJECTABLES;
    }
}
