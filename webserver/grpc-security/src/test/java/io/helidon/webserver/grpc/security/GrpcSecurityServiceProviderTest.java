/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.grpc.security;

import io.helidon.config.Config;
import io.helidon.security.Security;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

class GrpcSecurityServiceProviderTest {
    @Test
    void createsSecurityWithoutExplicitConfiguration() {
        GrpcSecurityServiceProvider provider = new GrpcSecurityServiceProvider();

        assertThat(provider.create(Config.empty(), GrpcSecurity.TYPE).type(), is(GrpcSecurity.TYPE));
    }

    @Test
    void registryCreationUsesOwningSecurity() {
        Security security = Security.create(Config.empty());
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .discoverServices(false)
                                                                                .putContractInstance(Security.class,
                                                                                                     security)
                                                                                .build());
        try {
            GrpcSecurity grpcSecurity = (GrpcSecurity) new GrpcSecurityServiceProvider()
                    .create(Config.empty(), GrpcSecurity.TYPE, manager.registry());

            assertThat(grpcSecurity.security(), sameInstance(security));
        } finally {
            manager.shutdown();
        }
    }
}
