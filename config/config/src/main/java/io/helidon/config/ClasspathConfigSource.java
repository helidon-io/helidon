/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParser.Content;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.ParsableSource;

/**
 * {@link ConfigSource} implementation that loads configuration content from a resource on a classpath.
 * Classpath config source does not support changes (neither through polling nor through change notifications).
 */
public class ClasspathConfigSource extends AbstractConfigSource implements ConfigSource,
                                                                           ParsableSource {
    private final String resource;
    private final URL resourceUrl;
    private final LazyValue<Optional<String>> mediaType;

    ClasspathConfigSource(Builder builder) {
        super(builder);

        this.resource = builder.resource;
        this.resourceUrl = builder.url;

        mediaType = LazyValue.create(() -> {
            if (null == resourceUrl) {
                return MediaTypes.detectType(resource);
            } else {
                return MediaTypes.detectType(resource);
            }
        });
    }

    /**
     * Initializes config source instance from configuration properties.
     * <p>
     * Mandatory {@code properties}, see {@link io.helidon.config.ConfigSources#classpath(String)}:
     * <ul>
     * <li>{@code resource} - type {@code String}</li>
     * </ul>
     * Optional {@code properties}: see {@link AbstractConfigSourceBuilder#config(io.helidon.config.Config)}.
     *
     * @param metaConfig meta-configuration used to initialize returned config source instance from.
     * @return new instance of config source described by {@code metaConfig}
     * @throws MissingValueException  in case the configuration tree does not contain all expected sub-nodes
     *                                required by the mapper implementation to provide instance of Java type.
     * @throws ConfigMappingException in case the mapper fails to map the (existing) configuration tree represented by the
     *                                supplied configuration node to an instance of a given Java type.
     * @see io.helidon.config.ConfigSources#classpath(String)
     * @see AbstractConfigSourceBuilder#config(Config)
     */
    public static ClasspathConfigSource create(Config metaConfig) throws ConfigMappingException, MissingValueException {
        return builder()
                .config(metaConfig)
                .build();
    }

    /**
     * Create a config source for the first resource on the classpath.
     *
     * @param resource resource to find
     * @return a config source based on the classpath resource
     */
    public static ClasspathConfigSource create(String resource) {
        return builder().resource(resource).build();
    }

    /**
     * Create config source for each resource on the classpath.
     *
     * @param resource resource to find
     * @return a collection of sources for each resource present on the classpath, always at least one
     */
    public static Collection<? super ClasspathConfigSource> createAll(String resource) {

        Enumeration<URL> resources = findAllResources(resource);

        if (resources.hasMoreElements()) {
            List<? super ClasspathConfigSource> sources = new LinkedList<>();
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                sources.add(builder().url(url).build());
            }
            return sources;
        } else {
            // there is none - let the default source handle it, to manage optional vs. mandatory
            // with configuration and not an empty list
            return List.of(create(resource));
        }
    }

    /**
     * Create config source for each resource on the classpath.
     *
     * @param metaConfig meta configuration of the config source
     * @return a collection of sources for each resource present on the classpath
     */
    public static List<ConfigSource> createAll(Config metaConfig) {
        // this must fail if the resource is not defined
        String resource = metaConfig.get("resource").asString().get();
        Enumeration<URL> resources = findAllResources(resource);

        if (resources.hasMoreElements()) {
            List<ConfigSource> sources = new LinkedList<>();
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                sources.add(builder()
                                    .config(metaConfig)
                                    // url must be configured after meta config, to override the default
                                    .url(url)
                                    .build());
            }
            return sources;
        } else {
            // there is none - let the default source handle it, to manage optional vs. mandatory
            // with configuration and not an empty list
            return List.of(create(metaConfig));
        }
    }

    /**
     * Create a new fluent API builder for classpath config source.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected String uid() {
        return (null == resourceUrl) ? resource : resourceUrl.toString();
    }

    @Override
    public Optional<Content> load() throws ConfigException {
        if (null == resourceUrl) {
            return Optional.empty();
        }

        InputStream inputStream;
        try {
            inputStream = resourceUrl.openStream();
        } catch (IOException e) {
            throw new ConfigException("Failed to read configuration from classpath, resource: " + resource, e);
        }

        Content.Builder builder = Content.builder()
                .data(inputStream);

        mediaType.get().ifPresent(builder::mediaType);

        return Optional.of(builder.build());
    }

    @Override
    public String toString() {
        return "classpath: " + resource;
    }

    @Override
    public Optional<String> mediaType() {
        return super.mediaType();
    }

    @Override
    public Optional<ConfigParser> parser() {
        return super.parser();
    }

    private static Enumeration<URL> findAllResources(String resource) {
        String cleaned = resource.startsWith("/") ? resource.substring(1) : resource;
        try {
            return Thread.currentThread()
                    .getContextClassLoader()
                    .getResources(cleaned);
        } catch (IOException e) {
            throw new ConfigException("Could not access config resource " + resource, e);
        }
    }

    /**
     * Classpath ConfigSource Builder.
     * <p>
     * It allows to configure following properties:
     * <ul>
     * <li>{@code resource} - configuration resource name;</li>
     * <li>{@code optional} - is existence of configuration resource mandatory (by default) or is {@code optional}?</li>
     * <li>{@code media-type} - configuration content media type to be used to look for appropriate {@link ConfigParser};</li>
     * <li>{@code parser} - or directly set {@link ConfigParser} instance to be used to parse the source;</li>
     * </ul>
     * <p>
     * If the ConfigSource is {@code mandatory} and a {@code resource} does not exist
     * then {@link io.helidon.config.spi.ParsableSource#load} throws {@link ConfigException}.
     * <p>
     * If {@code media-type} not set it tries to guess it from resource extension.
     */
    public static final class Builder extends AbstractConfigSourceBuilder<Builder, Void>
            implements ParsableSource.Builder<Builder>,
                       io.helidon.common.Builder<ClasspathConfigSource> {

        private URL url;
        private String resource;

        /**
         * Initialize builder.
         */
        private Builder() {
        }

        /**
         * Builds new instance of Classpath ConfigSource.
         * <p>
         * If {@code media-type} not set it tries to guess it from resource extension before parsing.
         *
         * @return new instance of Classpath ConfigSource.
         */
        @Override
        public ClasspathConfigSource build() {
            return new ClasspathConfigSource(this);
        }

        /**
         * {@inheritDoc}
         * <ul>
         *     <li>{@code resource} - the classpath resource to load</li>
         * </ul>
         * @param metaConfig configuration properties used to configure a builder instance.
         * @return updated builder instance
         */
        @Override
        public Builder config(Config metaConfig) {
            metaConfig.get("resource").asString().ifPresent(this::resource);
            return super.config(metaConfig);
        }

        @Override
        public Builder parser(ConfigParser parser) {
            return super.parser(parser);
        }

        @Override
        public Builder mediaType(String mediaType) {
            return super.mediaType(mediaType);
        }

        /**
         * Configure the classpath resource to load the configuration from.
         *
         * @param resource resource on classpath
         * @return updated builder instance
         */
        public Builder resource(String resource) {
            String cleaned = resource.startsWith("/") ? resource.substring(1) : resource;

            this.resource = resource;

            // the URL may not exist, and that is fine - maybe we are an optional config source
            this.url = Thread.currentThread()
                    .getContextClassLoader()
                    .getResource(cleaned);

            return this;
        }

        private Builder url(URL url) {
            this.url = url;
            return this;
        }
    }
}
