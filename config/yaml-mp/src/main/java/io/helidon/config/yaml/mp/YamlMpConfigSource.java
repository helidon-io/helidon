/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * MicroProfile {@link org.eclipse.microprofile.config.spi.ConfigSource} that can be used
 * to add YAML files from classpath or file system using the
 * {@link org.eclipse.microprofile.config.spi.ConfigProviderResolver#getBuilder()}.
 * <p>The YAML file is transformed to a flat map as follows:</p>
 * <strong>Object nodes</strong>
 * <p>
 * Each node in the tree is dot separated.
 * <pre>
 * server:
 *     host: "localhost"
 *     port: 8080
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
 * providers:
 *   - abac:
 *       enabled: true
 * names: ["first", "second", "third"]
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
public class YamlMpConfigSource implements ConfigSource {
    private final Map<String, String> properties;
    private final String name;

    private YamlMpConfigSource(String name, Map<String, String> properties) {
        this.properties = properties;
        this.name = "yaml: " + name;
    }

    /**
     * Load a YAML config source from file system.
     *
     * @param path path to the YAML file
     * @return config source loaded from the file
     * @see #create(java.net.URL)
     */
    public static ConfigSource create(Path path) {
        try {
            return create(path.toUri().toURL());
        } catch (MalformedURLException e) {
            throw new ConfigException("Failed to load YAML config source from path: " + path.toAbsolutePath(), e);
        }
    }

    /**
     * Load a YAML config source from URL.
     * The URL may be any URL which is support by the used JVM.
     *
     * @param url url of the resource
     * @return config source loaded from the URL
     */
    public static ConfigSource create(URL url) {
        try (InputStreamReader reader = new InputStreamReader(url.openConnection().getInputStream(), StandardCharsets.UTF_8)) {
            return create(url.toString(), reader);
        } catch (Exception e) {
            throw new ConfigException("Failed to configure YAML config source", e);
        }
    }

    /**
     * Create from YAML content as a reader.
     * This method will NOT close the reader.
     *
     * @param name name of the config source
     * @param content reader with the YAML content
     * @return config source loaded from the content
     */
    public static ConfigSource create(String name, Reader content) {
        Map yamlMap = toMap(content);
        if (yamlMap == null) { // empty source
            return new YamlMpConfigSource(name, Map.of());
        }

        return new YamlMpConfigSource(name, fromMap(yamlMap));

    }

    /**
     * Create from YAML file(s) on classpath.
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
            throw new IllegalStateException("Failed to read YAML \"" + resource + "\" from classpath", e);
        }

        return sources;
    }

    /**
     * Create from YAML file(s) on classpath with profile support.
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
                                    sources.add(create(create(it), create(url)));
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
            throw new IllegalStateException("Failed to read YAML \"" + resource + "\" from classpath", e);
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

    private static Map<String, String> fromMap(Map yamlMap) {
        Map<String, String> result = new HashMap<>();
        process(result, "", yamlMap);

        return result;
    }

    private static void process(Map<String, String> resultMap, String prefix, Map yamlMap) {
        yamlMap.forEach((key, value) -> {
            processNext(resultMap, prefix(prefix, key.toString()), value);
        });
    }

    private static void process(Map<String, String> resultMap, String prefix, List yamlList) {
        int counter = 0;
        for (Object value : yamlList) {
            processNext(resultMap, prefix(prefix, String.valueOf(counter)), value);

            counter++;
        }
    }

    private static void processNext(Map<String, String> resultMap,
                                    String prefix,
                                    Object value) {
        if (value instanceof List) {
            process(resultMap, prefix, (List) value);
        } else if (value instanceof Map) {
            process(resultMap, prefix, (Map) value);
        } else {
            String stringValue = (null == value) ? "" : value.toString();
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

    static Map toMap(Reader reader) {
        // the default of Snake YAML is a Map, safe constructor makes sure we never deserialize into anything
        // harmful
        Yaml yaml = new Yaml(new SafeConstructor());
        return (Map) yaml.loadAs(reader, Object.class);
    }
}
