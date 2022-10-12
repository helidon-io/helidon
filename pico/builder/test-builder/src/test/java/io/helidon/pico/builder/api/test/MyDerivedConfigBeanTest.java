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

import io.helidon.pico.builder.test.testsubjects.MyDerivedConfigBean;
import io.helidon.pico.builder.test.testsubjects.MyDerivedConfigBeanImpl;
import io.helidon.pico.testsupport.TestUtils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class MyDerivedConfigBeanTest {

    @Test
    public void testIt() {
        assertSame(MyDerivedConfigBean.class,
                     MyDerivedConfigBeanImpl.__getMetaConfigBeanType());
        assertEquals(
                "{enabled={key=, type=boolean}, name={key=, required=true, type=class java.lang.String}, "
                        + "port={key=, type=int}}",
                     TestUtils.sort(MyDerivedConfigBeanImpl.__getMetaAttributes()).toString());

        MyDerivedConfigBean cfg = MyDerivedConfigBeanImpl.builder().build();
        assertEquals("MyDerivedConfigBeanImpl(name=null, enabled=false, port=8080)", cfg.toString());
    }

}
