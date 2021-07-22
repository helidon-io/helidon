/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.abac.policy.el;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.el.ELContext;
import javax.el.PropertyNotWritableException;

import io.helidon.security.util.AbacSupport;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link AttributeResolver}.
 */
class AttributeResolverTest {
    @Test
    void testGetType() {
        AttributeResolver ar = new AttributeResolver();

        ELContext context = Mockito.mock(ELContext.class);

        Class<?> type = ar.getType(context, new MyResource(Map.of("name", new HashMap<>())), "name");

        assertThat(type, notNullValue());
        assertThat(type, sameInstance(HashMap.class));
    }

    @Test
    void testSetFails() {
        AttributeResolver ar = new AttributeResolver();
        ELContext context = Mockito.mock(ELContext.class);
        try {
            ar.setValue(context, new MyResource(Map.of("name", "jarda")), "name", "surname");
            fail("The resolver should be read-only");
        } catch (PropertyNotWritableException e) {
            // this is expected
        }
    }

    @Test
    public void testResolveAttribute() {
        AttributeResolver ar = new AttributeResolver();

        ELContext context = Mockito.mock(ELContext.class);

        Object name = ar.getValue(context, new MyResource(Map.of("name", "jarda")), "name");

        assertThat(name, notNullValue());
        assertThat(name, is("jarda"));
    }

    @Test
    public void testNoInterface() {
        AttributeResolver ar = new AttributeResolver();

        ELContext context = Mockito.mock(ELContext.class);

        Object name = ar.getValue(context, "just a string", "name");

        assertThat(name, nullValue());
    }

    @Test
    public void testMissingAttribute() {
        AttributeResolver ar = new AttributeResolver();

        ELContext context = Mockito.mock(ELContext.class);

        Object name = ar.getValue(context, new MyResource(Map.of("nickname", "jarda")), "name");

        assertThat(name, nullValue());
    }

    private static class MyResource implements AbacSupport {
        private AbacSupport.BasicAttributes attribs = BasicAttributes.create();

        private MyResource(Map<String, Object> attribs) {
            attribs.forEach(this.attribs::put);
        }

        @Override
        public Object abacAttributeRaw(String key) {
            return attribs.abacAttributeRaw(key);
        }

        @Override
        public Collection<String> abacAttributeNames() {
            return attribs.abacAttributeNames();
        }
    }
}
