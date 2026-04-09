/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe.info;

import java.util.HashMap;
import java.util.Map;

import io.helidon.http.HeaderValues;
import io.helidon.http.NotFoundException;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.json.JsonSupport;
import io.helidon.json.JsonObject;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

class InfoService implements HttpService {
    private static final EntityWriter<JsonObject> WRITER = JsonSupport.serverResponseWriter();

    private final Map<String, String> info;

    InfoService(Map<String, String> info) {
        this.info = Map.copyOf(new HashMap<>(info));
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::info)
                .get("/{name}", this::namedInfo);
    }

    private void namedInfo(ServerRequest req, ServerResponse res) {
        String name = req.path().pathParameters().get("name");

        String value = info.get(name);
        if (value == null) {
            throw new NotFoundException("Application info value for " + name + " is not defined.");
        } else {
            JsonObject json = JsonObject.builder()
                    .set("name", name)
                    .set("value", value)
                    .build();
            write(req, res, json);
        }
    }

    private void info(ServerRequest req, ServerResponse res) {
        var json = JsonObject.builder();

        info.forEach(json::set);

        write(req, res, json.build());
    }

    private void write(ServerRequest req, ServerResponse res, JsonObject json) {
        res.header(HeaderValues.X_CONTENT_TYPE_OPTIONS_NOSNIFF);
        WRITER.write(JsonSupport.JSON_OBJECT_TYPE,
                     json,
                     res.outputStream(),
                     req.headers(),
                     res.headers());
    }
}
