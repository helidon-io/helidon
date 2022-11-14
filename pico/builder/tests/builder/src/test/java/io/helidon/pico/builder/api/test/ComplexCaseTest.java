/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.builder.api.test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.pico.builder.test.testsubjects.ComplexCaseImpl;
import io.helidon.pico.builder.test.testsubjects.MyConfigBean;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

class ComplexCaseTest {

    @Test
    void testIt() {
        Map<String, List<? extends MyConfigBean>> mapWithNull = new HashMap<>();
        mapWithNull.put("key", null);

        ComplexCaseImpl val = ComplexCaseImpl.builder()
                .name("name")
                .mapOfKeyToConfigBeans(mapWithNull)
                .setOfLists(Collections.singleton(Collections.singletonList(null)))
                .build();
        assertThat(val.toString(),
                   equalTo("ComplexCase(name=name, enabled=false, port=8080, mapOfKeyToConfigBeans={key=null}, "
                                   + "listOfConfigBeans=[], setOfLists=[[null]])"));
    }

}
