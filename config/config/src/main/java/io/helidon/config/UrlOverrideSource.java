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

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import io.helidon.config.spi.ChangeWatcher;
import io.helidon.config.spi.ConfigContent.OverrideContent;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.OverrideSource;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.WatchableSource;

/**
 * {@link io.helidon.config.spi.OverrideSource} implementation that loads configuration override content from specified
 * endpoint URL.
 *
 * @see AbstractSource
 * @see OverrideSources
 */
public class UrlOverrideSource extends AbstractSource
        implements OverrideSource, PollableSource<Instant>, WatchableSource<URL> {

    private static final Logger LOGGER = Logger.getLogger(UrlOverrideSource.class.getName());

    private static final String GET_METHOD = "GET";
    private static final String URL_KEY = "url";

    private final URL url;

    UrlOverrideSource(Builder builder) {
        super(builder);

        this.url = builder.url;
    }

    /**
     * Create a new URL override source from meta configuration.
     *
     * @param metaConfig meta configuration containing at least the {@code url} key
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
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean isModified(Instant stamp) {
        return UrlHelper.isModified(url, stamp);
    }

    @Override
    public Optional<PollingStrategy> pollingStrategy() {
        return super.pollingStrategy();
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
    public Optional<ChangeWatcher<Object>> changeWatcher() {
        return super.changeWatcher();
    }

    @Override
    public Optional<OverrideContent> load() throws ConfigException {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(GET_METHOD);

            if (UrlHelper.STATUS_NOT_FOUND == connection.getResponseCode()) {
                return Optional.empty();
            }
            Instant timestamp;
            if (connection.getLastModified() == 0) {
                timestamp = Instant.now();
                LOGGER.fine("Missing GET '" + url + "' response header 'Last-Modified'. Used current time '"
                                    + timestamp + "' as a content timestamp.");
            } else {
                timestamp = Instant.ofEpochMilli(connection.getLastModified());
            }

            Reader reader = new InputStreamReader(connection.getInputStream(),
                                                  ConfigUtils.getContentCharset(connection.getContentEncoding()));

            OverrideContent.Builder builder = OverrideContent.builder()
                    .data(OverrideData.create(reader))
                    .stamp(timestamp);

            return Optional.of(builder.build());
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
     * If {@code media-type} not set it uses HTTP response header {@code content-type}.
     * If {@code media-type} not returned it tries to guess it from url suffix.
     */
    public static final class Builder extends AbstractSourceBuilder<Builder, URL>
            implements PollableSource.Builder<Builder>,
                       WatchableSource.Builder<Builder, URL>,
                       io.helidon.common.Builder<UrlOverrideSource> {
        private URL url;

        /**
         * Initialize builder.
         */
        private Builder() {
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

        @Override
        public Builder config(Config metaConfig) {
            metaConfig.get(URL_KEY).as(URL.class).ifPresent(this::url);
            return super.config(metaConfig);
        }

        /**
         * Configure the URL that is source of this overrides.
         *
         * @param url url of the resource to load
         * @return updated builder instance
         */
        public Builder url(URL url) {
            this.url = url;
            return this;
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
