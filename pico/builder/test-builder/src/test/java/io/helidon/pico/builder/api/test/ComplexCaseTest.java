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

import io.helidon.pico.builder.test.testsubjects.ComplexCaseImpl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ComplexCaseTest {

    @Test
    public void testIt() {
        ComplexCaseImpl val = ComplexCaseImpl.builder()
                .name("name")
                .addConfigBean(null)
                .addKeyToConfigBean("key", null)
                .setOfLists(Collections.singleton(Collections.singletonList(null)))
                .build();
        assertEquals(
                "ComplexCaseImpl(name=name, enabled=false, port=8080, mapOfKeyToConfigBeans={key=null}, "
                        + "listOfConfigBeans=[null], setOfLists=[[null]])",
                     val.toString());
    }

}
