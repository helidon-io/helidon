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
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.internal.ConfigUtils;
import io.helidon.config.spi.AbstractParsableConfigSource;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.PollingStrategy;

/**
 * {@link ConfigSource} implementation that loads configuration content from specified endpoint URL.
 *
 * @see AbstractParsableConfigSource.Builder
 */
public class UrlConfigSource extends AbstractParsableConfigSource<Instant> {

    private static final Logger LOGGER = Logger.getLogger(UrlConfigSource.class.getName());

    private static final String HEAD_METHOD = "HEAD";
    private static final String GET_METHOD = "GET";
    private static final String URL_KEY = "url";

    private final URL url;

    UrlConfigSource(UrlBuilder builder, URL url) {
        super(builder);

        this.url = url;
    }

    /**
     * Initializes config source instance from configuration properties.
     * <p>
     * Mandatory {@code properties}, see {@link io.helidon.config.ConfigSources#url(URL)}:
     * <ul>
     * <li>{@code url} - type {@link URL}</li>
     * </ul>
     * Optional {@code properties}: see {@link AbstractParsableConfigSource.Builder#config(Config)}.
     *
     * @param metaConfig meta-configuration used to initialize returned config source instance from.
     * @return new instance of config source described by {@code metaConfig}
     * @throws MissingValueException  in case the configuration tree does not contain all expected sub-nodes
     *                                required by the mapper implementation to provide instance of Java type.
     * @throws ConfigMappingException in case the mapper fails to map the (existing) configuration tree represented by the
     *                                supplied configuration node to an instance of a given Java type.
     * @see io.helidon.config.ConfigSources#url(URL)
     * @see AbstractParsableConfigSource.Builder#config(Config)
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
    public static UrlBuilder builder() {
        return new UrlBuilder();
    }

    @Override
    protected String uid() {
        return url.toString();
    }

    @Override
    protected ConfigParser.Content<Instant> content() throws ConfigException {
        // assumption about HTTP URL connection is wrong here
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
            throw new ConfigException("Configuration at url '" + url + "' GET is not accessible.", ex);
        }
    }

    private ConfigParser.Content<Instant> genericContent(URLConnection urlConnection) throws IOException, URISyntaxException {
        final String mediaType = mediaType(null);
        Reader reader = new InputStreamReader(urlConnection.getInputStream(),
                                              StandardCharsets.UTF_8);

        return ConfigParser.Content.create(reader, mediaType, Optional.of(Instant.now()));
    }

    private ConfigParser.Content<Instant> httpContent(HttpURLConnection connection) throws IOException, URISyntaxException {
        connection.setRequestMethod(GET_METHOD);

        final String mediaType = mediaType(connection.getContentType());
        final Optional<Instant> timestamp;
        if (connection.getLastModified() != 0) {
            timestamp = Optional.of(Instant.ofEpochMilli(connection.getLastModified()));
        } else {
            timestamp = Optional.of(Instant.now());
            LOGGER.fine("Missing GET '" + url + "' response header 'Last-Modified'. Used current time '"
                                + timestamp + "' as a content timestamp.");
        }

        Reader reader = new InputStreamReader(connection.getInputStream(),
                                              ConfigUtils.getContentCharset(connection.getContentEncoding()));

        return ConfigParser.Content.create(reader, mediaType, timestamp);
    }

    @Override
    protected String mediaType() {
        //do not call ConfigHelper.guessMediaType here - it is done in content() method
        return super.mediaType();
    }

    private String mediaType(String responseMediaType) throws URISyntaxException {
        String mediaType = mediaType();
        if (mediaType == null) {
            mediaType = responseMediaType;
            if (mediaType == null) {
                mediaType = probeContentType();
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("HTTP response does not contain content-type, used guessed one: " + mediaType + ".");
                }
            }
        }
        return mediaType;
    }

    private String probeContentType() {
        return MediaTypes.detectType(url).orElse(null);
    }

    @Override
    protected Optional<Instant> dataStamp() {
        // the URL may not be an HTTP URL
        try {
            URLConnection urlConnection = url.openConnection();
            if (urlConnection instanceof HttpURLConnection) {
                HttpURLConnection connection = (HttpURLConnection) urlConnection;
                try {
                    connection.setRequestMethod(HEAD_METHOD);

                    if (connection.getLastModified() != 0) {
                        return Optional.of(Instant.ofEpochMilli(connection.getLastModified()));
                    }
                } finally {
                    connection.disconnect();
                }
            }
        } catch (IOException ex) {
            LOGGER.log(Level.FINE, ex, () -> "Configuration at url '" + url + "' HEAD is not accessible.");
        }

        Optional<Instant> timestamp = Optional.of(Instant.MAX);
        LOGGER.finer("Missing HEAD '" + url + "' response header 'Last-Modified'. Used time '"
                             + timestamp + "' as a content timestamp.");
        return timestamp;
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
     * If the Url ConfigSource is {@code mandatory} and a {@code url} endpoint does not exist
     * then {@link ConfigSource#load} throws {@link ConfigException}.
     * <p>
     * If {@code media-type} not set it uses HTTP response header {@code content-type}.
     * If {@code media-type} not returned it tries to guess it from url suffix.
     */
    public static final class UrlBuilder extends Builder<UrlBuilder, URL, UrlConfigSource> {
        private URL url;

        /**
         * Initialize builder.
         */
        private UrlBuilder() {
            super(URL.class);
        }

        /**
         * URL of the configuration.
         *
         * @param url of configuration source
         * @return updated builder instance
         */
        public UrlBuilder url(URL url) {
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
        public UrlBuilder config(Config metaConfig) {
            metaConfig.get(URL_KEY).as(URL.class).ifPresent(this::url);
            return super.config(metaConfig);
        }

        @Override
        protected URL target() {
            return url;
        }

        /**
         * Builds new instance of Url ConfigSource.
         * <p>
         * If {@code media-type} not set it tries to use {@code content-type} response header or guesses it from file extension.
         *
         * @return new instance of Url ConfigSource.
         */
        public UrlConfigSource build() {
            if (null == url) {
                throw new IllegalArgumentException("url must be provided");
            }
            return new UrlConfigSource(this, url);
        }

        PollingStrategy pollingStrategyInternal() { //just for testing purposes
            return super.pollingStrategy();
        }
    }
}
