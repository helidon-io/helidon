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

package io.helidon.integrations.vault.sys;

import java.util.LinkedList;
import java.util.List;

import io.helidon.config.Config;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.SysApi;
import io.helidon.integrations.vault.spi.InjectionProvider;

/**
 * Java Service Loader service implementation for {@link SysRx}.
 */
public class HcpSysProvider implements io.helidon.integrations.vault.spi.SysProvider<SysRx>, InjectionProvider {
    private static final List<InjectionType<?>> INJECTABLES = new LinkedList<>();

    static {
        INJECTABLES.add(InjectionType.create(SysRx.class, (vault, vaultConfig, instanceConfig) -> vault.sys(SysRx.API)));
        INJECTABLES.add(InjectionType.create(Sys.class, (vault, vaultConfig, instanceConfig) -> {
            SysRx sys = vault.sys(SysRx.API);
            return new SysImpl(sys);
        }));
    }

    @Override
    public SysApi<SysRx> supportedApi() {
        return SysRx.API;
    }

    @Override
    public SysRx createSys(Config config, RestApi restAccess) {
        return new SysRxImpl(restAccess);
    }

    @Override
    public List<InjectionType<?>> injectables() {
        return INJECTABLES;
    }
}
