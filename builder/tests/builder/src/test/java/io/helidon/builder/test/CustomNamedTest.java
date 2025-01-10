/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.builder.test;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.helidon.builder.test.testsubjects.CustomNamed;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

class CustomNamedTest {

    @Test
    void testIt() throws Exception {
        CustomNamed.Builder customNamedBuilder = CustomNamed.builder()
                .addStringList("b")
                .addStringList("a")
                .addStringList("b")
                .addStringList("y")
                .putStringToIntegerMap("b", 1)
                .putStringToIntegerMap("e", 2)
                .putStringToIntegerMap("a", 3)
                .addStringSet("b")
                .addStringSet("a")
                .addStringSet("b")
                .addStringSet("y");

        CustomNamed customNamed = customNamedBuilder.build();

        assertThat("should be ordered since we are using linked types",
                   customNamed.toString(),
                   equalTo("CustomNamed{stringSet=[b, a, y],stringList=[b, a, b, y],stringToIntegerMap={b=1, e=2, a=3}}"));

        ObjectMapper mapper = JsonMapper.builder().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .build();
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        String json = mapper.writer(printer).writeValueAsString(customNamed);
        assertThat(json, equalTo("{"
                + System.lineSeparator()
                + "  \"stringSet\" : [ \"b\", \"a\", \"y\" ]"
                + System.lineSeparator()
                + "}"));
    }

}
