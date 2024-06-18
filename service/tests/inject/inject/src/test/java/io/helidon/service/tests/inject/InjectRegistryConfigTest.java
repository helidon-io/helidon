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

import java.util.List;
import java.util.Map;

import io.helidon.common.configurable.LruCache;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.service.inject.InjectConfig;
import io.helidon.service.inject.api.Activator;
import io.helidon.service.inject.api.InjectServiceInfo;
import io.helidon.service.inject.api.Lookup;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;

public class InjectRegistryConfigTest {
    @Test
    void testDefaults() {
        InjectConfig cfg = InjectConfig.create();
        // service registry config
        assertThat(cfg.config(), is(optionalEmpty()));
        assertThat(cfg.discoverServices(), is(true));
        assertThat(cfg.serviceDescriptors(), is(empty()));
        assertThat(cfg.serviceInstances().size(), is(0));
        // injection specific config
        assertThat(cfg.interceptionEnabled(), is(true));
        assertThat(cfg.limitRuntimePhase(), is(Activator.Phase.ACTIVE));
        assertThat(cfg.lookupCacheEnabled(), is(false));
        assertThat(cfg.lookupCache(), is(optionalEmpty()));
        assertThat(cfg.useApplication(), is(true));
    }

    @Test
    void testFromConfig() {
        Config config = Config.builder(
                        ConfigSources.create(
                                Map.of("inject.discover-services", "false",
                                       "inject.interception-enabled", "false",
                                       "inject.limit-runtime-phase", "CONSTRUCTING",
                                       "inject.lookup-cache-enabled", "true",
                                       "inject.lookup-cache.capacity", "200",
                                       "inject.use-application", "false"
                                ), "config-1"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        Config injectConfig = config.get("inject");
        InjectConfig cfg = InjectConfig.create(injectConfig);

        // service registry config
        assertThat(cfg.config(), optionalValue(sameInstance(injectConfig)));
        assertThat(cfg.discoverServices(), is(false));
        assertThat(cfg.serviceDescriptors(), is(empty()));
        assertThat(cfg.serviceInstances().size(), is(0));
        // injection specific config
        assertThat(cfg.interceptionEnabled(), is(false));
        assertThat(cfg.limitRuntimePhase(), is(Activator.Phase.CONSTRUCTING));
        assertThat(cfg.lookupCacheEnabled(), is(true));
        assertThat(cfg.lookupCache(), is(optionalPresent()));
        LruCache<Lookup, List<InjectServiceInfo>> cache = cfg.lookupCache().get();
        assertThat(cache.capacity(), is(200));
        assertThat(cfg.useApplication(), is(false));
    }
}
