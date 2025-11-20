/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
package io.helidon.docs.se.config;

import java.util.Optional;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.MetaConfig;
import io.helidon.config.spi.ConfigContent;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.ConfigSourceProvider;
import io.helidon.config.spi.NodeConfigSource;
import io.helidon.service.registry.Service;

@SuppressWarnings("ALL")
class ConfigProfilesSnippets {

    // stub
    class MyConfigSource implements ConfigSource {
        static MyConfigSource create(Config metaConfig) {
            return null;
        }
    }

    // tag::snippet_1[]
    public class MyConfigSourceProvider implements ConfigSourceProvider {
        private static final String TYPE = "my-type";

        @Override
        public boolean supports(String type) {
            return TYPE.equals(type);
        }

        @Override
        public ConfigSource create(String type, Config metaConfig) {
            // as we only support one in this implementation, we can just return it
            return MyConfigSource.create(metaConfig);
        }

        @Override
        public Set<String> supported() {
            return Set.of(TYPE);
        }
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @Service.Singleton
    @Service.Named(MyProfiledConfigSource.MY_TYPE)
    public class MyProfiledConfigSource implements NodeConfigSource {
        static final String MY_TYPE = "my-config-type";

        private final String value;

        MyProfiledConfigSource(@Service.Named(MY_TYPE) Optional<MetaConfig> metaConfig) {
            this.value = metaConfig.flatMap(it -> it.metaConfiguration()
                            .get("app.value2")
                            .asString()
                            .asOptional())
                    .orElse("meta-config-value-not-found");
        }

        @Override
        public Optional<ConfigContent.NodeContent> load() throws ConfigException {
            return Optional.of(ConfigContent.NodeContent.builder()
                                       .node(ConfigNode.ObjectNode.builder()
                                                     .addValue("app.value2", value)
                                                     .build())
                                       .build());
        }
    }
    // end::snippet_2[]
}
