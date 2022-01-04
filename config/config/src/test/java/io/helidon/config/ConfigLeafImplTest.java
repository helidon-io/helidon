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

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.helidon.config.Config.Type.VALUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link Config} API in case the node is {@link Config.Type#VALUE} type, i.e. {@link ConfigLeafImpl}.
 */
public class ConfigLeafImplTest extends AbstractConfigImplTestBase {

    public static Stream<TestContext> initParams() {
        return Stream.of(
                // use root config properties
                new TestContext("", 1, false),
                new TestContext("", 1, true),
                // and object properties
                new TestContext("object-1", 2, false),
                new TestContext("object-1", 2, true),
                // and sub-object properties
                new TestContext("object-1.object-2", 3, false),
                new TestContext("object-1.object-2", 3, true),
                // and object in a list properties
                new TestContext("list-1.1", 3, false),
                new TestContext("list-1.1", 3, true)
        );
    }

    @ParameterizedTest
    @MethodSource("initParams")
    public void testToArray(TestContext context) {
        init(context);
        String[] array = ConfigLeafImpl.toArray("a,b,");
        assertThat(array, arrayContaining("a", "b", ""));

        array = ConfigLeafImpl.toArray("large:cheese\\,mushroom,medium:chicken,small:pepperoni");
        assertThat(array, arrayContaining("large:cheese,mushroom", "medium:chicken", "small:pepperoni"));

        array = ConfigLeafImpl.toArray("microservice,microprofile,m\\,f,microservice");
        assertThat(array, arrayContaining("microservice", "microprofile", "m,f", "microservice"));

        array = ConfigLeafImpl.toArray(",a,b");
        assertThat(array, arrayContaining("", "a", "b"));

        array = ConfigLeafImpl.toArray("a\\,");
        assertThat(array, arrayContaining("a,"));

        array = ConfigLeafImpl.toArray("\\,");
        assertThat(array, arrayContaining(","));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTypeExists(TestContext context) {
        init(context);
        assertThat(config("text-" + level()).exists(), is(true));
        assertThat(config("text-" + level()).type().exists(), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTypeIsLeaf(TestContext context) {
        init(context);
        assertThat(config("text-" + level()).isLeaf(), is(true));
        assertThat(config("text-" + level()).type().isLeaf(), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testValue(TestContext context) {
        init(context);
        assertValue(key -> config(key).asString().get());
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalIntFailing(TestContext context) {
        init(context);
        getConfigAndExpectException("text-" + level(), config -> config.asInt().get());
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalLongFailing(TestContext context) {
        init(context);
        getConfigAndExpectException("text-" + level(), config -> config.asLong().get());
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalDoubleFailing(TestContext context) {
        init(context);
        getConfigAndExpectException("text-" + level(), config -> config.asDouble().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAs(TestContext context) {
        init(context);
        assertBean(key -> config(key).as(ValueConfigBean::fromConfig).get(), "fromConfig");
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsFailing(TestContext context) {
        init(context);
        getConfigAndExpectException("text-" + level(), aConfig -> aConfig.as(NoMapperConfigBean.class).get()); //no mapper
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsList(TestContext context) {
        init(context);
        assertThat(config("text-" + level()).asList(ValueConfigBean::fromConfig).get(),
                   contains(ValueConfigBean.utFromConfig("string value " + level())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsString(TestContext context) {
        init(context);
        assertValue(key -> config(key).asString().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsBoolean(TestContext context) {
        init(context);
        assertThat(config("bool-" + level()).asBoolean().get(), is(true));
        assertThat(config("text-" + level()).asBoolean().get(), is(false));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsInt(TestContext context) {
        init(context);
        assertThat(config("int-" + level()).asInt().get(), is(2147483640 + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsIntFailing(TestContext context) {
        init(context);
        getConfigAndExpectException("text-" + level(), config -> config.asInt().get());
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsIntWithDefaultFailing(TestContext context) {
        init(context);
        getConfigAndExpectException("text-" + level(), config -> config.asInt().orElse(42));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLong(TestContext context) {
        init(context);
        assertThat(config("long-" + level()).asLong().get(),
                   is(9223372036854775800L + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLongFailing(TestContext context) {
        init(context);
        getConfigAndExpectException("text-" + level(), config -> config.asLong().get());
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLongWithDefaultFailing(TestContext context) {
        init(context);
        getConfigAndExpectException("text-" + level(), config -> config.asLong().orElse(42L));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDouble(TestContext context) {
        init(context);
        assertThat(config("double-" + level()).asDouble().get(), is(1230.5678 + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDoubleFailing(TestContext context) {
        init(context);
        getConfigAndExpectException("text-" + level(), config -> config.asDouble().get());
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDoubleWithDefaultFailing(TestContext context) {
        init(context);
        getConfigAndExpectException("text-" + level(), config -> config.asDouble().orElse(Math.PI));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeList(TestContext context) {
        init(context);
        getConfigAndExpectException("text-" + level(), config -> config.asNodeList().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTimestamp(TestContext context) {
        init(context);
        testTimestamp(config("text-" + level()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testDetach(TestContext context) {
        init(context);
        Config detached = config("text-" + level()).detach();
        assertThat(detached.type(), is(VALUE));
        assertThat(detached.key().toString(), is(""));
        assertThat(detached.asString().get(), is("string value " + level()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNode(TestContext context) {
        init(context);
        AtomicBoolean called = new AtomicBoolean(false);
        config("text-" + level())
                .asNode()
                .ifPresent(node -> {
                    assertThat(node.key().toString(), is(key("text-" + level())));
                    called.set(true);
                });
        assertThat(called.get(), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testIfExists(TestContext context) {
        init(context);
        AtomicBoolean called = new AtomicBoolean(false);
        config("text-" + level())
                .ifExists(node -> {
                    assertThat(node.key().toString(), is(key("text-" + level())));
                    called.set(true);
                });
        assertThat(called.get(), is(true));
    }

    private void testMap(Map<String, String> map) {
        assertThat(map.entrySet(), hasSize(1));
        assertThat(map.get(key("text-" + level())), is("string value " + level()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsMap(TestContext context) {
        init(context);
        testMap(config("text-" + level()).asMap().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTraverseWithPredicate(TestContext context) {
        init(context);
        assertThat(config("text-" + level())
                           .traverse((node) -> false)
                           .collect(Collectors.toList()),
                   is(empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTraverse(TestContext context) {
        init(context);
        assertThat(config("text-" + level())
                           .traverse()
                           .collect(Collectors.toList()),
                   is(empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testToString(TestContext context) {
        init(context);
        String key = "text-" + level();
        assertThat(config(key).toString(), both(startsWith("["))
                .and(endsWith(key(key) + "] VALUE 'string value " + level() + "'")));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTypeExistsSupplier(TestContext context) {
        init(context);
        assertThat(config("text-" + level()).asNode().optionalSupplier().get().get().exists(), is(true));
        assertThat(config("text-" + level()).asNode().optionalSupplier().get().get().type().exists(), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTypeIsLeafSupplier(TestContext context) {
        init(context);
        assertThat(config("text-" + level()).asNode().optionalSupplier().get().get().isLeaf(), is(true));
        assertThat(config("text-" + level()).asNode().optionalSupplier().get().get().type().isLeaf(), is(true));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalIntFailingSupplier(TestContext context) {
        init(context);
        getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asInt().optionalSupplier().get());
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalLongFailingSupplier(TestContext context) {
        init(context);
        getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asLong().optionalSupplier().get());
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalDoubleFailingSupplier(TestContext context) {
        init(context);
        getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asDouble().optionalSupplier().get());
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsFailingSupplier(TestContext context) {
        init(context);
        getConfigAndExpectExceptionSupplier("text-" + level(),
                                            config -> config.as(NoMapperConfigBean.class).supplier().get()); //no mapper
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsIntFailingSupplier(TestContext context) {
        init(context);
        getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asInt().supplier().get());
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsIntWithDefaultFailingSupplier(TestContext context) {
        init(context);
        getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asInt().supplier(42).get());
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLongFailingSupplier(TestContext context) {
        init(context);
        getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asLong().supplier().get());
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLongWithDefaultFailingSupplier(TestContext context) {
        init(context);
        getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asLong().supplier(42L).get());
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDoubleFailingSupplier(TestContext context) {
        init(context);
        getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asDouble().supplier().get());
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDoubleWithDefaultFailingSupplier(TestContext context) {
        init(context);
        getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asDouble().supplier(Math.PI).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTimestampSupplier(TestContext context) {
        init(context);
        testTimestamp(configViaSupplier("text-" + level()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testDetachSupplier(TestContext context) {
        init(context);
        Config detached = config("text-" + level()).detach().asNode().optionalSupplier().get().get();
        assertThat(detached.type(), is(VALUE));
        assertThat(detached.key().toString(), is(""));
        assertThat(detached.asString().get(), is("string value " + level()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testNodeSupplier(TestContext context) {
        init(context);
        AtomicBoolean called = new AtomicBoolean(false);
        config("text-" + level())
                .asNode()
                .optionalSupplier()
                .get()
                .ifPresent(node -> {
                    assertThat(node.key().toString(), is(key("text-" + level())));
                    called.set(true);
                });
        assertThat(called.get(), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testIfExistsSupplier(TestContext context) {
        init(context);
        AtomicBoolean called = new AtomicBoolean(false);
        configViaSupplier("text-" + level())
                .ifExists(node -> {
                    assertThat(node.key().toString(), is(key("text-" + level())));
                    called.set(true);
                });
        assertThat(called.get(), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTraverseWithPredicateSupplier(TestContext context) {
        init(context);
        assertThat(configViaSupplier("text-" + level())
                           .traverse((node) -> false)
                           .collect(Collectors.toList()),
                   is(empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTraverseSupplier(TestContext context) {
        init(context);
        assertThat(configViaSupplier("text-" + level())
                           .traverse()
                           .collect(Collectors.toList()),
                   is(empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testToStringSupplier(TestContext context) {
        init(context);
        String key = "text-" + level();
        assertThat(configViaSupplier(key).toString(), both(startsWith("["))
                .and(endsWith(key(key) + "] VALUE 'string value " + level() + "'")));
    }

    //
    // helper
    //

    private <T> void getConfigAndExpectException(String key, Function<Config, T> op) {
        expectException(config(key), key, op);
    }

    private <T> void getConfigAndExpectExceptionSupplier(String key, Function<Config, T> op) {
        expectException(config(key).asNode().optionalSupplier().get().get(), key, op);
    }

    private <T> void expectException(Config config, String key, Function<Config, T> op) {
        ConfigMappingException ex = assertThrows(ConfigMappingException.class, () -> {
            op.apply(config);
        });
        assertThat(ex.getMessage(), containsString("'" + config.key() + "'"));
    }

    private String key(String key) {
        return super.config(key).key().toString();
    }

    /**
     * Invokes specified {@code valueFunction} asserts expected value for following text nodes:
     * <ul>
     * <li>{@code text-#} - test value</li>
     * <li>{@code str-list-#.0} - first element of list of strings {@code str-list-#}</li>
     * <li>{@code str-list-#.1} - second element of list of strings {@code str-list-#}</li>
     * <li>{@code str-list-#.2} - third element of list of strings {@code str-list-#}</li>
     * </ul>
     * Where {@code #} means level of config structure.
     *
     * @param valueFunction function to get string value of key
     */
    private void assertValue(Function<String, String> valueFunction) {
        assertThat(valueFunction.apply("text-" + level()), is("string value " + level()));
        assertThat(valueFunction.apply("str-list-" + level() + ".0"), is("aaa-" + level()));
        assertThat(valueFunction.apply("str-list-" + level() + ".1"), is("bbb-" + level()));
        assertThat(valueFunction.apply("str-list-" + level() + ".2"), is("ccc-" + level()));
    }

    /**
     * Invokes specified {@code valueFunction} asserts expected bean for following text nodes:
     * <ul>
     * <li>{@code text-#} - test value</li>
     * <li>{@code str-list-#.0} - first element of list of strings {@code str-list-#}</li>
     * <li>{@code str-list-#.1} - second element of list of strings {@code str-list-#}</li>
     * <li>{@code str-list-#.2} - third element of list of strings {@code str-list-#}</li>
     * </ul>
     * Where {@code #} means level of config structure.
     *
     * @param valueFunction function to get string value of key
     * @param meta
     */
    private void assertBean(Function<String, ValueConfigBean> valueFunction, String meta) {
        assertThat(valueFunction.apply("text-" + level()),
                   is(new ValueConfigBean(meta, "string value " + level())));
        assertThat(valueFunction.apply("str-list-" + level() + ".0"),
                   is(new ValueConfigBean(meta, "aaa-" + level())));
        assertThat(valueFunction.apply("str-list-" + level() + ".1"),
                   is(new ValueConfigBean(meta, "bbb-" + level())));
        assertThat(valueFunction.apply("str-list-" + level() + ".2"),
                   is(new ValueConfigBean(meta, "ccc-" + level())));
    }

}
