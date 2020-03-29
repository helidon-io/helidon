/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

/**
 * Media support.
 */
public final class MediaSupport {

    private final MessageBodyReaderContext readerContext;
    private final MessageBodyWriterContext writerContext;
    private final boolean serverErrorsIncludeStackTraces;

    private MediaSupport(MessageBodyReaderContext readerContext, MessageBodyWriterContext writerContext) {
        this(readerContext, writerContext, false);
    }

    private MediaSupport(MessageBodyReaderContext readerContext,
                         MessageBodyWriterContext writerContext,
                         boolean serverErrorsIncludeStackTraces) {
        this.readerContext = readerContext;
        this.writerContext = writerContext;
        this.serverErrorsIncludeStackTraces = serverErrorsIncludeStackTraces;
    }

    /**
     * Get the configured reader context.
     * @return MessageBodyReaderContext
     */
    public MessageBodyReaderContext readerContext() {
        return readerContext;
    }

    /**
     * Get the configured writer context.
     * @return MessageBodyWriterContext
     */
    public MessageBodyWriterContext writerContext() {
        return writerContext;
    }

    /**
     * Returns {@code true} if server errors will include stack trace
     * information.
     * @return {@code true} if server errors will include stack trace
     * information
     */
    public boolean serverErrorsIncludeStackTraces() {
        return serverErrorsIncludeStackTraces;
    }

    /**
     * Create a new instance with empty reader and writer contexts.
     * @return MediaSupport
     */
    public static MediaSupport create() {
        return builder().build();
    }

    /**
     * Create a new instance with empty reader and writer contexts.
     * @param config a {@link Config}
     * @return MediaSupport
     */
    public static MediaSupport create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Create a new instance with the default readers and writers registered on
     * the contexts.
     * @return MediaSupport
     */
    public static MediaSupport createWithDefaults() {
        return builder().registerDefaults().build();
    }

    /**
     * Create a new instance with the default readers and writers registered on
     * the contexts.
     * @param config a {@link Config} that will be passed to {@link
     * Builder#registerDefaults()}
     * @return MediaSupport
     */
    public static MediaSupport createWithDefaults(Config config) {
        return builder().config(config).registerDefaults().build();
    }

    /**
     * Create a new {@link Builder} instance.
     * @return a new {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * MediaSupport builder.
     */
    public static final class Builder implements io.helidon.common.Builder<MediaSupport> {

        private final MessageBodyReaderContext readerContext;
        private final MessageBodyWriterContext writerContext;
        private boolean serverErrorsIncludeStackTraces;

        Builder() {
            readerContext = MessageBodyReaderContext.create();
            writerContext = MessageBodyWriterContext.create();
        }

        /**
         * Configures this {@link Builder} from the supplied {@link Config}.
         * @param config a {@link Config}
         * @return this {@link Builder}
         */
        public Builder config(Config config) {
            config.get("server-errors-include-stack-traces").asBoolean().ifPresent(this::serverErrorsIncludeStackTraces);
            config.get("register-defaults").asBoolean().ifPresent(b -> {
                    if (b) {
                        registerDefaults();
                    }
                });
            return this;
        }

        /**
         * Ensures that {@link MediaSupport} instances {@linkplain
         * #build() built} by this {@link Builder} will include stack
         * trace information when server errors are returned.
         * @param serverErrorsIncludeStackTraces whether stack trace
         * information should be included
         * @return this {@link Builder}
         */
        public Builder serverErrorsIncludeStackTraces(boolean serverErrorsIncludeStackTraces) {
            this.serverErrorsIncludeStackTraces = serverErrorsIncludeStackTraces;
            return this;
        }

        /**
         * Register the default readers and writers.
         * <h3>Default readers</h3>
         * <ul>
         * <li>{@link StringBodyReader} - converts payload into
         * {@code String.class}</li>
         * <li>{@link InputStreamBodyReader} - converts payload into
         * {@code InputStream.class}</li>
         * </ul>
         * <h3>Default writers</h3>
         * <ul>
         * <li>{@link CharSequenceBodyWriter} - generates payload from
         * {@code CharSequence.class}</li>
         * <li>{@link ByteChannelBodyWriter} - generates payload from
         * {@code ReadableByteChannel.class}</li>
         * <li>{@link PathBodyWriter} - generates payload from
         * {@code Path.class}</li>
         * <li>{@link FileBodyWriter} - generates payload from
         * {@code File.class}</li>
         * <li>{@link ThrowableBodyWriter} - generates payload from
         * {@link Throwable Throwable.class}</li>
         * </ul>
         *
         * @return this {@link Builder}
         */
        public Builder registerDefaults() {
            // default readers
            readerContext
                    .registerReader(StringBodyReader.create())
                    .registerReader(InputStreamBodyReader.create());

            // default writers
            writerContext
                    .registerWriter(CharSequenceBodyWriter.create())
                    .registerWriter(ByteChannelBodyWriter.create())
                    .registerWriter(PathBodyWriter.create())
                    .registerWriter(FileBodyWriter.create())
                    .registerWriter(ThrowableBodyWriter.create(serverErrorsIncludeStackTraces));
            return this;
        }

        /**
         * Register a new reader.
         * @param reader reader to register
         * @return this builder instance
         */
        public Builder registerReader(MessageBodyReader<?> reader) {
            readerContext.registerReader(reader);
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
         * Register a new filter on the reader context (inbound payload).
         * @param filter filter to register
         * @return this builder instance
         */
        public Builder registerInboundFilter(MessageBodyFilter filter) {
            writerContext.registerFilter(filter);
            return this;
        }

        /**
         * Register a new writer.
         * @param writer writer to register
         * @return this builder instance
         */
        public Builder registerWriter(MessageBodyWriter<?> writer) {
            writerContext.registerWriter(writer);
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
         * Register a new filter on the writer context (outbound payload).
         * @param filter filter to register
         * @return this builder instance
         */
        public Builder registerOutboundFilter(MessageBodyFilter filter) {
            writerContext.registerFilter(filter);
            return this;
        }

        @Override
        public MediaSupport build() {
            return new MediaSupport(readerContext, writerContext);
        }
    }
}
