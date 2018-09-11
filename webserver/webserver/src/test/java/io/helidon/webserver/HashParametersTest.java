/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.http.Parameters;

import org.hamcrest.collection.IsCollectionWithSize;
import org.junit.jupiter.api.Test;

import static java.util.Optional.empty;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The HashParametersTest.
 */
public class HashParametersTest {

    @Test
    public void nonExistentKey() throws Exception {
        HashParameters hashParameters = new HashParameters();

        assertThat(hashParameters.all("a"), hasSize(0));
        assertThat(hashParameters.first("a"), is(empty()));
    }

    @Test
    public void addNull() throws Exception {
        HashParameters hashParameters = new HashParameters();

        hashParameters.add("a", ((String[]) null));
        hashParameters.add("a", "value");
        hashParameters.add("a", ((String[]) null));
        hashParameters.add("a", (Iterable<String>) null);

        assertThat(hashParameters.all("a"), contains(is("value")));
        assertThat(hashParameters.first("a").get(), is("value"));
    }

    @Test
    public void addMultipleAtOnce() throws Exception {
        HashParameters hashParameters = new HashParameters();

        hashParameters.add("a", "v1", "v2");

        assertThat(hashParameters.all("a"), contains(is("v1"), is("v2")));
        assertThat(hashParameters.first("a").get(), is("v1"));
    }

    @Test
    public void addMultipleOneByOne() throws Exception {
        HashParameters hashParameters = new HashParameters();

        hashParameters.add("a", "v1");
        hashParameters.add("a", "v2");

        assertThat(hashParameters.all("a"), contains(is("v1"), is("v2")));
        assertThat(hashParameters.first("a").get(), is("v1"));
    }

    @Test
    public void unmodifiabilityNonEmpty() throws Exception {
        HashParameters hashParameters = new HashParameters();

        hashParameters.add("a", "v1", "v2");

        assertThrows(UnsupportedOperationException.class, () -> {
            hashParameters.all("a").add("this should fail");
        });
    }

    @Test
    public void unmodifiabilityEmpty() throws Exception {
        HashParameters hashParameters = new HashParameters();

        assertThrows(UnsupportedOperationException.class, () -> {
            hashParameters.all("a").add("this should fail");
        });
    }

    @Test
    public void put() throws Exception {
        HashParameters hp = new HashParameters();
        List<String> result = hp.put("a", "v1", "v2", "v3");
        assertThat(hp.all("a"), contains("v1", "v2", "v3"));
        assertThat(result, IsCollectionWithSize.hasSize(0));
        result =hp.put("a", "x1", "x2");
        assertThat(result, contains("v1", "v2", "v3"));
        assertThat(hp.all("a"), contains("x1", "x2"));
        List<String> l = new ArrayList<>(Arrays.asList("y1", "y2"));
        hp.put("a", l);
        assertThat(hp.all("a"), contains("y1", "y2"));
        l.add("y3");
        assertEquals(2, hp.all("a").size());
        hp.put("a");
        assertFalse(hp.first("a").isPresent());
        hp.put("b", "b1", "b2");
        hp.put("b", (Iterable<String>) null);
        assertFalse(hp.first("b").isPresent());
    }

    @Test
    public void putIfAbsent() throws Exception {
        HashParameters hp = new HashParameters();
        List<String> result = hp.putIfAbsent("a", "v1", "v2", "v3");
        assertThat(result, IsCollectionWithSize.hasSize(0));
        assertThat(hp.all("a"), contains("v1", "v2", "v3"));
        result = hp.putIfAbsent("a", "x1", "x2", "x3");
        assertThat(result, contains("v1", "v2", "v3"));
        assertThat(hp.all("a"), contains("v1", "v2", "v3"));

        hp.putIfAbsent("b", Arrays.asList("v1", "v2", "v3"));
        assertThat(hp.all("b"), contains("v1", "v2", "v3"));
        hp.putIfAbsent("b", Arrays.asList("x1", "x2", "x3"));
        assertThat(hp.all("b"), contains("v1", "v2", "v3"));

        hp.putIfAbsent("b");
        assertThat(hp.all("b"), contains("v1", "v2", "v3"));
        hp.putIfAbsent("b", (Iterable<String>) null);
        assertThat(hp.all("b"), contains("v1", "v2", "v3"));

        result = hp.putIfAbsent("c");
        assertThat(result, IsCollectionWithSize.hasSize(0));
        result = hp.putIfAbsent("c", (Iterable<String>) null);
        assertThat(result, IsCollectionWithSize.hasSize(0));
    }

