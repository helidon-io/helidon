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

package io.helidon.common;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A set of static methods similar to Java9's List.of(), Set.of() and Map.of().
 */
public abstract class CollectionsHelper {

    private CollectionsHelper(){};

    /**
     * Returns an immutable list containing zero elements.
     *
     * @param <T> the {@code List}'s element type
     * @return an empty {@code List}
     */
    public static <T> List<T> listOf(){
        return Collections.emptyList();
    }

    /**
     * Returns an unmodifiable list containing the given elements.
     *
     * @param <T> the {@code List}'s element type
     * @param elts the elements
     * @return a {@code List} containing the specified elements
     *
     */
    @SafeVarargs
    public static <T> List<T> listOf(T ... elts){
        List<T> list = new ArrayList<>();
        list.addAll(Arrays.asList(elts));
        return Collections.unmodifiableList(list);
    }

    /**
     * Returns an immutable set containing zero elements.
     *
     * @param <T> the {@code Set}'s element type
     * @return an empty {@code Set}
     */
    public static <T> Set<T> setOf(){
        return Collections.emptySet();
    }

    /**
     * Create an unmodifiable set containing the given elements.
     *
     * @param <T> the {@code Set}'s element type
     * @param elts elements to add
     * @return a {@code Set} containing the specified elements
     */
    @SafeVarargs
    public static <T> Set<T> setOf(T ... elts){
        Set<T> set = new HashSet<>();
        set.addAll(Arrays.asList(elts));
        return Collections.unmodifiableSet(set);
    }

    /**
     * Returns an {@link java.util.Map.Entry} containing the given key and value.
     *
     * @param <K> the key's type
     * @param <V> the value's type
     * @param k key
     * @param v value
     * @return an {@code Entry} containing the specified key and value
     */
    public static <K, V> Map.Entry<K, V> mapEntry(K k, V v){
        return new AbstractMap.SimpleEntry<>(k, v);
    }

    /**
     * Returns an immutable map containing zero mappings.
     * @param <K> the {@code Map}'s key type
     * @param <V> the {@code Map}'s value type
     * @return an empty {@code Map}
     */
    public static <K, V> Map<K, V> mapOf(){
        return Collections.<K, V>emptyMap();
    }

    /**
     * Create an unmodifiable map containing a single mappings.
     *
     * @param <K> the {@code Map}'s key type
     * @param <V> the {@code Map}'s value type
     * @param k1 key
     * @param v1 value
     * @return a {@code Map} containing the specified mappings
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1){
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Create an unmodifiable map containing 2 mappings.
     *
     * @param <K> the {@code Map}'s key type
     * @param <V> the {@code Map}'s value type
     * @param k1 first key
     * @param v1 first value
     * @param k2 second key
     * @param v2 second value
     * @return a {@code Map} containing the specified mappings
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2){
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Create an unmodifiable map containing 3 mappings.
     *
     * @param <K> the {@code Map}'s key type
     * @param <V> the {@code Map}'s value type
     * @param k1 first key
     * @param v1 first value
     * @param k2 second key
     * @param v2 second value
     * @param k3 third key
     * @param v3 third value
     * @return a {@code Map} containing the specified mappings
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3){
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Create an unmodifiable map containing 4 mappings.
     *
     * @param <K> the {@code Map}'s key type
     * @param <V> the {@code Map}'s value type
     * @param k1 first key
     * @param v1 first value
     * @param k2 second key
     * @param v2 second value
     * @param k3 third key
     * @param v3 third value
     * @param k4 fourth key
     * @param v4 fourth value
     * @return a {@code Map} containing the specified mappings
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4){
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Create an unmodifiable map containing 5 mappings.
     *
     * @param <K> the {@code Map}'s key type
     * @param <V> the {@code Map}'s value type
     * @param k1 first key
     * @param v1 first value
     * @param k2 second key
     * @param v2 second value
     * @param k3 third key
     * @param v3 third value
     * @param k4 fourth key
     * @param v4 fourth value
     * @param k5 fifth key
     * @param v5 fifth value
     * @return a {@code Map} containing the specified mappings
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5){
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Create an unmodifiable map containing 5 mappings.
     *
     * @param <K> the {@code Map}'s key type
     * @param <V> the {@code Map}'s value type
     * @param k1 first key
     * @param v1 first value
     * @param k2 second key
     * @param v2 second value
     * @param k3 third key
     * @param v3 third value
     * @param k4 fourth key
     * @param v4 fourth value
     * @param k5 fifth key
     * @param v5 fifth value
     * @param k6 sixth key
     * @param v6 sixth value
     * @return a {@code Map} containing the specified mappings
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6){
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        map.put(k6, v6);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Create an unmodifiable map containing 5 mappings.
     *
     * @param <K> the {@code Map}'s key type
     * @param <V> the {@code Map}'s value type
     * @param k1 first key
     * @param v1 first value
     * @param k2 second key
     * @param v2 second value
     * @param k3 third key
     * @param v3 third value
     * @param k4 fourth key
     * @param v4 fourth value
     * @param k5 fifth key
     * @param v5 fifth value
     * @param k6 sixth key
     * @param v6 sixth value
     * @param k7 seventh key
     * @param v7 seventh value
     * @return a {@code Map} containing the specified mappings
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7){
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        map.put(k6, v6);
        map.put(k7, v7);
        return Collections.unmodifiableMap(map);
    }
}
