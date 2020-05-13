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
package io.helidon.media.multipart.common;

import io.helidon.media.common.MediaSupport;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.media.common.MessageBodyStreamReader;
import io.helidon.media.common.MessageBodyStreamWriter;
import io.helidon.media.common.MessageBodyWriter;
import java.util.Collection;
import java.util.List;

/**
 * Multipart media support.
 */
public final class MultiPartSupport implements MediaSupport {

    private final Collection<MessageBodyReader<?>> readers;
    private final Collection<MessageBodyWriter<?>> writers;
    private final Collection<MessageBodyStreamReader<?>> streamReaders;
    private final Collection<MessageBodyStreamWriter<?>> streamWriters;

    /**
     * Forces the use of {@link #create()}.
     */
    private MultiPartSupport(){
        readers = List.of(MultiPartBodyReader.create());
        writers = List.of(MultiPartBodyWriter.create());
        streamReaders = List.of(BodyPartBodyStreamReader.create());
        streamWriters = List.of(BodyPartBodyStreamWriter.create());
    }

    @Override
    public Collection<MessageBodyReader<?>> readers() {
        return readers;
    }

    @Override
    public Collection<MessageBodyWriter<?>> writers() {
        return writers;
    }

    @Override
    public Collection<MessageBodyStreamReader<?>> streamReaders() {
        return streamReaders;
    }

    @Override
    public Collection<MessageBodyStreamWriter<?>> streamWriters() {
        return streamWriters;
    }

    /**
     * Create a new instance of {@link MultiPartSupport}.
     * @return MultiPartSupport
     */
    public static MultiPartSupport create(){
        return new MultiPartSupport();
    }
}
