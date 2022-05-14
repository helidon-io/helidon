/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.config.hocon.mp;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

import io.helidon.config.ConfigException;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSourceProvider;

/**
 * HOCON/JSON config source provider for MicroProfile config that supports file {@code application.conf} or file
 * {@code application.json}. This class should not be used directly - it is loaded automatically by Java service loader.
 */
public class HoconMpConfigSourceProvider implements ConfigSourceProvider {
    private static final List<String> APPLICATION_CONFIG_NAMES = List.of("application.json", "application.conf");

    @Override
    public Iterable<ConfigSource> getConfigSources(ClassLoader classLoader) {
        Enumeration<URL> resources;
        List<ConfigSource> result = new LinkedList<>();
        for (String configName : APPLICATION_CONFIG_NAMES) {
            try {
                resources = classLoader.getResources(configName);
            } catch (IOException e) {
                throw new ConfigException("Failed to read resources from classpath", e);
            }

            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                result.add(HoconMpConfigSource.create(url));
            }
        }

        return result;
    }
}
