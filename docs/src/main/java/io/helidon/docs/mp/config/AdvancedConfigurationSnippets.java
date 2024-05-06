/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.mp.config;

import java.io.Reader;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.config.ConfigException;
import io.helidon.config.ConfigSources;
import io.helidon.config.ConfigValue;
import io.helidon.config.mp.MpConfigSources;
import io.helidon.config.mp.spi.MpMetaConfigProvider;
import io.helidon.config.yaml.mp.YamlMpConfigSource;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;

@SuppressWarnings("ALL")
class AdvancedConfigurationSnippets {

    void snippet_1() {
        // tag::snippet_1[]
        ConfigProviderResolver resolver = ConfigProviderResolver.instance();

        Config config = resolver.getBuilder() // <1>
                .withSources(MpConfigSources.environmentVariables()) // <2>
                .withSources(MpConfigSources.create(Map.of("key", "value"))) // <3>
                .build(); // <4>

        resolver.registerConfig(config, null); // <5>
        // end::snippet_1[]
    }

    void snippet_2(Path path) {
        // tag::snippet_2[]
        ConfigProviderResolver.instance().getBuilder()
                .withSources(YamlMpConfigSource.create(path))
                .build();
        // end::snippet_2[]
    }

    class Snippet3 {
        // tag::snippet_3[]
        public class CustomConfigSource implements ConfigSource {
            private static final String NAME = "MyConfigSource";
            private static final int ORDINAL = 200; // Default for MP is 100
            private static final Map<String, String> PROPERTIES = Map.of("app.greeting", "Hi");

            @Override
            public String getName() {
                return NAME; // <1>
            }

            @Override
            public Map<String, String> getProperties() {
                return PROPERTIES; // <2>
            }

            @Override
            public Set<String> getPropertyNames() {
                return PROPERTIES.keySet();
            }

            @Override
            public String getValue(String key) {
                return PROPERTIES.get(key); // <3>
            }

            @Override
            public int getOrdinal() {
                return ORDINAL; // <4>
            }
        }
        // end::snippet_3[]
    }

    // stub
    static List<ConfigSource> sourceFromPath(Path path, String profile) {
        return List.of();
    }

    // stub
    static List<ConfigSource> sourceFromClasspath(String classpath, String profile) {
        return List.of();
    }

    // stub
    static List<ConfigSource> sourceFromUrlMeta(URL url, String profile) {
        return List.of();
    }

    // tag::snippet_4[]
    public class CustomMpMetaConfigProvider implements MpMetaConfigProvider {

        @Override
        public Set<String> supportedTypes() {
            return Set.of("custom"); // <1>
        }

        @Override
        public List<? extends ConfigSource> create(String type, io.helidon.config.Config metaConfig, String profile) {
            ConfigValue<Path> pathConfig = metaConfig.get("path").as(Path.class);
            String location;
            if (pathConfig.isPresent()) { // <2>
                Path path = pathConfig.get();
                List<ConfigSource> sources = sourceFromPath(path, profile); // <3>
                if (sources != null && !sources.isEmpty()) {
                    return sources;
                }
                location = "path " + path.toAbsolutePath();
            } else {
                ConfigValue<String> classpathConfig = metaConfig.get("classpath").as(String.class);
                if (classpathConfig.isPresent()) { // <4>
                    String classpath = classpathConfig.get();
                    List<ConfigSource> sources = sourceFromClasspath(classpath, profile); // <5>
                    if (sources != null && !sources.isEmpty()) {
                        return sources;
                    }
                    location = "classpath " + classpath;
                } else {
                    ConfigValue<URL> urlConfig = metaConfig.get("url").as(URL.class);
                    if (urlConfig.isPresent()) { // <6>
                        URL url = urlConfig.get();
                        List<ConfigSource> sources = sourceFromUrlMeta(url, profile); // <7>
                        if (sources != null && !sources.isEmpty()) {
                            return sources;
                        }
                        location = "url " + url;
                    } else {
                        throw new ConfigException("No config source location for " + metaConfig.key());
                    }
                }
            }
            if (metaConfig.get("optional").asBoolean().orElse(false)) {
                return List.of(); // <8>
            }
            throw new ConfigException("Meta configuration could not find non-optional config source on " + location); // <9>
        }

        @Override
        public ConfigSource create(Reader content) {
            throw new UnsupportedOperationException("CustomMpMetaConfigProvider does not support #create.");
        }
    }
    // end::snippet_4[]

    void snippet_5(io.helidon.config.Config helidonConfigSource) {
        // tag::snippet_5[]
        Config config = ConfigProviderResolver.instance()
                .getBuilder()
                .withSources(MpConfigSources.create(helidonConfigSource)) // <1>
                .build();
        // end::snippet_5[]
    }

    void snippet_6() {
        // tag::snippet_6[]
        io.helidon.config.Config helidonConfig = io.helidon.config.Config.builder()
                .addSource(ConfigSources.create(Map.of("key", "value"))) // <1>
                .build();
        ConfigProviderResolver.instance();
        Config config = ConfigProviderResolver.instance()
                .getBuilder()
                .withSources(MpConfigSources.create(helidonConfig)) // <2>
                .build();
        // end::snippet_6[]
    }

}
