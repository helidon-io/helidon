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
import java.net.URL;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.OverrideSources;
import io.helidon.config.spi.AbstractOverrideSource;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.PollingStrategy;

/**
 * {@link io.helidon.config.spi.OverrideSource} implementation that loads configuration override content from specified endpoint URL.
 *
 * @see AbstractOverrideSource.Builder
 * @see OverrideSources
 */
public class UrlOverrideSource extends AbstractOverrideSource<Instant> {

    private static final Logger LOGGER = Logger.getLogger(UrlOverrideSource.class.getName());

    private static final String GET_METHOD = "GET";
    private static final String HEAD_METHOD = "HEAD";
    private static final String URL_KEY = "url";

    private final URL url;

    UrlOverrideSource(UrlBuilder builder) {
        super(builder);

        this.url = builder.url;
    }

    /**
     * Create a new URL override source from meta configuration.
     *
     * @param metaConfig meta configuration containing at least the {@key url} key
     * @return a new URL override source
     */
    public static UrlOverrideSource create(Config metaConfig) {
        return builder().config(metaConfig).build();
    }

    /**
     * Create a new fluent API builder to create URL override source.
     *
     * @return a new builder
     */
    public static UrlBuilder builder() {
        return new UrlBuilder();
    }

    @Override
    protected Data<OverrideData, Instant> loadData() throws ConfigException {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(GET_METHOD);

            Instant timestamp;
            if (connection.getLastModified() != 0) {
                timestamp = Instant.ofEpochMilli(connection.getLastModified());
            } else {
                timestamp = Instant.now();
                LOGGER.fine("Missing GET '" + url + "' response header 'Last-Modified'. Used current time '"
                                    + timestamp + "' as a content timestamp.");
            }

            Reader reader = new InputStreamReader(connection.getInputStream(),
                                                  ConfigUtils.getContentCharset(connection.getContentEncoding()));

            return new Data<>(Optional.of(OverrideData.create(reader)), Optional.of(timestamp));
        } catch (ConfigException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ConfigException("Configuration at url '" + url + "' GET is not accessible.", ex);

        }
    }

    @Override
    protected String uid() {
        return url.toString();
    }

    @Override
    protected Optional<Instant> dataStamp() {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(HEAD_METHOD);

            if (connection.getLastModified() != 0) {
                return Optional.of(Instant.ofEpochMilli(connection.getLastModified()));
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
     * Url Override Source Builder.
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
    public static final class UrlBuilder extends AbstractOverrideSource.Builder<UrlBuilder, URL> {
        private URL url;

        /**
         * Initialize builder.
         */
        private UrlBuilder() {
            super(URL.class);
        }

        /**
         * Configure the URL that is source of this overrides.
         *
         * @param url url of the resource to load
         * @return updated builder instance
         */
        public UrlBuilder url(URL url) {
            this.url = url;
            return this;
        }

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
        public UrlOverrideSource build() {
            Objects.requireNonNull(url, "url cannot be null");

            return new UrlOverrideSource(this);
        }

        PollingStrategy pollingStrategyInternal() { //just for testing purposes
            return super.pollingStrategy();
        }
    }

}
