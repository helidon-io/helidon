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

import io.helidon.pico.builder.test.testsubjects.MyConfigBean;
import io.helidon.pico.builder.test.testsubjects.MyConfigBeanImpl;
import io.helidon.pico.builder.test.testsubjects.MyConfigBeanManualImpl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MyConfigBeanTest {

    @Test
    public void manual() {
        MyConfigBean val = MyConfigBeanManualImpl.builder().build();
        assertEquals("MyConfigBeanManualImpl(name=null, enabled=false, port=0)", val.toString());

        val = MyConfigBeanManualImpl.toBuilder(val)
                .name("jeff")
                .enabled(true)
                .port(80)
                .build();
        assertEquals("MyConfigBeanManualImpl(name=jeff, enabled=true, port=80)", val.toString());
    }

    @Test
    public void codeGen() {
        MyConfigBean val = MyConfigBeanImpl.builder().build();
        assertEquals("MyConfigBeanImpl(name=null, enabled=false, port=8080)", val.toString());

        val = MyConfigBeanImpl.toBuilder(val)
                .name("jeff")
                .enabled(true)
                .port(80)
                .build();
        assertEquals("MyConfigBeanImpl(name=jeff, enabled=true, port=80)", val.toString());
    }

    @Test
    public void mixed() {
        MyConfigBean val1 = MyConfigBeanManualImpl.builder().build();
        val1              = MyConfigBeanImpl.toBuilder(val1)
                .name("jeff")
                .enabled(true)
                .port(80)
                .build();
        assertEquals("MyConfigBeanImpl(name=jeff, enabled=true, port=80)", val1.toString());

        MyConfigBean val2 = MyConfigBeanImpl.builder().build();
        val2              = MyConfigBeanManualImpl.toBuilder(val2)
                .name("jeff")
                .enabled(true)
                .port(80)
                .build();
        assertEquals("MyConfigBeanManualImpl(name=jeff, enabled=true, port=80)", val2.toString());

        assertEquals(val1.hashCode(), val2.hashCode());
        assertEquals(val1, val2);
        assertEquals(val2, val1);
    }

}
