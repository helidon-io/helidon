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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * URL based config source.
 */
class MpcSourceUrl implements ConfigSource {

    private final Map<String, String> props;
    private final String source;

    private MpcSourceUrl(Properties props, String source) {
        this.props = MpcSourceSystemProperties.toMap(props);
        this.source = source;
    }

    public static ConfigSource from(URL url) throws IOException {
        try (InputStream inputStream = url.openStream()) {
            Properties props = new Properties();
            props.load(inputStream);
            return new MpcSourceUrl(props, url.toString());
        }
    }

    @Override
    public Map<String, String> getProperties() {
        return Collections.unmodifiableMap(props);
    }

    @Override
    public String getValue(String propertyName) {
        return props.get(propertyName);
    }

    @Override
    public String getName() {
        return "helidon:url:" + source;
    }

    @Override
    public Set<String> getPropertyNames() {
        return Collections.unmodifiableSet(props.keySet());
    }
}
