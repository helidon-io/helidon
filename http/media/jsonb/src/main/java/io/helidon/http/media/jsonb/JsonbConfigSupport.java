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

package io.helidon.http.media.jsonb;

import java.util.Map;

import io.helidon.builder.api.Prototype;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

final class JsonbConfigSupport {
    private JsonbConfigSupport() {
    }

    static class Decorator implements Prototype.BuilderDecorator<JsonbSupportConfig.BuilderBase<?, ?>> {
        private static final Jsonb JSON_B = JsonbBuilder.create();

        @Override
        public void decorate(JsonbSupportConfig.BuilderBase<?, ?> target) {
            Map<String, Object> properties = target.properties();
            target.stringProperties().forEach(properties::putIfAbsent);
            target.booleanProperties().forEach(properties::putIfAbsent);
            target.classProperties().forEach(properties::putIfAbsent);

            if (target.jsonb().isEmpty()) {
                if (properties.isEmpty()) {
                    target.jsonb(JSON_B);
                } else {
                    JsonbConfig jsonbConfig = new JsonbConfig();
                    properties.forEach(jsonbConfig::setProperty);
                    target.jsonb(JsonbBuilder.create(jsonbConfig));
                }
            }
        }
    }
}

