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

package io.helidon.integrations.oci.vault;

import java.util.LinkedList;
import java.util.List;

import io.helidon.integrations.oci.connect.spi.InjectionProvider;

/**
 * Service provider for {@link io.helidon.integrations.oci.connect.spi.InjectionProvider}.
 * Only used by a service loader.
 * @deprecated do not use directly
 */
@Deprecated
public class OciVaultInjectionProvider implements InjectionProvider {
    private static final List<InjectionType<?>> INJECTABLES;

    static {
        List<InjectionType<?>> injectables = new LinkedList<>();

        injectables.add(InjectionType.create(OciVaultRx.class,
                                             (restApi, config) -> OciVaultRx.builder()
                                                     .restApi(restApi)
                                                     .config(config)
                                                     .build()));

        injectables.add(InjectionType.create(OciVault.class,
                                             (restApi, config) -> OciVault.create(OciVaultRx.builder()
                                                                                          .restApi(restApi)
                                                                                          .config(config)
                                                                                          .build())));

        INJECTABLES = List.copyOf(injectables);
    }

    /**
     * This constructor is only intended for service loader.
     * DO NOT USE DIRECTLY.
     * @deprecated do not use
     */
    @Deprecated
    public OciVaultInjectionProvider() {
    }

    @Override
    public List<InjectionType<?>> injectables() {
        return INJECTABLES;
    }
}
