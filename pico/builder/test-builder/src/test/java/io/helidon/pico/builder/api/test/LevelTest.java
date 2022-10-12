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
import java.util.function.Supplier;

import io.helidon.pico.builder.test.testsubjects.Level0;
import io.helidon.pico.builder.test.testsubjects.Level0Impl;
import io.helidon.pico.builder.test.testsubjects.Level0ManualImpl;
import io.helidon.pico.builder.test.testsubjects.Level1;
import io.helidon.pico.builder.test.testsubjects.Level1Impl;
import io.helidon.pico.builder.test.testsubjects.Level1ManualImpl;
import io.helidon.pico.builder.test.testsubjects.Level2;
import io.helidon.pico.builder.test.testsubjects.Level2Impl;
import io.helidon.pico.builder.test.testsubjects.Level2ManualImpl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LevelTest {

    @Test
    public void manualGeneric() {
        Level2 val = Level2ManualImpl.builder()
                .level0StringAttribute("a")
                .level1booleanAttribute(false)
                .level1IntegerAttribute(null)
                .level2Level0Info(null)
                .level2Level0Info(Collections.singleton(Level0ManualImpl.builder().build()))
                .addLevel0(Level0ManualImpl.builder().build())
                .addStringToLevel1("key", Level1ManualImpl.builder().build())
                .build();
        assertEquals("Level2ManualImpl(level0StringAttribute=a, level1intAttribute=1, level1IntegerAttribute=null, "
                        + "level1booleanAttribute=false, level1BooleanAttribute=null, "
                        + "level2Level0Info=[Level0ManualImpl(level0StringAttribute=1)], "
                        + "level2ListOfLevel0s=[Level0ManualImpl(level0StringAttribute=1)], "
                        + "level2MapOfStringToLevel1s={key=Level1ManualImpl(level0StringAttribute=1, "
                        + "level1intAttribute=1, level1IntegerAttribute=1, level1booleanAttribute=true, "
                        + "level1BooleanAttribute=null)})",
                val.toString());

        Level2 val2 = Level2ManualImpl.builder()
                .level0StringAttribute("a")
                .level1booleanAttribute(false)
                .level1IntegerAttribute(null)
                .level2Level0Info(null)
                .level2Level0Info(Collections.singleton(Level0ManualImpl.builder().build()))
                .addLevel0(Level0ManualImpl.builder().build())
                .addStringToLevel1("key", Level1ManualImpl.builder().build())
                .build();
        assertNotNull(val2);

        val2 = Level2ManualImpl.toBldr(val).build();
        assertEquals(val, val2);
    }

    @Test
    public void manualNonGeneric() {
        Level2 val = Level2ManualImpl.bldr()
                .level0StringAttribute("a")
                .level1booleanAttribute(false)
                .level1IntegerAttribute(null)
                .level2Level0Info(null)
                .level2Level0Info(Collections.singleton(Level0ManualImpl.builder().build()))
                .addLevel0(Level0ManualImpl.builder().build())
                .addStringToLevel1("key", Level1ManualImpl.builder().build())
                .build();
        assertEquals("Level2ManualImpl(level0StringAttribute=a, level1intAttribute=1, level1IntegerAttribute=null, "
                             + "level1booleanAttribute=false, level1BooleanAttribute=null, "
                             + "level2Level0Info=[Level0ManualImpl(level0StringAttribute=1)], "
                             + "level2ListOfLevel0s=[Level0ManualImpl(level0StringAttribute=1)], "
                             + "level2MapOfStringToLevel1s={key=Level1ManualImpl(level0StringAttribute=1, "
                             + "level1intAttribute=1, level1IntegerAttribute=1, level1booleanAttribute=true, "
                             + "level1BooleanAttribute=null)})",
                     val.toString());

        Level2 val2 = Level2ManualImpl.toBldr(val).build();
        assertEquals(val, val2);
    }

    @Test
    public void codeGen() {
        Level2 val = Level2Impl.builder()
                .level0StringAttribute("a")
                .level1booleanAttribute(false)
                .level1IntegerAttribute(null)
                .level2Level0Info(null)
                .level2Level0Info(Collections.singleton(Level0Impl.builder().build()))
                .addLevel0(Level0Impl.builder().build())
                .addStringToLevel1("key", Level1Impl.builder().build())
                .build();
        assertEquals("Level2Impl(level0StringAttribute=a, level1intAttribute=1, level1IntegerAttribute=null, "
                             + "level1booleanAttribute=false, level1BooleanAttribute=null, "
                             + "level2Level0Info=[Level0Impl(level0StringAttribute=1)], "
                             + "level2ListOfLevel0s=[Level0Impl(level0StringAttribute=1)], "
                             + "level2MapOfStringToLevel1s={key=Level1Impl(level0StringAttribute=1, "
                             + "level1intAttribute=1, level1IntegerAttribute=1, level1booleanAttribute=true, "
                             + "level1BooleanAttribute=null)})",
                     val.toString());

        Level2 val2 = Level2Impl.builder()
                .level0StringAttribute("a")
                .level1booleanAttribute(false)
                .level1IntegerAttribute(null)
                .level2Level0Info(null)
                .level2Level0Info(Collections.singleton(Level0Impl.builder().build()))
                .addLevel0(Level0Impl.builder().build())
                .addStringToLevel1("key", Level1Impl.builder().build())
                .build();
        assertEquals(val, val2);
        assertEquals(val2, val);
        assertEquals(val, val);
    }

    @Test
    public void toBuilderAndEquals() {
        Level2 val = Level2Impl.builder()
                .level0StringAttribute("a")
                .level1booleanAttribute(false)
                .level1IntegerAttribute(null)
                .level2Level0Info(null)
                .level2Level0Info(Collections.singleton(Level0Impl.builder().build()))
                .addLevel0(Level0Impl.builder().build())
                .addStringToLevel1("key", Level1Impl.builder().build())
                .build();
        Level2 val2 = Level2Impl.toBuilder(val).build();
        assertEquals(val, val2);
    }

    @Test
    public void streams() {
        Supplier<Level2> m1 = Level2ManualImpl.builder().level0StringAttribute("hello").build();
        Level1ManualImpl.Builder m2 = Level1ManualImpl.builder();
        m2.accept(m1.get());
        assertEquals(
                "Level1ManualImpl(level0StringAttribute=hello, level1intAttribute=1, level1IntegerAttribute=1, "
                        + "level1booleanAttribute=true, level1BooleanAttribute=null)",
                     m2.build().toString());
    }

    @Test
    public void levelDefaults() {
        Level2 val2 = Level2Impl.builder().build();
        assertEquals(
                "Level2Impl(level0StringAttribute=2, level1intAttribute=1, level1IntegerAttribute=1, "
                        + "level1booleanAttribute=true, level1BooleanAttribute=null, level2Level0Info=[], "
                        + "level2ListOfLevel0s=[], level2MapOfStringToLevel1s={})",
                val2.toString());

        Level1 val1 = Level1Impl.builder().build();
        assertEquals(
                "Level1Impl(level0StringAttribute=1, level1intAttribute=1, level1IntegerAttribute=1, "
                        + "level1booleanAttribute=true, level1BooleanAttribute=null)",
                val1.toString());

        Level0 val0 = Level0Impl.builder().build();
        assertEquals("Level0Impl(level0StringAttribute=1)",
                     val0.toString());
    }

}
