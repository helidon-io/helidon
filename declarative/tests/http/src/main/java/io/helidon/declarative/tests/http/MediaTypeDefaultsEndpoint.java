/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.http;

import java.util.function.Supplier;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderNames;
import io.helidon.http.Http;
import io.helidon.json.JsonObject;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.RestServer;
import io.helidon.webserver.http.ServerRequest;

@Http.Path("/media-type-defaults")
@Http.Consumes(MediaTypes.APPLICATION_JSON_VALUE)
@Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
@RestServer.Listener("@default")
@RestServer.Endpoint
@Service.Singleton
class MediaTypeDefaultsEndpoint {
    private final Supplier<ServerRequest> requestSupplier;

    @Service.Inject
    MediaTypeDefaultsEndpoint(Supplier<ServerRequest> requestSupplier) {
        this.requestSupplier = requestSupplier;
    }

    @Http.POST
    JsonObject defaults(@Http.Entity JsonObject entity) {
        return entity;
    }

    @Http.POST
    @Http.Path("/override")
    @Http.Consumes(MediaTypes.TEXT_PLAIN_VALUE)
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    String override(@Http.Entity String entity) {
        return entity;
    }

    @Http.GET
    @Http.Path("/no-entity")
    JsonObject noEntity() {
        return JsonObject.builder()
                .set("contentTypePresent", requestSupplier.get().headers().contentType().isPresent())
                .build();
    }

    @Http.POST
    @Http.Path("/clear")
    @Http.Consumes({})
    @Http.Produces({})
    String clear(@Http.Entity String entity) {
        var headers = requestSupplier.get().headers();
        boolean textPlain = headers.contentType()
                .map(it -> it.mediaType().equals(MediaTypes.TEXT_PLAIN))
                .orElse(false);
        boolean acceptsJson = headers.contains(HeaderNames.ACCEPT)
                && headers.get(HeaderNames.ACCEPT).values().contains(MediaTypes.APPLICATION_JSON_VALUE);
        return entity + "|" + textPlain + "|" + acceptsJson;
    }
}
