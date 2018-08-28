/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * System properties config source.
 */
class MpcSourceSystemProperties implements ConfigSource {
    static Map<String, String> toMap(Properties properties) {
        Map<String, String> result = new HashMap<>();

        for (String name : properties.stringPropertyNames()) {
            result.put(name, properties.getProperty(name));
        }

        return result;
    }

    @Override
    public Map<String, String> getProperties() {
        return toMap(System.getProperties());
    }

    @Override
    public String getValue(String propertyName) {
        return System.getProperty(propertyName);
    }

    @Override
    public String getName() {
        return "helidon:system-properties";
    }

    @Override
    public int getOrdinal() {
        return 400;
    }

    @Override
    public Set<String> getPropertyNames() {
        return System.getProperties().stringPropertyNames();
    }
}
