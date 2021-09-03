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

package io.helidon.config.hocon;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.config.Config;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.ConfigSources;
import io.helidon.config.MissingValueException;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParser.Content;
import io.helidon.config.spi.ConfigParserException;

import com.typesafe.config.ConfigResolveOptions;
import org.junit.jupiter.api.Test;

import static io.helidon.config.ConfigValues.simpleValue;
import static io.helidon.config.testing.ValueNodeMatcher.valueNode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link HoconConfigParser}.
 */
public class HoconConfigParserTest {

    @Test
    public void testResolveEnabled() {
        ConfigParser parser = HoconConfigParser.create();
        ObjectNode node = parser.parse((StringContent) () -> ""
                + "aaa = 1 \n"
                + "bbb = ${aaa} \n"
                + "ccc = \"${aaa}\" \n"
                + "ddd = ${?zzz}");

        assertThat(node.entrySet(), hasSize(3));
        assertThat(node.get("aaa"), valueNode("1"));
        assertThat(node.get("bbb"), valueNode("1"));
        assertThat(node.get("ccc"), valueNode("${aaa}"));
    }

    @Test
    public void testResolveDisabled() {
        ConfigParserException cpe = assertThrows(ConfigParserException.class, () -> {
            ConfigParser parser = HoconConfigParser.builder().disableResolving().build();
            parser.parse((StringContent) () -> ""
                    + "aaa = 1 \n"
                    + "bbb = ${aaa} \n"
                    + "ccc = \"${aaa}\" \n"
                    + "ddd = ${?zzz}");
        });

        assertThat(cpe.getMessage(),
                   stringContainsInOrder(List.of(
                           "Cannot read from source",
                           "substitution not resolved",
                           "${aaa}")));
        assertThat(cpe.getCause(), instanceOf(com.typesafe.config.ConfigException.NotResolved.class));
    }

    @Test
    public void testResolveEnabledEnvVar() {
        ConfigParser parser = HoconConfigParser.create();
        ObjectNode node = parser.parse((StringContent) () -> "env-var = ${HOCON_TEST_PROPERTY}");

        assertThat(node.entrySet(), hasSize(1));
        assertThat(node.get("env-var"), valueNode("This Is My ENV VARS Value."));
    }

    @Test
    public void testResolveEnabledEnvVarDisabled() {
        ConfigParserException cpe = assertThrows(ConfigParserException.class, () -> {
            ConfigParser parser = HoconConfigParser.builder()
                    .resolveOptions(ConfigResolveOptions.noSystem())
                    .build();
            parser.parse((StringContent) () -> "env-var = ${HOCON_TEST_PROPERTY}");
        });

        assertThat(cpe.getMessage(),
                   stringContainsInOrder(List.of(
                           "Cannot read from source",
                           "not resolve substitution ",
                           "${HOCON_TEST_PROPERTY}")));
        assertThat(cpe.getCause(), instanceOf(com.typesafe.config.ConfigException.UnresolvedSubstitution.class));
    }

    @Test
    public void testEmpty() {
        HoconConfigParser parser = HoconConfigParser.create();
        ObjectNode node = parser.parse((StringContent) () -> "");

        assertThat(node.entrySet(), hasSize(0));
    }

    @Test
    public void testSingleValue() {
        HoconConfigParser parser = HoconConfigParser.create();
        ObjectNode node = parser.parse((StringContent) () -> "aaa = bbb");

        assertThat(node.entrySet(), hasSize(1));
        assertThat(node.get("aaa"), valueNode("bbb"));
    }

    @Test
    public void testStringListValue() {
        HoconConfigParser parser = HoconConfigParser.create();
        ObjectNode node = parser.parse((StringContent) () -> "aaa = [ bbb, ccc, ddd ]");

        assertThat(node.entrySet(), hasSize(1));

        List<ConfigNode> aaa = ((ListNode) node.get("aaa"));
        assertThat(aaa, hasSize(3));
        assertThat(aaa.get(0), valueNode("bbb"));
        assertThat(aaa.get(1), valueNode("ccc"));
        assertThat(aaa.get(2), valueNode("ddd"));
    }

    @Test
    public void testComplexValue() {
        HoconConfigParser parser = HoconConfigParser.create();
        ObjectNode node = parser.parse((StringContent) () -> ""
                + "aaa =  \"bbb\"\n"
                + "arr = [ bbb, 13, true, 3.14159 ] \n"
                + "obj1 = { aaa = bbb, ccc = false } \n"
                + "arr2 = [ aaa, false, { bbb = 3.14159, c = true }, { ooo { ppp { xxx = yyy }}} ]"
        );

        assertThat(node.entrySet(), hasSize(4));
        assertThat(node.get("aaa"), valueNode("bbb"));
        assertThat(((ObjectNode) node.get("obj1")).get("aaa"), valueNode("bbb"));
        assertThat(((ObjectNode) node.get("obj1")).get("ccc"), valueNode("false"));
        //arr
        List<ConfigNode> arr = ((ListNode) node.get("arr"));
        assertThat(arr, hasSize(4));
        assertThat(arr.get(0), valueNode("bbb"));
        assertThat(arr.get(1), valueNode("13"));
        assertThat(arr.get(2), valueNode("true"));
        assertThat(arr.get(3), valueNode("3.14159"));
        //arr2
        List<ConfigNode> arr2 = ((ListNode) node.get("arr2"));
        assertThat(arr2, hasSize(4));
        assertThat(arr2.get(0), valueNode("aaa"));
        assertThat(arr2.get(1), valueNode("false"));
        //arr2[2]
        final Map<String, ConfigNode> arr2_2 = ((ObjectNode) arr2.get(2));
        assertThat(arr2_2.entrySet(), hasSize(2));
        assertThat(arr2_2.get("bbb"), valueNode("3.14159"));
        assertThat(arr2_2.get("c"), valueNode("true"));
        //arr2[3]
        final Map<String, ConfigNode> arr2_3 = ((ObjectNode) arr2.get(3));
        assertThat(arr2_3.entrySet(), hasSize(1));
        assertThat(((ObjectNode) ((ObjectNode) arr2_3.get("ooo")).get("ppp")).get("xxx"), valueNode("yyy"));
    }

