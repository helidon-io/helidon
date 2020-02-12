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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.concurrent.Flow.Publisher;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.mapper.Mapper;
import io.helidon.common.reactive.Single;

/**
 * Message body writer for {@link Throwable}.
 */
public final class ThrowableBodyWriter implements MessageBodyWriter<Throwable> {

    private final boolean writeStackTrace;

    private ThrowableBodyWriter() {
        this(false);
    }

    private ThrowableBodyWriter(boolean writeStackTrace) {
        super();
        this.writeStackTrace = writeStackTrace;
    }

    @Override
    public boolean accept(GenericType<?> type, MessageBodyWriterContext context) {
        return false;
    }

    @Override
    public Publisher<DataChunk> write(Single<Throwable> content,
                                      GenericType<? extends Throwable> type,
                                      MessageBodyWriterContext context) {
        return content.mapMany(new ThrowableToChunks(context));
    }

    /**
     * Creates a new {@link ThrowableBodyWriter}.
     * @return a new {@link ThrowableBodyWriter}; never {@code null}
     * @see #create(boolean)
     */
    public static ThrowableBodyWriter create() {
        return create(false);
    }

    /**
     * Creates a new {@link ThrowableBodyWriter}.
     * @param writeStackTrace whether stack traces are to be written
     * @return a new {@link ThrowableBodyWriter}; never {@code null}
     */
    public static ThrowableBodyWriter create(boolean writeStackTrace) {
        return new ThrowableBodyWriter(writeStackTrace);
    }

    private static final class ThrowableToChunks implements Mapper<Throwable, Publisher<DataChunk>> {

        private final MessageBodyWriterContext context;

        ThrowableToChunks(MessageBodyWriterContext context) {
            super();
            this.context = context;
        }

        @Override
        public Publisher<DataChunk> map(Throwable throwable) {
            context.contentType(MediaType.TEXT_PLAIN);
            final Publisher<DataChunk> returnValue;
            if (throwable == null) {
                context.contentLength(0);
                returnValue = Single.<DataChunk>empty();
            } else {
                final StringWriter stringWriter = new StringWriter();
                final PrintWriter printWriter = new PrintWriter(stringWriter);
                String stackTraceString = null;
                try {
                    throwable.printStackTrace(printWriter);
                    stackTraceString = stringWriter.toString();
                } finally {
                    printWriter.close();
                }
                assert stackTraceString != null;
                if (stackTraceString.isEmpty()) {
                    context.contentLength(0);
                    returnValue = Single.<DataChunk>empty();
                } else {
                    final Charset charset = context.charset();
                    context.contentLength(stackTraceString.getBytes(charset).length);
                    returnValue = ContentWriters.writeCharSequence(stackTraceString, charset);
                }
            }
            return returnValue;
        }
    }

}
