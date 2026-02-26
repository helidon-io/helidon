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

package io.helidon.webserver.observe.health;

import java.math.BigDecimal;
import java.util.Map;

import io.helidon.health.HealthCheckResponse;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonValue;

final class HealthHelper {
    private HealthHelper() {
    }

    static JsonObject toJson(String name, HealthCheckResponse response) {
        var check = JsonObject.builder();
        check.set("name", name);
        check.set("status", response.status().toString());
        Map<String, Object> details = response.details();
        if (!details.isEmpty()) {
            var dataObject = JsonObject.builder();
            details.forEach((key, value) -> setValue(dataObject, key, value));
            check.set("data", dataObject.build());
        }
        return check.build();
    }

    private static void setValue(JsonObject.Builder dataObject, String key, Object value) {
        switch (value) {
        case Integer i -> dataObject.set(key, i);
        case String s -> dataObject.set(key, s);
        case Float f -> dataObject.set(key, f);
        case Double d -> dataObject.set(key, d);
        case Boolean b -> dataObject.set(key, b);
        case Long l -> dataObject.set(key, l);
        case BigDecimal bd -> dataObject.set(key, bd);
        case JsonValue val -> dataObject.set(key, val);
        default -> dataObject.set(key, String.valueOf(value));
        }
    }
}
