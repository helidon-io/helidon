/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Map based config source.
 */
class MpMapSource implements ConfigSource {
    private final Map<String, String> map;
    private final String name;

    MpMapSource(String name, Map<String, String> map) {
        this.name = name;
        this.map = map;
    }

    @Override
    public Set<String> getPropertyNames() {
        return Collections.unmodifiableSet(map.keySet());
    }

    @Override
    public Map<String, String> getProperties() {
        return Collections.unmodifiableMap(map);
    }

    @Override
    public String getValue(String propertyName) {
        return map.get(propertyName);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName() + " (" + getOrdinal() + ")";
    }
}
