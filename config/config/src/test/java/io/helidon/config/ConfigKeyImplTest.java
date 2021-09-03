/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.config;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;

/**
 * Tests {@link io.helidon.config.ConfigKeyImpl}.
 */
public class ConfigKeyImplTest {
    private static final ConfigKeyImpl ROOT = ConfigKeyImpl.of();
    
    @Test
    public void testConfigKeyOf() {
        assertThatKey((ConfigKeyImpl) Config.Key.create(""), true, null, "", "");
        assertThatKey((ConfigKeyImpl) Config.Key.create("aaa"), false, is(ROOT), "aaa", "aaa");
        assertThatKey((ConfigKeyImpl) Config.Key.create("aaa.bbb.ccc"), false, not(ROOT), "ccc", "aaa.bbb.ccc");
    }

    @Test
    public void testOfRoot() {
        assertThatKey(ConfigKeyImpl.of(), true, is(ROOT), "", "");
        assertThatKey(ConfigKeyImpl.of(""), true, is(ROOT), "", "");
        assertThatKey(ConfigKeyImpl.of().child(""), true, is(ROOT), "", "");
        assertThatKey(ConfigKeyImpl.of().child(ConfigKeyImpl.of()), true, is(ROOT), "", "");
    }

    @Test
    public void testOf() {
        assertThatKey(ConfigKeyImpl.of("aaa"), false, is(ROOT), "aaa", "aaa");
        assertThatKey(ConfigKeyImpl.of("aaa.bbb"), false, not(ROOT), "bbb", "aaa.bbb");
        assertThatKey(ConfigKeyImpl.of("aaa.bbb.ccc"), false, not(ROOT), "ccc", "aaa.bbb.ccc");
    }

    @Test
    public void testChildLevel1() {
        assertThatKey(ConfigKeyImpl.of().child("aaa"), false, is(ROOT), "aaa", "aaa");
        assertThatKey(ConfigKeyImpl.of().child(ConfigKeyImpl.of("aaa")), false, is(ROOT), "aaa", "aaa");
    }

    @Test
    public void testChildLevel2() {
        assertThatKey(ConfigKeyImpl.of("aaa").child("bbb"), false, not(ROOT), "bbb", "aaa.bbb");
        assertThatKey(ConfigKeyImpl.of("aaa").child(ConfigKeyImpl.of("bbb")), false, not(ROOT), "bbb", "aaa.bbb");

        assertThatKey(ConfigKeyImpl.of().child("aaa.bbb"), false, not(ROOT), "bbb", "aaa.bbb");
        assertThatKey(ConfigKeyImpl.of().child(ConfigKeyImpl.of("aaa.bbb")), false, not(ROOT), "bbb", "aaa.bbb");
    }

    @Test
    public void testChildLevel3() {
        assertThatKey(ConfigKeyImpl.of().child("aaa").child("bbb").child("ccc"), false, not(ROOT), "ccc", "aaa.bbb.ccc");
        assertThatKey(ConfigKeyImpl.of().child("aaa.bbb").child("ccc"), false, not(ROOT), "ccc", "aaa.bbb.ccc");
        assertThatKey(ConfigKeyImpl.of().child("aaa").child("bbb.ccc"), false, not(ROOT), "ccc", "aaa.bbb.ccc");
        assertThatKey(ConfigKeyImpl.of().child("aaa.bbb.ccc"), false, not(ROOT), "ccc", "aaa.bbb.ccc");
    }

    private void assertThatKey(ConfigKeyImpl key, boolean root, Matcher<Object> parentMatcher, String name, String toString) {
        assertThat(key.isRoot(), is(root));
        if (root) {
            Assertions.assertThrows(IllegalStateException.class, key::parent);
        } else {
            assertThat(key.parent(), parentMatcher);
        }
        assertThat(key.name(), is(name));
        assertThat(key.toString(), is(toString));
    }

    @Test
    public void testEquals() {
        assertThat(ConfigKeyImpl.of(""),
                   is(ConfigKeyImpl.of()));

        assertThat(ConfigKeyImpl.of("aaa").child(ConfigKeyImpl.of()),
                   is(ConfigKeyImpl.of("aaa")));

        assertThat(ConfigKeyImpl.of("bbb"),
                   is(ConfigKeyImpl.of("bbb")));

        assertThat(ConfigKeyImpl.of("aaa").child(ConfigKeyImpl.of("bbb")),
                   is(ConfigKeyImpl.of("aaa.bbb")));
    }

    @Test
    public void testCompareTo() {
        assertThat(ConfigKeyImpl.of("").compareTo(ConfigKeyImpl.of()), is(0));

        assertThat(ConfigKeyImpl.of("aaa").compareTo(ConfigKeyImpl.of("bbb")), is(lessThan(0)));
        assertThat(ConfigKeyImpl.of("bbb").compareTo(ConfigKeyImpl.of("aaa")), is(greaterThan(0)));

        assertThat(ConfigKeyImpl.of("aaa").compareTo(ConfigKeyImpl.of("aaa.bbb")), is(lessThan(0)));
        assertThat(ConfigKeyImpl.of("aaa.bbb").compareTo(ConfigKeyImpl.of("aaa")), is(greaterThan(0)));
    }

}
