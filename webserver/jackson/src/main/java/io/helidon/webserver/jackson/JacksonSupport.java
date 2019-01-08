/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.webserver.jackson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Objects;
import java.util.function.BiFunction;

import io.helidon.common.http.MediaType;
import io.helidon.webserver.ContentReaders;
import io.helidon.webserver.ContentWriters;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A {@link Service} and a {@link Handler} that provides Jackson
 * support to Helidon.
 */
public final class JacksonSupport implements Service, Handler {

    private final BiFunction<? super ServerRequest, ? super ServerResponse, ? extends ObjectMapper> objectMapperProvider;

    /**
     * Creates a new {@link JacksonSupport}.
     *
     * @param objectMapperProvider a {@link BiFunction} that returns
     * an {@link ObjectMapper} when given a {@link ServerRequest} and
     * a {@link ServerResponse}; must not be {@code null}
     *
     * @exception NullPointerException if {@code objectMapperProvider}
     * is {@code null}
     */
    @SuppressWarnings("checkstyle:linelength")
    public JacksonSupport(final BiFunction<? super ServerRequest, ? super ServerResponse, ? extends ObjectMapper> objectMapperProvider) {
        super();
        this.objectMapperProvider = Objects.requireNonNull(objectMapperProvider);
    }

    @Override
    public void update(final Routing.Rules routingRules) {
        routingRules.any(this);
    }

    @Override
    public void accept(final ServerRequest request, final ServerResponse response) {
        final ObjectMapper objectMapper = this.objectMapperProvider.apply(request, response);
        request.content()
            .registerReader(cls -> objectMapper.canDeserialize(objectMapper.constructType(cls)),
                            (publisher, cls) -> ContentReaders.byteArrayReader()
                                .apply(publisher)
                                .thenApply(bytes -> {
                                        try {
                                            return objectMapper.readValue(bytes, cls);
                                        } catch (final IOException wrapMe) {
                                            throw new RuntimeException(wrapMe.getMessage(), wrapMe);
                                        }
                                    }));
        response.registerWriter(payload -> objectMapper.canSerialize(payload.getClass()) && this.wantsJson(request, response),
                                payload -> {
                                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                    try {
                                        objectMapper.writeValue(baos, payload);
                                    } catch (final IOException wrapMe) {
                                        throw new RuntimeException(wrapMe.getMessage(), wrapMe);
                                    }
                                    return ContentWriters.byteArrayWriter(false)
                                        .apply(baos.toByteArray());
                                });
        request.next();
    }

    private static boolean wantsJson(final ServerRequest request, final ServerResponse response) {
        final boolean returnValue;
        final MediaType outgoingMediaType = response.headers().contentType().orElse(null);
        if (outgoingMediaType == null) {
            final MediaType preferredType;
            final Collection<? extends MediaType> acceptedTypes = request.headers().acceptedTypes();
            if (acceptedTypes == null || acceptedTypes.isEmpty()) {
                preferredType = MediaType.APPLICATION_JSON;
            } else {
                preferredType = acceptedTypes
                    .stream()
                    .map(type -> {
                            if (type.test(MediaType.APPLICATION_JSON)) {
                                return MediaType.APPLICATION_JSON;
                            } else if (type.hasSuffix("json")) {
                                return MediaType.create(type.type(), type.subtype());
                            } else {
                                return null;
                            }
                        })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            }
            if (preferredType == null) {
                returnValue = false;
            } else {
                response.headers().contentType(preferredType);
                returnValue = true;
            }
        } else {
            returnValue = MediaType.JSON_PREDICATE.test(outgoingMediaType);
        }
        return returnValue;
    }

}
