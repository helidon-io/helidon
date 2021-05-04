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

package io.helidon.config.tests;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.GenericType;
import io.helidon.config.Config;
import io.helidon.config.ConfigMappers;
import io.helidon.config.ConfigSources;
import io.helidon.config.ConfigValue;
import io.helidon.config.spi.ConfigParser;

import org.junit.jupiter.api.Test;

import static io.helidon.config.ConfigValues.simpleValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * Provides complete Config API test that is supposed to be used to test appropriate config parsers.
 */
public abstract class AbstractComplexConfigTest {

    protected final Config getConfig() {
        return Config.builder()
                .sources(ConfigSources.classpath(getClasspathResourceName()))
                .addParser(createConfigParser())
                .build();
    }

    protected final Config getMissingConfig() {
        return getConfig().get("non-existing-node");
    }

    protected abstract String getClasspathResourceName();

    protected abstract ConfigParser createConfigParser();

    //
    // text
    //

    private void testString(Config node, String expectedString) {
        ConfigValue<String> expected = simpleValue(expectedString);
        assertThat(node.asString(), is(expected));
        assertThat(node.as(String.class), is(expected));
        assertThat(node.asString().as(String::toString), is(expected));
    }

    private void testString(String key, String expected) {
        testString(getConfig().get(key), expected);
    }

    private void testStringList(String key, String... expected) {
        assertThat(getConfig().get(key).asList(String.class).get(), contains(expected));
        assertThat(getConfig().get(key).asList(config -> config.asString().get()).get(), contains(expected));
    }

    private void testString(String prefix) {
        final String empty = "";
        final String text = "string value";

        testString(prefix + "empty", empty);
        testString(prefix + "text", text);
        testStringList(prefix + "array", empty, text);
    }

    @Test
    public void testStringInRoot() {
        testString("text-");
    }

    @Test
    public void testStringInObject() {
        testString("text.");
    }

    //
    // boolean
    //

    private void testBoolean(Config node, Boolean expected) {
        assertThat(node.asBoolean().get(), is(expected));
        assertThat(node.as(Boolean.class).get(), is(expected));
        assertThat(node.asString().as(ConfigMappers::toBoolean).get(), is(expected));

        assertThat(node.as(Boolean.class).asOptional().get(), is(expected));
        assertThat(node.asString().as(ConfigMappers::toBoolean).asOptional().get(), is(expected));
    }

    private void testBoolean(String key, Boolean expected) {
        testBoolean(getConfig().get(key), expected);
    }

    private void testBooleanList(String key, Boolean... expected) {
        assertThat(getConfig().get(key).asList(Boolean.class).get(), contains(expected));
    }

    private void testBool(String prefix) {
        final boolean n = false;
        final boolean p = true;

        testBoolean(prefix + "n", n);
        testBoolean(prefix + "p", p);
        testBooleanList(prefix + "array", n, p);
    }

    @Test
    public void testBoolInRoot() {
        testBool("bool-");
    }

    @Test
    public void testBoolInObject() {
        testBool("bool.");
    }

    //
    // int
    //

    private void testInt(Config node, int expected) {
        assertThat(node.asInt().get(), is(expected));
        assertThat(node.as(Integer.class).get(), is(expected));
        assertThat(node.asString().as(ConfigMappers::toInt).get(), is(expected));
    }

    private void testInt(String key, int expected) {
        testInt(getConfig().get(key), expected);
    }

    private void testIntList(String key, Integer... expected) {
        assertThat(getConfig().get(key).asList(Integer.class).get(), contains(expected));
    }

    private void testInt(String prefix) {
        final int n = Integer.MIN_VALUE;
        final int z = 0;
        final int p = Integer.MAX_VALUE;

        testInt(prefix + "n", n);
        testInt(prefix + "z", z);
        testInt(prefix + "p", p);
        testIntList(prefix + "array", n, z, p);
    }

    @Test
    public void testIntInRoot() {
        testInt("int-");
    }

    @Test
    public void testIntInObject() {
        testInt("int.");
    }

    //
    // long
    //

    private void testLong(Config node, long expected) {
        ConfigValue<Long> expectedValue = simpleValue(expected);
        assertThat(node.asLong(), is(expectedValue));
        assertThat(node.as(Long.class), is(expectedValue));
        assertThat(node.asString().as(ConfigMappers::toLong), is(expectedValue));
    }