    @Test
    public void computeIfAbsent() throws Exception {
        AtomicBoolean visited = new AtomicBoolean(false);
        HashParameters hp = new HashParameters();

        List<String> result = hp.computeIfAbsent("a", k -> {
            visited.set(true);
            return null;
        });
        assertTrue(visited.get());
        assertThat(result, IsCollectionWithSize.hasSize(0));
        assertThat(hp.all("a"), IsCollectionWithSize.hasSize(0));

        visited.set(false);
        result = hp.computeIfAbsent("a", k -> {
            visited.set(true);
            return Arrays.asList("v1", "v2", "v3");
        });
        assertTrue(visited.get());
        assertThat(result, contains("v1", "v2", "v3"));
        assertThat(hp.all("a"), contains("v1", "v2", "v3"));

        visited.set(false);
        result = hp.computeIfAbsent("a", k -> {
            visited.set(true);
            return Arrays.asList("x1", "x2", "x3");
        });
        assertFalse(visited.get());
        assertThat(result, contains("v1", "v2", "v3"));
        assertThat(hp.all("a"), contains("v1", "v2", "v3"));

        visited.set(false);
        result = hp.computeSingleIfAbsent("b", k -> {
            visited.set(true);
            return "x1";
        });
        assertTrue(visited.get());
        assertThat(result, contains("x1"));
        assertThat(hp.all("b"), contains("x1"));

        visited.set(false);
        result = hp.computeSingleIfAbsent("c", k -> {
            visited.set(true);
            return null;
        });
        assertTrue(visited.get());
        assertThat(result, IsCollectionWithSize.hasSize(0));
        assertFalse(hp.first("c").isPresent());
    }

    @Test
    public void remove() throws Exception {
        HashParameters hp = new HashParameters();
        List<String> removed = hp.remove("a");
        assertThat(removed, IsCollectionWithSize.hasSize(0));
        hp.put("a", "v1", "v2");
        removed = hp.remove("a");
        assertThat(removed, contains("v1", "v2"));
        assertThat(hp.all("a"), IsCollectionWithSize.hasSize(0));
    }

    @Test
    public void toMap() throws Exception {
        HashParameters hp = new HashParameters();
        hp.put("a", "v1", "v2");
        hp.put("b", "v3", "v4");
        Map<String, List<String>> map = hp.toMap();
        assertEquals(2, map.size());
        assertThat(map.get("a"), contains("v1", "v2"));
        assertThat(map.get("b"), contains("v3", "v4"));
    }

    @Test
    public void putAll() throws Exception {
        HashParameters hp = new HashParameters();
        hp.put("a", "a1", "a2");
        hp.put("b", "b1", "b2");
        HashParameters hp2 = new HashParameters();
        hp2.put("c", "c1", "c2");
        hp2.put("b", "b3", "b4");

        hp.putAll(hp2);
        assertThat(hp.all("a"), contains("a1", "a2"));
        assertThat(hp.all("b"), contains("b3", "b4"));
        assertThat(hp.all("c"), contains("c1", "c2"));

        hp.putAll(null);
        assertThat(hp.all("a"), contains("a1", "a2"));
        assertThat(hp.all("b"), contains("b3", "b4"));
    }

    @Test
    public void addAll() throws Exception {
        HashParameters hp = new HashParameters();
        hp.put("a", "a1", "a2");
        hp.put("b", "b1", "b2");
        HashParameters hp2 = new HashParameters();
        hp2.put("c", "c1", "c2");
        hp2.put("b", "b3", "b4");

        hp.addAll(hp2);
        assertThat(hp.all("a"), contains("a1", "a2"));
        assertThat(hp.all("b"), contains("b1", "b2", "b3", "b4"));
        assertThat(hp.all("c"), contains("c1", "c2"));

        hp.addAll(null);
        assertThat(hp.all("b"), contains("b1", "b2", "b3", "b4"));
    }

    @Test
    public void concatNullAndEmpty() throws Exception {
        Parameters[] prms = null;
        HashParameters concat = HashParameters.concat(prms);
        assertNotNull(concat);
        prms = new Parameters[10];
        concat = HashParameters.concat(prms);
        assertNotNull(concat);
        concat = HashParameters.concat();
        assertNotNull(concat);
    }

    @Test
    public void concat() throws Exception {
        HashParameters p1 = new HashParameters();
        p1.add("a", "1", "2");
        p1.add("b", "3", "4", "5");
        HashParameters p2 = new HashParameters();
        p2.add("a", "6");
        p2.add("c", "7", "8");
        HashParameters p3 = new HashParameters();
        HashParameters p4 = new HashParameters();
        p2.add("a", "9");
        p2.add("c", "10");
        p2.add("d", "11", "12");
        HashParameters concat = HashParameters.concat(p1, p2, null, p3, null, p4, null, null);
        assertThat(concat.all("a"), contains("1", "2", "6", "9"));
        assertThat(concat.all("b"), contains("3", "4", "5"));
        assertThat(concat.all("c"), contains("7", "8", "10"));
        assertThat(concat.all("d"), contains("11", "12"));

        concat = HashParameters.concat(p1);
        assertEquals(p1, concat);
    }

}
