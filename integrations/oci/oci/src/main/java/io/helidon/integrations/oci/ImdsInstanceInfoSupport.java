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

package io.helidon.integrations.oci;

import java.io.StringReader;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;

final class ImdsInstanceInfoSupport {
    private ImdsInstanceInfoSupport() {
    }

    static JsonObject toJson(jakarta.json.JsonObject jsonObject) {
        return JsonParser.create(jsonObject.toString())
                .readJsonObject();
    }

    static jakarta.json.JsonObject toJsonObject(JsonObject json) {
        try (var reader = jakarta.json.Json.createReader(new StringReader(json.toString()))) {
            return reader.readObject();
        }
    }

    @SuppressWarnings("removal")
    static class Decorator implements Prototype.BuilderDecorator<ImdsInstanceInfo.BuilderBase<?, ?>> {
        Decorator() {
        }

        @Override
        public void decorate(ImdsInstanceInfo.BuilderBase<?, ?> target) {
            Optional<JsonObject> json = target.json();
            Optional<jakarta.json.JsonObject> jsonObject = target.jsonObject();

            if (json.isPresent()) {
                target.jsonObject(toJsonObject(json.get()));
                return;
            }

            jsonObject.ifPresent(it -> target.json(toJson(it)));
        }
    }
}
