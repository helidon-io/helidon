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
package io.helidon.docs.se.config;

import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.ConfigSourceProvider;

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

}
