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

package io.helidon.http.media.multipart;

import java.lang.System.Logger.Level;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.common.config.Config;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.MediaSupport;

/**
 * Media support implementation for support of multipart.
 */
@SuppressWarnings("unchecked")
public class MultiPartSupport implements MediaSupport {
    /**
     * The default boundary used for encoding multipart messages.
     */
    public static final String DEFAULT_BOUNDARY = "[^._.^]==>boundary<==[^._.^]";

    private static final System.Logger LOGGER = System.getLogger(MultiPartSupport.class.getName());
    private static final HttpMediaType DEFAULT_HTTP_MEDIA_TYPE = HttpMediaType.create(MediaTypes.MULTIPART_FORM_DATA)
            .withParameter("boundary", DEFAULT_BOUNDARY);

    private final String name;
    private MediaContext context;

    private MultiPartSupport(String name) {
        this.name = Objects.requireNonNull(name);
    }

    /**
     * Creates a new {@link MultiPartSupport}.
     *
     * @param config must not be {@code null}
     * @return a new {@link MultiPartSupport}
     */
    public static MediaSupport create(Config config) {
        return create(config, "multi-part");
    }

    /**
     * Creates a new named {@link MultiPartSupport}.
     *
     * @param config must not be {@code null}
     * @param name name of the multi-part support
     * @return a new {@link MultiPartSupport}
     */
    public static MediaSupport create(Config config, String name) {
        return new MultiPartSupport(name);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return "multi-part";
    }

    @Override
    public void init(MediaContext context) {
        this.context = context;
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type, Headers requestHeaders) {
        if (unsupportedRead(type)) {
            return ReaderResponse.unsupported();
        }

        Optional<HttpMediaType> httpMediaType = requestHeaders.contentType();
        if (httpMediaType.isEmpty()) {
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, "Requested multipart type, yet the request does not contain Content-Type header");
            }
            return ReaderResponse.unsupported();
        }
        HttpMediaType mediaType = httpMediaType.get();
        if (!mediaType.test(MediaTypes.MULTIPART_FORM_DATA)) {
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, "Requested multipart type, yet the request is not multipart/form-data, but "
                        + mediaType);
            }
            return ReaderResponse.unsupported();
        }
        String boundary = mediaType.parameters().get("boundary");
        if (boundary == null) {
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, "Requested multipart type, yet the request does not contain boundary parameter "
                        + mediaType);
            }
            return ReaderResponse.unsupported();
        }
        return new ReaderResponse<>(SupportLevel.SUPPORTED, () -> (EntityReader<T>) new MultiPartReader(context,
                                                                                                        boundary));
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type,
                                        Headers requestHeaders,
                                        WritableHeaders<?> responseHeaders) {
        if (unsupportedWrite(type)) {
            return WriterResponse.unsupported();
        }

        boolean compatible = false; // by default supported
        Optional<HttpMediaType> httpMediaType = responseHeaders.contentType();
        String boundary;
        HttpMediaType usedType;
        if (httpMediaType.isPresent()) {
            HttpMediaType configuredType = httpMediaType.get();
            if (!configuredType.test(MediaTypes.MULTIPART_FORM_DATA)) {
                compatible = true;
            }
            usedType = configuredType;
            String configuredBoundary = configuredType.parameters().get("boundary");
            if (configuredBoundary == null) {
                boundary = DEFAULT_BOUNDARY;
            } else {
                boundary = configuredBoundary;
            }
        } else {
            boundary = DEFAULT_BOUNDARY;
            usedType = DEFAULT_HTTP_MEDIA_TYPE;
        }
        if (!requestHeaders.isAccepted(MediaTypes.MULTIPART_FORM_DATA)) {
            compatible = true;
        }
        SupportLevel level = compatible ? SupportLevel.COMPATIBLE : SupportLevel.SUPPORTED;
        return new WriterResponse<>(level, () -> (EntityWriter<T>) new MultiPartWriter(context, usedType, boundary));
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type,
                                        Headers requestHeaders,
                                        Headers responseHeaders) {
        if (unsupportedRead(type)) {
            return ReaderResponse.unsupported();
        }

        Optional<HttpMediaType> httpMediaType = responseHeaders.contentType();
        if (httpMediaType.isEmpty()) {
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, "Got multipart type, yet the request does not contain Content-Type header");
            }
            return ReaderResponse.unsupported();
        }
        HttpMediaType mediaType = httpMediaType.get();
        if (!mediaType.test(MediaTypes.MULTIPART_FORM_DATA)) {
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, "Got multipart type, yet the request is not multipart/form-data, but "
                        + mediaType);
            }
            return ReaderResponse.unsupported();
        }
        String boundary = mediaType.parameters().get("boundary");
        if (boundary == null) {
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, "Got multipart type, yet the request does not contain boundary parameter "
                        + mediaType);
            }
            return ReaderResponse.unsupported();
        }
        return new ReaderResponse<>(SupportLevel.SUPPORTED, () -> (EntityReader<T>) new MultiPartReader(context,
                                                                                                        boundary));
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
        if (unsupportedWrite(type)) {
            return WriterResponse.unsupported();
        }

        boolean compatible = false; // by default supported
        Optional<HttpMediaType> httpMediaType = requestHeaders.contentType();
        String usedBoundary;
        HttpMediaType usedType;
        if (httpMediaType.isPresent()) {
            HttpMediaType configuredType = httpMediaType.get();
            if (!configuredType.test(MediaTypes.MULTIPART_FORM_DATA)) {
                compatible = true;
            }
            usedType = configuredType;
            String configuredBoundary = configuredType.parameters().get("boundary");
            if (configuredBoundary == null) {
                usedBoundary = DEFAULT_BOUNDARY;
            } else {
                usedBoundary = configuredBoundary;
            }
        } else {
            usedBoundary = DEFAULT_BOUNDARY;
            usedType = DEFAULT_HTTP_MEDIA_TYPE;
        }

        if (!requestHeaders.isAccepted(MediaTypes.MULTIPART_FORM_DATA)) {
            compatible = true;
        }
        SupportLevel level = compatible ? SupportLevel.COMPATIBLE : SupportLevel.SUPPORTED;

        return new WriterResponse<>(level, () -> (EntityWriter<T>) new MultiPartWriter(context, usedType, usedBoundary));
    }

    private static boolean unsupportedWrite(GenericType<?> type) {
        // write is supported for any subtype of writable multi-part
        if (type.equals(WriteableMultiPart.GENERIC_TYPE)) {
            return false;
        }
        return !WriteableMultiPart.GENERIC_TYPE.rawType().isAssignableFrom(type.rawType());
    }

    private static boolean unsupportedRead(GenericType<?> type) {
        // read is only support for the exact same type
        return !type.equals(MultiPart.GENERIC_TYPE);
    }
}
