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

package io.helidon.config;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.config.Config.Key;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.test.infra.RestoreSystemPropertiesExt;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.helidon.config.Config.Type.LIST;
import static io.helidon.config.Config.Type.OBJECT;
import static io.helidon.config.Config.Type.VALUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * General {@link Config} tests.
 *
 * @see ConfigObjectImplTest
 * @see ConfigListImplTest
 * @see ConfigMissingImplTest
 * @see ConfigLeafImplTest
 */
@ExtendWith(RestoreSystemPropertiesExt.class)
public class ConfigTest {

    private static final String TEST_SYS_PROP_NAME = "this_is_my_property-ConfigTest";
    private static final String TEST_SYS_PROP_VALUE = "This Is My SYS PROPS Value.";

    private static final String TEST_ENV_VAR_NAME = "FOO_BAR";
    private static final String TEST_ENV_VAR_VALUE = "mapped-env-value";

    private static final String OVERRIDE_NAME = "simple";
    private static final String OVERRIDE_ENV_VAR_VALUE = "unmapped-env-value";
    private static final String OVERRIDE_SYS_PROP_VALUE = "unmapped-sys-prop-value";

    private static final boolean LOG = false;

    private static final String OBJECT_VALUE_PREFIX = "object-";
    private static final String LIST_VALUE_PREFIX = "list-";

    private static void testKeyNotSet(Config config) {
        assertThat(config, not(nullValue()));
        assertThat(config.traverse().collect(Collectors.toList()), not(empty()));
        assertThat(config.get(TEST_SYS_PROP_NAME).type(), is(Config.Type.MISSING));
    }

    @Test
    public void testCreateKeyNotSet() {
        testKeyNotSet(Config.create());
    }

    @Test
    public void testBuilderDefaultConfigSourceKeyNotSet() {
        testKeyNotSet(Config.builder().build());
    }

    private void testKeyFromSysProps(Config config) {
        assertThat(config, not(nullValue()));
        assertThat(config.traverse().collect(Collectors.toList()), not(empty()));
        assertThat(config.get(TEST_SYS_PROP_NAME).asString(), is(ConfigValues.simpleValue(TEST_SYS_PROP_VALUE)));
    }

    @Test
    public void testCreateKeyFromSysProps() {
        System.setProperty(TEST_SYS_PROP_NAME, TEST_SYS_PROP_VALUE);

        testKeyFromSysProps(Config.create());
    }

    @Test
    public void testBuilderDefaultConfigSourceKeyFromSysProps() {
        System.setProperty(TEST_SYS_PROP_NAME, TEST_SYS_PROP_VALUE);

        testKeyFromSysProps(Config.builder().build());
    }

    private void testKeyFromEnvVars(Config config) {
        assertThat(config, not(nullValue()));
        assertThat(config.traverse().collect(Collectors.toList()), not(empty()));
        assertThat(config.get(ConfigSourceTest.TEST_ENV_VAR_NAME).asString(), is(ConfigValues.simpleValue(ConfigSourceTest.TEST_ENV_VAR_VALUE)));
    }

    @Test
    public void testKeyAndTypeAndGet() {
        Config config = createTestConfig(3);
        testKeyAndTypeAndGet(config);
    }

