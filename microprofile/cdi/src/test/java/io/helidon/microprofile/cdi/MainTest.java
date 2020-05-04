/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.cdi;

import java.util.List;
import java.util.Map;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;

import io.helidon.config.mp.MpConfigProviderResolver;
import io.helidon.config.mp.MpConfigSources;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

/**
 * Unit test for {@link Main}.
 */
class MainTest {
    @Test
    void testCdiStarted() {
        Main.main(new String[0]);

        Instance<TestBean> select = CDI.current().select(TestBean.class);
        TestBean bean = select.get();
        assertThat(bean, notNullValue());
        TestBean2 testBean2 = bean.getTestBean2();
        assertThat(testBean2, notNullValue());
        assertThat(testBean2.message(), is("Hello"));

        Main.shutdown();
    }

    @Test
    void testEvents() {
        // build time
        HelidonContainer instance = HelidonContainer.instance();

        BeanManager beanManager = CDI.current().getBeanManager();
        TestExtension extension = beanManager.getExtension(TestExtension.class);

        assertThat(extension.runtimeConfig(), nullValue());
        assertThat(extension.events(), contains(TestExtension.BUILD_TIME_START,
                                                TestExtension.BUILD_TIME_END));


        Config config = ConfigProviderResolver.instance()
                .getBuilder()
                .withSources(MpConfigSources.create(Map.of("key", "value")))
                .build();

        ConfigProviderResolver.instance()
                .registerConfig(config, Thread.currentThread().getContextClassLoader());

        instance.start();

        Object runtimeConfig = extension.runtimeConfig();
        assertThat(runtimeConfig, instanceOf(MpConfigProviderResolver.ConfigDelegate.class));
        assertThat(((MpConfigProviderResolver.ConfigDelegate) runtimeConfig).delegate(), sameInstance(config));

        instance.shutdown();
        assertThat(extension.events(), is(List.of(TestExtension.BUILD_TIME_START,
                                                  TestExtension.BUILD_TIME_END,
                                                  TestExtension.RUNTIME_INIT,
                                                  TestExtension.APPLICATION_INIT,
                                                  TestExtension.APPLICATION_BEFORE_DESTROYED,
                                                  TestExtension.APPLICATION_DESTROYED)));
    }
}