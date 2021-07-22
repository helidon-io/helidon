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
package io.helidon.common;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link GenericType}.
 */
class GenericTypeTest {
    @Test
    void testNoSubclass() {
        assertThrows(IllegalArgumentException.class,
                     GenericType::new);
    }

    @Test
    void testNoTypeArguments() {
        assertThrows(IllegalArgumentException.class,
                     NoTypeArgs::new);
    }

    @Test
    void testIsClass() {
        GenericType<String> type = new GenericType<String>(){};
        assertThat(type.isClass(), is(true));

        GenericType<List<String>> type2 = new GenericType<List<String>>(){};
        assertThat(type2.isClass(), is(false));

        GenericType<List> type3 = new GenericType<List>(){};
        assertThat(type3.isClass(), is(true));
    }


    @Test
    void testTypeArguments() {
        GenericType<String> type = new TypeArgs();

        assertThat(type.type(), is((Type)String.class));
        assertThat(type.rawType(), sameInstance(String.class));
    }

    @Test
    void testGenericTypeList() {
        GenericType<Set<List<String>>> type = new GenericType<Set<List<String>>>(){};

        assertThat(type.rawType(), sameInstance(Set.class));

        Type reflectType = type.type();
        assertThat(reflectType, instanceOf(ParameterizedType.class));

        ParameterizedType pType = (ParameterizedType) reflectType;

        assertThat(pType.getRawType(), is((Type)Set.class));

        Type secondType = pType.getActualTypeArguments()[0];
        assertThat(secondType, instanceOf(ParameterizedType.class));
        pType = (ParameterizedType) secondType;
        assertThat(pType.getRawType(), is((Type)List.class));
        assertThat(pType.getActualTypeArguments()[0], is((Type)String.class));
    }

    private static class TypeArgs extends GenericType<String> {

    }

    private static class NoTypeArgs<T> extends GenericType<T> {

    }
}
