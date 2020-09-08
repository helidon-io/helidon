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

import java.io.File;
import java.io.InputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import io.helidon.common.http.FormParams;
import io.helidon.common.reactive.RetrySchema;
import io.helidon.config.Config;

/**
 * MediaSupport which registers default readers and writers to the contexts.
 */
public class DefaultMediaSupport implements MediaSupport {

    private final ByteChannelBodyWriter byteChannelBodyWriter;
    private final ThrowableBodyWriter throwableBodyWriter;

    private DefaultMediaSupport(Builder builder) {
        if (builder.schema == null) {
            byteChannelBodyWriter = ByteChannelBodyWriter.create();
        } else {
            byteChannelBodyWriter = ByteChannelBodyWriter.create(builder.schema);
        }
        throwableBodyWriter = ThrowableBodyWriter.create(builder.includeStackTraces);
    }

    /**
     * Creates new instance of {@link DefaultMediaSupport}.
     *
     * @return new service instance
     */
    public static DefaultMediaSupport create() {
        return builder().build();
    }

    /**
     * Return new {@link Builder} of the {@link DefaultMediaSupport}.
     *
     * @return default media support builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Return {@link String} reader instance.
     *
     * @return {@link String} reader
     */
    public static MessageBodyReader<String> stringReader() {
        return StringBodyReader.create();
    }

    /**
     * Return {@link InputStream} reader instance.
     *
     * @return {@link InputStream} reader
     */
    public static MessageBodyReader<InputStream> inputStreamReader() {
        return InputStreamBodyReader.create();
    }

    /**
     * Return {@link CharSequence} writer instance.
     *
     * @return {@link CharSequence} writer
     */
    public static MessageBodyWriter<CharSequence> charSequenceWriter() {
        return CharSequenceBodyWriter.create();
    }

    /**
     * Return {@link CharSequence} stream writer instance.
     *
     * @return {@link CharSequence} writer
     */
    public static MessageBodyStreamWriter<CharSequence> charSequenceStreamWriter() {
        return CharSequenceBodyStreamWriter.create();
    }

    /**
     * Create a new instance of {@link ReadableByteChannel} writer.
     *
     * @return {@link ReadableByteChannel} writer
     */
    public static MessageBodyWriter<ReadableByteChannel> byteChannelWriter() {
        return ByteChannelBodyWriter.create();
    }

    /**
     * Return new {@link ReadableByteChannel} writer instance with specific {@link RetrySchema}.
     *
     * @param schema retry schema
     * @return {@link ReadableByteChannel} writer
     */
    public static MessageBodyWriter<ReadableByteChannel> byteChannelWriter(RetrySchema schema) {
        return ByteChannelBodyWriter.create(schema);
    }

    /**
     * Return {@link Path} writer instance.
     *
     * @return {@link Path} writer
     */
    public static MessageBodyWriter<Path> pathWriter() {
        return PathBodyWriter.create();
    }

    /**
     * Return {@link File} writer instance.
     *
     * @return {@link File} writer
     */
    public static MessageBodyWriter<File> fileWriter() {
        return FileBodyWriter.create();
    }

    /**
     * Return {@link FormParams} writer instance.
     *
     * @return {@link FormParams} writer
     */
    public static MessageBodyWriter<FormParams> formParamWriter() {
        return FormParamsBodyWriter.create();
    }

    /**
     * Return {@link FormParams} reader instance.
     *
     * @return {@link FormParams} reader
     */
    public static MessageBodyReader<FormParams> formParamReader() {
        return FormParamsBodyReader.create();
    }

    /**
     * Return {@link Throwable} writer instance.
     *
     * @param includeStackTraces whether stack traces are to be written
     * @return {@link Throwable} writer
     */
    public static MessageBodyWriter<Throwable> throwableWriter(boolean includeStackTraces) {
        return ThrowableBodyWriter.create(includeStackTraces);
    }

    @Override
    public Collection<MessageBodyReader<?>> readers() {
        return List.of(stringReader(),
                       inputStreamReader(),
                       formParamReader());
    }

    @Override
    public Collection<MessageBodyWriter<?>> writers() {
        return List.of(charSequenceWriter(),
                       byteChannelBodyWriter,
                       pathWriter(),
                       fileWriter(),
                       throwableBodyWriter,
                       formParamWriter());
    }

    @Override
    public Collection<MessageBodyStreamWriter<?>> streamWriters() {
        return List.of(charSequenceStreamWriter());
    }

    /**
     * Default media support builder.
     */
    public static class Builder implements io.helidon.common.Builder<DefaultMediaSupport> {

        private boolean includeStackTraces = false;
        private RetrySchema schema;

        private Builder() {
        }

        @Override
        public DefaultMediaSupport build() {
            return new DefaultMediaSupport(this);
        }

        /**
         * Whether stack traces should be included in response.
         *
         * @param includeStackTraces include stack trace
         * @return updated builder instance
         */
        public Builder includeStackTraces(boolean includeStackTraces) {
            this.includeStackTraces = includeStackTraces;
            return this;
        }

        /**
         * Set specific {@link RetrySchema} to the byte channel.
         *
         * @param schema retry schema
         * @return updated builder instance
         */
        public Builder byteChannelRetrySchema(RetrySchema schema) {
            this.schema = Objects.requireNonNull(schema);
            return this;
        }

        /**
         * Configures this {@link DefaultMediaSupport.Builder} from the supplied {@link Config}.
         * <table class="config">
         * <caption>Optional configuration parameters</caption>
         * <tr>
         *     <th>key</th>
         *     <th>description</th>
         * </tr>
         * <tr>
         *     <td>include-stack-traces</td>
         *     <td>Whether stack traces should be included in response</td>
         * </tr>
         * </table>
         *
         * @param config media support config
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("include-stack-traces").asBoolean().ifPresent(this::includeStackTraces);
            return this;
        }
    }
}
