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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Utilities for MicroProfile Config {@link org.eclipse.microprofile.config.spi.ConfigSource}.
 * <p>
 * The following methods create MicroProfile config sources to help with manual setup of Config
 * from {@link org.eclipse.microprofile.config.spi.ConfigProviderResolver#getBuilder()}:
 * <ul>
 *     <li>{@link #systemProperties()} - system properties config source</li>
 *     <li>{@link #environmentVariables()} - environment variables config source</li>
 *     <li>{@link #create(java.nio.file.Path)} - load a properties file from file system</li>
 *     <li>{@link #create(String, java.nio.file.Path)} - load a properties file from file system with custom name</li>
 *     <li>{@link #create(java.util.Map)} - create an in-memory source from map</li>
 *     <li>{@link #create(String, java.util.Map)} - create an in-memory source from map with custom name</li>
 *     <li>{@link #create(java.util.Properties)} - create an in-memory source from properties</li>
 *     <li>{@link #create(String, java.util.Properties)} - create an in-memory source from properties with custom name</li>
 * </ul>
 * The following methods add integration with Helidon SE Config:
 * <ul>
 *     <li>{@link #create(io.helidon.config.spi.ConfigSource)} - create a MicroProfile config source from Helidon SE config
 *     source</li>
 *     <li>{@link #create(io.helidon.config.Config)} - create a MicroProfile config source from Helidon SE Config instance</li>
 * </ul>
 */
public final class MpConfigSources {
    private MpConfigSources() {
    }

    /**
     * In memory config source based on the provided map.
     * The config source queries the map each time {@link org.eclipse.microprofile.config.spi.ConfigSource#getValue(String)}
     * is called.
     *
     * @param name name of the source
     * @param theMap map serving as configuration data
     * @return a new config source
     */
    public static ConfigSource create(String name, Map<String, String> theMap) {
        return new MpMapSource(name, theMap);
    }

    /**
     * In memory config source based on the provided map.
     * The config source queries the map each time {@link org.eclipse.microprofile.config.spi.ConfigSource#getValue(String)}
     * is called.
     *
     * @param theMap map serving as configuration data
     * @return a new config source
     */
    public static ConfigSource create(Map<String, String> theMap) {
        return create("Map", theMap);
    }

    /**
     * {@link java.util.Properties} config source based on a file on file system.
     * The file is read just once, when the source is created and further changes to the underlying file are
     * ignored.
     *
     * @param path path of the properties file on the file system
     * @return a new config source
     */
    public static ConfigSource create(Path path) {
        return create(path.toString(), path);
    }

    /**
     * {@link java.util.Properties} config source based on a URL.
     * The URL is read just once, when the source is created and further changes to the underlying resource are
     * ignored.
     *
     * @param url url of the properties file (any URL scheme supported by JVM can be used)
     * @return a new config source
     */
    public static ConfigSource create(URL url) {
        String name = url.toString();

        try {
            URLConnection urlConnection = url.openConnection();
            try (InputStream inputStream = urlConnection.getInputStream()) {
                Properties properties = new Properties();
                properties.load(inputStream);

                return create(name, properties);
            }
        } catch (Exception e) {
            throw new ConfigException("Failed to load ", e);
        }
    }

    /**
     * {@link java.util.Properties} config source based on a URL with a profile override.
     * The URL is read just once, when the source is created and further changes to the underlying resource are
     * ignored.
     *
     * @param url url of the properties file (any URL scheme supported by JVM can be used)
     * @param profileUrl url of the properties file of profile specific configuration
     * @return a new config source
     */
    public static ConfigSource create(URL url, URL profileUrl) {
        ConfigSource defaultSource = create(url);
        ConfigSource profileSource = create(profileUrl);

        return composite(profileSource, defaultSource);
    }

    /**
     * {@link java.util.Properties} config source based on a file on file system.
     * The file is read just once, when the source is created and further changes to the underlying file are
     * ignored.
     *
     * @param name name of the config source
     * @param path path of the properties file on the file system
     * @return a new config source
     */
    public static ConfigSource create(String name, Path path) {
        Properties props = new Properties();

        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            throw new ConfigException("Failed to read properties from " + path.toAbsolutePath());
        }

        return create(name, props);
    }

    /**
     * In memory config source based on the provided properties.
     * The config source queries the properties each time
     * {@link org.eclipse.microprofile.config.spi.ConfigSource#getValue(String)}
     * is called.
     *
     * @param properties serving as configuration data
     * @return a new config source
     */
    public static ConfigSource create(Properties properties) {
        return create("Properties", properties);
    }

    /**
     * In memory config source based on the provided properties.
     * The config source queries the properties each time
     * {@link org.eclipse.microprofile.config.spi.ConfigSource#getValue(String)}
     * is called.
     *
     * @param name name of the config source
     * @param properties serving as configuration data
     * @return a new config source
     */
    public static ConfigSource create(String name, Properties properties) {
        Map<String, String> result = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            result.put(key, properties.getProperty(key));
        }
        return new MpMapSource(name, result);
    }

    /**
     * Environment variables config source.
     * This source takes care of replacement of properties by environment variables as defined
     * in MicroProfile Config specification.
     * This config source is immutable and caching.
     *
     * @return a new config source
     */
    public static ConfigSource environmentVariables() {
        return new MpEnvironmentVariablesSource();
    }

    /**
     * In memory config source based on system properties.
     * The config source queries the properties each time
     * {@link org.eclipse.microprofile.config.spi.ConfigSource#getValue(String)}
     * is called.
     *
     * @return a new config source
     */
    public static ConfigSource systemProperties() {
        return new MpSystemPropertiesSource();
    }

    /**
     * Find all resources on classpath and return a config source for each.
     * Order is kept as provided by class loader.
     *
     * @param resource resource to find
     * @return a config source for each resource on classpath, empty if none found
     */
    public static List<ConfigSource> classPath(String resource) {
        return classPath(Thread.currentThread().getContextClassLoader(), resource);
    }

    /**
     * Find all resources on classpath and return a config source for each.
     * Order is kept as provided by class loader.
     *
     * The profile will be used to locate a source with {@code -${profile}} name, such as
     * {@code microprofile-config-dev.properties} for dev profile.
     *
     * @param resource resource to find
     * @param profile configuration profile to use, must not be null
     * @return a config source for each resource on classpath, empty if none found
     */
    public static List<ConfigSource> classPath(String resource, String profile) {
        return classPath(Thread.currentThread().getContextClassLoader(), resource, profile);
    }

    /**
     * Find all resources on classpath and return a config source for each.
     * Order is kept as provided by class loader.
     *
     * @param classLoader class loader to use to locate the resources
     * @param resource resource to find
     * @return a config source for each resource on classpath, empty if none found
     */
    public static List<ConfigSource> classPath(ClassLoader classLoader, String resource) {
        List<ConfigSource> sources = new LinkedList<>();
        try {
            classLoader.getResources(resource)
                    .asIterator()
                    .forEachRemaining(it -> sources.add(create(it)));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read \"" + resource + "\" from classpath", e);
        }

        return sources;
    }

    /**
     * Find all resources on classpath and return a config source for each with a profile.
     * Order is kept as provided by class loader.
     *
     * The profile will be used to locate a source with {@code -${profile}} name, such as
     * {@code microprofile-config-dev.properties} for dev profile.
     *
     * @param classLoader class loader to use to locate the resources
     * @param resource resource to find
     * @param profile configuration profile to use, must not be null
     * @return a config source for each resource on classpath, empty if none found
     */
    public static List<ConfigSource> classPath(ClassLoader classLoader, String resource, String profile) {
        Objects.requireNonNull(profile, "Profile must be defined");

        List<ConfigSource> sources = new LinkedList<>();

        try {
            Enumeration<URL> baseResources = classLoader.getResources(resource);
            Enumeration<URL> profileResources = classLoader.getResources(toProfileResource(resource, profile));

            if (profileResources.hasMoreElements()) {
                List<URL> profileResourceList = new LinkedList<>();
                profileResources.asIterator()
                        .forEachRemaining(profileResourceList::add);

                baseResources.asIterator()
                        .forEachRemaining(it -> {
                            String pathBase = pathBase(it.toString());
                            // we need to find profile that belongs to this
                            for (URL url : profileResourceList) {
                                String profilePathBase = pathBase(url.toString());
                                if (pathBase.equals(profilePathBase)) {
                                    sources.add(create(it, url));
                                } else {
                                    sources.add(create(it));
                                }
                            }
                        });
            } else {
                baseResources
                        .asIterator()
                        .forEachRemaining(it -> sources.add(create(it)));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read \"" + resource + "\" from classpath", e);
        }

        return sources;
    }

    /**
     * Config source based on a Helidon SE config source.
     * This is to support Helidon SE features in Helidon MP.
     *
     * The config source will be immutable regardless of configured polling strategy or change watchers.
     *
     * @param helidonConfigSource config source to use
     * @return a new MicroProfile Config config source
     */
    public static ConfigSource create(io.helidon.config.spi.ConfigSource helidonConfigSource) {
        return MpHelidonSource.create(helidonConfigSource);
    }

    /**
     * Config source base on a Helidon SE config instance.
     * This is to support advanced Helidon SE features in Helidon MP.
     *
     * The config source will be mutable if the config uses polling strategy and/or change watchers.
     * Each time the {@link org.eclipse.microprofile.config.spi.ConfigSource#getValue(String)} is called,
     * the latest config version will be queried.
     *
     * @param config Helidon SE configuration
     * @return a new MicroProfile Config config source
     */
    public static ConfigSource create(Config config) {
        return new MpHelidonConfigSource(config);
    }

    /**
     * Create a composite config source that uses the main first, and if it does not find
     * a property in main, uses fallback. This is useful to set up a config source with a profile,
     * where the profile source is {@code main} and the non-profile source is {@code fallback}.
     *
     * @param main look for properties here first
     * @param fallback if not found in main, look here
     * @return a new config source
     */
    static ConfigSource composite(ConfigSource main, ConfigSource fallback) {
        String name = main.getName() + " (" + fallback.getName() + ")";

        return new ConfigSource() {
            @Override
            public Set<String> getPropertyNames() {
                Set<String> result = new HashSet<>(fallback.getPropertyNames());
                result.addAll(main.getPropertyNames());

                return result;
            }

            @Override
            public String getValue(String propertyName) {
                String value = main.getValue(propertyName);
                if (value == null) {
                    return fallback.getValue(propertyName);
                }
                return value;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public Map<String, String> getProperties() {
                Map<String, String> result = new HashMap<>(fallback.getProperties());
                result.putAll(main.getProperties());

                return result;
            }
        };
    }

    private static String pathBase(String path) {
        int i = path.lastIndexOf('/');
        int y = path.lastIndexOf('!');
        int z = path.lastIndexOf(':');
        int b = path.lastIndexOf('\\');

        // we need the last index before the file name - so the highest number of all of the above
        int max = Math.max(i, y);
        max = Math.max(max, z);
        max = Math.max(max, b);

        if (max > -1) {
            return path.substring(0, max);
        }
        return path;
    }

    private static String toProfileResource(String resource, String profile) {
        int i = resource.lastIndexOf('.');
        if (i > -1) {
            return resource.substring(0, i) + "-" + profile + resource.substring(i);
        }
        return resource + "-" + profile;
    }
}
