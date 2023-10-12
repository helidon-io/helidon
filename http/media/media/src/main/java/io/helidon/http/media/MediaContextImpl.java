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

package io.helidon.http.media;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.GenericType;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.MediaSupport.ReaderResponse;
import io.helidon.http.media.MediaSupport.WriterResponse;

import static io.helidon.http.media.MediaSupport.SupportLevel.COMPATIBLE;
import static io.helidon.http.media.MediaSupport.SupportLevel.SUPPORTED;

@SuppressWarnings("unchecked")
class MediaContextImpl implements MediaContext {
    private static final System.Logger LOGGER = System.getLogger(MediaContextImpl.class.getName());
    private static final ConcurrentHashMap<GenericType<?>, AtomicBoolean> LOGGED_READERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<GenericType<?>, AtomicBoolean> LOGGED_WRITERS = new ConcurrentHashMap<>();

    private final List<MediaSupport> supports;
    private final MediaContext fallback;
    private final MediaContextConfig prototype;

    MediaContextImpl(MediaContextConfig prototype) {
        this.supports = prototype.mediaSupports();
        this.supports.forEach(it -> it.init(this));
        this.fallback = prototype.fallback().orElse(null);
        this.prototype = prototype;
    }

    @Override
    public <T> EntityReader<T> reader(GenericType<T> type, Headers headers) {
        ReaderResponse<T> compatible = null;
        for (MediaSupport support : supports) {
            ReaderResponse<T> response = support.reader(type, headers);
            if (response.support() == SUPPORTED) {
                return entityReader(response);
            } else if (response.support() == COMPATIBLE) {
                compatible = compatible == null ? response : compatible;
            }
        }
        if (compatible == null) {
            if (fallback == null) {
                return FailingReader.instance();
            } else {
                return fallback.reader(type, headers);
            }
        }
        return entityReader(compatible);
    }

    @Override
    public <T> EntityWriter<T> writer(GenericType<T> type,
                                      Headers requestHeaders,
                                      WritableHeaders<?> responseHeaders) {
        WriterResponse<T> compatible = null;
        for (MediaSupport support : supports) {
            WriterResponse<T> response = support.writer(type, requestHeaders, responseHeaders);
            if (response.support() == SUPPORTED) {
                return entityWriter(response);
            }
            if (response.support() == COMPATIBLE) {
                compatible = compatible == null ? response : compatible;
            }
        }

        if (compatible == null) {
            if (fallback == null) {
                return FailingWriter.instance();
            } else {
                return fallback.writer(type, requestHeaders, responseHeaders);
            }
        }
        return entityWriter(compatible);
    }

    @Override
    public <T> EntityReader<T> reader(GenericType<T> type,
                                      Headers requestHeaders,
                                      Headers responseHeaders) {

        ReaderResponse<T> compatible = null;
        for (MediaSupport support : supports) {
            ReaderResponse<T> response = support.reader(type, requestHeaders, responseHeaders);
            if (response.support() == SUPPORTED) {
                return entityReader(response);
            }
            if (response.support() == COMPATIBLE) {
                compatible = compatible == null ? response : compatible;
            }
        }
        if (compatible == null) {
            if (fallback == null) {
                return FailingReader.instance();
            } else {
                return fallback.reader(type, requestHeaders, responseHeaders);
            }
        }
        return entityReader(compatible);
    }

    @Override
    public <T> EntityWriter<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
        WriterResponse<T> compatible = null;
        for (MediaSupport support : supports) {
            WriterResponse<T> response = support.writer(type, requestHeaders);
            if (response.support() == SUPPORTED) {
                return entityWriter(response);
            }
            if (response.support() == COMPATIBLE) {
                compatible = compatible == null ? response : compatible;
            }
        }

