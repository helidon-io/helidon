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

package io.helidon.webserver.observe.config;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.http.HeaderValues;
import io.helidon.http.NotFoundException;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.json.JsonSupport;
import io.helidon.json.JsonObject;
import io.helidon.service.registry.Services;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.SecureHandler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

class ConfigService implements HttpService {
    private static final EntityWriter<JsonObject> WRITER = JsonSupport.serverResponseWriter();
    private static final String OBFUSCATED_VALUE = "*********";

    private final List<Pattern> secretPatterns;
    private final List<Pattern> safeKeyPatterns;
    private final String profile;
    private final boolean permitAll;
    private final boolean unsafeValues;
    private final ConcurrentMap<String, Boolean> obfuscatedKeys = new ConcurrentHashMap<>();
    private final LazyValue<Config> config = LazyValue.create(() -> Services.get(Config.class));

    ConfigService(List<Pattern> secretPatterns,
                  List<Pattern> safeKeyPatterns,
                  String profile,
                  boolean permitAll,
                  boolean unsafeValues) {
        this.secretPatterns = secretPatterns;
        this.safeKeyPatterns = safeKeyPatterns;
        this.profile = profile;
        this.permitAll = permitAll;
        this.unsafeValues = unsafeValues;
    }

    @Override
    public void routing(HttpRules rules) {
        if (!permitAll) {
            rules.any(SecureHandler.authorize("webserver-observe"));
        }
        rules.get("/profile", this::profile)
                .get("/values", this::values)
                .get("/values/{name}", this::value);
    }

    private void value(ServerRequest req, ServerResponse res) {
        String name = req.path().pathParameters().get("name");

        Config valueConfig = config.get().get(name);
        if (valueConfig.hasValue()) {
            String key = Config.Key.unescapeName(valueConfig.key().toString());
            var json = JsonObject.builder()
                    .set("name", name);

            json.set("value", value(key, () -> valueConfig.asString().get()));
            write(req, res, json.build());
        } else {
            throw new NotFoundException("Config value for key: " + name);
        }
    }

    private void values(ServerRequest req, ServerResponse res) {
        var json = JsonObject.builder();
        Config root = config.get();

        if (root.isLeaf()) {
            String key = Config.Key.unescapeName(root.key().toString());
            json.set(key, value(key, () -> root.asString().get()));
        } else {
            root.traverse()
                    .filter(Config::hasValue)
                    .forEach(node -> {
                        String key = Config.Key.unescapeName(node.key().toString());
                        json.set(key, value(key, () -> node.asString().get()));
                    });
        }

        write(req, res, json.build());
    }

    private void profile(ServerRequest req, ServerResponse res) {
        JsonObject profile = JsonObject.builder()
                .set("name", this.profile)
                .build();

        write(req, res, profile);
    }

    private String value(String key, Supplier<String> valueSupplier) {
        return obfuscatedKeys.computeIfAbsent(key, this::shouldObfuscate)
                ? OBFUSCATED_VALUE
                : valueSupplier.get();
    }

    private boolean shouldObfuscate(String key) {
        for (Pattern pattern : secretPatterns) {
            if (pattern.matcher(key).matches()) {
                return true;
            }
        }

        if (unsafeValues) {
            return false;
        }

        for (Pattern pattern : safeKeyPatterns) {
            if (pattern.matcher(key).matches()) {
                return false;
            }
        }

        return true;
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
