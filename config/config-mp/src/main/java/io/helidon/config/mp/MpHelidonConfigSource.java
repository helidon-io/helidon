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

package io.helidon.config.mp;

import java.util.Map;

import org.eclipse.microprofile.config.spi.ConfigSource;

final class MpHelidonConfigSource implements ConfigSource {
    private final io.helidon.config.Config helidonConfig;

    MpHelidonConfigSource(io.helidon.config.Config helidonConfig) {
        this.helidonConfig = helidonConfig;
    }

    @Override
    public Map<String, String> getProperties() {
        return helidonConfig.context()
                .last()
                .asMap()
                .orElseGet(Map::of);
    }

    @Override
    public String getValue(String s) {
        return helidonConfig.context()
                .last()
                .get(s)
                .asString()
                .orElse(null);
    }

    @Override
    public String getName() {
        return "Helidon Config";
    }
}
