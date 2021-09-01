/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.microstream.cdi;

import java.time.Duration;
import java.util.HashMap;

import javax.cache.Cache;
import javax.inject.Inject;

import io.helidon.integrations.microstream.cache.ConfigException;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

@HelidonTest
@AddConfig(key = "one.microstream.cache-HashMap.key-type", value = "java.lang.Integer")
@AddConfig(key = "one.microstream.cache-HashMap.value-type", value = "java.util.HashMap")
@AddConfig(key = "one.microstream.cache-wrongTypes.key-type", value = "java.lang.Integer")
@AddConfig(key = "one.microstream.cache-wrongTypes.value-type", value = "java.lang.String")
class CacheManagerExtentionTest {

    @Inject
    @MicrostreamCache(name = "intStrCache")
    Cache<Integer, String> cacheIntStr;

    @Inject
    @MicrostreamCache(name = "intStrCache_2")
    Cache<Integer, String> cacheIntStr_2;

    @Inject
    @MicrostreamCache(name = "intStrCache")
    Cache<Integer, String> cacheIntStr_3;

    @Inject
    @MicrostreamCache(configNode = "one.microstream.cache-wrongTypes", name = "wrongKeyType")
    Cache<String, String> cacheWrongKeyType;

    @Inject
    @MicrostreamCache(configNode = "one.microstream.cache-wrongTypes", name = "wrongValueType")
    Cache<Integer, Integer> cacheWrongValueType;

    @Inject
    @MicrostreamCache(configNode = "one.microstream.cache-HashMap", name = "cacheHashMap")
    Cache<Integer, HashMap<String, Duration>> cacheHashMap;

    @Test
    void sameInstanceTest() {
        assertThat(cacheIntStr, is(sameInstance(cacheIntStr_3)));
    }

    @Test
    void differentInstancesTest() {
        assertThat(cacheIntStr, is(not(sameInstance(cacheIntStr_2))));
    }

    @Test
    void createTestWithoutConfigTest() {
        cacheIntStr.put(1, "Hello");
        assertThat(cacheIntStr.get(1), is("Hello"));
    }

    @Test
    void wrongValueTypeTest() {
        assertThrows(ConfigException.class, () -> cacheWrongValueType.put(1, 42));
    }

    @Test
    void wrongKeyTypeTest() {
        assertThrows(ConfigException.class, () -> cacheWrongKeyType.put("1", "Hello"));
    }

    @Test
    void hashmapCacheTest() {
        HashMap<String, Duration> e1 = new HashMap<>();
        e1.put("Duration_1", Duration.ofDays(2));
        HashMap<String, Duration> e2 = new HashMap<>();
        e1.put("Duration_1", Duration.ofSeconds(2));
        cacheHashMap.put(10, e1);
        cacheHashMap.put(11, e2);
        assertThat(cacheHashMap.get(10), sameInstance(e1));
        assertThat(cacheHashMap.get(11), sameInstance(e2));
    }

}
