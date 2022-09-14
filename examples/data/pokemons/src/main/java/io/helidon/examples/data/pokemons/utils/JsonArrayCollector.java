/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
package io.helidon.examples.data.pokemons.utils;

import io.helidon.common.reactive.Collector;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

public final class JsonArrayCollector implements Collector<JsonObject, JsonArray> {

    private final JsonArrayBuilder targetArrayBuilder;

    public JsonArrayCollector() {
        this.targetArrayBuilder = Json.createArrayBuilder();
    }


    @Override
    public void collect(JsonObject item) {
        targetArrayBuilder.add(item);
    }

    @Override
    public JsonArray value() {
        return targetArrayBuilder.build();
    }

}