    /**
     * This is same test as in {@code config} module, {@code ConfigTest} class, method {@code testConfigKeyEscapedNameComplex}.
     */
    @Test
    public void testConfigKeyEscapedNameComplex() {
        String JSON = ""
                + "{\n"
                + "    \"oracle.com\": {\n"
                + "        \"prop1\": \"val1\",\n"
                + "        \"prop2\": \"val2\"\n"
                + "    },\n"
                + "    \"oracle\": {\n"
                + "        \"com\": \"1\",\n"
                + "        \"cz\": \"2\"\n"
                + "    }\n"
                + "}\n";

        Config config = Config
                .builder(ConfigSources.create(JSON, HoconConfigParser.MEDIA_TYPE_APPLICATION_JSON))
                .addParser(HoconConfigParser.create())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableParserServices()
                .disableMapperServices()
                .disableFilterServices()
                .build();

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

    @Test
    public void testGetSupportedMediaTypes() {
        HoconConfigParser parser = HoconConfigParser.create();

        assertThat(parser.supportedMediaTypes(), is(not(empty())));
    }

    @Test
    public void testCustomTypeMapping() {
        Config config = Config
                .builder(ConfigSources.create(AppType.DEF, HoconConfigParser.MEDIA_TYPE_APPLICATION_JSON))
                .addParser(HoconConfigParser.create())
                .addMapper(AppType.class, new AppTypeMapper())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableParserServices()
                .disableMapperServices()
                .disableFilterServices()
                .build();
        AppType app = config.get("app").as(AppType.class).get();
        assertThat("greeting", app.getGreeting(), is(AppType.GREETING));
        assertThat("name", app.getName(), is(AppType.NAME));
        assertThat("page-size", app.getPageSize(), is(AppType.PAGE_SIZE));
        assertThat("basic-range", app.getBasicRange(), is(AppType.BASIC_RANGE));

    }

    @Test
    void testParserFromJson() {
        Config config = Config.builder()
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .disableParserServices()
                .addParser(HoconConfigParser.create())
                .addSource(ConfigSources.classpath("config.json"))
                .build();

        Optional<String> property = config.get("oracle.com").asString().asOptional();
        assertThat(property, is(Optional.of("1")));

        property = config.get("nulls.null").asString().asOptional();
        assertThat(property, is(Optional.of("")));

        List<String> properties = config.get("nulls-array").asList(String.class).get();
        assertThat(properties, hasItems("test", ""));
    }

    //
    // helper
    //

    @FunctionalInterface
    private interface StringContent extends Content {
        @Override
        default Optional<String> mediaType() {
            return Optional.of(HoconConfigParser.MEDIA_TYPE_APPLICATION_HOCON);
        }

        @Override
        default InputStream data() {
            return new ByteArrayInputStream(getContent().getBytes(StandardCharsets.UTF_8));
        }

        @Override
        default Charset charset() {
            return StandardCharsets.UTF_8;
        }

        String getContent();
    }

    public static class AppType {

        private static final String GREETING = "Hello";
        private static final String NAME = "Demo";
        private static final int PAGE_SIZE = 20;
        private static final List<Integer> BASIC_RANGE = List.of(-20, 20);

        static final String DEF = ""
                + "app {\n"
                + "  greeting = \"" + GREETING + "\"\n"
                + "  name = \"" + NAME + "\"\n"
                + "  page-size = " + PAGE_SIZE + "\n"
                + "  basic-range = [ " + BASIC_RANGE.get(0) + ", " + BASIC_RANGE.get(1) + " ]\n"
                + "  storagePassphrase = \"${AES=thisIsEncriptedPassphrase}\""
                + "}";

        private final String greeting;
        private final String name;
        private final int pageSize;
        private final List<Integer> basicRange;
        private final String storagePassphrase;

        public AppType(
                String name,
                String greeting,
                int pageSize,
                List<Integer> basicRange,
                String storagePassphrase) {
            this.name = name;
            this.greeting = greeting;
            this.pageSize = pageSize;
            this.basicRange = copyBasicRange(basicRange);
            this.storagePassphrase = storagePassphrase;
        }

        private List<Integer> copyBasicRange(List<Integer> source) {
            return (source != null) ? new ArrayList<>(source) : Collections.emptyList();
        }

        public String getGreeting() {
            return greeting;
        }

        public String getName() {
            return name;
        }

        public int getPageSize() {
            return pageSize;
        }

        public List<Integer> getBasicRange() {
            return basicRange;
        }

        public String getStoragePassphrase() {
            return storagePassphrase;
        }
    }

    private static class AppTypeMapper implements Function<Config, AppType> {

        @Override
        public AppType apply(Config config) throws ConfigMappingException, MissingValueException {

            return new AppType(
                    config.get("name").asString().get(),
                    config.get("greeting").asString().get(),
                    config.get("page-size").asInt().get(),
                    config.get("basic-range").asList(Integer.class).get(),
                    config.get("storagePassphrase").asString().get()
            );
        }
    }
}