    private void testKeyAndTypeAndGet(Config config) {

        //root
        assertKeyNameAndType(config.get(""), "", "", OBJECT);
        assertKeyNameAndType(config.get("text-1"), "text-1", "text-1", VALUE);
        assertKeyNameAndType(config.get("object-1"), "object-1", "object-1", OBJECT);
        assertKeyNameAndType(config.get("list-1"), "list-1", "list-1", LIST);
        //object, level 2
        assertKeyNameAndType(config.get("object-1.text-2"), "object-1.text-2", "text-2", VALUE);
        assertKeyNameAndType(config.get("object-1.object-2"), "object-1.object-2", "object-2", OBJECT);
        assertKeyNameAndType(config.get("object-1.list-2"), "object-1.list-2", "list-2", LIST);
        //list, level 2
        assertKeyNameAndType(config.get("list-1.0"), "list-1.0", "0", VALUE);
        assertKeyNameAndType(config.get("list-1.1"), "list-1.1", "1", OBJECT);
        assertKeyNameAndType(config.get("list-1.2"), "list-1.2", "2", LIST);
        //object, level 3
        assertKeyNameAndType(config.get("list-1.1.text-3"), "list-1.1.text-3", "text-3", VALUE);
        assertKeyNameAndType(config.get("list-1.1.object-3"), "list-1.1.object-3", "object-3", OBJECT);
        assertKeyNameAndType(config.get("list-1.1.list-3"), "list-1.1.list-3", "list-3", LIST);
        //list, level 3
        assertKeyNameAndType(config.get("list-1.2.0"), "list-1.2.0", "0", VALUE);
        assertKeyNameAndType(config.get("list-1.2.1"), "list-1.2.1", "1", OBJECT);
        assertKeyNameAndType(config.get("list-1.2.2"), "list-1.2.2", "2", LIST);

        //gets
        assertThat(config.get("").get("").get(""), is(equalTo(config)));
        assertThat(config.get("object-1").get("text-2"), is(equalTo(config.get("object-1.text-2"))));
        assertThat(config.get("list-1.1").get("text-3"), is(equalTo(config.get("list-1.1.text-3"))));
        assertThat(config.get("list-1").get("2.2"), is(equalTo(config.get("list-1.2.2"))));
    }

    @Test
    public void testTraverseOnObjectNode() {
        Config config = createTestConfig(3).get("object-1");

        //full traverse -> count
        List<Key> allSubKeys = config
                .traverse()
                .peek(node -> log(node.type() + "\t" + node.key()))
                .map(Config::key)
                .collect(Collectors.toList());
        assertThat(allSubKeys, hasSize(33));
        log("--------");

        //traverse with predicate -> assert keys
        List<String> noListSubKeys = config
                // do NOT go into LIST nodes
                .traverse(node -> node.type() != Config.Type.LIST)
                .peek(node -> log("\"" + node.key() + "\", // " + node.type()))
                .map(Config::key)
                .map(Key::toString)
                .collect(Collectors.toList());

        assertThat(noListSubKeys, containsInAnyOrder(
                "object-1.object-2", //OBJECT
                "object-1.object-2.double-3", //VALUE
                "object-1.object-2.object-3", //OBJECT
                "object-1.object-2.long-3", //VALUE
                "object-1.object-2.text-3", //VALUE
                "object-1.object-2.bool-3", //VALUE
                "object-1.object-2.int-3", //VALUE
                "object-1.double-2", //VALUE
                "object-1.bool-2", //VALUE
                "object-1.long-2", //VALUE
                "object-1.text-2", //VALUE
                "object-1.int-2" //VALUE
        ));
    }

    @Test
    public void testTraverseOnListNode() {
        Config config = createTestConfig(3).get("list-1");

        //full traverse -> count
        List<Key> allSubKeys = config
                .traverse()
                .peek(node -> log(node.type() + "\t" + node.key()))
                .map(Config::key)
                .collect(Collectors.toList());
        assertThat(allSubKeys, hasSize(33));
        log("--------");

        //traverse with predicate -> assert keys
        List<String> noObjectSubKeys = config
                // do NOT go into OBJECT nodes
                .traverse(node -> node.type() != Config.Type.OBJECT)
                .peek(node -> log("\"" + node.key() + "\", // " + node.type()))
                .map(Config::key)
                .map(Key::toString)
                .collect(Collectors.toList());

        assertThat(noObjectSubKeys, containsInAnyOrder(
                "list-1.0", // VALUE
                "list-1.2", // LIST
                "list-1.2.0", // VALUE
                "list-1.2.2", // LIST
                "list-1.2.3", // VALUE
                "list-1.2.4", // VALUE
                "list-1.2.5", // VALUE
                "list-1.2.6", // VALUE
                "list-1.2.7", // LIST
                "list-1.2.7.0", // VALUE
                "list-1.2.7.1", // VALUE
                "list-1.2.7.2", // VALUE
                "list-1.3", // VALUE
                "list-1.4", // VALUE
                "list-1.5", // VALUE
                "list-1.6", // VALUE
                "list-1.7", // LIST
                "list-1.7.0", // VALUE
                "list-1.7.1", // VALUE
                "list-1.7.2" // VALUE
        ));
    }

