/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.media.common;

import io.helidon.config.Config;
import io.helidon.media.common.spi.MediaService;

/**
 * Media support.
 */
public final class MediaSupport {

    private final MessageBodyReaderContext readerContext;
    private final MessageBodyWriterContext writerContext;

    private MediaSupport(MessageBodyReaderContext readerContext, MessageBodyWriterContext writerContext) {
        this.readerContext = readerContext;
        this.writerContext = writerContext;
    }

    /**
     * Create a new instance with default readers and writers registered to the contexts.
     *
     * @return MediaSupport
     */
    public static MediaSupport create() {
        return builder().build();
    }

    /**
     * Create a new instance based on the configuration.
     *
     * @param config a {@link Config}
     * @return MediaSupport
     */
    public static MediaSupport create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Creates new empty instance without registered defaults.
     *
     * @return empty instance
     */
    public static MediaSupport empty() {
        return builder().registerDefaults(false).build();
    }

    /**
     * Create a new {@link Builder} instance.
     *
     * @return a new {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the configured reader context.
     *
     * @return MessageBodyReaderContext
     */
    public MessageBodyReaderContext readerContext() {
        return readerContext;
    }

    /**
     * Get the configured writer context.
     *
     * @return MessageBodyWriterContext
     */
    public MessageBodyWriterContext writerContext() {
        return writerContext;
    }

    /**
     * MediaSupport builder.
     */
    public static class Builder implements io.helidon.common.Builder<MediaSupport>,
                                           BaseMediaSupportBuilder<Builder> {

        private final MessageBodyReaderContext readerContext;
        private final MessageBodyWriterContext writerContext;
        private boolean registerDefaults = true;
        private boolean includeStackTraces = false;

        private Builder() {
            this.readerContext = MessageBodyReaderContext.create();
            this.writerContext = MessageBodyWriterContext.create();
        }

        /**
         * Configures this {@link Builder} from the supplied {@link Config}.
         *
         * @param config a {@link Config}
         * @return this {@link Builder}
         */
        public Builder config(Config config) {
            config.get("server-errors-include-stack-traces").asBoolean().ifPresent(this::includeStackTraces);
            config.get("register-defaults").asBoolean().ifPresent(this::registerDefaults);
            return this;
        }

        @Override
        public Builder addMediaService(MediaService mediaService) {
            mediaService.register(readerContext, writerContext);
            return this;
        }

        @Override
        public Builder addReader(MessageBodyReader<?> reader) {
            readerContext.registerReader(reader);
            return this;
        }

        @Override
        public Builder addStreamReader(MessageBodyStreamReader<?> streamReader) {
            readerContext.registerReader(streamReader);
            return this;
        }

        @Override
        public Builder addWriter(MessageBodyWriter<?> writer) {
            writerContext.registerWriter(writer);
            return this;
        }

        @Override
        public Builder addStreamWriter(MessageBodyStreamWriter<?> streamWriter) {
            writerContext.registerWriter(streamWriter);
            return this;
        }

        /**
         * Register a new stream reader.
         * @param reader reader to register
         * @return this builder instance
         */
        public Builder registerStreamReader(MessageBodyStreamReader<?> reader) {
            readerContext.registerReader(reader);
            return this;
        }

        /**
         * Register a new stream writer.
         * @param writer writer to register
         * @return this builder instance
         */
        public Builder registerStreamWriter(MessageBodyStreamWriter<?> writer) {
            writerContext.registerWriter(writer);
            return this;
        }

        /**
         * Whether defaults should be included.
         *
         * @param registerDefaults register defaults
         * @return this builder instance
         */
        public Builder registerDefaults(boolean registerDefaults) {
            this.registerDefaults = registerDefaults;
            return this;
        }

        /**
         *
         * @param includeStackTraces include stack traces
         * @return this builder instance
         */
        public Builder includeStackTraces(boolean includeStackTraces) {
            this.includeStackTraces = includeStackTraces;
            return this;
        }

        @Override
        public MediaSupport build() {
            if (registerDefaults) {
                addMediaService(DefaultMediaService.create(includeStackTraces));
            }
            return new MediaSupport(readerContext, writerContext);
        }
    }

}
