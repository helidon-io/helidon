/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.spi.ChangeWatcher;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParser.Content;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.ParsableSource;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.WatchableSource;

/**
 * {@link ConfigSource} implementation that loads configuration content from specified endpoint URL.
 *
 * @see AbstractConfigSourceBuilder
 */
public final class UrlConfigSource extends AbstractConfigSource
        implements WatchableSource<URL>, ParsableSource, PollableSource<Instant> {

    private static final Logger LOGGER = Logger.getLogger(UrlConfigSource.class.getName());

    private static final String GET_METHOD = "GET";
    private static final String URL_KEY = "url";
    private static final int STATUS_NOT_FOUND = 404;

    private final URL url;

    private UrlConfigSource(Builder builder) {
        super(builder);

        this.url = builder.url;
    }

    /**
     * Initializes config source instance from configuration properties.
     * <p>
     * Mandatory {@code properties}, see {@link io.helidon.config.ConfigSources#url(URL)}:
     * <ul>
     * <li>{@code url} - type {@link URL}</li>
     * </ul>
     * Optional {@code properties}: see {@link AbstractConfigSourceBuilder#config(Config)}.
     *
     * @param metaConfig meta-configuration used to initialize returned config source instance from.
     * @return new instance of config source described by {@code metaConfig}
     * @throws MissingValueException  in case the configuration tree does not contain all expected sub-nodes
     *                                required by the mapper implementation to provide instance of Java type.
     * @throws ConfigMappingException in case the mapper fails to map the (existing) configuration tree represented by the
     *                                supplied configuration node to an instance of a given Java type.
     * @see io.helidon.config.ConfigSources#url(URL)
     * @see AbstractConfigSourceBuilder#config(Config)
     */
    public static UrlConfigSource create(Config metaConfig) throws ConfigMappingException, MissingValueException {
        return builder()
                .config(metaConfig)
                .build();
    }

    /**
     * A new fluent API builder.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected String uid() {
        return url.toString();
    }

    @Override
    public URL target() {
        return url;
    }

    @Override
    public Class<URL> targetType() {
        return URL.class;
    }

    @Override
    public Optional<ConfigParser> parser() {
        return super.parser();
    }

    @Override
    public Optional<String> mediaType() {
        return super.mediaType();
    }

    @Override
    public Optional<PollingStrategy> pollingStrategy() {
        return super.pollingStrategy();
    }

    @Override
    public Optional<ChangeWatcher<Object>> changeWatcher() {
        return super.changeWatcher();
    }

    @Override
    public boolean isModified(Instant stamp) {
        return UrlHelper.isModified(url, stamp);
    }

    @Override
    public Optional<Content> load() throws ConfigException {
        try {
            URLConnection urlConnection = url.openConnection();

            if (urlConnection instanceof HttpURLConnection) {
                return httpContent((HttpURLConnection) urlConnection);
            } else {
                return genericContent(urlConnection);
            }
        } catch (ConfigException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ConfigException("Configuration at url '" + url + "' is not accessible.", ex);
        }
    }

    private Optional<Content> genericContent(URLConnection urlConnection) throws IOException {
        InputStream is = urlConnection.getInputStream();

        Content.Builder builder = Content.builder()
                .data(is)
                .stamp(Instant.now());

        this.probeContentType().ifPresent(builder::mediaType);

        return Optional.ofNullable(builder.build());
    }

    private Optional<Content> httpContent(HttpURLConnection connection) throws IOException {
        connection.setRequestMethod(GET_METHOD);

        try {
            connection.connect();
        } catch (IOException e) {
            // considering this to be unavailable
            LOGGER.log(Level.FINEST, "Failed to connect to " + url + ", considering this source to be missing", e);
            return Optional.empty();
        }

        if (STATUS_NOT_FOUND == connection.getResponseCode()) {
            return Optional.empty();
        }

        Optional<String> mediaType = mediaType(connection.getContentType());
        final Instant timestamp;
        if (connection.getLastModified() == 0) {
            timestamp = Instant.now();
            LOGGER.fine("Missing GET '" + url + "' response header 'Last-Modified'. Used current time '"
                                + timestamp + "' as a content timestamp.");
        } else {
            timestamp = Instant.ofEpochMilli(connection.getLastModified());
        }

        InputStream inputStream = connection.getInputStream();
        Charset charset = ConfigUtils.getContentCharset(connection.getContentEncoding());

        Content.Builder builder = Content.builder();

        builder.data(inputStream);
        builder.charset(charset);
        builder.stamp(timestamp);
        mediaType.ifPresent(builder::mediaType);

        return Optional.of(builder.build());
    }

    private Optional<String> mediaType(String responseMediaType) {
        return mediaType()
                .or(() -> Optional.ofNullable(responseMediaType))
                .or(() -> {
                    Optional<String> mediaType = probeContentType();
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.fine("HTTP response does not contain content-type, used guessed one: " + mediaType + ".");
                    }
                    return mediaType;
                });
    }

    private Optional<String> probeContentType() {
        return MediaTypes.detectType(url);
    }

    /**
     * Url ConfigSource Builder.
     * <p>
     * It allows to configure following properties:
     * <ul>
     * <li>{@code url} - configuration endpoint URL;</li>
     * <li>{@code mandatory} - is existence of configuration resource mandatory (by default) or is {@code optional}?</li>
     * <li>{@code media-type} - configuration content media type to be used to look for appropriate {@link ConfigParser};</li>
     * <li>{@code parser} - or directly set {@link ConfigParser} instance to be used to parse the source;</li>
     * </ul>
     * <p>
     * If {@code media-type} not set it uses HTTP response header {@code content-type}.
     * If {@code media-type} not returned it tries to guess it from url suffix.
     */
    public static final class Builder extends AbstractConfigSourceBuilder<Builder, URL>
            implements PollableSource.Builder<Builder>,
                       WatchableSource.Builder<Builder, URL>,
                       ParsableSource.Builder<Builder>,
                       io.helidon.common.Builder<UrlConfigSource> {
        private URL url;

        /**
         * Initialize builder.
         */
        private Builder() {
        }

        /**
         * URL of the configuration.
         *
         * @param url of configuration source
         * @return updated builder instance
         */
        public Builder url(URL url) {
            this.url = url;
            return this;
        }

        /**
         * {@inheritDoc}
         * <ul>
         *     <li>{@code url} - URL of the configuration source</li>
         * </ul>
         * @param metaConfig configuration properties used to configure a builder instance.
         * @return updated builder instance
         */
        @Override
        public Builder config(Config metaConfig) {
            metaConfig.get(URL_KEY).as(URL.class).ifPresent(this::url);
            return super.config(metaConfig);
        }

        /**
         * Builds new instance of Url ConfigSource.
         * <p>
         * If {@code media-type} not set it tries to use {@code content-type} response header or guesses it from file extension.
         *
         * @return new instance of Url ConfigSource.
         */
        @Override
        public UrlConfigSource build() {
            if (null == url) {
                throw new IllegalArgumentException("url must be provided");
            }
            return new UrlConfigSource(this);
        }

        @Override
        public Builder parser(ConfigParser parser) {
            return super.parser(parser);
        }

        @Override
        public Builder mediaType(String mediaType) {
            return super.mediaType(mediaType);
        }

        @Override
        public Builder changeWatcher(ChangeWatcher<URL> changeWatcher) {
            return super.changeWatcher(changeWatcher);
        }

        @Override
        public Builder pollingStrategy(PollingStrategy pollingStrategy) {
            return super.pollingStrategy(pollingStrategy);
        }
    }
}
