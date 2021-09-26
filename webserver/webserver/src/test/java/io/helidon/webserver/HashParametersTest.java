/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

import io.helidon.common.http.HashParameters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.http.Parameters;

import org.hamcrest.collection.IsCollectionWithSize;
import org.junit.jupiter.api.Test;

import static java.util.Optional.empty;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The HashParametersTest.
 */
public class HashParametersTest {

    @Test
    public void nonExistentKey() throws Exception {
        HashParameters hashParameters = HashParameters.create();

        assertThat(hashParameters.all("a"), hasSize(0));
        assertThat(hashParameters.first("a"), is(empty()));
    }

    @Test
    public void addNull() throws Exception {
        HashParameters hashParameters = HashParameters.create();

        hashParameters.add("a", ((String[]) null));
        hashParameters.add("a", "value");
        hashParameters.add("a", ((String[]) null));
        hashParameters.add("a", (Iterable<String>) null);

        assertThat(hashParameters.all("a"), contains(is("value")));
        assertThat(hashParameters.first("a").get(), is("value"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void addMultipleAtOnce() throws Exception {
        HashParameters hashParameters = HashParameters.create();

        hashParameters.add("a", "v1", "v2");

        assertThat(hashParameters.all("a"), contains(is("v1"), is("v2")));
        assertThat(hashParameters.first("a").get(), is("v1"));
    }

  @SuppressWarnings("unchecked")
    @Test
    public void addMultipleOneByOne() throws Exception {
        HashParameters hashParameters = HashParameters.create();

        hashParameters.add("a", "v1");
        hashParameters.add("a", "v2");

        assertThat(hashParameters.all("a"), contains(is("v1"), is("v2")));
        assertThat(hashParameters.first("a").get(), is("v1"));
    }

    @Test
    public void unmodifiabilityNonEmpty() throws Exception {
        HashParameters hashParameters = HashParameters.create();

        hashParameters.add("a", "v1", "v2");

        assertThrows(UnsupportedOperationException.class, () -> {
            hashParameters.all("a").add("this should fail");
        });
    }

    @Test
    public void unmodifiabilityEmpty() throws Exception {
        HashParameters hashParameters = HashParameters.create();

        assertThrows(UnsupportedOperationException.class, () -> {
            hashParameters.all("a").add("this should fail");
        });
    }

    @Test
    public void put() throws Exception {
        HashParameters hp = HashParameters.create();
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
        assertThat(hp.all("a").size(), is(2));
        hp.put("a");
        assertThat(hp.first("a").isPresent(), is(false));
        hp.put("b", "b1", "b2");
        hp.put("b", (Iterable<String>) null);
        assertThat(hp.first("b").isPresent(), is(false));
    }

    @Test
    public void putIfAbsent() throws Exception {
        HashParameters hp = HashParameters.create();
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
        HashParameters hp = HashParameters.create();

        List<String> result = hp.computeIfAbsent("a", k -> {
            visited.set(true);
            return null;
        });
        assertThat(visited.get(), is(true));
        assertThat(result, IsCollectionWithSize.hasSize(0));
        assertThat(hp.all("a"), IsCollectionWithSize.hasSize(0));

        visited.set(false);
        result = hp.computeIfAbsent("a", k -> {
            visited.set(true);
            return Arrays.asList("v1", "v2", "v3");
        });
        assertThat(visited.get(), is(true));
        assertThat(result, contains("v1", "v2", "v3"));
        assertThat(hp.all("a"), contains("v1", "v2", "v3"));

        visited.set(false);
        result = hp.computeIfAbsent("a", k -> {
            visited.set(true);
            return Arrays.asList("x1", "x2", "x3");
        });
        assertThat(visited.get(), is(false));
        assertThat(result, contains("v1", "v2", "v3"));
        assertThat(hp.all("a"), contains("v1", "v2", "v3"));

        visited.set(false);
        result = hp.computeSingleIfAbsent("b", k -> {
            visited.set(true);
            return "x1";
        });
        assertThat(visited.get(), is(true));
        assertThat(result, contains("x1"));
        assertThat(hp.all("b"), contains("x1"));

        visited.set(false);
        result = hp.computeSingleIfAbsent("c", k -> {
            visited.set(true);
            return null;
        });
        assertThat(visited.get(), is(true));
        assertThat(result, IsCollectionWithSize.hasSize(0));
        assertThat(hp.first("c").isPresent(), is(false));
    }

    @Test
    public void remove() throws Exception {
        HashParameters hp = HashParameters.create();
        List<String> removed = hp.remove("a");
        assertThat(removed, IsCollectionWithSize.hasSize(0));
        hp.put("a", "v1", "v2");
        removed = hp.remove("a");
        assertThat(removed, contains("v1", "v2"));
        assertThat(hp.all("a"), IsCollectionWithSize.hasSize(0));
    }

    @Test
    public void toMap() throws Exception {
        HashParameters hp = HashParameters.create();
        hp.put("a", "v1", "v2");
        hp.put("b", "v3", "v4");
        Map<String, List<String>> map = hp.toMap();
        assertThat(map.size(), is(2));
        assertThat(map.get("a"), contains("v1", "v2"));
        assertThat(map.get("b"), contains("v3", "v4"));
    }

    @Test
    public void putAll() throws Exception {
        HashParameters hp = HashParameters.create();
        hp.put("a", "a1", "a2");
        hp.put("b", "b1", "b2");
        HashParameters hp2 = HashParameters.create();
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
        HashParameters hp = HashParameters.create();
        hp.put("a", "a1", "a2");
        hp.put("b", "b1", "b2");
        HashParameters hp2 = HashParameters.create();
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
        assertThat(concat, notNullValue());
        prms = new Parameters[10];
        concat = HashParameters.concat(prms);
        assertThat(concat, notNullValue());
        concat = HashParameters.concat();
        assertThat(concat, notNullValue());
    }

    @Test
    public void concat() throws Exception {
        HashParameters p1 = HashParameters.create();
        p1.add("a", "1", "2");
        p1.add("b", "3", "4", "5");
        HashParameters p2 = HashParameters.create();
        p2.add("a", "6");
        p2.add("c", "7", "8");
        HashParameters p3 = HashParameters.create();
        HashParameters p4 = HashParameters.create();
        p2.add("a", "9");
        p2.add("c", "10");
        p2.add("d", "11", "12");
        HashParameters concat = HashParameters.concat(p1, p2, null, p3, null, p4, null, null);
        assertThat(concat.all("a"), contains("1", "2", "6", "9"));
        assertThat(concat.all("b"), contains("3", "4", "5"));
        assertThat(concat.all("c"), contains("7", "8", "10"));
        assertThat(concat.all("d"), contains("11", "12"));

        concat = HashParameters.concat(p1);
        assertThat(concat, is(p1));
    }

}