    private void testLong(String key, long expected) {
        testLong(getConfig().get(key), expected);
    }

    private void testLongList(String key, Long... expected) {
        assertThat(getConfig().get(key).asList(Long.class).get(), contains(expected));
    }

    private void testLong(String prefix) {
        final long n = Long.MIN_VALUE;
        final long z = 0;
        final long p = Long.MAX_VALUE;

        testLong(prefix + "n", n);
        testLong(prefix + "z", z);
        testLong(prefix + "p", p);
        testLongList(prefix + "array", n, z, p);
    }

    @Test
    public void testLongInRoot() {
        testLong("long-");
    }

    @Test
    public void testLongInObject() {
        testLong("long.");
    }

    //
    // double
    //

    private void testDouble(Config node, double expectedDouble) {
        ConfigValue<Double> expected = simpleValue(expectedDouble);
        assertThat(node.asDouble(), is(expected));
        assertThat(node.as(Double.class), is(expected));
        assertThat(node.as(new GenericType<Double>(){}), is(expected));
        assertThat(node.asString().as(ConfigMappers::toDouble), is(expected));
    }

    private void testDouble(String key, double expected) {
        testDouble(getConfig().get(key), expected);
    }

    private void testDoubleList(String key, Double... expected) {
        assertThat(getConfig().get(key).asList(Double.class).get(), contains(expected));
    }

    private void testDouble(String prefix) {
        final double n = -1234.5678;
        final double z = 0;
        final double p = 1234.5678;

        testDouble(prefix + "n", n);
        testDouble(prefix + "z", z);
        testDouble(prefix + "p", p);
        testDoubleList(prefix + "array", n, z, p);
    }

    @Test
    public void testDoubleInRoot() {
        testDouble("double-");
    }

    @Test
    public void testDoubleInObject() {
        testDouble("double.");
    }

    //
    // URI
    //

    private void testUri(Config node, URI expectedUri) {
        ConfigValue<URI> expected = simpleValue(expectedUri);

        assertThat(node.as(URI.class), is(expected));
        assertThat(node.asString().as(ConfigMappers::toUri), is(expected));
    }

    private void testUri(String key, URI expected) {
        testUri(getConfig().get(key), expected);
    }

    private void testUriList(String key, URI... expected) {
        assertThat(getConfig().get(key).asList(URI.class).get(), contains(expected));
    }

    private void testUri(String prefix) {
        final URI localhost = URI.create("http://localhost");

        testUri(prefix + "localhost", localhost);
        testUriList(prefix + "array", localhost, localhost, localhost);
    }

    @Test
    public void testUriInRoot() {
        testUri("uri-");
    }

    @Test
    public void testUriInObject() {
        testUri("uri.");
    }

    //
    // mixed list
    //

    private void testMixedList(List<Config> list) {
        assertThat(list, hasSize(3));

        testString(list.get(0).asNodeList().get().get(0), "string value");
        testBoolean(list.get(0).asNodeList().get().get(1).get("p"), true);
        testInt(list.get(1), Integer.MIN_VALUE);
        testLong(list.get(2).get("o1.p"), Long.MAX_VALUE);
        testDouble(list.get(2).get("o1.o2.n"), -1234.5678);
        testUri(list.get(2).get("o1.o2.o3.localhost"), URI.create("http://localhost"));
    }

    private void testMixedList(String prefix) {
        testMixedList(getConfig().get(prefix + "array").asNodeList().get());
    }

    @Test
    public void testMixedListInRoot() {
        testMixedList("mixed-");
    }

    @Test
    public void testMixedListInObject() {
        testMixedList("mixed.");
    }

    //
    // defaults
    //

    @Test
    public void testStringDefault() {
        Config node = getMissingConfig();
        String defaultValue = "default-value";
        String expected = defaultValue;

        assertThat(node.asString().orElse(defaultValue), is(expected));
        assertThat(node.as(String.class).orElse(defaultValue), is(expected));
    }

    @Test
    public void testStringListDefault() {
        Config node = getMissingConfig();
        List<String> defaultValue = Arrays.asList("def-1", "def-2", "def-3");
        String[] expected = defaultValue.toArray(new String[0]);

        assertThat(node.asList(String.class).orElse(defaultValue), contains(expected));
        assertThat(node.asList(aConfig -> aConfig.asString().get()).orElse(defaultValue), contains(expected));
    }

