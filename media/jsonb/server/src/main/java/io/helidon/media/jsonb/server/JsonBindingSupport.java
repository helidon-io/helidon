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

package io.helidon.media.jsonb.server;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import io.helidon.common.http.MediaType;
import io.helidon.media.jsonb.common.JsonBinding;
import io.helidon.webserver.Handler;
import io.helidon.webserver.RequestHeaders;
import io.helidon.webserver.ResponseHeaders;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import static io.helidon.media.common.ContentTypeCharset.determineCharset;

/**
 * A {@link Service} and a {@link Handler} that provides <a
 * href="http://json-b.net/">JSON-B</a> support to Helidon.
 */
public final class JsonBindingSupport implements Service, Handler {

    private final BiFunction<? super ServerRequest, ? super ServerResponse, ? extends Jsonb> jsonbProvider;

    private JsonBindingSupport(final BiFunction<? super ServerRequest,
                                                ? super ServerResponse,
                                                ? extends Jsonb> jsonbProvider) {
        super();
        this.jsonbProvider = Objects.requireNonNull(jsonbProvider);
    }

    @Override
    public void update(final Routing.Rules routingRules) {
        routingRules.any(this);
    }

    @Override
    public void accept(final ServerRequest request, final ServerResponse response) {
        final Jsonb jsonb = this.jsonbProvider.apply(request, response);
        request.content()
            .registerReader(cls -> true,
                            JsonBinding.reader(jsonb));
        response.registerWriter(payload -> testOrSetJsonContentType(request.headers(), response.headers()),
                                JsonBinding.writer(jsonb, determineCharset(response.headers())));
        request.next();
    }

    /**
     * Tests the request {@code Accept} and response {@code Content-Type} headers to determine if JSON can be written.
     * <p>
     * If the response has no {@code Content-Type} header then it will be computed based on the client's {@code Accept} headers.
     *
     * @param requestHeaders  a client's request headers
     * @param responseHeaders the server's response headers
     * @return {@code true} if JSON can be written
     */
    private boolean testOrSetJsonContentType(RequestHeaders requestHeaders, ResponseHeaders responseHeaders) {
        Optional<MediaType> contentType = responseHeaders.contentType();
        if (contentType.isPresent()) {
            return MediaType.JSON_PREDICATE.test(contentType.get());
        } else {
            Optional<MediaType> computedType = computeJsonContentType(requestHeaders.acceptedTypes());
            if (computedType.isPresent()) {
                responseHeaders.contentType(computedType.get());
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Attempts to determine which JSON media type to use in the response's {@code Content-Type} header.
     * <p>
     * If the request specifies only non-JSON media types acceptable then no media type will be returned.
     *
     * @param acceptedTypes all media types acceptable in the response
     * @return an acceptable JSON media type to use for {@code Content-Type} if one was found
     */
    private static Optional<MediaType> computeJsonContentType(List<MediaType> acceptedTypes) {
        if (acceptedTypes.isEmpty()) {
            return Optional.of(MediaType.APPLICATION_JSON);
        } else {
            return acceptedTypes.stream()
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
                    .findFirst();
        }
    }

    /**
     * Creates a new {@link JsonBindingSupport}.
     *
     * @param jsonbProvider a {@link BiFunction} that returns a {@link
     * Jsonb} when given a {@link ServerRequest} and a {@link
     * ServerResponse}; must not be {@code null}
     *
     * @return a new {@link JsonBindingSupport}
     *
     * @exception NullPointerException if {@code jsonbProvider} is
     * {@code null}
     */
    public static JsonBindingSupport create(final BiFunction<? super ServerRequest,
                                                             ? super ServerResponse,
                                                             ? extends Jsonb> jsonbProvider) {
        return new JsonBindingSupport(jsonbProvider);
    }

    /**
     * Creates a new {@link JsonBindingSupport}.
     *
     * @param jsonb the Jsonb} to use; must not be {@code null}
     *
     * @return a new {@link JsonBindingSupport}
     *
     * @exception NullPointerException if {@code jsonb} is {@code
     * null}
     */
    public static JsonBindingSupport create(final Jsonb jsonb) {
        Objects.requireNonNull(jsonb);
        return create((req, res) -> jsonb);
    }

    /**
     * Creates a new {@link JsonBindingSupport}.
     *
     * @return a new {@link JsonBindingSupport}
     */
    public static JsonBindingSupport create() {
        final Jsonb jsonb = JsonbBuilder.create();
        return create(jsonb);
    }

}
