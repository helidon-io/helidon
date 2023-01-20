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

package io.helidon.nima.http.encoding;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.Set;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.config.Config;
import io.helidon.common.http.Headers;
import io.helidon.nima.http.encoding.spi.ContentEncodingProvider;

/**
 * Content encoding support to obtain encoders and decoders.
 */
public interface ContentEncodingContext {
    /**
     * Create a new encoding support.
     *
     * @return content encoding support
     */
    static ContentEncodingContext create() {
        return builder().build();
    }

    /**
     * There is at least one content encoder.
     *
     * @return whether there is at least one content encoder
     */
    boolean contentEncodingEnabled();

    /**
     * There is at least one content decoder.
     *
     * @return whether there is at least one content decoder
     */
    boolean contentDecodingEnabled();

    /**
     * Whether there is a content encoder for the provided id.
     *
     * @param encodingId encoding id
     * @return whether a provider exists for this id
     */
    boolean contentEncodingSupported(String encodingId);

    /**
     * Whether there is a content decoder for the provided id.
     *
     * @param encodingId encoding id
     * @return whether a provider exists for this id
     */
    boolean contentDecodingSupported(String encodingId);

    /**
     * Obtain a content encoder for the id.
     *
     * @param encodingId encoding id
     * @return content encoder to use
     * @throws NoSuchElementException in case an encoding provider does not exist
     */
    ContentEncoder encoder(String encodingId) throws NoSuchElementException;

    /**
     * Obtain a content decoder for the id.
     *
     * @param encodingId encoding id
     * @return content decoder to use
     * @throws NoSuchElementException in case a decoding provider does not exist
     */
    ContentDecoder decoder(String encodingId) throws NoSuchElementException;

    /**
     * Discover content encoder based on the HTTP headers.
     *
     * @param headers headers to analyze
     * @return content encoder to use
     */
    ContentEncoder encoder(Headers headers);

    /**
     * Builder to set up this encoding support content.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent API builder for {@link ContentEncodingContext}.
     */
    class Builder implements io.helidon.common.Builder<Builder, ContentEncodingContext> {

        private static final String IDENTITY_ENCODING = "identity";

        private final HelidonServiceLoader.Builder<ContentEncodingProvider> encodingProviders
                = HelidonServiceLoader.builder(ServiceLoader.load(ContentEncodingProvider.class));

        /**
         * Update this builder from configuration.
         * <p>
         * Configuration:<ul>
         *     <li><b>disable: true</b>  - to disable content encoding support</li>
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
         * Disable content encoding support.
         *
         * @return updated builder
         */
        public Builder discoverServices(boolean discoverServices) {
            this.encodingProviders.useSystemServiceLoader(discoverServices);
            return this;
        }

        /**
         * Configure content encoding provider.
         * This instance has priority over provider(s) discovered by service loader.
         *
         * @param encodingProvider explicit content encoding provider
         * @return updated builder
         */
        public Builder addEncodingProvider(ContentEncodingProvider encodingProvider) {
            encodingProviders.addService(encodingProvider);
            return this;
        }

        @Override
        public ContentEncodingContext build() {
            List<ContentEncodingProvider> providers = encodingProviders.build().asList();
            Map<String, ContentEncoder> encoders = new HashMap<>();
            Map<String, ContentDecoder> decoders = new HashMap<>();
            ContentEncoder firstEncoder = null;

            for (ContentEncodingProvider provider : providers) {
                Set<String> ids = provider.ids();

                if (provider.supportsEncoding()) {
                    for (String id : ids) {
                        ContentEncoder encoder = provider.encoder();
                        if (firstEncoder == null) {
                            firstEncoder = encoder;
                        }
                        encoders.putIfAbsent(id, encoder);
                    }
                }

                if (provider.supportsDecoding()) {
                    for (String id : ids) {
                        decoders.putIfAbsent(id, provider.decoder());
                    }
                }

            }

            encoders.put(IDENTITY_ENCODING, ContentEncoder.NO_OP);
            decoders.put(IDENTITY_ENCODING, ContentDecoder.NO_OP);

            return new ContentEncodingSupportImpl(Map.copyOf(encoders), Map.copyOf(decoders), firstEncoder);
        }

    }

}
