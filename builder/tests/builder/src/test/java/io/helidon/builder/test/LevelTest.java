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

import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.builder.test.testsubjects.Level0;
import io.helidon.builder.test.testsubjects.Level0Impl;
import io.helidon.builder.test.testsubjects.Level0ManualImpl;
import io.helidon.builder.test.testsubjects.Level1;
import io.helidon.builder.test.testsubjects.Level1Impl;
import io.helidon.builder.test.testsubjects.Level1ManualImpl;
import io.helidon.builder.test.testsubjects.Level2;
import io.helidon.builder.test.testsubjects.Level2Impl;
import io.helidon.builder.test.testsubjects.Level2ManualImpl;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

class LevelTest {

    @Test
    void manualGeneric() {
        Level2 val = Level2ManualImpl.builder()
                .level0StringAttribute("a")
                .level1booleanAttribute(false)
                .level1IntegerAttribute(null)
                .level2Level0Info(null)
                .level2Level0Info(Set.of(Level0ManualImpl.builder().build()))
                .addLevel0(Level0ManualImpl.builder().build())
                .addStringToLevel1("key", Level1ManualImpl.builder().build())
                .build();
        assertThat(val.toString(),
                   equalTo("Level2ManualImpl(level0StringAttribute=a, level1intAttribute=1, level1IntegerAttribute=null, "
                                   + "level1booleanAttribute=false, level1BooleanAttribute=Optional.empty, "
                                   + "level2Level0Info=[Level0ManualImpl(level0StringAttribute=1)], "
                                   + "level2ListOfLevel0s=[Level0ManualImpl(level0StringAttribute=1)], "
                                   + "level2MapOfStringToLevel1s={key=Level1ManualImpl(level0StringAttribute=1, "
                                   + "level1intAttribute=1, level1IntegerAttribute=1, level1booleanAttribute=true, "
                                   + "level1BooleanAttribute=Optional.empty)})"));

        Level2 val2 = Level2ManualImpl.builder()
                .level0StringAttribute("a")
                .level1booleanAttribute(false)
                .level1IntegerAttribute(null)
                .level2Level0Info(null)
                .level2Level0Info(Collections.singleton(Level0ManualImpl.builder().build()))
                .addLevel0(Level0ManualImpl.builder().build())
                .addStringToLevel1("key", Level1ManualImpl.builder().build())
                .build();
        assertThat(val2, notNullValue());

        val2 = Level2ManualImpl.toBldr(val).build();
        assertThat(val, equalTo(val2));
    }

    @Test
    void manualNonGeneric() {
        Level2 val = Level2ManualImpl.bldr()
                .level0StringAttribute("a")
                .level1booleanAttribute(false)
                .level1IntegerAttribute(null)
                .level2Level0Info(null)
                .level2Level0Info(Set.of(Level0ManualImpl.builder().build()))
                .addLevel0(Level0ManualImpl.builder().build())
                .addStringToLevel1("key", Level1ManualImpl.builder().build())
                .build();
        assertThat(val.toString(), equalTo("Level2ManualImpl(level0StringAttribute=a, level1intAttribute=1, level1IntegerAttribute=null, "
                             + "level1booleanAttribute=false, level1BooleanAttribute=Optional.empty, "
                             + "level2Level0Info=[Level0ManualImpl(level0StringAttribute=1)], "
                             + "level2ListOfLevel0s=[Level0ManualImpl(level0StringAttribute=1)], "
                             + "level2MapOfStringToLevel1s={key=Level1ManualImpl(level0StringAttribute=1, "
                             + "level1intAttribute=1, level1IntegerAttribute=1, level1booleanAttribute=true, "
                             + "level1BooleanAttribute=Optional.empty)})"));

        Level2 val2 = Level2ManualImpl.toBldr(val).build();
        assertThat(val, equalTo(val2));
    }

    @Test
    void codeGen() {
        Level2 val = Level2Impl.builder()
                .level0StringAttribute("a")
                .level1booleanAttribute(false)
                .level2Level0Info(Collections.singleton(Level0Impl.builder().build()))
                .addLevel0(Level0Impl.builder().build())
                .addStringToLevel1("key", Level1Impl.builder().build())
                .build();
        assertThat(val.toString(),
                   equalTo("Level2(level0StringAttribute=a, level1intAttribute=1, level1IntegerAttribute=1, "
                                   + "level1booleanAttribute=false, level1BooleanAttribute=Optional.empty, "
                                   + "level2Level0Info=[Level0(level0StringAttribute=1)], "
                                   + "level2ListOfLevel0s=[Level0(level0StringAttribute=1)], "
                                   + "level2MapOfStringToLevel1s={key=Level1(level0StringAttribute=1, "
                                   + "level1intAttribute=1, level1IntegerAttribute=1, level1booleanAttribute=true, "
                                   + "level1BooleanAttribute=Optional.empty)})"));

        Level2 val2 = Level2Impl.builder()
                .level0StringAttribute("a")
                .level1booleanAttribute(false)
                .level2Level0Info(Set.of(Level0Impl.builder().build()))
                .addLevel0(Level0Impl.builder().build())
                .addStringToLevel1("key", Level1Impl.builder().build())
                .build();
        assertThat(val, equalTo(val2));
        assertThat(val2, equalTo(val));
    }

    @Test
    void toBuilderAndEquals() {
        Level2 val = Level2Impl.builder()
                .level0StringAttribute("a")
                .level1booleanAttribute(false)
                .level2Level0Info(Collections.singleton(Level0Impl.builder().build()))
                .addLevel0(Level0Impl.builder().build())
                .addStringToLevel1("key", Level1Impl.builder().build())
                .build();
        Level2 val2 = Level2Impl.toBuilder(val).build();
        assertThat(val, equalTo(val2));
    }

    @Test
    void streams() {
        Supplier<Level2> m1 = Level2ManualImpl.builder().level0StringAttribute("hello").build();
        Level1Impl.Builder m2 = Level1Impl.builder();
        m2.accept(m1.get());
        assertThat(m2.build().toString(),
                   equalTo("Level1(level0StringAttribute=hello, level1intAttribute=1, level1IntegerAttribute=1, "
                                   + "level1booleanAttribute=true, level1BooleanAttribute=Optional.empty)"));
    }

    @Test
    void levelDefaults() {
        Level2 val2 = Level2Impl.builder().build();
        assertThat(val2.toString(),
                   equalTo("Level2(level0StringAttribute=2, level1intAttribute=1, level1IntegerAttribute=1, "
                                   + "level1booleanAttribute=true, level1BooleanAttribute=Optional.empty, level2Level0Info=[], "
                                   + "level2ListOfLevel0s=[], level2MapOfStringToLevel1s={})"));

        Level1 val1 = Level1Impl.builder().build();
        assertThat(val1.toString(),
                equalTo("Level1(level0StringAttribute=1, level1intAttribute=1, level1IntegerAttribute=1, "
                        + "level1booleanAttribute=true, level1BooleanAttribute=Optional.empty)"));

        Level0 val0 = Level0Impl.builder().build();
        assertThat(val0.toString(),
                   equalTo("Level0(level0StringAttribute=1)"));
    }

}
