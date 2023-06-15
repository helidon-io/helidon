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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.builder.test.testsubjects.Level0;
import io.helidon.builder.test.testsubjects.Level0ManualImpl;
import io.helidon.builder.test.testsubjects.Level1;
import io.helidon.builder.test.testsubjects.Level1ManualImpl;
import io.helidon.builder.test.testsubjects.Level2;
import io.helidon.builder.test.testsubjects.Level2ManualImpl;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsMapContaining.hasKey;
import static org.junit.jupiter.api.Assertions.assertAll;

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
        assertThat(val.toString(),
                   equalTo("Level2ManualImpl(level0StringAttribute=a, level1intAttribute=1, level1IntegerAttribute=null, "
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
        Level2 l2 = Level2.builder()
                .setLevel0StringAttribute("a")
                .setLevel1booleanAttribute(false)
                .setLevel2Level0Info(List.of(Level0.create()))
                .addLevel0(Level0.create())
                .putStringToLevel1("key", Level1.create())
                .build();

        assertAll(
                () -> assertThat(l2.getLevel0StringAttribute(), is("a")),
                () -> assertThat(l2.getLevel1intAttribute(), is(1)),
                () -> assertThat(l2.getLevel1IntegerAttribute(), is(1)),
                () -> assertThat(l2.getLevel1booleanAttribute(), is(false)),
                () -> assertThat(l2.getLevel1BooleanAttribute(), optionalEmpty()),
                () -> assertThat(l2.getLevel2Level0Info(), hasSize(1)),
                () -> assertThat(l2.getLevel2ListOfLevel0s(), hasSize(1))
        );

        Map<String, Level1> level2MapOfStringToLevel1s = l2.getLevel2MapOfStringToLevel1s();
        assertThat(level2MapOfStringToLevel1s, hasKey("key"));
        Level1 l1 = level2MapOfStringToLevel1s.get("key");
        assertAll(
                () -> assertThat(l1.getLevel0StringAttribute(), is("1")),
                () -> assertThat(l1.getLevel1intAttribute(), is(1)),
                () -> assertThat(l1.getLevel1IntegerAttribute(), is(1)),
                () -> assertThat(l1.getLevel1booleanAttribute(), is(true)),
                () -> assertThat(l1.getLevel1BooleanAttribute(), optionalEmpty())
        );

        Level2 val2 = Level2.builder()
                .setLevel0StringAttribute("a")
                .setLevel1booleanAttribute(false)
                .setLevel2Level0Info(List.of(Level0.builder().build()))
                .addLevel0(Level0.builder().build())
                .putStringToLevel1("key", Level1.builder().build())
                .build();
        assertThat(l2, equalTo(val2));
        assertThat(val2, equalTo(l2));
        assertThat(l2.hashCode(), is(val2.hashCode()));
    }

    @Test
    void toBuilderAndEquals() {
        Level2 val = Level2.builder()
                .setLevel0StringAttribute("a")
                .setLevel1BooleanAttribute(false)
                .setLevel2Level0Info(List.of(Level0.builder().build()))
                .addLevel0(Level0.builder().build())
                .putStringToLevel1("key", Level1.builder().build())
                .build();
        Level2 val2 = Level2.builder(val).build();
        assertThat(val, equalTo(val2));
    }

    @Test
    void streams() {
        Supplier<Level2> m1 = Level2ManualImpl.builder().level0StringAttribute("hello").build();
        Level1.Builder m2 = Level1.builder();
        m2.from(m1.get());

        Level1 l1 = m2.build();
        assertAll(
                () -> assertThat(l1.getLevel0StringAttribute(), is("hello")),
                () -> assertThat(l1.getLevel1intAttribute(), is(1)),
                () -> assertThat(l1.getLevel1IntegerAttribute(), is(1)),
                () -> assertThat(l1.getLevel1booleanAttribute(), is(true)),
                () -> assertThat(l1.getLevel1BooleanAttribute(), optionalEmpty())
        );
    }

    @Test
    void levelDefaults() {
        Level2 val2 = Level2.create();

        assertAll(
                () -> assertThat(val2.getLevel0StringAttribute(), is("2")),
                () -> assertThat(val2.getLevel1intAttribute(), is(1)),
                () -> assertThat(val2.getLevel1IntegerAttribute(), is(1)),
                () -> assertThat(val2.getLevel1booleanAttribute(), is(true)),
                () -> assertThat(val2.getLevel1BooleanAttribute(), optionalEmpty()),
                () -> assertThat(val2.getLevel2Level0Info(), is(empty())),
                () -> assertThat(val2.getLevel2ListOfLevel0s(), is(empty())),
                () -> assertThat(val2.getLevel2MapOfStringToLevel1s(), is(Map.of()))
        );

        Level1 val1 = Level1.create();

        assertAll(
                () -> assertThat(val1.getLevel0StringAttribute(), is("1")),
                () -> assertThat(val1.getLevel1intAttribute(), is(1)),
                () -> assertThat(val1.getLevel1IntegerAttribute(), is(1)),
                () -> assertThat(val1.getLevel1booleanAttribute(), is(true)),
                () -> assertThat(val1.getLevel1BooleanAttribute(), optionalEmpty())
        );

        Level0 val0 = Level0.builder().build();
        assertThat(val0.getLevel0StringAttribute(), is("1"));
    }

}
