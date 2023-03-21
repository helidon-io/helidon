/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.observe.info;

import java.util.LinkedHashMap;
import java.util.Map;

import io.helidon.common.http.NotFoundException;
import io.helidon.common.config.Config;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http.media.jsonp.JsonpMediaSupportProvider;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

class InfoService implements HttpService {
    private static final EntityWriter<JsonObject> WRITER = JsonpMediaSupportProvider.serverResponseWriter();
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    private final Map<String, Object> info;

    private InfoService(Map<String, Object> info) {
        this.info = info;
    }

    public static HttpService create(Config config) {
        Map<String, Object> info = new LinkedHashMap<>(config.get("values").detach().asMap().orElseGet(Map::of));

        return new InfoService(info);
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::info)
                .get("/{name}", this::namedInfo);
    }

    private void namedInfo(ServerRequest req, ServerResponse res) {
        String name = req.path().pathParameters().value("name");

        Object value = info.get(name);
        if (value == null) {
            throw new NotFoundException("Application info value for " + name + " is not defined.");
        } else {
            JsonObjectBuilder json = JSON.createObjectBuilder()
                    .add("name", name)
                    .add("value", String.valueOf(value));
            write(req, res, json.build());
        }
    }

    private void info(ServerRequest req, ServerResponse res) {
        JsonObjectBuilder json = JSON.createObjectBuilder(info);

        write(req, res, json.build());
    }

    private void write(ServerRequest req, ServerResponse res, JsonObject json) {
        WRITER.write(JsonpMediaSupportProvider.JSON_OBJECT_TYPE,
                     json,
                     res.outputStream(),
                     req.headers(),
                     res.headers());
    }
}