    @Test
    public void testBooleanDefault() {
        Config node = getMissingConfig();
        Boolean defaultValue = true;
        Boolean expected = defaultValue;

        assertThat(node.asBoolean().orElse(defaultValue), is(expected));
        assertThat(node.as(Boolean.class).orElse(defaultValue), is(expected));
        assertThat(node.asString().as(ConfigMappers::toBoolean).orElse(defaultValue), is(expected));

        assertThat(node.as(Boolean.class).orElse(defaultValue), is(expected));
    }

    @Test
    public void testBooleanListDefault() {
        Config node = getMissingConfig();
        List<Boolean> defaultValue = Arrays.asList(true, false, true);
        Boolean[] expected = defaultValue.toArray(new Boolean[0]);

        assertThat(node.asList(Boolean.class).orElse(defaultValue), contains(expected));
    }

    @Test
    public void testIntDefault() {
        Config node = getMissingConfig();
        int expected = 42;
        int defaultValue = expected;

        assertThat(node.asInt().orElse(defaultValue), is(expected));
        assertThat(node.as(Integer.class).orElse(defaultValue), is(expected));
        assertThat(node.asString().as(ConfigMappers::toInt).orElse(defaultValue), is(expected));

        assertThat(node.asInt().orElse(defaultValue), is(expected));
        assertThat(node.as(Integer.class).orElse(defaultValue), is(expected));
    }

    @Test
    public void testIntListDefault() {
        Config node = getMissingConfig();
        List<Integer> defaultValue = Arrays.asList(Integer.MIN_VALUE, 0, Integer.MAX_VALUE);
        Integer[] expected = defaultValue.toArray(new Integer[0]);

        assertThat(node.asList(Integer.class).orElse(defaultValue), contains(expected));
    }

    @Test
    public void testLongDefault() {
        Config node = getMissingConfig();
        long expected = 42;
        long defaultValue = expected;

        assertThat(node.asLong().orElse(defaultValue), is(expected));
        assertThat(node.as(Long.class).orElse(defaultValue), is(expected));
        assertThat(node.asString().as(ConfigMappers::toLong).orElse(defaultValue), is(expected));

        assertThat(node.asLong().orElse(defaultValue), is(expected));
        assertThat(node.as(Long.class).orElse(defaultValue), is(expected));
    }

    @Test
    public void testLongListDefault() {
        Config node = getMissingConfig();
        List<Long> defaultValue = Arrays.asList(Long.MIN_VALUE, 0L, Long.MAX_VALUE);
        Long[] expected = defaultValue.toArray(new Long[0]);

        assertThat(node.asList(Long.class).orElse(defaultValue), contains(expected));
    }

    @Test
    public void testDoubleDefault() {
        Config node = getMissingConfig();
        double expected = -1234.5678;
        double defaultValue = expected;

        assertThat(node.asDouble().orElse(defaultValue), is(expected));
        assertThat(node.as(Double.class).orElse(defaultValue), is(expected));
        assertThat(node.asString().as(ConfigMappers::toDouble).orElse(defaultValue), is(expected));

        assertThat(node.asDouble().orElse(defaultValue), is(expected));
        assertThat(node.as(Double.class).orElse(defaultValue), is(expected));
    }

    @Test
    public void testDoubleListDefault() {
        Config node = getMissingConfig();
        List<Double> defaultValue = Arrays.asList(-1234.5678, 0.0, 1234.5678);
        Double[] expected = defaultValue.toArray(new Double[0]);

        assertThat(node.asList(Double.class).orElse(defaultValue), contains(expected));
    }

    @Test
    public void testUriDefault() {
        Config node = getMissingConfig();
        URI expected = URI.create("http://localhost");
        URI defaultValue = expected;

        assertThat(node.as(URI.class).orElse(defaultValue), is(expected));
        assertThat(node.asString().as(ConfigMappers::toUri).orElse(defaultValue), is(expected));

        assertThat(node.as(URI.class).orElse(defaultValue), is(expected));
        assertThat(node.asString().as(ConfigMappers::toUri).orElse(defaultValue), is(expected));
    }

    @Test
    public void testUriListDefault() {
        Config node = getMissingConfig();
        List<URI> defaultValue = Arrays.asList(URI.create("http://localhost"), URI.create("http://localhost"));
        URI[] expected = defaultValue.toArray(new URI[0]);

        assertThat(node.asList(URI.class).orElse(defaultValue), contains(expected));
    }

