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
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.annotation.Priority;

import org.eclipse.microprofile.config.spi.ConfigSource;

@Priority(400)
class MpSystemPropertiesSource implements ConfigSource {
    private final Properties props;

    MpSystemPropertiesSource() {
        this.props = System.getProperties();
    }

    @Override
    public Set<String> getPropertyNames() {
        return Collections.unmodifiableSet(props.stringPropertyNames());
    }

    @Override
    public Map<String, String> getProperties() {
        Set<String> strings = props.stringPropertyNames();

        Map<String, String> result = new HashMap<>();
        strings.forEach(it -> result.put(it, props.getProperty(it)));
        return result;
    }

    @Override
    public String getValue(String propertyName) {
        return props.getProperty(propertyName);
    }

    @Override
    public String getName() {
        return "System Properties";
    }

    @Override
    public String toString() {
        return getName() + " (" + getOrdinal() + ")";
    }
}
