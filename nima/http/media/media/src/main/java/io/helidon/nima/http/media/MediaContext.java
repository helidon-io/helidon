/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.http.media;

import java.util.ServiceLoader;

import io.helidon.common.GenericType;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.config.Config;
import io.helidon.common.http.Headers;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.http.media.spi.MediaSupportProvider;

/**
 * Media context to obtain readers and writers of various supported content types.
 */
public interface MediaContext {

    /**
     * Create a new media context from {@link java.util.ServiceLoader}.
     *
     * @return media context
     */
    static MediaContext create() {
        return builder().build();
    }

    /**
     * Create a new media context and apply provided configuration.
     *
     * @param config configuration to use
     * @return media context
     */
    static MediaContext create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Reader for entity.
     *
     * @param type    type to read into (such as Pojo, JsonObject)
     * @param headers headers related to this entity
     * @param <T>     type
     * @return entity reader for the type, or a reader that will fail if none found
     */
    <T> EntityReader<T> reader(GenericType<T> type, Headers headers);

    /**
     * Writer for server response entity.
     *
     * @param type            type to write
     * @param requestHeaders  request headers, containing accepted types
     * @param responseHeaders response headers to be updated with content type
     * @param <T>             type
     * @return entity writer for the type, or a writer that will fail if none found
     */
    <T> EntityWriter<T> writer(GenericType<T> type,
                               Headers requestHeaders,
                               WritableHeaders<?> responseHeaders);

    /**
     * Reader for client response entity.
     *
     * @param type            type to read into
     * @param requestHeaders  request headers containing accepted types
     * @param responseHeaders response headers containing content type
     * @param <T>             type
     * @return entity reader for the type, or a reader that will fail if none found
     */
    <T> EntityReader<T> reader(GenericType<T> type,
                               Headers requestHeaders,
                               Headers responseHeaders);

    /**
     * Writer for client request entity.
     *
     * @param type           type to write
     * @param requestHeaders request headers to write content type to
     * @param <T>            type
     * @return entity writer for the type, or a writer that will fail if none found
     */
    <T> EntityWriter<T> writer(GenericType<T> type,
                               WritableHeaders<?> requestHeaders);

    /**
     * Builder to set up this media support context.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent API builder for {@link MediaContext}.
     */
    class Builder implements io.helidon.common.Builder<Builder, MediaContext> {

        private final HelidonServiceLoader.Builder<MediaSupportProvider> mediaSupportProviders;

        // Builder instance must be created using factory method.
        private Builder() {
            mediaSupportProviders = HelidonServiceLoader.builder(ServiceLoader.load(MediaSupportProvider.class));
        }

        /**
         * Update this builder from configuration.
         * <p>
         * Configuration:<ul>
         *     <li><b>discover-services: false</b> - to disable media support providers service loader discovery</li>
         * </ul>
         *
         * @param config configuration to use
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("discover-services").asBoolean().ifPresent(this::discoverServices);
            return this;
        }

        /**
         * Whether Java Service Loader should be used to load {@link MediaSupportProvider}.
         *
         * @return updated builder
         */
        public Builder discoverServices(boolean discoverServices) {
            this.mediaSupportProviders.useSystemServiceLoader(discoverServices);
            return this;
        }

        /**
         * Configure media support provider.
         * This instance has priority over provider(s) discovered by service loader.
         *
         * @param mediaSupportProvider explicit media support provider
         * @return updated builder
         */
        public Builder addMediaSupportProvider(MediaSupportProvider mediaSupportProvider) {
            mediaSupportProviders.addService(mediaSupportProvider);
            return this;
        }

        @Override
        public MediaContext build() {
            return new MediaContextImpl(
                    mediaSupportProviders
                            .addService(new StringSupportProvider())
                            .addService(new FormParamsSupportProvider())
                            .addService(new PathSupportProvider())
                            .build()
                            .asList()
            );
        }
    }

}
