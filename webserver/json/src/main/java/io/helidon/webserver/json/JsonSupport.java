/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.json;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import javax.json.Json;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonStructure;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;

import io.helidon.common.http.Content;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;
import io.helidon.common.http.Reader;
import io.helidon.common.reactive.Flow;
import io.helidon.webserver.ContentReaders;
import io.helidon.webserver.ContentWriters;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import io.helidon.webserver.WebServer;


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
 * Use factory methods {@link #get()} or {@link #create(Map)} to acquire an instance.
 *
 * <h3>Usage with Routing</h3>
 * {@code JsonSupport} should be registered on the routing before any business logic handlers.
 * <pre>{@code
 * Routing.builder()
 *        .register(JsonSupport.get())
 *        .etc.... // Business logic related handlers
 * }</pre>
 * Instance behaves also as a routing filter. It means that it can be registered on any routing rule (for example HTTP method)
 * and then it can be used in following handlers with compatible rules.
 * <pre>{@code
 * // Register JsonSupport only for POST of 'foo'
 * Routing.builder()
 *        .post("/foo/{}", JsonSupport.get())
 *        .post("/foo/bar", ...) // It can use JSON structures
 *        .get("/foo/bar", ...);  // It can NOT use JSON structures
 * }</pre>
 *
 * @see Routing
 * @see JsonStructure
 * @see JsonReader
 * @see JsonWriter
 */
public final class JsonSupport implements Service, Handler {

    /**
     * JSONP (JSON with Pending) can have this weired type.
     */
    private static final MediaType APPLICATION_JAVASCRIPT = new MediaType("application", "javascript");

    /**
     * A singleton holder for JsonSupport with default (empty) configuration.
     */
    private static final class DefaultJsonSupportHolder {
        private static final JsonSupport INTANCE = new JsonSupport(null);
    }

    private final JsonReaderFactory jsonReaderFactory;
    private final JsonWriterFactory jsonWriterFactory;

    /**
     * Creates new instance on top of {@link JsonReader} and {@link javax.json.JsonWriter} created with provided configuration.
     *
     * @param config a configuration for {@link Json} factory methods
     */
    private JsonSupport(Map<String, ?> config) {
        this.jsonReaderFactory = Json.createReaderFactory(config);
        this.jsonWriterFactory = Json.createWriterFactory(config);
    }

    /**
     * It registers reader and writer for {@link JsonSupport} on {@link ServerRequest}/{@link ServerResponse} for any
     * {@link Http.Method HTTP method}.
     * <p>
     * This method is called from {@link Routing} during build process. The user should register whole class
     * ot the routing: {@code Routing.builder().}{@link Routing.Builder#register(Service...) register}{@code (JsonSupport.get())}.
     *
     * @param routingRules a routing configuration where JSON support should be registered
     * @see Routing
     */
    @Override
    public void update(Routing.Rules routingRules) {
        routingRules.any(this);
    }

    /**
     * It registers reader and writer for {@link JsonSupport} on {@link ServerRequest}/{@link ServerResponse} on provided
     * routing criteria.
     * <p>
     * This method is called from {@link Routing} during build process. The user should register whole class
     * ot the routing criteria. For example: {@code Routing.builder().}
     * {@link Routing.Builder#post(String, Handler...) post}{@code ("/foo", JsonSupport.get())}.
     * <p>
     * It calls {@code ServerRequest.}{@link ServerRequest#next() next()} method to invoke following handlers with
     * particular business logic.
     *
     * @param request a server request
     * @param response a server response
     * @see Routing
     */
    @Override
    public void accept(ServerRequest request, ServerResponse response) {
        // Reader
        request.content()
               .registerReader(JsonStructure.class::isAssignableFrom, (publisher, type) -> {
                   Charset charset = determineCharset(request.headers());
                   return reader(charset).apply(publisher);
               });
        // Writer
        response.registerWriter(json -> (json instanceof JsonStructure) && testOrSetContentType(request, response),
                                json -> {
                                    Charset charset = determineCharset(response.headers());
                                    return writer(charset).apply((JsonStructure) json);
                                });
        request.next();
    }

    /**
     * Deals with request {@code Accept} and response {@code Content-Type} headers to determine if writer can be used.
     * <p>
     * If response has no {@code Content-Type} header then it is set to the response.
     *
     * @param request a server request
     * @param response a server response
     * @return {@code true} if JSON writer can be used
     */
    private boolean testOrSetContentType(ServerRequest request, ServerResponse response) {
        MediaType mt = response.headers().contentType().orElse(null);
        if (mt == null) {
            // Find if accepts any JSON compatible type
            List<MediaType> acceptedTypes = request.headers().acceptedTypes();
            MediaType preferredType;
            if (acceptedTypes.isEmpty()) {
                preferredType = MediaType.APPLICATION_JSON;
            } else {
                preferredType = acceptedTypes
                        .stream()
                        .map(type -> {
                            if (type.test(MediaType.APPLICATION_JSON)) {
                                return MediaType.APPLICATION_JSON;
                            } else if (type.test(APPLICATION_JAVASCRIPT)) {
                                return APPLICATION_JAVASCRIPT;
                            } else if (type.hasSuffix("json")) {
                                return new MediaType(type.getType(), type.getSubtype());
                            } else {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
            }
            if (preferredType == null) {
                return false;
            } else {
                response.headers().contentType(preferredType);
                return true;
            }
        } else {
            return MediaType.JSON_PREDICATE.test(mt);
        }
    }

    /**
     * Returns a charset from {@code Content-Type} header parameter or {@code null} if not defined.
     *
     * @param headers parameters representing request or response headers
     * @return a charset or {@code null}
     * @throws RuntimeException if charset is not supported
     */
    private Charset determineCharset(Parameters headers) {
        return headers.first(Http.Header.CONTENT_TYPE)
                .map(MediaType::parse)
                .flatMap(MediaType::getCharset)
                .map(sch -> {
                    try {
                        return Charset.forName(sch);
                    } catch (Exception e) {
                        return null; // Do not need default charset. Can use JSON specification.
                    }
                })
                .orElse(null);
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
        return (publisher, clazz) ->
                ContentReaders.byteArrayReader()
                              .apply(publisher)
                              .thenApply(bytes -> {
                                  JsonReader reader;
                                  if (charset == null) {
                                      reader = jsonReaderFactory.createReader(new ByteArrayInputStream(bytes));
                                  } else {
                                      reader = jsonReaderFactory.createReader(new ByteArrayInputStream(bytes), charset);
                                  }
                                  return reader.read();
                              });
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
        return reader(null);
    }

    /**
     * Returns a function (writer) converting {@link JsonStructure} to the {@link Flow.Publisher Publisher}
     * of {@link DataChunk}s.
     *
     * @param charset a charset to use or {@code null} for default charset
     * @return created function
     */
    public Function<JsonStructure, Flow.Publisher<DataChunk>> writer(Charset charset) {
        return json -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            JsonWriter writer = (charset == null)
                                ? jsonWriterFactory.createWriter(baos)
                                : jsonWriterFactory.createWriter(baos, charset);
            writer.write(json);
            writer.close();
            return ContentWriters.byteArrayWriter(false)
                                 .apply(baos.toByteArray());
        };
    }

    /**
     * Returns a function (writer) converting {@link JsonStructure} to the {@link Flow.Publisher Publisher}
     * of {@link DataChunk}s.
     *
     * @return created function
     */
    public Function<JsonStructure, Flow.Publisher<DataChunk>> writer() {
        return writer(null);
    }

    /**
     * Returns a singleton instance of JsonSupport with default configuration.
     * <p>
     * Use {@link #create(Map)} method to create a new instance with specific configuration.
     *
     * @return a singleton instance with default configuration
     */
    public static JsonSupport get() {
        return DefaultJsonSupportHolder.INTANCE;
    }

    /**
     * Returns an instance of JsonSupport with provided configuration. If configuration is {@code null} or empty
     * then returns a singleton.
     *
     * @param config a configuration for {@link Json} factory methods
     * @return an instance with provided configuration
     */
    public static JsonSupport create(Map<String, ?> config) {
        if (config == null || config.isEmpty()) {
            return get();
        } else {
            return new JsonSupport(config);
        }
    }


}
