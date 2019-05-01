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
package io.helidon.media.jackson.server;

import java.util.Collection;
import java.util.Objects;
import java.util.function.BiFunction;

import io.helidon.common.http.MediaType;
import io.helidon.media.jackson.common.JacksonProcessing;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import static io.helidon.media.common.ContentTypeCharset.determineCharset;

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
    private JacksonSupport(final BiFunction<? super ServerRequest,
                                           ? super ServerResponse,
                                           ? extends ObjectMapper> objectMapperProvider) {
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
                            JacksonProcessing.reader(objectMapper));
        response.registerWriter(payload -> objectMapper.canSerialize(payload.getClass()) && this.wantsJson(request, response),
                                JacksonProcessing.writer(objectMapper, determineCharset(response.headers())));
        request.next();
    }

    /**
     * Creates a new {@link JacksonSupport}.
     *
     * @return a new {@link JacksonSupport}
     */
    public static JacksonSupport create() {
        final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new ParameterNamesModule())
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule());
        return create((req, res) -> mapper);
    }

    /**
     * Creates a new {@link JacksonSupport}.
     *
     * @param objectMapperProvider a {@link BiFunction} that returns
     * an {@link ObjectMapper} when given a {@link ServerRequest} and
     * a {@link ServerResponse}; must not be {@code null}
     *
     * @return a new {@link JacksonSupport}
     *
     * @exception NullPointerException if {@code objectMapperProvider}
     * is {@code null}
     */
    public static JacksonSupport create(final BiFunction<? super ServerRequest,
                                                         ? super ServerResponse,
                                                         ? extends ObjectMapper> objectMapperProvider) {
        return new JacksonSupport(objectMapperProvider);
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
