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

import java.util.Objects;
import java.util.function.BiFunction;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import io.helidon.media.jsonb.common.JsonBinding;
import io.helidon.webserver.Handler;
import io.helidon.webserver.JsonService;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import static io.helidon.media.common.ContentTypeCharset.determineCharset;

/**
 * A {@link Service} and a {@link Handler} that provides <a
 * href="http://json-b.net/">JSON-B</a> support to Helidon.
 */
public final class JsonBindingSupport extends JsonService {

    private final BiFunction<? super ServerRequest, ? super ServerResponse, ? extends Jsonb> jsonbProvider;

    private JsonBindingSupport(final BiFunction<? super ServerRequest,
                                                ? super ServerResponse,
                                                ? extends Jsonb> jsonbProvider) {
        super();
        this.jsonbProvider = Objects.requireNonNull(jsonbProvider);
    }

    @Override
    public void accept(final ServerRequest request, final ServerResponse response) {
        final Jsonb jsonb = this.jsonbProvider.apply(request, response);
        // Don't register reader/writer if content is a CharSequence (String) (see #645)
        request.content()
            .registerReader(cls -> !CharSequence.class.isAssignableFrom(cls),
                            JsonBinding.reader(jsonb));
        response.registerWriter(payload -> !(payload instanceof CharSequence) && acceptsJson(request, response),
                                JsonBinding.writer(jsonb, determineCharset(response.headers())));
        request.next();
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
