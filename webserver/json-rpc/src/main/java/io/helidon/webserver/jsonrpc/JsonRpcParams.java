/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.webserver.jsonrpc;

import java.util.Optional;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

public interface JsonRpcParams {

    JsonObject asJsonObject();

    JsonValue get(String name);

    String getString(String name);

    Optional<JsonValue> optionalGet(String name);

    JsonArray asJsonArray();

    JsonValue get(int index);

    String getString(int index);

    Optional<JsonValue> optionalGet(int index);

    <T> T as(Class<T> type) throws Exception;

    <T> T getAs(String name, Class<T> type) throws Exception;
}
