/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.internal;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigHelper;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.MissingValueException;
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
     * Optional {@code properties}: see {@link AbstractParsableConfigSource.Builder#init(Config)}.
     *
     * @param metaConfig meta-configuration used to initialize returned config source instance from.
     * @return new instance of config source described by {@code metaConfig}
     * @throws MissingValueException  in case the configuration tree does not contain all expected sub-nodes
     *                                required by the mapper implementation to provide instance of Java type.
     * @throws ConfigMappingException in case the mapper fails to map the (existing) configuration tree represented by the
     *                                supplied configuration node to an instance of a given Java type.
     * @see io.helidon.config.ConfigSources#url(URL)
     * @see AbstractParsableConfigSource.Builder#init(Config)
     */
    public static UrlConfigSource create(Config metaConfig) throws ConfigMappingException, MissingValueException {
        return (UrlConfigSource) new UrlBuilder(metaConfig.get(URL_KEY).as(URL.class).get())
                .init(metaConfig)
                .build();
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

    private String probeContentType() throws URISyntaxException {
        URI uri = url.toURI();
        Path path;
        switch (uri.getScheme()) {
            case "jar":
                String relativePath = uri.getSchemeSpecificPart();
                int idx = relativePath.indexOf("!");
                if (idx > 0 && idx < relativePath.length()) {
                    relativePath = relativePath.substring(idx + 1);
                }
                path = Paths.get(relativePath);
                break;
            case "file":
                path = Paths.get(uri);
                break;
            default:
                path = Paths.get(url.getPath());
        }
        return ConfigHelper.detectContentType(path);
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
    public static final class UrlBuilder extends Builder<UrlBuilder, URL> {
        private URL url;

        /**
         * Initialize builder.
         *
         * @param url configuration url
         */
        public UrlBuilder(URL url) {
            super(URL.class);

            Objects.requireNonNull(url, "url cannot be null");

            this.url = url;
        }

        @Override
        protected UrlBuilder init(Config metaConfig) {
            return super.init(metaConfig);
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
        public ConfigSource build() {
            return new UrlConfigSource(this, url);
        }

        PollingStrategy pollingStrategyInternal() { //just for testing purposes
            return super.pollingStrategy();
        }
    }
}
