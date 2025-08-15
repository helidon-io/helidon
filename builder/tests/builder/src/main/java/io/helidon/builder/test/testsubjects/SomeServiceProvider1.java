/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.builder.test.testsubjects;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.config.Config;

@Weight(Weighted.DEFAULT_WEIGHT + 10)
public class SomeServiceProvider1 implements SomeProvider {
    @Override
    public String configKey() {
        return "some-1";
    }

    @Override
    public SomeService create(Config config, String name) {
        return new SomeService1(name, config.get("prop").asString().orElse("some-1"));
    }

    private final class SomeService1 implements SomeService {
        private final String name;
        private final String prop;

        private SomeService1(String name, String prop) {
            this.name = name;
            this.prop = prop;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String type() {
            return "some-1";
        }

        @Override
        public String prop() {
            return prop;
        }
    }
}