    @Test
    public void testMixedListDefault() {
        Config node = getMissingConfig();
        List<Config> defaultValue = Arrays.asList(Config.create(), Config.create());
        Config[] expected = defaultValue.toArray(new Config[0]);

        assertThat(node.asNodeList().orElse(defaultValue), contains(expected));
        assertThat(node.asList(Config.class).orElse(defaultValue), contains(expected));
    }

    //
    // children
    //

    @Test
    public void testChildren() {
        Set<String> expectedKeys = new HashSet<>();
        expectedKeys.add("tree.single1");
        expectedKeys.add("tree.array1");
        expectedKeys.add("tree.object1");

        List<String> unexpectedKeys = getConfig().get("tree")
                .asNodeList()
                .get()
                .stream()
                .filter(node -> !expectedKeys.remove(node.key().toString()))
                .map(Config::key)
                .map(Config.Key::toString)
                .collect(Collectors.toList());

        assertThat("Unvisited keys during traversing.", expectedKeys, is(empty()));
        assertThat("Unexpected keys during traversing.", unexpectedKeys, is(empty()));
    }

    //
    // traverse
    //

    @Test
    public void testTraverse() {
        Set<String> expectedKeys = new HashSet<>();
        expectedKeys.add("tree.single1");
        expectedKeys.add("tree.array1");
        expectedKeys.add("tree.array1.0");
        expectedKeys.add("tree.array1.1");
        expectedKeys.add("tree.array1.2");
        expectedKeys.add("tree.object1");
        expectedKeys.add("tree.object1.single2");
        expectedKeys.add("tree.object1.array2");
        expectedKeys.add("tree.object1.array2.0");
        expectedKeys.add("tree.object1.array2.1");
        expectedKeys.add("tree.object1.array2.2");

        List<String> unexpectedKeys = getConfig().get("tree").traverse()
                .filter(node -> !expectedKeys.remove(node.key().toString()))
                .map(Config::key)
                .map(Config.Key::toString)
                .collect(Collectors.toList());

        assertThat("Unvisited keys during traversing.", expectedKeys, is(empty()));
        assertThat("Unexpected keys during traversing.", unexpectedKeys, is(empty()));
    }

    //
    // escaped key name
    //

    /**
     * This is same test as in {@code config} module, {@code ConfigTest} class, method {@code testConfigKeyEscapedNameComplex}.
     */
    @Test
    public void testConfigKeyEscapedNameComplex() {
        Config config = getConfig().get("escaped").detach();

        //key
        assertThat(config.get("oracle~1com.prop1").asString(), is(simpleValue("val1")));
        assertThat(config.get("oracle~1com.prop2").asString(), is(simpleValue("val2")));
        assertThat(config.get("oracle.com").asString(), is(simpleValue("1")));
        assertThat(config.get("oracle.cz").asString(), is(simpleValue("2")));

        //name
        assertThat(config.get("oracle~1com").name(), is("oracle.com"));
        assertThat(config.get("oracle~1com.prop1").name(), is("prop1"));
        assertThat(config.get("oracle~1com.prop2").name(), is("prop2"));
        assertThat(config.get("oracle").name(), is("oracle"));
        assertThat(config.get("oracle.com").name(), is("com"));
        assertThat(config.get("oracle.cz").name(), is("cz"));

        //child nodes
        List<Config> children = config.asNodeList().get();
        assertThat(children, hasSize(2));
        assertThat(children.stream().map(Config::name).collect(Collectors.toSet()),
                   containsInAnyOrder("oracle.com", "oracle"));

        //traverse
        Set<String> keys = config.traverse().map(Config::key).map(Config.Key::toString).collect(Collectors.toSet());
        assertThat(keys, hasSize(6));
        assertThat(keys, containsInAnyOrder("oracle~1com", "oracle~1com.prop1", "oracle~1com.prop2",
                                            "oracle", "oracle.com", "oracle.cz"));

        //map
        Map<String, String> map = config.asMap().get();
        assertThat(map.keySet(), hasSize(4));
        assertThat(map.get("oracle~1com.prop1"), is("val1"));
        assertThat(map.get("oracle~1com.prop2"), is("val2"));
        assertThat(map.get("oracle.com"), is("1"));
        assertThat(map.get("oracle.cz"), is("2"));
    }

}
