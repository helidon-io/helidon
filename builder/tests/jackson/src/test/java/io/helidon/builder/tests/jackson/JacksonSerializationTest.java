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

package io.helidon.builder.tests.jackson;

import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class JacksonSerializationTest {
    private static final String SERIALIZED_VALUE = """
            {"encrypted":true,"workflowId":47,"size":42}""";
    private static final String SERIALIZED_VALUE_WITH_NULL = """
            {"encrypted":true,"workflowId":47,"size":42,"label":null}""";
    private static final String SERIALIZED_VALUE_WITH_LABEL = """
            {"encrypted":true,"workflowId":47,"size":42,"label":"some-label"}""";
    static ObjectMapper mapper;

    @BeforeAll
    static void beforeAll() {
        mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
    }

    @Test
    void testDeserialization() throws JsonProcessingException {
        DemoWorkflowArguments result = mapper.readValue(SERIALIZED_VALUE, DemoWorkflowArguments.class);
        assertThat(result.getSize(), is(42L));
        assertThat(result.getWorkflowId(), is(47));
        assertThat(result.isEncrypted(), is(true));
        assertThat(result.getLabel(), is(Optional.empty()));
    }

    @Test
    void testDeserializationWithNull() throws JsonProcessingException {
        DemoWorkflowArguments result = mapper.readValue(SERIALIZED_VALUE_WITH_NULL, DemoWorkflowArguments.class);
        assertThat(result.getSize(), is(42L));
        assertThat(result.getWorkflowId(), is(47));
        assertThat(result.isEncrypted(), is(true));
        assertThat(result.getLabel(), is(Optional.empty()));
    }

    @Test
    void testDeserializationWithLabel() throws JsonProcessingException {
        DemoWorkflowArguments result = mapper.readValue(SERIALIZED_VALUE_WITH_LABEL, DemoWorkflowArguments.class);
        assertThat(result.getSize(), is(42L));
        assertThat(result.getWorkflowId(), is(47));
        assertThat(result.isEncrypted(), is(true));
        assertThat(result.getLabel(), is(Optional.of("some-label")));
    }

    @Test
    void testSerialization() throws JsonProcessingException {
        var instance = DemoWorkflowArguments.builder()
                .setEncrypted(true)
                .setSize(42)
                .setWorkflowId(47)
                .build();

        String result = mapper.writeValueAsString(instance);

        assertThat(result, is(SERIALIZED_VALUE_WITH_NULL));
    }
}