    @Test
    public void testAsMapOnObjectNode() {
        Config config = createTestConfig(3).get("object-1");

        //full map -> count
        Map<String, String> allLeafs = config.asMap().get();
        allLeafs.forEach((key, value) -> log(key));

        assertThat(allLeafs.keySet(), hasSize(24));
        log("--------");

        //sub-map -> assert keys
        Map<String, String> subLeafs = config.get("object-2").asMap().get();
        subLeafs.forEach((key, value) -> log("\"" + key + "\","));
        assertThat(subLeafs.keySet(), containsInAnyOrder(
                "object-1.object-2.bool-3",
                "object-1.object-2.int-3",
                "object-1.object-2.double-3",
                "object-1.object-2.str-list-3.2",
                "object-1.object-2.str-list-3.1",
                "object-1.object-2.str-list-3.0",
                "object-1.object-2.text-3",
                "object-1.object-2.long-3"
        ));
    }

    @Test
    public void testAsMapOnListNode() {
        Config config = createTestConfig(3).get("list-1");

        //full map -> count
        Map<String, String> allLeafs = config.asMap().get();
        allLeafs.forEach((key, value) -> log(key));

        assertThat(allLeafs.keySet(), hasSize(24));
        log("--------");

        //sub-map -> assert keys
        Map<String, String> subLeafs = config.get("2").asMap().get();
        subLeafs.forEach((key, value) -> log("\"" + key + "\","));
        assertThat(subLeafs.keySet(), containsInAnyOrder(
                "list-1.2.0",
                "list-1.2.3",
                "list-1.2.5",
                "list-1.2.4",
                "list-1.2.6",
                "list-1.2.7.1",
                "list-1.2.7.2",
                "list-1.2.7.0"
        ));
    }

    @Test
    public void testAsPropertiesOnObjectNode() {
        Config config = createTestConfig(3).get("object-1");

        //full properties -> count
        Properties allLeafs = config.as(Properties.class).get();
        allLeafs.forEach((key, value) -> log(key));

        assertThat(allLeafs.keySet(), hasSize(24));
        log("--------");

        //sub-properties -> assert keys
        Properties subLeafs = config.get("object-2").as(Properties.class).get();
        subLeafs.forEach((key, value) -> log("\"" + key + "\","));
        assertThat(subLeafs.keySet(), containsInAnyOrder(
                "object-1.object-2.bool-3",
                "object-1.object-2.int-3",
                "object-1.object-2.double-3",
                "object-1.object-2.str-list-3.2",
                "object-1.object-2.str-list-3.1",
                "object-1.object-2.str-list-3.0",
                "object-1.object-2.text-3",
                "object-1.object-2.long-3"
        ));
    }

    @Test
    public void testAsPropertiesOnListNode() {
        Config config = createTestConfig(3).get("list-1");

        //full properties -> count
        Properties allLeafs = config.as(Properties.class).get();
        allLeafs.forEach((key, value) -> log(key));

        assertThat(allLeafs.keySet(), hasSize(24));
        log("--------");

        //sub-properties -> assert keys
        Properties subLeafs = config.get("2").as(Properties.class).get();
        subLeafs.forEach((key, value) -> log("\"" + key + "\","));
        assertThat(subLeafs.keySet(), containsInAnyOrder(
                "list-1.2.0",
                "list-1.2.3",
                "list-1.2.5",
                "list-1.2.4",
                "list-1.2.6",
                "list-1.2.7.1",
                "list-1.2.7.2",
                "list-1.2.7.0"
        ));
    }