        if (compatible == null) {
            if (fallback == null) {
                return FailingWriter.instance();
            } else {
                return fallback.writer(type, requestHeaders);
            }
        }
        return entityWriter(compatible);
    }

    @Override
    public MediaContextConfig prototype() {
        return prototype;
    }

    private <T> EntityWriter<T> entityWriter(WriterResponse<T> response) {
        return new CloseStreamWriter(response.supplier().get());
    }

    private <T> EntityReader<T> entityReader(ReaderResponse<T> response) {
        return new CloseStreamReader(response.supplier().get());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class FailingWriter implements EntityWriter {
        private static final FailingWriter INSTANCE = new FailingWriter();

        static <T> EntityWriter<T> instance() {
            return INSTANCE;
        }

        @Override
        public void write(GenericType type,
                          Object object,
                          OutputStream outputStream,
                          Headers requestHeaders,
                          WritableHeaders responseHeaders) {
            if (LOGGED_WRITERS.computeIfAbsent(type, it -> new AtomicBoolean()).compareAndSet(false, true)) {
                LOGGER.log(System.Logger.Level.WARNING, "There is no media writer configured for " + type);
            }

            throw new UnsupportedTypeException("No server response media writer for " + type + " configured");
        }

        @Override
        public void write(GenericType type,
                          Object object,
                          OutputStream outputStream,
                          WritableHeaders headers) {

            if (LOGGED_WRITERS.computeIfAbsent(type, it -> new AtomicBoolean()).compareAndSet(false, true)) {
                LOGGER.log(System.Logger.Level.WARNING, "There is no media writer configured for " + type);
            }

            throw new UnsupportedTypeException("No client request media writer for " + type + " configured");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class FailingReader implements EntityReader {
        private static final FailingReader INSTANCE = new FailingReader();

        static <T> EntityReader<T> instance() {
            return INSTANCE;
        }

        @Override
        public Object read(GenericType type, InputStream stream, Headers headers) {
            if (LOGGED_READERS.computeIfAbsent(type, it -> new AtomicBoolean()).compareAndSet(false, true)) {
                LOGGER.log(System.Logger.Level.WARNING, "There is no media reader configured for " + type);
            }
            throw new UnsupportedTypeException("No server request media support for " + type + " configured");
        }

        @Override
        public Object read(GenericType type,
                           InputStream stream,
                           Headers requestHeaders,
                           Headers responseHeaders) {
            if (LOGGED_READERS.computeIfAbsent(type, it -> new AtomicBoolean()).compareAndSet(false, true)) {
                LOGGER.log(System.Logger.Level.WARNING, "There is no media reader configured for " + type);
            }
            throw new UnsupportedTypeException("No client response media support for " + type + " configured");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class CloseStreamReader implements EntityReader {
        private final EntityReader delegate;

        CloseStreamReader(EntityReader delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object read(GenericType type, InputStream stream, Headers headers) {
            try (stream) {
                return delegate.read(type, stream, headers);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read server request", e);
            }
        }

        @Override
        public Object read(GenericType type,
                           InputStream stream,
                           Headers requestHeaders,
                           Headers responseHeaders) {
            try (stream) {
                return delegate.read(type, stream, requestHeaders, responseHeaders);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read client response", e);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class CloseStreamWriter implements EntityWriter {
        private final EntityWriter delegate;

        CloseStreamWriter(EntityWriter delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean supportsInstanceWriter() {
            return delegate.supportsInstanceWriter();
        }

        @Override
        public InstanceWriter instanceWriter(GenericType type, Object object, WritableHeaders requestHeaders) {
            return delegate.instanceWriter(type, object, requestHeaders);
        }

        @Override
        public InstanceWriter instanceWriter(GenericType type,
                                             Object object,
                                             Headers requestHeaders,
                                             WritableHeaders responseHeaders) {
            return delegate.instanceWriter(type, object, requestHeaders, responseHeaders);
        }

        @Override
        public void write(GenericType type,
                          Object object,
                          OutputStream outputStream,
                          Headers requestHeaders,
                          WritableHeaders responseHeaders) {
            delegate.write(type, object, outputStream, requestHeaders, responseHeaders);
        }

        @Override
        public void write(GenericType type,
                          Object object,
                          OutputStream outputStream,
                          WritableHeaders headers) {
            delegate.write(type,
                           object,
                           outputStream,
                           headers);
        }
    }
}
