/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.builder.test.testsubjects.EdgeCases;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

class EdgeCasesTest {

    @Test
    void testBasics() {
        EdgeCases val = EdgeCases.builder().build();
        assertThat(val.optionalIntegerWithDefault().get(), is(-1));
        assertThat(val.optionalStringWithDefault().get(), equalTo("test"));

        val = EdgeCases.builder(val).optionalIntegerWithDefault(-2).build();
        assertThat(val.optionalIntegerWithDefault().get(), is(-2));
        assertThat(val.optionalStringWithDefault().get(), equalTo("test"));
    }

    @Test
    void listOfObjects() {
        List<?> listOfGenericObjects = List.of("test1");
        EdgeCases val = EdgeCases.builder()
                .listOfObjects(listOfGenericObjects)
                .addListOfObject("test2")
                .build();
        assertThat(val.listOfObjects(), equalTo(List.of("test1", "test2")));
    }

    @Test
    void mapOfEdgeCases() {
        AnotherEdgeCase anotherEdgeCase = mock(AnotherEdgeCase.class);
        Map<String, ? extends EdgeCases> mapOfEdgeCases = Map.of("test1", anotherEdgeCase);
        EdgeCases val = EdgeCases.builder()
                .mapOfEdgeCases(mapOfEdgeCases)
                .putMapOfEdgeCase("test2", anotherEdgeCase)
                .build();
        assertThat(val.mapOfEdgeCases().keySet(), equalTo(Set.of("test1", "test2")));
    }


    interface AnotherEdgeCase extends EdgeCases {
    }

}