    @Test
    public void testDetachOnObjectNode() {
        Config config = createTestConfig(3).detach();

        /////////////
        // 1ST DETACH
        {
            config = config.get("object-1").detach();
            {
                //detach & map
                Map<String, String> subLeafs = config.asMap().get();
                subLeafs.forEach((key, value) -> log("\"" + key + "\","));
                //expected size
                assertThat(subLeafs.keySet(), hasSize(24));
                //no key prefixed by 'object-1'
                assertThat(subLeafs.keySet().stream()
                                   .filter(key -> key.startsWith("object1"))
                                   .collect(Collectors.toList()),
                           is(empty()));
            }
            log("--------");
            {
                //detach & traverse
                List<String> subKeys = config
                        .traverse()
                        .peek(node -> log("\"" + node.key() + "\", // " + node.type()))
                        .map(Config::key)
                        .map(Key::toString)
                        .collect(Collectors.toList());
                //expected size
                assertThat(subKeys, hasSize(33));
                //no key prefixed by 'object-1'
                assertThat(subKeys.stream()
                                   .filter(key -> key.startsWith("object1"))
                                   .collect(Collectors.toList()),
                           is(empty()));
            }
        }

        /////////////
        // 2ND DETACH
        {
            config = config.get("object-2").detach();
            {
                //detach & map
                Map<String, String> subLeafs = config.asMap().get();
                subLeafs.forEach((key, value) -> log("\"" + key + "\","));
                assertThat(subLeafs.keySet(), containsInAnyOrder(
                        "bool-3",
                        "int-3",
                        "double-3",
                        "str-list-3.2",
                        "str-list-3.1",
                        "str-list-3.0",
                        "text-3",
                        "long-3"
                ));
            }
            log("--------");
            {
                //detach & traverse
                List<String> subKeys = config
                        .traverse()
                        .peek(node -> log("\"" + node.key() + "\", // " + node.type()))
                        .map(Config::key)
                        .map(Key::toString)
                        .collect(Collectors.toList());

                assertThat(subKeys, containsInAnyOrder(
                        "double-3", // VALUE
                        "object-3", // OBJECT
                        "str-list-3", // LIST
                        "str-list-3.0", // VALUE
                        "str-list-3.1", // VALUE
                        "str-list-3.2", // VALUE
                        "long-3", // VALUE
                        "text-3", // VALUE
                        "bool-3", // VALUE
                        "list-3", // LIST
                        "int-3" // VALUE
                ));
            }
        }
    }

    @Test
    public void testDetachOnListNode() {
        Config config = createTestConfig(3).detach();

        /////////////
        // 1ST DETACH
        {
            config = config.get("list-1").detach();
            {
                //detach & map
                Map<String, String> subLeafs = config.asMap().get();
                subLeafs.forEach((key, value) -> log("\"" + key + "\","));
                //expected size
                assertThat(subLeafs.keySet(), hasSize(24));
                //no key prefixed by 'object-1'
                assertThat(subLeafs.keySet().stream()
                                   .filter(key -> key.startsWith("list"))
                                   .collect(Collectors.toList()),
                           is(empty()));
            }
            log("--------");
            {
                //detach & traverse
                List<String> subKeys = config
                        .traverse()
                        .peek(node -> log("\"" + node.key() + "\", // " + node.type()))
                        .map(Config::key)
                        .map(Key::toString)
                        .collect(Collectors.toList());
                //expected size
                assertThat(subKeys, hasSize(33));
                //no key prefixed by 'object-1'
                assertThat(subKeys.stream()
                                   .filter(key -> key.startsWith("list"))
                                   .collect(Collectors.toList()),
                           is(empty()));
            }
        }
        log("========");

        /////////////
        // 2ND DETACH
        {
            config = config.get("2").detach();
            {
                //detach & map
                Map<String, String> subLeafs = config.asMap().get();
                subLeafs.forEach((key, value) -> log("\"" + key + "\","));
                assertThat(subLeafs.keySet(), containsInAnyOrder(
                        "0",
                        "3",
                        "4",
                        "5",
                        "6",
                        "7.0",
                        "7.1",
                        "7.2"
                ));
            }
            log("--------");
            {
                //detach & traverse
                List<String> subKeys = config
                        .traverse()
                        .peek(node -> log("\"" + node.key() + "\", // " + node.type()))
                        .map(Config::key)
                        .map(Key::toString)
                        .collect(Collectors.toList());

                assertThat(subKeys, containsInAnyOrder(
                        "0", // VALUE
                        "1", // OBJECT
                        "2", // LIST
                        "3", // VALUE
                        "4", // VALUE
                        "5", // VALUE
                        "6", // VALUE
                        "7", // LIST
                        "7.0", // VALUE
                        "7.1", // VALUE
                        "7.2" // VALUE
                ));
            }
        }
    }

