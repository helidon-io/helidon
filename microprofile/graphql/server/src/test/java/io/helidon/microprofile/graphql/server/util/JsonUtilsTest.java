/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.graphql.server.util;

import java.util.HashMap;
import java.util.Map;

import io.helidon.microprofile.graphql.server.AbstractGraphQLTest;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for {@link JandexUtils}.
 */
class JsonUtilsTest extends AbstractGraphQLTest {
    
    @Test
    @SuppressWarnings("unchecked")
    public void testValidJSON() {
        Map<String, Object> jsonMap = JsonUtils.convertJSONtoMap("{\"name\": \"tim\" }");
        assertThat(jsonMap, CoreMatchers.is(CoreMatchers.notNullValue()));
        assertThat(jsonMap.size(), CoreMatchers.is(1));
        assertThat(jsonMap.get("name"), CoreMatchers.is("tim"));

        jsonMap = JsonUtils.convertJSONtoMap("{\"name\": \"tim\", \"address\": { \"address1\": \"address line 1\", \"city\": \"Perth\" } }");
        assertThat(jsonMap, CoreMatchers.is(CoreMatchers.notNullValue()));
        assertThat(jsonMap.size(), CoreMatchers.is(2));
        assertThat(jsonMap.get("name"), CoreMatchers.is("tim"));

        Map<String, Object> mapAddress = (Map<String, Object>) jsonMap.get("address");
        assertThat(mapAddress, CoreMatchers.is(CoreMatchers.notNullValue()));
        assertThat(mapAddress.size(), CoreMatchers.is(2));

        assertThat(mapAddress.get("address1"), CoreMatchers.is("address line 1"));
        assertThat(mapAddress.get("city"), CoreMatchers.is("Perth"));
    }

    @Test
    public void testNullJson() {
        Map<String, Object> jsonMap = JsonUtils.convertJSONtoMap(null);
        assertThat(jsonMap.size(), CoreMatchers.is(0));
    }

    @Test
    public void testEmptyJson() {
        Map<String, Object> jsonMap = JsonUtils.convertJSONtoMap("   ");
        assertThat(jsonMap.size(), CoreMatchers.is(0));
    }

    @Test
    public void testConvertToJson() {
       Map<String, Object> map = new HashMap<>();
       map.put("name", "tim");
       assertThat(JsonUtils.convertMapToJson(map), CoreMatchers.is("{\"name\":\"tim\"}"));
    }

}