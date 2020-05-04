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

import io.helidon.media.common.spi.MediaService;

/**
 * MediaService which registers default readers and writers to the contexts.
 */
public class DefaultMediaService implements MediaService {

    private final boolean includeStackTraces;

    private DefaultMediaService(boolean includeStackTraces) {
        this.includeStackTraces = includeStackTraces;
    }

    /**
     * Creates new instance of {@link DefaultMediaService}.
     *
     * @param includeStackTraces include stack traces
     * @return new service instance
     */
    public static DefaultMediaService create(boolean includeStackTraces) {
        return new DefaultMediaService(includeStackTraces);
    }

    @Override
    public void register(MessageBodyReaderContext readerContext, MessageBodyWriterContext writerContext) {
        readerContext
                .registerReader(StringBodyReader.create())
                .registerReader(InputStreamBodyReader.create());

        writerContext
                .registerWriter(CharSequenceBodyWriter.create())
                .registerWriter(ByteChannelBodyWriter.create())
                .registerWriter(PathBodyWriter.create())
                .registerWriter(FileBodyWriter.create())
                .registerWriter(ThrowableBodyWriter.create(includeStackTraces));
    }
}
