/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.configdriven.runtime;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.builder.config.spi.GeneratedConfigBeanBase;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static io.helidon.pico.configdriven.runtime.DefaultPicoConfigBeanRegistry.DEFAULT_INSTANCE_ID;
import static io.helidon.pico.configdriven.runtime.DefaultPicoConfigBeanRegistry.setConfigBeanInstanceId;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultPicoConfigBeanRegistryTest {

    @Test
    void testsetConfigBeanInstanceId() {
        // the setup
        AtomicInteger setCalls = new AtomicInteger();
        AtomicReference<String> instanceId = new AtomicReference<>(DEFAULT_INSTANCE_ID);

        GeneratedConfigBeanBase cfgBean = mock(GeneratedConfigBeanBase.class);
        when(cfgBean.__instanceId()).thenAnswer(im -> instanceId.get());
        cfgBean.__instanceId(anyString());
        doAnswer(im -> {
            setCalls.incrementAndGet();
            instanceId.set(im.getArgument(1).toString());
            return null;
        }).when(cfgBean).__instanceId(anyString());

        Config config = Config.builder(
                ConfigSources.create(
                        Map.of("other.than.name", "the-name"), "config-root-plus-one-socket"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        AbstractConfiguredServiceProvider<?, ?> csp = mock(AbstractConfiguredServiceProvider.class);
        doAnswer(im -> {
            setCalls.incrementAndGet();
            instanceId.set(im.getArgument(1).toString());
            return null;
        }).when(csp).configBeanInstanceId(any(), anyString());
        when(csp.configBean()).thenAnswer(im -> Optional.of(cfgBean));

        // the 1st test
        setConfigBeanInstanceId(config, cfgBean, csp);
        assertThat(instanceId.get(),
                   equalTo(DEFAULT_INSTANCE_ID));
        assertThat(setCalls.get(),
                   is(1));

        // reset
        setCalls.set(0);
        config = Config.builder(
                        ConfigSources.create(
                                Map.of("name", "the-name"), "config-root-plus-one-socket"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        // the next test
        setConfigBeanInstanceId(config, cfgBean, csp);
        assertThat(instanceId.get(),
                   equalTo("the-name"));
        assertThat(setCalls.get(),
                   is(1));
    }

}
