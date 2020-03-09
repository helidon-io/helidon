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
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import io.helidon.config.spi.ConfigContent.OverrideContent;
import io.helidon.config.spi.OverrideSource;

/**
 * {@link OverrideSource} implementation that loads override definitions from a resource on a classpath.
 *
 * @see io.helidon.config.spi.Source.Builder
 */
public class ClasspathOverrideSource extends AbstractSource implements OverrideSource {
    private final String resource;
    private final URL resourceUrl;

    ClasspathOverrideSource(Builder builder) {
        super(builder);

        this.resource = builder.resource;
        this.resourceUrl = builder.url;
    }

    @Override
    protected String uid() {
        return ClasspathSourceHelper.uid(resource);
    }

    @Override
    public Optional<OverrideContent> load() throws ConfigException {
        if (null == resourceUrl) {
            return Optional.empty();
        }

        InputStream inputStream;
        try {
            inputStream = resourceUrl.openStream();
        } catch (IOException e) {
            throw new ConfigException("Failed to read configuration from classpath, resource: " + resource, e);
        }

        OverrideData data = OverrideData.create(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        return Optional.of(OverrideContent.builder()
                           .data(data)
                           .build());
    }

    /**
     * Create a new classpath override source from meta configuration, containing {@code resource} key and other options.
     * @param config meta configuration
     * @return a new classpath override source
     */
    public static ClasspathOverrideSource create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Create a new fluent API builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Classpath OverrideSource Builder.
     * <p>
     * It allows to configure following properties:
     * <ul>
     * <li>{@code resource} - override resource name;</li>
     * <li>{@code mandatory} - is existence of override resource mandatory (by default) or is {@code optional}?</li>
     * </ul>
     * <p>
     * If the {@code OverrideSource} is {@code mandatory} and the {@code resource} does not exist
     * then {@link OverrideSource#load} throws {@link ConfigException}.
     */
    public static final class Builder extends AbstractSourceBuilder<Builder, Void>
            implements io.helidon.common.Builder<ClasspathOverrideSource> {

        private URL url;
        private String resource;

        /**
         * Initialize builder.
         */
        private Builder() {
        }

        /**
         * Builds new instance of Classpath OverrideSource.
         *
         * @return new instance of Classpath OverrideSource.
         */
        @Override
        public ClasspathOverrideSource build() {
            return new ClasspathOverrideSource(this);
        }

        /**
         * Update builder from meta configuration.
         *
         * @param metaConfig meta configuration to load this override source from
         * @return updated builder instance
         */
        public Builder config(Config metaConfig) {
            metaConfig.get("resource").asString().ifPresent(this::resource);
            return super.config(metaConfig);
        }

        /**
         * Configure the classpath resource to be used as a source.
         *
         * @param resource classpath resource path
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
    }
}
