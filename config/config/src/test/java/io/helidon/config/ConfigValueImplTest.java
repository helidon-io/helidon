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

package io.helidon.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.common.CollectionsHelper;
import static io.helidon.config.Config.Type.VALUE;
import java.util.stream.Stream;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests {@link Config} API in case the node is {@link Config.Type#VALUE} type, i.e. {@link ConfigValueImpl}.
 */
public class ConfigValueImplTest extends AbstractConfigImplTest {

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
        String[] array = ConfigValueImpl.toArray("a,b,");
        assertThat(array, arrayContaining("a", "b", ""));

        array = ConfigValueImpl.toArray("large:cheese\\,mushroom,medium:chicken,small:pepperoni");
        assertThat(array, arrayContaining("large:cheese,mushroom", "medium:chicken", "small:pepperoni"));

        array = ConfigValueImpl.toArray("microservice,microprofile,m\\,f,microservice");
        assertThat(array, arrayContaining("microservice", "microprofile", "m,f", "microservice"));

        array = ConfigValueImpl.toArray(",a,b");
        assertThat(array, arrayContaining("", "a", "b"));


        array = ConfigValueImpl.toArray("a\\,");
        assertThat(array, arrayContaining("a,"));

        array = ConfigValueImpl.toArray("\\,");
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
	assertValue(key -> config(key).value().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalString(TestContext context) {
        init(context);
	assertValue(key -> config(key).asOptionalString().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testNodeList(TestContext context) {
        init(context);
	getConfigAndExpectException("text-" + level(), config -> config.nodeList());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptional(TestContext context) {
        init(context);
	assertBean(key -> config(key).asOptional(ValueConfigBean.class).get(), "fromConfig");
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalList(TestContext context) {
        init(context);
	assertThat(config("text-" + level()).asOptionalList(ValueConfigBean.class).get(),
                   containsInAnyOrder(ValueConfigBean.utFromConfig("string value " + level())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalStringList(TestContext context) {
        init(context);
	assertThat(config("text-" + level()).asOptionalStringList().get(),
                   containsInAnyOrder("string value " + level()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalWithFunction(TestContext context) {
        init(context);
	assertBean(key -> config(key).mapOptional(ValueConfigBean::fromString).get(), "fromString");
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalWithConfigMapper(TestContext context) {
        init(context);
	assertBean(key -> config(key).mapOptional(ValueConfigBean::fromConfig).get(), "fromConfig");
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalBoolean(TestContext context) {
        init(context);
	assertThat(config("bool-" + level()).asOptionalBoolean().get(), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalInt(TestContext context) {
        init(context);
	assertThat(config("int-" + level()).asOptionalInt().getAsInt(), is(2147483640 + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalIntFailing(TestContext context) {
        init(context);
	getConfigAndExpectException("text-" + level(), config -> config.asOptionalInt());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalLong(TestContext context) {
        init(context);
	assertThat(config("long-" + level()).asOptionalLong().getAsLong(), is(9223372036854775800L + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalLongFailing(TestContext context) {
        init(context);
	getConfigAndExpectException("text-" + level(), config -> config.asOptionalLong());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalDouble(TestContext context) {
        init(context);
	assertThat(config("double-" + level()).asOptionalDouble().getAsDouble(), is(1230.5678 + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalDoubleFailing(TestContext context) {
        init(context);
	getConfigAndExpectException("text-" + level(), config -> config.asOptionalDouble());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalListWithFunction(TestContext context) {
        init(context);
	getConfigAndExpectException("text-" + level(), config -> config.mapOptionalList(ValueConfigBean::fromString));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalListWithConfigMapper(TestContext context) {
        init(context);
	getConfigAndExpectException("text-" + level(), config -> config.mapOptionalList(ValueConfigBean::fromConfig));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAs(TestContext context) {
        init(context);
	assertBean(key -> config(key).as(ValueConfigBean.class), "fromConfig"); //uses MyConfigBean.fromConfig as a mapper
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsFailing(TestContext context) {
        init(context);
	getConfigAndExpectException("text-" + level(), config -> config.as(NoMapperConfigBean.class)); //no mapper
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsWithDefault(TestContext context) {
        init(context);
	assertBean(key -> config(key).as(ValueConfigBean.class, ValueConfigBean.EMPTY),
                   "fromConfig"); //uses MyConfigBean.fromConfig as a mapper
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithFunction(TestContext context) {
        init(context);
	assertBean(key -> config(key).map(ValueConfigBean::fromString), "fromString");
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithFunctionAndDefault(TestContext context) {
        init(context);
	assertBean(key -> config(key).map(ValueConfigBean::fromString, ValueConfigBean.EMPTY), "fromString");
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithConfigMapper(TestContext context) {
        init(context);
	assertBean(key -> config(key).map(ValueConfigBean::fromConfig), "fromConfig");
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithConfigMapperAndDefault(TestContext context) {
        init(context);
	assertBean(key -> config(key).map(ValueConfigBean::fromConfig, ValueConfigBean.EMPTY), "fromConfig");
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsList(TestContext context) {
        init(context);
	assertThat(config("text-" + level()).asList(ValueConfigBean.class),
                   contains(ValueConfigBean.utFromConfig("string value " + level())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsListWithDefault(TestContext context) {
        init(context);
	assertThat(config("text-" + level()).asList(ValueConfigBean.class, CollectionsHelper.listOf(ValueConfigBean.EMPTY)),
                   contains(ValueConfigBean.utFromConfig("string value " + level())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithFunction(TestContext context) {
        init(context);
	getConfigAndExpectException("text-" + level(), config -> config.mapList(ValueConfigBean::fromString));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithFunctionAndDefault(TestContext context) {
        init(context);
	getConfigAndExpectException("text-" + level(), config -> config.mapList(ValueConfigBean::fromString, CollectionsHelper.listOf(ValueConfigBean.EMPTY)));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithConfigMapper(TestContext context) {
        init(context);
	getConfigAndExpectException("text-" + level(), config -> config.mapList(ValueConfigBean::fromConfig));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithConfigMapperAndDefault(TestContext context) {
        init(context);
	getConfigAndExpectException("text-" + level(), config -> config.mapList(ValueConfigBean::fromConfig, CollectionsHelper.listOf(ValueConfigBean.EMPTY)));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsString(TestContext context) {
        init(context);
	assertValue(key -> config(key).asString());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsStringWithDefault(TestContext context) {
        init(context);
	assertValue(key -> config(key).asString("default value"));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsBoolean(TestContext context) {
        init(context);
	assertThat(config("bool-" + level()).asBoolean(), is(true));
        assertThat(config("text-" + level()).asBoolean(), is(false));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsBooleanWithDefault(TestContext context) {
        init(context);
	assertThat(config("bool-" + level()).asBoolean(false), is(true));
        assertThat(config("text-" + level()).asBoolean(true), is(false));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsInt(TestContext context) {
        init(context);
	assertThat(config("int-" + level()).asInt(), is(2147483640 + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsIntFailing(TestContext context) {
        init(context);
	getConfigAndExpectException("text-" + level(), config -> config.asInt());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsIntWithDefault(TestContext context) {
        init(context);
	assertThat(config("int-" + level()).asInt(42), is(2147483640 + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsIntWithDefaultFailing(TestContext context) {
        init(context);
	getConfigAndExpectException("text-" + level(), config -> config.asInt(42));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLong(TestContext context) {
        init(context);
	assertThat(config("long-" + level()).asLong(), is(9223372036854775800L + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLongFailing(TestContext context) {
        init(context);
	getConfigAndExpectException("text-" + level(), config -> config.asLong());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLongWithDefault(TestContext context) {
        init(context);
	assertThat(config("long-" + level()).asLong(42), is(9223372036854775800L + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLongWithDefaultFailing(TestContext context) {
        init(context);
	getConfigAndExpectException("text-" + level(), config -> config.asLong(42));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDouble(TestContext context) {
        init(context);
	assertThat(config("double-" + level()).asDouble(), is(1230.5678 + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDoubleFailing(TestContext context) {
        init(context);
	getConfigAndExpectException("text-" + level(), config -> config.asDouble());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDoubleWithDefault(TestContext context) {
        init(context);
	assertThat(config("double-" + level()).asDouble(Math.PI), is(1230.5678 + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDoubleWithDefaultFailing(TestContext context) {
        init(context);
	getConfigAndExpectException("text-" + level(), config -> config.asDouble(Math.PI));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsStringList(TestContext context) {
        init(context);
	assertThat(config("text-" + level()).asStringList(),
                   contains("string value " + level()));

    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsStringListWithDefault(TestContext context) {
        init(context);
	assertThat(config("text-" + level()).asStringList(CollectionsHelper.listOf("default", "value")),
                   contains("string value " + level()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeList(TestContext context) {
        init(context);
	getConfigAndExpectException("text-" + level(), config -> config.asNodeList());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeListWithDefault(TestContext context) {
        init(context);
	getConfigAndExpectException("text-" + level(), config -> config.asNodeList(CollectionsHelper.listOf(Config.empty())));
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
        assertThat(detached.asString(), is("string value " + level()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testNode(TestContext context) {
        init(context);
	AtomicBoolean called = new AtomicBoolean(false);
        config("text-" + level())
                .node()
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

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testIfExistsOrElse(TestContext context) {
        init(context);
	AtomicBoolean called = new AtomicBoolean(false);
        config("text-" + level())
                .ifExistsOrElse(node -> {
                                    assertThat(node.key().toString(), is(key("text-" + level())));
                                    called.set(true);
                                },
                                () -> fail("Node with key text-" + level() + " unexpectedly does not exists"));
        assertThat(called.get(), is(true));
    }

    private void testMap(Map<String, String> map) {
        assertThat(map.entrySet(), hasSize(1));
        assertThat(map.get(key("text-" + level())), is("string value " + level()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalMap(TestContext context) {
        init(context);
	testMap(config("text-" + level()).asOptionalMap().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsMap(TestContext context) {
        init(context);
	testMap(config("text-" + level()).asMap());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsMapWithDefault(TestContext context) {
        init(context);
	testMap(config("text-" + level()).asMap(CollectionsHelper.mapOf()));
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
	assertThat(config("text-" + level()).nodeSupplier().get().get().exists(), is(true));
        assertThat(config("text-" + level()).nodeSupplier().get().get().type().exists(), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTypeIsLeafSupplier(TestContext context) {
        init(context);
	assertThat(config("text-" + level()).nodeSupplier().get().get().isLeaf(), is(true));
        assertThat(config("text-" + level()).nodeSupplier().get().get().type().isLeaf(), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testOptionalStringSupplier(TestContext context) {
        init(context);
	assertValue(key -> config(key).asOptionalStringSupplier().get().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testNodeListSupplier(TestContext context) {
        init(context);
	getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asNodeListSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalSupplier(TestContext context) {
        init(context);
	assertBean(key -> config(key).asSupplier(ValueConfigBean.class).get(), "fromConfig");
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalListSupplier(TestContext context) {
        init(context);
	Config config = config("text-" + level());
        List<ValueConfigBean> list = config.asOptionalListSupplier(ValueConfigBean.class).get().get();

        assertThat(list, contains(ValueConfigBean.utFromConfig("string value " + level())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalStringListSupplier(TestContext context) {
        init(context);
	Config config = config("text-" + level());
        List<String> strings = config.asOptionalStringListSupplier().get().get();

        assertThat(strings, contains("string value " + level()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalWithFunctionSupplier(TestContext context) {
        init(context);
	assertBean(key -> config(key).mapOptionalSupplier(ValueConfigBean::fromString).get().get(), "fromString");
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalWithConfigMapperSupplier(TestContext context) {
        init(context);
	assertBean(key -> config(key).mapOptionalSupplier(ValueConfigBean::fromConfig).get().get(), "fromConfig");
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalBooleanSupplier(TestContext context) {
        init(context);
	assertThat(config("bool-" + level()).asOptionalBooleanSupplier().get().get(), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalIntSupplier(TestContext context) {
        init(context);
	assertThat(config("int-" + level()).asOptionalIntSupplier().get().getAsInt(), is(2147483640 + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalIntFailingSupplier(TestContext context) {
        init(context);
	getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asOptionalIntSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalLongSupplier(TestContext context) {
        init(context);
	assertThat(config("long-" + level()).asOptionalLongSupplier().get().getAsLong(), is(9223372036854775800L + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalLongFailingSupplier(TestContext context) {
        init(context);
	getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asOptionalLongSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalDoubleSupplier(TestContext context) {
        init(context);
	assertThat(config("double-" + level()).asOptionalDoubleSupplier().get().getAsDouble(), is(1230.5678 + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalDoubleFailingSupplier(TestContext context) {
        init(context);
	getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asOptionalDoubleSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalListWithFunctionSupplier(TestContext context) {
        init(context);
	getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.mapOptionalList(ValueConfigBean::fromString).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalListWithConfigMapperSupplier(TestContext context) {
        init(context);
	getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.mapOptionalListSupplier(ValueConfigBean::fromConfig).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsSupplier(TestContext context) {
        init(context);
	assertBean(key -> config(key).asSupplier(ValueConfigBean.class).get(), "fromConfig");
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsFailingSupplier(TestContext context) {
        init(context);
	getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asSupplier(NoMapperConfigBean.class).get()); //no mapper
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsWithDefaultSupplier(TestContext context) {
        init(context);
	assertBean(key -> config(key).asSupplier(ValueConfigBean.class, ValueConfigBean.EMPTY).get(),
                   "fromConfig"); //uses MyConfigBean.fromConfig as a mapper
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithFunctionSupplier(TestContext context) {
        init(context);
	assertBean(key -> config(key).mapSupplier(ValueConfigBean::fromString).get(), "fromString");
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithFunctionAndDefaultSupplier(TestContext context) {
        init(context);
	assertBean(key -> config(key).mapSupplier(ValueConfigBean::fromString, ValueConfigBean.EMPTY).get(), "fromString");
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithConfigMapperSupplier(TestContext context) {
        init(context);
	assertBean(key -> config(key).mapSupplier(ValueConfigBean::fromConfig).get(), "fromConfig");
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithConfigMapperAndDefaultSupplier(TestContext context) {
        init(context);
	assertBean(key -> config(key).mapSupplier(ValueConfigBean::fromConfig, ValueConfigBean.EMPTY).get(), "fromConfig");
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsListSupplier(TestContext context) {
        init(context);
	assertThat(config("text-" + level()).asListSupplier(ValueConfigBean.class).get(),
                   contains(ValueConfigBean.utFromConfig("string value " + level())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsListWithDefaultSupplier(TestContext context) {
        init(context);
	assertThat(config("text-" + level()).asListSupplier(ValueConfigBean.class, CollectionsHelper.listOf(ValueConfigBean.EMPTY)).get(),
                   contains(ValueConfigBean.utFromConfig("string value " + level())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithFunctionSupplier(TestContext context) {
        init(context);
	getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.mapListSupplier(ValueConfigBean::fromString).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithFunctionAndDefaultSupplier(TestContext context) {
        init(context);
	getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.mapListSupplier(ValueConfigBean::fromString, CollectionsHelper.listOf(ValueConfigBean.EMPTY)).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithConfigMapperSupplier(TestContext context) {
        init(context);
	getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.mapListSupplier(ValueConfigBean::fromConfig).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithConfigMapperAndDefaultSupplier(TestContext context) {
        init(context);
	getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.mapListSupplier(ValueConfigBean::fromConfig, CollectionsHelper.listOf(ValueConfigBean.EMPTY)).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsStringSupplier(TestContext context) {
        init(context);
	assertValue(key -> config(key).asStringSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsStringWithDefaultSupplier(TestContext context) {
        init(context);
	assertValue(key -> config(key).asStringSupplier("default value").get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsBooleanSupplier(TestContext context) {
        init(context);
	assertThat(config("bool-" + level()).asBooleanSupplier().get(), is(true));
        assertThat(config("text-" + level()).asBooleanSupplier().get(), is(false));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsBooleanWithDefaultSupplier(TestContext context) {
        init(context);
	assertThat(config("bool-" + level()).asBooleanSupplier(false).get(), is(true));
        assertThat(config("text-" + level()).asBooleanSupplier(true).get(), is(false));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsIntSupplier(TestContext context) {
        init(context);
	assertThat(config("int-" + level()).asIntSupplier().get(), is(2147483640 + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsIntFailingSupplier(TestContext context) {
        init(context);
	getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asIntSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsIntWithDefaultSupplier(TestContext context) {
        init(context);
	assertThat(config("int-" + level()).asIntSupplier(42).get(), is(2147483640 + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsIntWithDefaultFailingSupplier(TestContext context) {
        init(context);
	getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asIntSupplier(42).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLongSupplier(TestContext context) {
        init(context);
	assertThat(config("long-" + level()).asLongSupplier().get(), is(9223372036854775800L + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLongFailingSupplier(TestContext context) {
        init(context);
	getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asLongSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLongWithDefaultSupplier(TestContext context) {
        init(context);
	assertThat(config("long-" + level()).asLongSupplier(42).get(), is(9223372036854775800L + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLongWithDefaultFailingSupplier(TestContext context) {
        init(context);
	getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asLongSupplier(42).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDoubleSupplier(TestContext context) {
        init(context);
	assertThat(config("double-" + level()).asDoubleSupplier().get(), is(1230.5678 + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDoubleFailingSupplier(TestContext context) {
        init(context);
	getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asDoubleSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDoubleWithDefaultSupplier(TestContext context) {
        init(context);
	assertThat(config("double-" + level()).asDoubleSupplier(Math.PI).get(), is(1230.5678 + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDoubleWithDefaultFailingSupplier(TestContext context) {
        init(context);
	getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asDoubleSupplier(Math.PI).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsStringListSupplier(TestContext context) {
        init(context);
	assertThat(config("text-" + level()).asStringListSupplier().get(),
                   contains("string value " + level()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsStringListWithDefaultSupplier(TestContext context) {
        init(context);
	assertThat(config("text-" + level()).asStringListSupplier(CollectionsHelper.listOf("default", "value")).get(),
                   contains("string value " + level()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeListSupplier(TestContext context) {
        init(context);
	getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asNodeListSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeListWithDefaultSupplier(TestContext context) {
        init(context);
	getConfigAndExpectExceptionSupplier("text-" + level(), config -> config.asNodeListSupplier(CollectionsHelper.listOf(Config.empty())).get());
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
	Config detached = config("text-" + level()).detach().nodeSupplier().get().get();
        assertThat(detached.type(), is(VALUE));
        assertThat(detached.key().toString(), is(""));
        assertThat(detached.asString(), is("string value " + level()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testNodeSupplier(TestContext context) {
        init(context);
	AtomicBoolean called = new AtomicBoolean(false);
        config("text-" + level())
                .nodeSupplier()
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
    public void testIfExistsOrElseSupplier(TestContext context) {
        init(context);
	AtomicBoolean called = new AtomicBoolean(false);
        configViaSupplier("text-" + level())
                .ifExistsOrElse(node -> {
                                    assertThat(node.key().toString(), is(key("text-" + level())));
                                    called.set(true);
                                },
                                () -> fail("Node with key text-" + level() + " unexpectedly does not exists"));
        assertThat(called.get(), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalMapSupplier(TestContext context) {
        init(context);
	testMap(config("text-" + level()).asOptionalMapSupplier().get().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsMapSupplier(TestContext context) {
        init(context);
	testMap(config("text-" + level()).asMapSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsMapWithDefaultSupplier(TestContext context) {
        init(context);
	testMap(config("text-" + level()).asMapSupplier(CollectionsHelper.mapOf()).get());
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

    private <T> void getConfigAndExpectException(String key, Function<Config,T> op) {
        expectException(config(key), key, op);
    }

    private <T> void getConfigAndExpectExceptionSupplier(String key, Function<Config,T> op) {
        expectException(config(key).nodeSupplier().get().get(), key, op);
    }
    
    private <T> void expectException(Config config, String key, Function<Config,T> op) {
        ConfigMappingException ex = assertThrows(ConfigMappingException.class, () -> {
            op.apply(config);
                });
        assertTrue(ex.getMessage().contains("'" + config.key() + "'"));
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
