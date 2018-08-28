/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test of class to instance map.
 */
@SuppressWarnings("ConstantConditions")
public class ClassToInstanceStoreTest {

    @Test
    public void simpleInstance() {
        ClassToInstanceStore<Object> ctim = new ClassToInstanceStore<>();

        String first = "a String";
        String second = "a second String";

        Optional<String> existing = ctim.putInstance(String.class, first);

        assertThat(existing.isPresent(), is(false));

        existing = ctim.putInstance(String.class, second);

        assertThat(existing.isPresent(), is(true));
        assertThat(existing.get(), is(first));

        existing = ctim.getInstance(String.class);

        assertThat(existing.isPresent(), is(true));
        assertThat(existing.get(), is(second));

        assertThat(ctim.getInstance(CharSequence.class).isPresent(), is(false));

        Optional<String> removed = ctim.removeInstance(String.class);
        assertThat(removed.get(), is(second));

        removed = ctim.getInstance(String.class);
        assertThat(removed.isPresent(), is(false));
    }

    @Test
    public void ifaceAndImpl() {
        ClassToInstanceStore<Object> ctim = new ClassToInstanceStore<>();

        CharSequence first = "a String";
        CharSequence second = "a second String";

        Optional<CharSequence> existing = ctim.putInstance(CharSequence.class, first);

        assertThat(existing.isPresent(), is(false));

        existing = ctim.putInstance(CharSequence.class, second);

        assertThat(existing.isPresent(), is(true));
        assertThat(existing.get(), is(first));

        existing = ctim.getInstance(CharSequence.class);

        assertThat(existing.isPresent(), is(true));
        assertThat(existing.get(), is(second));

        assertThat(ctim.getInstance(String.class).isPresent(), is(false));
    }

    @Test
    public void putAll() {
        ClassToInstanceStore<Object> ctim = new ClassToInstanceStore<>();

        String first = "a String";
        String second = "a second String";

        ctim.putInstance(CharSequence.class, first);
        ctim.putInstance(String.class, second);

        ClassToInstanceStore<Object> copy = new ClassToInstanceStore<>();

        copy.putAll(ctim);

        assertThat(copy.getInstance(CharSequence.class).get(), is(first));
        assertThat(copy.getInstance(String.class).get(), is(second));
    }

    @Test
    public void containsKey() {
        ClassToInstanceStore<Object> ctim = new ClassToInstanceStore<>();

        String first = "a String";
        String second = "a second String";

        ctim.putInstance(CharSequence.class, first);
        ctim.putInstance(String.class, second);

        assertThat(ctim.containsKey(CharSequence.class), is(true));
        assertThat(ctim.containsKey(String.class), is(true));
        assertThat(ctim.containsKey(Serializable.class), is(false));
    }

    @Test
    public void testIsEmpty() {
        ClassToInstanceStore<CharSequence> ctis = new ClassToInstanceStore<>();

        assertThat(ctis.isEmpty(), is(true));

        ctis.putInstance(String.class, "test");

        assertThat(ctis.isEmpty(), is(false));
    }

    @Test
    public void testPutInstanceNoClass() {
        ClassToInstanceStore<CharSequence> ctis = new ClassToInstanceStore<>();
        Optional<String> optional = ctis.putInstance("test");

        if (optional.isPresent()) {
            fail("There should have been no existing mapping");
        }

        ctis.putInstance(new StringBuilder("content"));

        //the class returned by "dd".getClass()
        assertThat(ctis.containsKey(String.class), is(true));
        //string builder
        assertThat(ctis.containsKey(StringBuilder.class), is(true));

        //interface should not be there
        assertThat(ctis.containsKey(CharSequence.class), is(false));

        assertThat(ctis.getInstance(String.class).get(), is("test"));

        Optional<String> other = ctis.putInstance("other");

        if (other.isPresent()) {
            assertThat(other.get(), is("test"));
        } else {
            fail("There should have been an existing mapping");
        }
    }

    @Test
    public void testKeysAndValues() {
        ClassToInstanceStore<CharSequence> ctis = new ClassToInstanceStore<>();
        String value1 = "aValue";
        StringBuffer value2 = new StringBuffer("anotherValue");
        StringBuilder value3 = new StringBuilder("someValue");

        ctis.putInstance(String.class, value1);
        ctis.putInstance(StringBuffer.class, value2);
        ctis.putInstance(CharSequence.class, value3);

        Collection<Class<? extends CharSequence>> keys = ctis.keys();
        Collection<CharSequence> values = ctis.values();

        assertThat(keys.size(), is(3));
        assertThat(values.size(), is(3));

        assertThat(keys, hasItems(String.class, CharSequence.class, StringBuffer.class));
        assertThat(values, hasItems(value1, value2, value3));
    }

    @Test
    public void testToString() {
        ClassToInstanceStore<CharSequence> ctis = new ClassToInstanceStore<>();
        ctis.putInstance("MyValue");
        ctis.putInstance(new StringBuilder("Some value"));

        String result = ctis.toString();

        assertThat(result, containsString("MyValue"));
        assertThat(result, containsString("Some value"));
    }
}
