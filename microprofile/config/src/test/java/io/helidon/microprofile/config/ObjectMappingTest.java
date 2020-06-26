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

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.helidon.config.mp.MpConfigSources;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

public class ObjectMappingTest {
    @Test
    void testIt() {
        // Removed use of system properties, as those stay around after test is finished
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        ConfigProviderResolver configProvider = ConfigProviderResolver.instance();

        Config orig = configProvider.getConfig(cl);

        Map<String, String> configMap = Map.of("built.it", "configured",
                                               "list.0.it", "first",
                                               "list.1.it", "second");

        configProvider.registerConfig(configProvider.getBuilder()
                                              .withSources(MpConfigSources.create(configMap))
                                              .build(),
                                      cl);


        // need to go through resolver to wrap config in our SE/MP wrapper
        io.helidon.config.Config helidonConfig = (io.helidon.config.Config) configProvider.getConfig(cl);

        try {
            Built built = helidonConfig.get("built").as(Built.class).get();
            assertThat(built.it, is("configured"));

            List<Built> list = helidonConfig.get("list").asList(Built.class).get();
            assertThat(list, hasSize(2));
            assertThat(list, Matchers.contains(new Built("first"), new Built("second")));

        } finally {
            configProvider.registerConfig(orig, cl);
        }
    }

    public static class Built {
        private final String it;

        private Built(Builder builder) {
            this.it = builder.it;
        }

        private Built(String it) {
            this.it = it;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Built built = (Built) o;
            return it.equals(built.it);
        }

        @Override
        public int hashCode() {
            return Objects.hash(it);
        }

        public static class Builder {
            private String it;

            public Builder it(String it) {
                this.it = it;
                return this;
            }

            public Built build() {
                return new Built(this);
            }
        }
    }
}
