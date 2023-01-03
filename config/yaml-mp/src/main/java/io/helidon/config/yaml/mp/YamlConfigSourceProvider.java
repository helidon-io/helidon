/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

package io.helidon.config.yaml.mp;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import io.helidon.config.ConfigException;
import io.helidon.config.mp.spi.MpConfigSourceProvider;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * YAML config source provider for MicroProfile config that supports file {@code application.yaml}.
 * This class should not be used directly - it is loaded automatically by Java service loader.
 */
public class YamlConfigSourceProvider implements MpConfigSourceProvider {
    private static final String RESOURCE_NAME = "application.yaml";

    @Override
    public Iterable<ConfigSource> getConfigSources(ClassLoader classLoader) {
        return configSources(classLoader, null);
    }

    @Override
    public Iterable<ConfigSource> getConfigSources(ClassLoader classLoader, String profile) {
        Objects.requireNonNull(profile, "Profile must not be null");
        return configSources(classLoader, profile);
    }

    private Iterable<ConfigSource> configSources(ClassLoader classLoader, String profile) {
        List<ConfigSource> result = new LinkedList<>();
        if (profile == null) {
            Enumeration<URL> resources;
            try {
                resources = classLoader.getResources(RESOURCE_NAME);
            } catch (IOException e) {
                throw new ConfigException("Failed to read resources from classpath", e);
            }
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                result.add(YamlMpConfigSource.create(url));
            }
        } else {
            result.addAll(YamlMpConfigSource.classPath(RESOURCE_NAME, profile, classLoader));
        }

        return result;
    }
}
