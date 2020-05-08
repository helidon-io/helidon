/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved.
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

import javax.json.JsonStructure;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.media.common.MediaSupport;
import io.helidon.media.jsonp.common.JsonpBodyReader;
import io.helidon.media.jsonp.common.JsonpBodyWriter;
import io.helidon.media.jsonp.common.JsonpSupport;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import io.helidon.webserver.WebServer;


/**
 * It provides contains JSON-P ({@code javax.json}) support for
 * {@link WebServer WebServer}'s {@link Routing}. It is intended to provide
 * readers and writers for {@code javax.json} objects such as
 * {@link javax.json.JsonObject JsonObject} or
 * {@link javax.json.JsonArray JsonArray}. If registered on the
 * {@code Web Server} {@link Routing}, then all {@link Handler Handlers} can use
 * {@code ServerRequest.}{@link ServerRequest#content() content()}{@code .}
 * {@link io.helidon.common.http.Content#as(Class) as(...)} and
 * {@code ServerResponse.}{@link ServerResponse#send(Object) send()} with
 * {@link JsonStructure JSON} objects.
 *
 * <h3>Get Instance</h3>
 * Use factory methods {@link #create()} or
 * {@link #create(JsonpSupport)} to acquire an
 * instance.
 *
 * <h3>Usage with Routing</h3> {@code JsonSupport} should be registered on the
 * routing before any business logic handlers.
 * <pre>{@code
 * Routing.builder()
 *        .register(JsonSupport.create())
 *        .etc.... // Business logic related handlers
 * }</pre> Instance behaves also as a routing filter. It means that it can be
 * registered on any routing rule (for example HTTP method) and then it can be
 * used in following handlers with compatible rules.
 * <pre>{@code
 * // Register JsonSupport only for POST of 'foo'
 * Routing.builder()
 *        .post("/foo/{}", JsonSupport.create())
 *        .post("/foo/bar", ...) // It can use JSON structures
 *        .get("/foo/bar", ...);  // It can NOT use JSON structures
 * }</pre>
 *
 * @deprecated use {@link io.helidon.media.jsonp.common.JsonpSupport} with {@link WebServer.Builder#addMediaSupport(MediaSupport)}
 * @see Routing
 * @see JsonStructure
 * @see JsonpBodyReader
 * @see JsonpBodyWriter
 */
@Deprecated
public final class JsonSupport implements Service, Handler {

    static {
        HelidonFeatures.register(HelidonFlavor.SE, "WebServer", "JSON-P");
    }

    private static final JsonSupport INSTANCE = new JsonSupport(JsonpSupport.create());

    private final JsonpBodyReader reader;
    private final JsonpBodyWriter writer;

    private JsonSupport(JsonpSupport processing) {
        reader = processing.newReader();
        writer = processing.newWriter();
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.any(this);
    }

    @Override
    public void accept(final ServerRequest request, final ServerResponse response) {
        request.content().registerReader(reader);
        response.registerWriter(writer);
        request.next();
    }

    JsonpBodyReader reader() {
        return reader;
    }

    /**
     * Returns a singleton instance of JsonSupport with default configuration.
     * <p>
     * Use {@link #create(JsonpSupport)} method
     * to create a new instance with specific configuration.
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
     * @return JsonSupport to register with web server
     * @see JsonpSupport#builder()
     */
    public static JsonSupport create(JsonpSupport processing) {
        if (null == processing) {
            throw new NullPointerException("JsonProcessing argument must not be null.");
        }
        return new JsonSupport(processing);
    }
}