    /**
     * Similar test is copied to in {@code config-hocon} module, {@code HoconConfigParserTest} class,
     * method {@code testConfigKeyEscapedNameComplex};
     * and in {@code integration-tests} module, {@code AbstractComplexConfigTest} class,
     * method {@code testConfigKeyEscapedNameComplex}.
     */
    @Test
    public void testConfigKeyEscapedNameComplex() {
        Config config = Config
                .builder(
                        ConfigSources.create(ObjectNode
                                     .builder()
                                     .addObject(Key.escapeName("oracle.com"), ObjectNode.builder()
                                             .addValue("prop1", "val1")
                                             .addValue("prop2", "val2")
                                             .build())
                                     .addObject("oracle", ObjectNode.builder()
                                             .addValue("com", "1")
                                             .addValue("cz", "2")
                                             .build())
                                     .build()))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableParserServices()
                .disableMapperServices()
                .disableFilterServices()
                .build();

        //key
        assertThat(config.get("oracle~1com.prop1").asString(), is(ConfigValues.simpleValue("val1")));
        assertThat(config.get("oracle~1com.prop2").asString(), is(ConfigValues.simpleValue("val2")));
        assertThat(config.get("oracle.com").asString(), is(ConfigValues.simpleValue("1")));
        assertThat(config.get("oracle.cz").asString(), is(ConfigValues.simpleValue("2")));

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
        Set<String> keys = config.traverse().map(Config::key).map(Key::toString).collect(Collectors.toSet());
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

    @ExtendWith(RestoreSystemPropertiesExt.class)
    @Test
    public void testConfigKeyEscapeUnescapeName() {
        testConfigKeyEscapeUnescapeName("", "");
        testConfigKeyEscapeUnescapeName("~", "~0");
        testConfigKeyEscapeUnescapeName(".", "~1");
        testConfigKeyEscapeUnescapeName("~.", "~0~1");
        testConfigKeyEscapeUnescapeName(".~", "~1~0");
        testConfigKeyEscapeUnescapeName("qwerty", "qwerty");
        testConfigKeyEscapeUnescapeName(".qwerty~", "~1qwerty~0");
        testConfigKeyEscapeUnescapeName("${qwerty}", "${qwerty}");
        testConfigKeyEscapeUnescapeName("${qwe.rty}", "${qwe~1rty}");
        testConfigKeyEscapeUnescapeName("${qwe.rty}.asd", "${qwe~1rty}~1asd");
    }

    @Test
    public void testComplexNodesWithSimpleValues() {
        /*
        This method uses variants of the methods for creating test configs and
        objects to assign values to the complex nodes (object- and list-type)
        and make sure the values are as expected.
         */
        final String valueQual = "xx"; // to help avoid confusion between key and value
        final String obj1Value = OBJECT_VALUE_PREFIX + valueQual + "-2";
        final String obj1_2Value = obj1Value + "-3";

        Config config = createTestConfig(3, valueQual);

        testKeyAndTypeAndGet(config);

        assertThat(config.get("object-1").asString(), is(ConfigValues.simpleValue(obj1Value)));
        assertThat(config.get("object-1.object-2").asString(), is(ConfigValues.simpleValue(obj1_2Value)));
        assertThat(config.get("list-1").asString(), is(ConfigValues.simpleValue(LIST_VALUE_PREFIX + valueQual + "-2")));
    }

    @Test
    void testImplicitSysPropAndEnvVarPrecedence() {
        System.setProperty(OVERRIDE_NAME, OVERRIDE_SYS_PROP_VALUE);
        System.setProperty(TEST_SYS_PROP_NAME, TEST_SYS_PROP_VALUE);

        Config config = Config.create();

        assertThat(config.get(TEST_SYS_PROP_NAME).asString().get(), is(TEST_SYS_PROP_VALUE));
        assertThat(config.get(TEST_ENV_VAR_NAME).asString().get(), is(TEST_ENV_VAR_VALUE));

        assertThat(config.get(OVERRIDE_NAME).asString().get(), is(OVERRIDE_SYS_PROP_VALUE));
    }

    @Test
    void testExplicitSysPropAndEnvVarPrecedence() {
        System.setProperty(OVERRIDE_NAME, OVERRIDE_SYS_PROP_VALUE);
        System.setProperty(TEST_SYS_PROP_NAME, TEST_SYS_PROP_VALUE);

        Config config = Config.builder()
                              .sources(ConfigSources.systemProperties(),
                                       ConfigSources.environmentVariables())
                              .build();

        assertThat(config.get(TEST_SYS_PROP_NAME).asString().get(), is(TEST_SYS_PROP_VALUE));
        assertThat(config.get(TEST_ENV_VAR_NAME).asString().get(), is(TEST_ENV_VAR_VALUE));

        assertThat(config.get(OVERRIDE_NAME).asString().get(), is(OVERRIDE_SYS_PROP_VALUE));

        config = Config.builder()
                       .sources(ConfigSources.environmentVariables(),
                                ConfigSources.systemProperties())
                       .build();

        assertThat(config.get(TEST_SYS_PROP_NAME).asString().get(), is(TEST_SYS_PROP_VALUE));
        assertThat(config.get(TEST_ENV_VAR_NAME).asString().get(), is(TEST_ENV_VAR_VALUE));

        assertThat(config.get(OVERRIDE_NAME).asString().get(), is(OVERRIDE_ENV_VAR_VALUE));
    }

    @Test
    void testExplicitEnvVarSourceAndImplicitSysPropSourcePrecedence() {
        System.setProperty(OVERRIDE_NAME, OVERRIDE_SYS_PROP_VALUE);
        System.setProperty(TEST_SYS_PROP_NAME, TEST_SYS_PROP_VALUE);

        Config config = Config.builder()
                              .sources(ConfigSources.environmentVariables())
                              .build();

        assertThat(config.get(TEST_SYS_PROP_NAME).asString().get(), is(TEST_SYS_PROP_VALUE));
        assertThat(config.get(TEST_ENV_VAR_NAME).asString().get(), is(TEST_ENV_VAR_VALUE));

        // Implicit sources always take precedence! (??)
        assertThat(config.get(OVERRIDE_NAME).asString().get(), is(OVERRIDE_SYS_PROP_VALUE));
    }

    @Test
    void testExplicitSysPropSourceAndImplicitEnvVarSourcePrecedence() {
        System.setProperty(OVERRIDE_NAME, OVERRIDE_SYS_PROP_VALUE);
        System.setProperty(TEST_SYS_PROP_NAME, TEST_SYS_PROP_VALUE);

        Config config = Config.builder()
                              .sources(ConfigSources.systemProperties())
                              .build();

        assertThat(config.get(TEST_SYS_PROP_NAME).asString().get(), is(TEST_SYS_PROP_VALUE));
        assertThat(config.get(TEST_ENV_VAR_NAME).asString().get(), is(TEST_ENV_VAR_VALUE));

        // Implicit sources always take precedence! (??)
        assertThat(config.get(OVERRIDE_NAME).asString().get(), is(OVERRIDE_ENV_VAR_VALUE));
    }

    private void testConfigKeyEscapeUnescapeName(String name, String escapedName) {
        assertThat(Key.escapeName(name), is(escapedName));
        assertThat(Key.unescapeName(escapedName), is(name));
    }

    private static void assertKeyNameAndType(Config node, String key, String name, Config.Type type) {
        assertThat(node.key().toString(), is(key));
        assertThat(node.name(), is(name));
        assertThat(node.type(), is(type));
    }

    public static Config createTestConfig(int maxLevels) {
        return createTestConfigBuilder(maxLevels)
                .build();
    }

    public static Config createTestConfig(int maxLevels, String valueQualifier) {
        return createTestConfigBuilder(maxLevels, valueQualifier)
                .build();
    }

    private static Config.Builder createTestConfigBuilder(int maxLevels) {
        return createTestConfigBuilder(createTestObject(1, maxLevels));
    }

    private static Config.Builder createTestConfigBuilder(
            int maxLevels, String valueQualifier) {
        return createTestConfigBuilder(createTestObject(1, maxLevels, valueQualifier));
    }

    private static Config.Builder createTestConfigBuilder(ObjectNode oNode) {
        return Config.builder()
                .sources(ConfigSources.create(oNode))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableParserServices()
                .disableMapperServices()
                .disableFilterServices();
    }

    private static ObjectNode createTestObject(int level, int maxLevels) {
        if (maxLevels < level) {
            return ObjectNode.empty();
        }
        return createTestObjectBuilder(level, maxLevels)
                .build();
    }

    private static ObjectNode createTestObject(
            int level, int maxLevels, String valueQualifier) {
        if (maxLevels < level) {
            return ObjectNode.empty();
        }
        return createTestObjectBuilder(level, maxLevels, valueQualifier)
                .value(OBJECT_VALUE_PREFIX + valueQualifier)
                .build();
    }

    private static ObjectNode.Builder createTestObjectBuilder(
            int level, int maxLevels) {
        return createTestObjectBuilder(
                level,
                createTestObject(level + 1, maxLevels),
                createTestList(level + 1, maxLevels));
    }

    private static ObjectNode.Builder createTestObjectBuilder(
            int level, int maxLevels, String valueQualifier) {
        String childValue = valueQualifier + "-" + Integer.toString(level + 1);
        return createTestObjectBuilder(
                level,
                createTestObject(level + 1, maxLevels, childValue),
                createTestList(level + 1, maxLevels, childValue));
    }

    private static ObjectNode.Builder createTestObjectBuilder(
            int level,
            ObjectNode testObject,
            ListNode testList) {
        return ObjectNode.builder()
                .addValue("text-" + level, "string value " + level)
                .addObject("object-" + level, testObject)
                .addList("list-" + level, testList)
                .addValue("bool-" + level, "true")
                .addValue("double-" + level, "123" + level + ".5678")
                .addValue("int-" + level, "214748364" + level)
                .addValue("long-" + level, "922337203685477580" + level)
                .addList("str-list-" + level, ListNode.builder()
                        .addValue("aaa-" + level)
                        .addValue("bbb-" + level)
                        .addValue("ccc-" + level)
                        .build());
    }

    private static ListNode createTestList(int level, int maxLevels) {
        if (maxLevels < level) {
            return ListNode.builder().build();
        }
        return createTestListBuilder(level, maxLevels)
                .build();
    }

    private static ListNode createTestList(int level, int maxLevels, String valueQualifier) {
        if (maxLevels < level) {
            return ListNode.builder().value(valueQualifier).build();
        }
        return createTestListBuilder(level, maxLevels)
                .value(LIST_VALUE_PREFIX + valueQualifier)
                .build();
    }

    private static ListNode.Builder createTestListBuilder(int level, int maxLevels) {
        return ListNode.builder()
                .addValue("string value " + level)
                .addObject(createTestObject(level + 1, maxLevels))
                .addList(createTestList(level + 1, maxLevels))
                .addValue("true")
                .addValue("123" + level + ".5678")
                .addValue("214748364" + level)
                .addValue("922337203685477580" + level)
                .addList(ListNode.builder()
                                 .addValue("aaa-" + level)
                                 .addValue("bbb-" + level)
                                 .addValue("ccc-" + level).build());
    }

    public static void waitFor(Supplier<Boolean> test, long maxMillis, long stepSleepMillis) throws InterruptedException {
        int repeat = 0;
        long deadline = System.nanoTime() + (maxMillis * 1_000_000);
        while (!test.get()) {
            TimeUnit.MILLISECONDS.sleep(stepSleepMillis);
            repeat++;
            if (deadline < System.nanoTime()) {
                log("! TIMEOUT, repeat #" + repeat);
                break;
            }
        }
        assertThat(test.get(), is(true));
    }

    public static <T> void waitForAssert(Supplier<T> actual, Matcher<T> matcher) throws InterruptedException {
        waitForAssert(actual, matcher, 1000, 10);
    }

    public static <T> void waitForAssert(Supplier<T> actual, Matcher<T> matcher, long maxMillis, long stepSleepMillis)
            throws InterruptedException {
        int repeat = 0;
        long deadline = System.nanoTime() + (maxMillis * 1_000_000);
        while (!matcher.matches(actual.get())) {
            TimeUnit.MILLISECONDS.sleep(stepSleepMillis);
            repeat++;
            if (deadline < System.nanoTime()) {
                log("! TIMEOUT, repeat #" + repeat);
                break;
            }
        }
        assertThat(actual.get(), matcher);
    }

    private static void log(Object msg) {
        if (LOG) {
            System.out.println(msg);
        }
    }

}
