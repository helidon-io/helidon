/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.helidon.config.ConfigException;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * MicroProfile {@link org.eclipse.microprofile.config.spi.ConfigSource} that can be used
 * to add HOCON/JSON files from classpath or file system using the
 * {@link org.eclipse.microprofile.config.spi.ConfigProviderResolver#getBuilder()}.
 * <p>The HOCON/JSON file is transformed to a flat map as follows:</p>
 * <strong>Object nodes</strong>
 * <p>
 * Each node in the tree is dot separated.
 * <pre>
 * server = {
 *   host = "localhost"
 *   port= 8080
 * }
 * </pre>
 * Will be transformed to the following properties:
 * <pre>
 * server.host=localhost
 * server.port=8080
 * </pre>
 * <strong>List nodes (arrays)</strong>
 * <p>
 * Each node will be indexed (0 based)
 * <pre>
 * providers =
 *   [{abac = {enabled = true}}]
 * names = [
 *   first
 *   second
 *   third
 * ]
 * </pre>
 * Will be transformed to the following properties:
 * <pre>
 * providers.0.abac.enabled=true
 * names.0=first
 * names.1=second
 * names.2=third
 * </pre>
 */
@SuppressWarnings("rawtypes")
public class HoconMpConfigSource implements ConfigSource {
    private final String name;
    private Map<String, String> properties;

    private static final boolean RESOLVING_ENABLED = true;
    private static final ConfigResolveOptions RESOLVE_OPTIONS = ConfigResolveOptions.defaults();

    private HoconMpConfigSource(String name, Map<String, String> properties) {
        this.properties = properties;
        this.name = "hocon: " + name;
    }

    /**
     * Load a HOCON/JSON config source from file system.
     *
     * @param path path to the HOCON/JSON file
     * @return config source loaded from the file
     * @see #create(java.net.URL)
     */
    public static ConfigSource create(Path path) {
        String name = path.toAbsolutePath().toString();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            ConfigParseOptions parseOptions = getConfigParseOptions(path);
            return create(name, reader, parseOptions);
        } catch (IOException e) {
            throw new ConfigException("Failed to load HOCON/JSON config source from path: " + path.toAbsolutePath(), e);
        }
    }

    /**
     * Load a HOCON/JSON config source from a reader.
     *
     * @param name the name of the config
     * @param reader that will read the configuration
     * @param parseOptions of the content
     * @return config source loaded
     */
    public static ConfigSource create(String name, Reader reader, ConfigParseOptions parseOptions) {
        Config typesafeConfig = toConfig(reader, parseOptions);
        // this is a mutable HashMap that we can use
        Map<String, String> props =
                fromConfig(typesafeConfig.root().isEmpty() ? ConfigFactory.empty().root() : typesafeConfig.root());
        return new HoconMpConfigSource(name, props);
    }

    /**
     * Load a HOCON/JSON config source from URL.
     * The URL may be any URL which is supported by the used JVM.
     *
     * @param url url of the resource
     * @return config source loaded from the URL
     */
    public static ConfigSource create(URL url) {
        try (InputStreamReader reader = new InputStreamReader(url.openConnection().getInputStream(), StandardCharsets.UTF_8)) {
            String name = url.toString();
            ConfigParseOptions parseOptions = getConfigParseOptions(url);
            Config typesafeConfig = toConfig(reader, parseOptions);
            if (typesafeConfig.root().isEmpty()) { // empty source
                return new HoconMpConfigSource(name, Map.of());
            }
            return new HoconMpConfigSource(name, fromConfig(typesafeConfig.root()));
        } catch (Exception e) {
            throw new ConfigException("Failed to configure HOCON/JSON config source", e);
        }
    }

    private static <T> ConfigParseOptions getConfigParseOptions(URL url) {
        HoconMpConfigIncluder includer = new HoconMpConfigIncluder();
        ConfigParseOptions parseOptions = ConfigParseOptions.defaults().appendIncluder(includer);
        includer.parseOptions(parseOptions);
        includer.relativeUrl(getParentResourcePath(url.toString()));
        includer.charset(StandardCharsets.UTF_8);
        return parseOptions;
    }

    private static ConfigParseOptions getConfigParseOptions(Path path) {
        HoconMpConfigIncluder includer = new HoconMpConfigIncluder();
        ConfigParseOptions parseOptions = ConfigParseOptions.defaults().appendIncluder(includer);
        includer.parseOptions(parseOptions);
        includer.relativePath(path.getParent());
        includer.charset(StandardCharsets.UTF_8);
        return parseOptions;
    }

    private static Config toConfig(Reader content, ConfigParseOptions parseOptions) {
        Config typesafeConfig = ConfigFactory.parseReader(
                content, parseOptions);
        if (RESOLVING_ENABLED) {
            typesafeConfig = typesafeConfig.resolve(RESOLVE_OPTIONS);
        }
        return typesafeConfig;
    }

    /**
     * Create from HOCON/JSON file(s) on classpath.
     *
     * @param resource resource name to locate on classpath (looks for all instances)
     * @return list of config sources discovered (may be zero length)
     */
    public static List<ConfigSource> classPath(String resource) {
        List<ConfigSource> sources = new LinkedList<>();
        try {
            Thread.currentThread().getContextClassLoader().getResources(resource)
                    .asIterator()
                    .forEachRemaining(it -> sources.add(create(it)));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read HOCON/JSON \"" + resource + "\" from classpath", e);
        }

        return sources;
    }

    /**
     * Create from HOCON/JSON file(s) on classpath with profile support.
     *
     * @param resource resource name to locate on classpath (looks for all instances)
     * @param profile name of the profile to use
     * @return list of config sources discovered (may be zero length)
     */
    public static List<ConfigSource> classPath(String resource, String profile) {
        Objects.requireNonNull(profile, "Profile must be defined");

        List<ConfigSource> sources = new LinkedList<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

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
                                    // Main is the profile config file and fallback is the original config file
                                    sources.add(create(create(url), create(it)));
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
            throw new IllegalStateException("Failed to read HOCON/JSON \"" + resource + "\" from classpath", e);
        }

        return sources;
    }

    private static ConfigSource create(ConfigSource main, ConfigSource fallback) {
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

    private static String getParentResourcePath(String resource) {
        // this works the same on windows and Unix systems (classpath is always forward slashes)
        int lastSlash = resource.lastIndexOf('/');
        String rootOfResource;
        if (lastSlash > -1) {
            rootOfResource = resource.substring(0, lastSlash + 1);
        } else {
            rootOfResource = resource;
        }
        return rootOfResource;
    }

    private static Map<String, String> fromConfig(ConfigObject config) {
        Map<String, String> result = new HashMap<>();
        process(result, "", config);

        return result;
    }

    private static void process(Map<String, String> resultMap, String prefix, ConfigObject config) {
        config.forEach((unescapedKey, value) -> {
            String key = io.helidon.config.Config.Key.escapeName(unescapedKey);
            processNext(resultMap, prefix(prefix, key), value);
        });
    }

    private static void process(Map<String, String> resultMap, String prefix, ConfigList configList) {
        int counter = 0;
        for (ConfigValue value : configList) {
            processNext(resultMap, prefix(prefix, String.valueOf(counter)), value);
            counter++;
        }
    }

    private static void processNext(Map<String, String> resultMap,
                                    String prefix,
                                    ConfigValue value) {
        if (value instanceof ConfigList) {
            process(resultMap, prefix, (ConfigList) value);
        } else if (value instanceof ConfigObject) {
            process(resultMap, prefix, (ConfigObject) value);
        } else {
            Object unwrapped = value.unwrapped();
            String stringValue = (null == unwrapped) ? "" : String.valueOf(unwrapped);
            resultMap.put(prefix, stringValue);
        }
    }

    private static String prefix(String prefix, String stringKey) {
        if (prefix.isEmpty()) {
            return stringKey;
        }

        return prefix + "." + stringKey;
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

    @Override
    public Set<String> getPropertyNames() {
        return Collections.unmodifiableSet(properties.keySet());
    }

    @Override
    public Map<String, String> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    @Override
    public String getValue(String propertyName) {
        return properties.get(propertyName);
    }

    @Override
    public String getName() {
        return name;
    }
}
