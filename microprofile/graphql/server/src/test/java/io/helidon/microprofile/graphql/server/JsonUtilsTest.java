/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import io.helidon.microprofile.graphql.server.AbstractGraphQLTest;
import org.antlr.v4.runtime.tree.Tree;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for {@link JandexUtils}.
 */
class JsonUtilsTest extends AbstractGraphQLTest {
    
    @Test
    @SuppressWarnings("unchecked")
    public void testValidJSON() {
        Map<String, Object> jsonMap = JsonUtils.convertJSONtoMap("{\"name\": \"tim\" }");
        assertThat(jsonMap, is(CoreMatchers.notNullValue()));
        assertThat(jsonMap.size(), is(1));
        assertThat(jsonMap.get("name"), is("tim"));

        jsonMap = JsonUtils.convertJSONtoMap("{\"name\": \"tim\", \"address\": { \"address1\": \"address line 1\", \"city\": \"Perth\" } }");
        assertThat(jsonMap, is(CoreMatchers.notNullValue()));
        assertThat(jsonMap.size(), is(2));
        assertThat(jsonMap.get("name"), is("tim"));

        Map<String, Object> mapAddress = (Map<String, Object>) jsonMap.get("address");
        assertThat(mapAddress, is(CoreMatchers.notNullValue()));
        assertThat(mapAddress.size(), is(2));

        assertThat(mapAddress.get("address1"), is("address line 1"));
        assertThat(mapAddress.get("city"), is("Perth"));
    }

    @Test
    public void testNullJson() {
        Map<String, Object> jsonMap = JsonUtils.convertJSONtoMap(null);
        assertThat(jsonMap.size(), is(0));
    }

    @Test
    public void testEmptyJson() {
        Map<String, Object> jsonMap = JsonUtils.convertJSONtoMap("   ");
        assertThat(jsonMap.size(), is(0));
    }

    @Test
    public void testConvertToJson() {
       Map<String, Object> map = new HashMap<>();
       map.put("name", "tim");
       assertThat(JsonUtils.convertMapToJson(map), is("{\"name\":\"tim\"}"));
    }

    @Test
    public void testGraphQLSDLGeneration() {

        assertThat(JsonUtils.convertJsonToGraphQLSDL(10), is("10"));
        assertThat(JsonUtils.convertJsonToGraphQLSDL("hello"), is("\"hello\""));

        Map<String, Object> map = new TreeMap<>();
        map.put("id", "ID-1");
        map.put("value", BigDecimal.valueOf(100));
        assertThat(JsonUtils.convertJsonToGraphQLSDL(map), is("{ id: \"ID-1\" value: 100 }"));

        map.clear();
        map.put("name", "This contains a quote \"");
        assertThat(JsonUtils.convertJsonToGraphQLSDL(map), is("{ name: \"This contains a quote \\\"\" }"));
        
        map.clear();
        map.put("field1", "key");
        map.put("field2", Arrays.asList("one", "two", "three"));
        assertThat(JsonUtils.convertJsonToGraphQLSDL(map), is("{ field1: \"key\" field2: [\"one\", \"two\", \"three\"] }"));
    }

}
