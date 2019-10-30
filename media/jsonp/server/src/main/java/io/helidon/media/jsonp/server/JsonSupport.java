/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.media.jsonp.server;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.function.Function;

import javax.json.JsonReader;
import javax.json.JsonStructure;
import javax.json.JsonWriter;

import io.helidon.common.http.Content;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Reader;
import io.helidon.common.reactive.Flow;
import io.helidon.media.jsonp.common.JsonProcessing;
import io.helidon.webserver.Handler;
import io.helidon.webserver.JsonService;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.WebServer;

import static io.helidon.media.common.ContentTypeCharset.determineCharset;

/**
 * It provides contains JSON-P ({@code javax.json}) support for {@link WebServer WebServer}'s
 * {@link Routing}. It is intended to provide readers and writers for {@code javax.json} objects such
 * as {@link javax.json.JsonObject JsonObject} or {@link javax.json.JsonArray JsonArray}. If registered on the
 * {@code Web Server} {@link Routing}, then all {@link Handler Handlers} can use
 * {@code ServerRequest.}{@link ServerRequest#content() content()}{@code .}
 * {@link Content#as(java.lang.Class) as(...)} and
 * {@code ServerResponse.}{@link ServerResponse#send(Object) send()}
 * with {@link JsonStructure JSON} objects.
 *
 * <h3>Get Instance</h3>
 * Use factory methods {@link #create()} or {@link #create(io.helidon.media.jsonp.common.JsonProcessing)} to acquire an instance.
 *
 * <h3>Usage with Routing</h3>
 * {@code JsonSupport} should be registered on the routing before any business logic handlers.
 * <pre>{@code
 * Routing.builder()
 *        .register(JsonSupport.create())
 *        .etc.... // Business logic related handlers
 * }</pre>
 * Instance behaves also as a routing filter. It means that it can be registered on any routing rule (for example HTTP method)
 * and then it can be used in following handlers with compatible rules.
 * <pre>{@code
 * // Register JsonSupport only for POST of 'foo'
 * Routing.builder()
 *        .post("/foo/{}", JsonSupport.create())
 *        .post("/foo/bar", ...) // It can use JSON structures
 *        .get("/foo/bar", ...);  // It can NOT use JSON structures
 * }</pre>
 *
 * @see Routing
 * @see JsonStructure
 * @see JsonReader
 * @see JsonWriter
 */
public final class JsonSupport extends JsonService {
    private static final JsonSupport INSTANCE = new JsonSupport(JsonProcessing.create());

    private final JsonProcessing processingSupport;

    private JsonSupport(JsonProcessing processing) {
        this.processingSupport = processing;
    }

    /**
     * It registers reader and writer for {@link JsonSupport} on {@link ServerRequest}/{@link ServerResponse} on provided
     * routing criteria.
     * <p>
     * This method is called from {@link Routing} during build process. The user should register whole class
     * ot the routing criteria. For example: {@code Routing.builder().}
     * {@link Routing.Builder#post(String, Handler...) post}{@code ("/foo", JsonSupport.create())}.
     * <p>
     * It calls {@code ServerRequest.}{@link ServerRequest#next() next()} method to invoke following handlers with
     * particular business logic.
     *
     * @param request  a server request
     * @param response a server response
     * @see Routing
     */
    @Override
    public void accept(ServerRequest request, ServerResponse response) {
        // Reader
        request.content()
                .registerReader(JsonStructure.class::isAssignableFrom, (publisher, type) -> {
                    Charset charset = determineCharset(request.headers());
                    return reader(charset).apply(publisher, type);
                });
        // Writer
        response.registerWriter(json -> (json instanceof JsonStructure) && acceptsJson(request, response),
                                json -> {
                                    Charset charset = determineCharset(response.headers());
                                    return writer(charset).apply((JsonStructure) json);
                                });
        request.next();
    }

    /**
     * Returns a function (reader) converting {@link Flow.Publisher Publisher} of {@link ByteBuffer}s to
     * a JSON-P object.
     * <p>
     * It is intended for derivation of others, more specific readers.
     *
     * @param charset a charset to use or {@code null} for default charset
     * @return the byte array content reader that transforms a publisher of byte buffers to a completion stage that
     *         might end exceptionally with a {@link IllegalArgumentException} in case of I/O error or
     *         a {@link javax.json.JsonException}
     */
    public Reader<JsonStructure> reader(Charset charset) {
        return processingSupport.reader(charset);
    }

    /**
     * Returns a function (reader) converting {@link Flow.Publisher Publisher} of {@link ByteBuffer}s to
     * a JSON-P object.
     * <p>
     * It is intended for derivation of others, more specific readers.
     *
     * @return the byte array content reader that transforms a publisher of byte buffers to a completion stage that
     *         might end exceptionally with a {@link IllegalArgumentException} in case of I/O error or
     *         a {@link javax.json.JsonException}
     */
    public Reader<JsonStructure> reader() {
        return processingSupport.reader();
    }

    /**
     * Returns a function (writer) converting {@link JsonStructure} to the {@link Flow.Publisher Publisher}
     * of {@link DataChunk}s.
     *
     * @param charset a charset to use or {@code null} for default charset
     * @return created function
     */
    public Function<JsonStructure, Flow.Publisher<DataChunk>> writer(Charset charset) {
        return processingSupport.writer(charset);
    }

    /**
     * Returns a function (writer) converting {@link JsonStructure} to the {@link Flow.Publisher Publisher}
     * of {@link DataChunk}s.
     *
     * @return created function
     */
    public Function<JsonStructure, Flow.Publisher<DataChunk>> writer() {
        return processingSupport.writer();
    }

    /**
     * Returns a singleton instance of JsonSupport with default configuration.
     * <p>
     * Use {@link #create(io.helidon.media.jsonp.common.JsonProcessing)} method to create a new instance with specific
     * configuration.
     *
     * @return a singleton instance with default configuration
     */
    public static JsonSupport create() {
        return INSTANCE;
    }

    /**
     * Create a JsonSupport with customized processing configuration.
     *
     * @param processing processing to get JSON-P readers and writers
     * @return json support to register with web server
     * @see io.helidon.media.jsonp.common.JsonProcessing#builder()
     */
    public static JsonSupport create(JsonProcessing processing) {
        if (null == processing) {
            throw new NullPointerException("JsonProcessing argument must not be null.");
        }
        return new JsonSupport(processing);
    }
}
