/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.config;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.se.SeContainer;
import javax.inject.Inject;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddBean(value = MutableMpTest.Bean.class, scope = Dependent.class)
class MutableMpTest {
    private static MutableSource source;

    @BeforeAll
    static void initClass() {
        source = new MutableSource("initial");
        ConfigProviderResolver configProvider = ConfigProviderResolver.instance();

        configProvider.registerConfig(configProvider.getBuilder()
                                              .addDefaultSources()
                                              .withSources(source)
                                              .build(),
                                      Thread.currentThread().getContextClassLoader());
    }

    @Test
    public void testMutable(SeContainer cdi) {
        Bean bean = cdi.select(Bean.class).get();

        assertThat(bean.value, is("initial"));

        source.setValue("updated");

        bean = cdi.select(Bean.class).get();

        assertThat(bean.value, is("updated"));
    }

    public static class Bean {
        @Inject
        @ConfigProperty(name = "value")
        public String value;
    }

    // class must be public so helidon can see it and invoke methods through reflection
    public static class MutableSource implements ConfigSource {
        private AtomicReference<String> value = new AtomicReference<>();
        private BiConsumer<String, String> listener;

        public MutableSource(String initial) {
            value.set(initial);
        }

        public void registerChangeListener(BiConsumer<String, String> listener) {
            this.listener = listener;
        }

        @Override
        public Map<String, String> getProperties() {
            return Map.of("value", value.get());
        }

        @Override
        public String getValue(String propertyName) {
            if ("value".equals(propertyName)) {
                return value.get();
            }
            return null;
        }

        @Override
        public String getName() {
            return "mutable-unit-test";
        }

        public void setValue(String value) {
            this.value.set(value);
            if (null != listener) {
                listener.accept("value", value);
            }
        }
    }
}
