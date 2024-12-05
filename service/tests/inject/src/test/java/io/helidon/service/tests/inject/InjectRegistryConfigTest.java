/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.tests.inject;

import io.helidon.service.registry.ActivationPhase;
import io.helidon.service.registry.ServiceRegistryConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;

public class InjectRegistryConfigTest {
    @Test
    void testDefaults() {
        ServiceRegistryConfig cfg = ServiceRegistryConfig.create();
        // service registry config
        assertThat(cfg.discoverServices(), is(true));
        assertThat(cfg.serviceDescriptors(), is(empty()));
        assertThat(cfg.serviceInstances().size(), is(0));
        // injection specific config
        assertThat(cfg.interceptionEnabled(), is(true));
        assertThat(cfg.limitActivationPhase(), is(ActivationPhase.ACTIVE));
        assertThat(cfg.lookupCacheEnabled(), is(false));
        assertThat(cfg.lookupCacheSize(), is(10000));
        assertThat(cfg.useBinding(), is(true));
    }
}
