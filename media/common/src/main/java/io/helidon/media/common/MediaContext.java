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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.media.common.spi.MediaSupportProvider;

/**
 * Media support.
 */
public final class MediaContext {

    private final MessageBodyReaderContext readerContext;
    private final MessageBodyWriterContext writerContext;

    private MediaContext(MessageBodyReaderContext readerContext, MessageBodyWriterContext writerContext) {
        this.readerContext = readerContext;
        this.writerContext = writerContext;
    }

    /**
     * Create a new instance with default readers and writers registered to the contexts.
     *
     * @return instance with defaults
     */
    public static MediaContext create() {
        return builder().build();
    }

    /**
     * Create a new instance based on the configuration.
     *
     * @param config a {@link Config}
     * @return instance based on config
     */
    public static MediaContext create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Creates new empty instance without registered defaults.
     *
     * @return empty instance
     */
    public static MediaContext empty() {
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
    public static class Builder implements io.helidon.common.Builder<MediaContext>,
                                           MediaContextBuilder<Builder> {

        private final static int DEFAULTS_PRIORITY = 100;
        private final static int BUILDER_PRIORITY = 200;
        private final static int LOADER_PRIORITY = 300;

        private final HelidonServiceLoader.Builder<MediaSupportProvider> services = HelidonServiceLoader
                .builder(ServiceLoader.load(MediaSupportProvider.class));

        private final List<MessageBodyReader<?>> builderReaders = new ArrayList<>();
        private final List<MessageBodyStreamReader<?>> builderStreamReaders = new ArrayList<>();
        private final List<MessageBodyWriter<?>> builderWriters = new ArrayList<>();
        private final List<MessageBodyStreamWriter<?>> builderStreamWriter = new ArrayList<>();
        private final List<MediaSupport> mediaSupports = new ArrayList<>();
        private final MessageBodyReaderContext readerContext;
        private final MessageBodyWriterContext writerContext;
        private boolean registerDefaults = true;
        private boolean includeStackTraces = false;
        private Config config = Config.empty();

        private Builder() {
            this.readerContext = MessageBodyReaderContext.create();
            this.writerContext = MessageBodyWriterContext.create();
        }

        /**
         * Configures this {@link Builder} from the supplied {@link Config}.
         * <table class="config">
         * <caption>Optional configuration parameters</caption>
         * <tr>
         *     <th>key</th>
         *     <th>description</th>
         * </tr>
         * <tr>
         *     <td>server-errors-include-stack-traces</td>
         *     <td>Whether stack traces should be included in the response (server only)</td>
         * </tr>
         * <tr>
         *     <td>register-defaults</td>
         *     <td>Whether to register default reader and writers</td>
         * </tr>
         * </table>
         * @param config a {@link Config}
         * @return this {@link Builder}
         */
        public Builder config(Config config) {
            config.get("server-errors-include-stack-traces").asBoolean().ifPresent(this::includeStackTraces);
            config.get("register-defaults").asBoolean().ifPresent(this::registerDefaults);
            this.config = config;
            return this;
        }

        @Override
        public Builder addMediaSupport(MediaSupport mediaSupport) {
            Objects.requireNonNull(mediaSupport);
            mediaSupport.register(readerContext, writerContext);
            return this;
        }

        public Builder addMediaSupport(MediaSupport mediaSupport, int priority) {
            services.addService((config) -> mediaSupport, priority);
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
         * Whether stack traces should be included in response.
         *
         * This is server side setting.
         *
         * @param includeStackTraces include stack traces
         * @return this builder instance
         */
        public Builder includeStackTraces(boolean includeStackTraces) {
            this.includeStackTraces = includeStackTraces;
            return this;
        }

        @Override
        public MediaContext build() {
            if (registerDefaults) {
                addMediaSupport(DefaultMediaSupport.create(includeStackTraces), DEFAULTS_PRIORITY);
            }
            services.useSystemServiceLoader(true)
                    .defaultPriority(LOADER_PRIORITY)
                    .addService(config -> new MediaSupport() {
                        @Override
                        public void register(MessageBodyReaderContext readerContext, MessageBodyWriterContext writerContext) {
                            builderReaders.forEach(readerContext::registerReader);
                            builderWriters.forEach(writerContext::registerWriter);
                        }
                    }, BUILDER_PRIORITY)
                    .addService(config -> new MediaSupport() {
                        @Override
                        public void register(MessageBodyReaderContext readerContext, MessageBodyWriterContext writerContext) {
                            mediaSupports.forEach(it -> it.register(readerContext, writerContext));
                        }
                    })
                    .build()
                    .asList()
                    .stream()
                    .map(it -> it.create(config.get(it.configKey())))
                    .collect(Collectors.toCollection(LinkedList::new))
                    .descendingIterator()
                    .forEachRemaining(mediaService -> mediaService.register(readerContext, writerContext));

            return new MediaContext(readerContext, writerContext);
        }
    }

}
