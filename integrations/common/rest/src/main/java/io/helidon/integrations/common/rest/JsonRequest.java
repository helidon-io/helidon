/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.common.rest;

/**
 * Implementation of a {@link io.helidon.integrations.common.rest.ApiRequest} that allows
 * free configuration of the JSON object.
 *
 * @see io.helidon.integrations.common.rest.ApiJsonRequest
 */
public final class JsonRequest extends ApiJsonRequest<JsonRequest> {
    private JsonRequest() {
    }

    /**
     * Create a new request builder.
     * @return request builder
     */
    public static JsonRequest builder() {
        return new JsonRequest();
    }

    @Override
    public JsonRequest addToArray(String name, String element) {
        return super.addToArray(name, element);
    }

    @Override
    public JsonRequest add(String name, String value) {
        return super.add(name, value);
    }

    @Override
    public JsonRequest add(String name, int value) {
        return super.add(name, value);
    }

    @Override
    public JsonRequest add(String name, double value) {
        return super.add(name, value);
    }

    @Override
    public JsonRequest add(String name, boolean value) {
        return super.add(name, value);
    }

    @Override
    public JsonRequest emptyArray(String name) {
        return super.emptyArray(name);
    }

    @Override
    public JsonRequest addToArray(String name, ApiJsonBuilder<?> element) {
        return super.addToArray(name, element);
    }

    @Override
    public JsonRequest addToObject(String name, String key, String value) {
        return super.addToObject(name, key, value);
    }

    @Override
    public JsonRequest addToArray(String name, int element) {
        return super.addToArray(name, element);
    }

    @Override
    public JsonRequest addToArray(String name, long element) {
        return super.addToArray(name, element);
    }

    @Override
    public JsonRequest addToArray(String name, double element) {
        return super.addToArray(name, element);
    }

    @Override
    public JsonRequest addToArray(String name, boolean element) {
        return super.addToArray(name, element);
    }

    @Override
    public JsonRequest add(String name, long value) {
        return super.add(name, value);
    }

    @Override
    public JsonRequest addToObject(String name, String key, int value) {
        return super.addToObject(name, key, value);
    }

    @Override
    public JsonRequest addToObject(String name, String key, long value) {
        return super.addToObject(name, key, value);
    }

    @Override
    public JsonRequest addToObject(String name, String key, double value) {
        return super.addToObject(name, key, value);
    }

    @Override
    public JsonRequest addToObject(String name, String key, boolean value) {
        return super.addToObject(name, key, value);
    }

    @Override
    public JsonRequest add(String name, ApiJsonBuilder<?> object) {
        return super.add(name, object);
    }

    @Override
    public JsonRequest addBase64(String name, Base64Value base64Value) {
        return super.addBase64(name, base64Value);
    }
}
