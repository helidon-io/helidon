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

package io.helidon.security;

import java.util.ArrayList;
import java.util.List;

import io.helidon.config.Config;
import io.helidon.security.provider.PathBasedProvider;
import io.helidon.security.provider.ResourceBasedProvider;
import io.helidon.security.spi.ProviderSelectionPolicy;
import io.helidon.security.spi.SecurityProvider;

import org.junit.jupiter.api.BeforeAll;

/**
 * Unit test for {@link CompositeOutboundProvider} loaded from configuration.
 */
public class CompositePolicyConfigTest extends CompositePolicyTest {
    private static ProviderSelectionPolicy psp;
    private static Security security;

    @BeforeAll
    public static void initClass() {
        Config config = Config.create();

        security = Security.fromConfig(config);

        PathBasedProvider pbp = new PathBasedProvider();
        ResourceBasedProvider rbp = new ResourceBasedProvider();

        psp = CompositeProviderSelectionPolicy.fromConfig(config.get("unit.test"))
                .apply(new ProviderSelectionPolicy.Providers() {
                    @Override
                    public <T extends SecurityProvider> List<NamedProvider<T>> getProviders(Class<T> providerType) {
                        List<NamedProvider<T>> result = new ArrayList<>();
                        result.add(new NamedProvider<>("first", providerType.cast(pbp)));
                        result.add(new NamedProvider<>("second", providerType.cast(rbp)));
                        return result;
                    }
                });
    }

    @Override
    Security getSecurity() {
        return security;
    }

    @Override
    ProviderSelectionPolicy getPsp() {
        return psp;
    }
}
