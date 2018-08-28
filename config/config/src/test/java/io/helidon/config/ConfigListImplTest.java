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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import io.helidon.common.CollectionsHelper;
import static io.helidon.config.Config.Type.LIST;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests {@link Config} API in case the node is {@link Config.Type#LIST} type, i.e. {@link ConfigListImpl}.
 */
public class ConfigListImplTest extends AbstractConfigImplTest {

    public ConfigListImplTest() {}

    private static Stream<TestContext> initParams() {
        return Stream.of(
                // list
                new TestContext("list-1", 2, false),
                new TestContext("list-1", 2, true),
                // sub-object's list
                new TestContext("object-1.list-2", 3, false),
                new TestContext("object-1.list-2", 3, true),
                // list's list
                new TestContext("list-1.2", 3, false),
                new TestContext("list-1.2", 3, true)
        );
    }

    @Override
    @ParameterizedTest
    @MethodSource("initParams")
    public void testTypeExists(TestContext context) {
        init(context);
        assertThat(config().exists(), is(true));
        assertThat(config().type().exists(), is(true));
    }

    @Override
    @ParameterizedTest
    @MethodSource("initParams")
    public void testTypeIsLeaf(TestContext context) {
	init(context);
        assertThat(config().isLeaf(), is(false));
        assertThat(config().type().isLeaf(), is(false));
    }

    @Override
    @ParameterizedTest
    @MethodSource("initParams")
    public void testValue(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.value());
    }

    @Override
    @ParameterizedTest
    @MethodSource("initParams")
    public void testAsOptionalString(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asOptionalString());
    }

    @Override
    @ParameterizedTest
    @MethodSource("initParams")
    public void testNodeList(TestContext context) {
	init(context);
        assertThat(nodeNames(config().nodeList().get()),
                   containsInAnyOrder(listNames()));
    }

    @Override
    @ParameterizedTest
    @MethodSource("initParams")
    public void testAsOptional(TestContext context) {
	init(context);
        assertThat(config().asOptional(ObjectConfigBean.class).get(),
                   is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
    }

    @Override
    @ParameterizedTest
    @MethodSource("initParams")
    public void testAsOptionalList(TestContext context) {
	init(context);
        assertThat(config().asOptionalList(ObjectConfigBean.class).get(),
                   containsInAnyOrder(expectedObjectConfigBeans()));
    }

    @Override
    @ParameterizedTest
    @MethodSource("initParams")
    public void testAsOptionalStringList(TestContext context) {
	init(context);
        assertThat(config("7").asStringList(),
                   containsInAnyOrder("aaa-" + level(), "bbb-" + level(), "ccc-" + level()));
    }

    @ParameterizedTest
    @MethodSource("initParams")
    public void testAsOptionalListFromString(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asOptionalList(ValueConfigBean.class));
    }

    @Override
    @ParameterizedTest
    @MethodSource("initParams")
    public void testMapOptionalWithFunction(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.mapOptional(ValueConfigBean::fromString));
    }

    @Override
    @ParameterizedTest
    @MethodSource("initParams")
    public void testMapOptionalWithConfigMapper(TestContext context) {
	init(context);
        assertThat(config().mapOptional(ObjectConfigBean::fromConfig).get(),
                   is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalBoolean(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asOptionalBoolean());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalInt(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asOptionalInt());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalLong(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asOptionalLong());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalDouble(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asOptionalDouble());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalListWithFunction(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.mapOptionalList(ValueConfigBean::fromString));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalListWithConfigMapper(TestContext context) {
	init(context);
        assertThat(config().mapOptionalList(ObjectConfigBean::fromConfig).get(),
                   containsInAnyOrder(expectedObjectConfigBeans()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAs(TestContext context) {
	init(context);
        assertThat(config().as(ObjectConfigBean.class),
                   is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsWithDefault(TestContext context) {
	init(context);
        assertThat(config().as(ObjectConfigBean.class, ObjectConfigBean.EMPTY),
                   is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithFunction(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.map(ValueConfigBean::fromString));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithFunctionAndDefault(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.map(ValueConfigBean::fromString, ObjectConfigBean.EMPTY));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithConfigMapper(TestContext context) {
	init(context);
        assertThat(config().map(ObjectConfigBean::fromConfig),
                   is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithConfigMapperAndDefault(TestContext context) {
	init(context);
        assertThat(config().map(ObjectConfigBean::fromConfig, ObjectConfigBean.EMPTY),
                   is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsList(TestContext context) {
	init(context);
        assertThat(config().asList(ObjectConfigBean.class),
                   containsInAnyOrder(expectedObjectConfigBeans()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsListWithDefault(TestContext context) {
	init(context);
        assertThat(config().asList(ObjectConfigBean.class, CollectionsHelper.listOf(ObjectConfigBean.EMPTY)),
                   containsInAnyOrder(expectedObjectConfigBeans()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithFunction(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.mapList(ValueConfigBean::fromString));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithFunctionAndDefault(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.mapList(ValueConfigBean::fromString, CollectionsHelper.listOf(ValueConfigBean.EMPTY)));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithConfigMapper(TestContext context) {
	init(context);
        assertThat(config().mapList(ObjectConfigBean::fromConfig),
                   containsInAnyOrder(expectedObjectConfigBeans()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithConfigMapperAndDefault(TestContext context) {
	init(context);
        assertThat(config().mapList(ObjectConfigBean::fromConfig, CollectionsHelper.listOf(ObjectConfigBean.EMPTY)),
                   containsInAnyOrder(expectedObjectConfigBeans()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsString(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asString());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsStringWithDefault(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asString("default value"));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsBoolean(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asBoolean());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsBooleanWithDefault(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asBoolean(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsInt(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asInt());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsIntWithDefault(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asInt(42));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLong(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asLong());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLongWithDefault(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asLong(23));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDouble(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asDouble());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDoubleWithDefault(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asDouble(Math.PI));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsStringList(TestContext context) {
	init(context);
        assertThat(config("7").asStringList(),
                   containsInAnyOrder("aaa-" + level(), "bbb-" + level(), "ccc-" + level()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsStringListWithDefault(TestContext context) {
	init(context);
        assertThat(config("7").asStringList(CollectionsHelper.listOf("default", "value")),
                   containsInAnyOrder("aaa-" + level(), "bbb-" + level(), "ccc-" + level()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeList(TestContext context) {
	init(context);
        assertThat(nodeNames(config().asNodeList()),
                   containsInAnyOrder(listNames()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeListWithDefault(TestContext context) {
	init(context);
        assertThat(nodeNames(config().asNodeList(CollectionsHelper.listOf(Config.empty()))),
                   containsInAnyOrder(listNames()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTimestamp(TestContext context) {
	init(context);
        testTimestamp(config());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testDetach(TestContext context) {
	init(context);
        Config detached = config().detach();
        assertThat(detached.type(), is(LIST));
        assertThat(detached.key().toString(), is(""));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testNode(TestContext context) {
	init(context);
        AtomicBoolean called = new AtomicBoolean(false);
        config()
                .node()
                .ifPresent(node -> {
                    assertThat(node.key(), is(key()));
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
        config().ifExists(
                node -> {
                    assertThat(node.key(), is(key()));
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
        config().ifExistsOrElse(
                node -> {
                    assertThat(node.key(), is(key()));
                    called.set(true);
                },
                () -> fail("Expected node  does not exist"));

        assertThat(called.get(), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalMap(TestContext context) {
	init(context);
        assertThat(config().asOptionalMap().get().entrySet(), hasSize(subLeafs()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsMap(TestContext context) {
	init(context);
        assertThat(config().asMap().entrySet(), hasSize(subLeafs()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsMapWithDefault(TestContext context) {
	init(context);
        assertThat(config().asMap(CollectionsHelper.mapOf()).entrySet(), hasSize(subLeafs()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTraverseWithPredicate(TestContext context) {
	init(context);
        List<Config.Key> allSubKeys = config()
                // ignore whole list nodes
                .traverse(node -> node.type() != Config.Type.OBJECT)
                .map(Config::key)
                .collect(Collectors.toList());

        assertThat(allSubKeys, hasSize(subNodesNoObjects()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTypeExistsSupplier(TestContext context) {
	init(context);
        assertThat(config().nodeSupplier().get().get().exists(), is(true));
        assertThat(config().nodeSupplier().get().get().type().exists(), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTypeIsLeafSupplier(TestContext context) {
	init(context);
        assertThat(config().nodeSupplier().get().get().isLeaf(), is(false));
        assertThat(config().nodeSupplier().get().get().type().isLeaf(), is(false));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testOptionalStringSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asOptionalStringSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testNodeListSupplier(TestContext context) {
	init(context);
        assertThat(nodeNames(config().asNodeListSupplier().get()),
                   containsInAnyOrder(listNames()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalSupplier(TestContext context) {
	init(context);
        assertThat(config().asSupplier(ObjectConfigBean.class).get(),
                   is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalListSupplier(TestContext context) {
	init(context);
        assertThat(config().asOptionalList(ObjectConfigBean.class).get(),
                   containsInAnyOrder(expectedObjectConfigBeans()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalStringListSupplier(TestContext context) {
	init(context);
        assertThat(config("7").asOptionalStringList().get(),
                   containsInAnyOrder("aaa-" + level(), "bbb-" + level(), "ccc-" + level()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalListFromStringSupplier(TestContext context) {
	init(context);
	init(context);
        getConfigAndExpectException(config -> config.asOptionalList(ValueConfigBean.class));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalWithFunctionSupplier(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.mapOptional(ValueConfigBean::fromString));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalWithConfigMapperSupplier(TestContext context) {
	init(context);
        assertThat(config().mapOptional(ObjectConfigBean::fromConfig).get(),
                   is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalIntSupplier(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asOptionalInt());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalBooleanSupplier(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asOptionalBoolean());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalLongSupplier(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asOptionalLong());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalDoubleSupplier(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asOptionalDouble());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalListWithFunctionSupplier(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.mapOptionalList(ValueConfigBean::fromString));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalListWithConfigMapperSupplier(TestContext context) {
	init(context);
        assertThat(config().mapOptionalList(ObjectConfigBean::fromConfig).get(),
                   containsInAnyOrder(expectedObjectConfigBeans()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsSupplier(TestContext context) {
	init(context);
        assertThat(config().as(ObjectConfigBean.class),
                   is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsWithDefaultSupplier(TestContext context) {
	init(context);
        assertThat(config().as(ObjectConfigBean.class, ObjectConfigBean.EMPTY),
                   is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithFunctionSupplier(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.map(ValueConfigBean::fromString));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithFunctionAndDefaultSupplier(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.map(ValueConfigBean::fromString, ObjectConfigBean.EMPTY));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithConfigMapperSupplier(TestContext context) {
	init(context);
        assertThat(config().map(ObjectConfigBean::fromConfig),
                   is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithConfigMapperAndDefaultSupplier(TestContext context) {
	init(context);
        assertThat(config().map(ObjectConfigBean::fromConfig, ObjectConfigBean.EMPTY),
                   is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsListSupplier(TestContext context) {
	init(context);
        assertThat(config().asList(ObjectConfigBean.class),
                   containsInAnyOrder(expectedObjectConfigBeans()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsListWithDefaultSupplier(TestContext context) {
	init(context);
        assertThat(config().asList(ObjectConfigBean.class, CollectionsHelper.listOf(ObjectConfigBean.EMPTY)),
                   containsInAnyOrder(expectedObjectConfigBeans()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithFunctionSupplier(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.mapList(ValueConfigBean::fromString));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithFunctionAndDefaultSupplier(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.mapList(ValueConfigBean::fromString, CollectionsHelper.listOf(ValueConfigBean.EMPTY)));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithConfigMapperSupplier(TestContext context) {
	init(context);
        assertThat(config().mapList(ObjectConfigBean::fromConfig),
                   containsInAnyOrder(expectedObjectConfigBeans()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithConfigMapperAndDefaultSupplier(TestContext context) {
	init(context);
        assertThat(config().mapList(ObjectConfigBean::fromConfig, CollectionsHelper.listOf(ObjectConfigBean.EMPTY)),
                   containsInAnyOrder(expectedObjectConfigBeans()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsStringSupplier(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asString());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsStringWithDefaultSupplier(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asString("default value"));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsBooleanSupplier(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asBoolean());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsBooleanWithDefaultSupplier(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asBoolean(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsIntSupplier(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asInt());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsIntWithDefaultSupplier(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asInt(42));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLongSupplier(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asLong());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLongWithDefaultSupplier(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asLong(23));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDoubleSupplier(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asDouble());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDoubleWithDefaultSupplier(TestContext context) {
	init(context);
        getConfigAndExpectException(config -> config.asDouble(Math.PI));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsStringListSupplier(TestContext context) {
	init(context);
        assertThat(config("7").asStringList(),
                   containsInAnyOrder("aaa-" + level(), "bbb-" + level(), "ccc-" + level()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsStringListWithDefaultSupplier(TestContext context) {
	init(context);
        assertThat(config("7").asStringList(CollectionsHelper.listOf("default", "value")),
                   containsInAnyOrder("aaa-" + level(), "bbb-" + level(), "ccc-" + level()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeListSupplier(TestContext context) {
	init(context);
        assertThat(nodeNames(config().asNodeList()),
                   containsInAnyOrder(listNames()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeListWithDefaultSupplier(TestContext context) {
	init(context);
        assertThat(nodeNames(config().asNodeList(CollectionsHelper.listOf(Config.empty()))),
                   containsInAnyOrder(listNames()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTimestampSupplier(TestContext context) {
	init(context);
        testTimestamp(config());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testDetachSupplier(TestContext context) {
	init(context);
        Config detached = config().detach();
        assertThat(detached.type(), is(LIST));
        assertThat(detached.key().toString(), is(""));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testNodeSupplier(TestContext context) {
	init(context);
        AtomicBoolean called = new AtomicBoolean(false);
        config()
                .node()
                .ifPresent(node -> {
                    assertThat(node.key(), is(key()));
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
        config().ifExists(
                node -> {
                    assertThat(node.key(), is(key()));
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
        config().ifExistsOrElse(
                node -> {
                    assertThat(node.key(), is(key()));
                    called.set(true);
                },
                () -> {fail("Expected config does not exists");});

        assertThat(called.get(), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalMapSupplier(TestContext context) {
	init(context);
        assertThat(config().asOptionalMap().get().entrySet(), hasSize(subLeafs()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsMapSupplier(TestContext context) {
	init(context);
        assertThat(config().asMap().entrySet(), hasSize(subLeafs()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsMapWithDefaultSupplier(TestContext context) {
	init(context);
        assertThat(config().asMap(CollectionsHelper.mapOf()).entrySet(), hasSize(subLeafs()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTraverseWithPredicateSupplier(TestContext context) {
	init(context);
        List<Config.Key> allSubKeys = config()
                // ignore whole list nodes
                .traverse(node -> node.type() != Config.Type.OBJECT)
                .map(Config::key)
                .collect(Collectors.toList());

        assertThat(allSubKeys, hasSize(subNodesNoObjects()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTraverseSupplier(TestContext context) {
	init(context);
        List<Config.Key> allSubKeys = config()
                .traverse()
                .map(Config::key)
                .collect(Collectors.toList());

        assertThat(allSubKeys.size(), is(subNodes()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testToStringSupplier(TestContext context) {
	init(context);
        /*
            VALUE	list-2.0
            OBJECT	list-2.1
            LIST	list-2.2
            VALUE	list-2.3
            VALUE	list-2.4
            VALUE	list-2.5
            VALUE	list-2.6
            LIST	list-2.7
         */
        assertThat(config().toString(), both(startsWith("["))
                .and(endsWith(key() + "] LIST (elements: 8)")));
    }

    /**
     * Count number of sub-nodes.
     *
     * @param levelCount     number of nodes on single level
     * @param includeObjects if count also OBJECT trees
     * @return summary count
     */
    private int subCount(int levelCount, boolean includeObjects) {
        //TODO improved "computation"
        switch (level()) {
            case MAX_LEVELS:
                return levelCount;
            case MAX_LEVELS - 1:
                return (includeObjects ? 3 : 2) * levelCount;
            case MAX_LEVELS - 2:
                return (includeObjects ? 7 : 3) * levelCount;
            default:
                break;
        }
        return -1;
    }

    private int subNodes() {
        /*
            VALUE	list-2.0
            OBJECT	list-2.1
            LIST	list-2.2
            VALUE	list-2.3
            VALUE	list-2.4
            VALUE	list-2.5
            VALUE	list-2.6
            LIST	list-2.7
            VALUE	list-2.7.0
            VALUE	list-2.7.1
            VALUE	list-2.7.2
         */
        return subCount(11, true);
    }

    private int subNodesNoObjects() {
        /*
            VALUE	list-2.0
            LIST	list-2.2
            VALUE	list-2.3
            VALUE	list-2.4
            VALUE	list-2.5
            VALUE	list-2.6
            LIST	list-2.7
            VALUE	list-2.7.0
            VALUE	list-2.7.1
            VALUE	list-2.7.2
         */
        return subCount(10, false);
    }

    private int subLeafs() {
        /*
            VALUE	list-2.0
            VALUE	list-2.3
            VALUE	list-2.4
            VALUE	list-2.5
            VALUE	list-2.6
            VALUE	list-2.7.0
            VALUE	list-2.7.1
            VALUE	list-2.7.2
         */
        return subCount(8, true);
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTraverse(TestContext context) {
	init(context);
        List<Config.Key> allSubKeys = config()
                .traverse()
                .map(Config::key)
                .collect(Collectors.toList());

        assertThat(allSubKeys.size(), is(subNodes()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testToString(TestContext context) {
	init(context);
        /*
            VALUE	list-2.0
            OBJECT	list-2.1
            LIST	list-2.2
            VALUE	list-2.3
            VALUE	list-2.4
            VALUE	list-2.5
            VALUE	list-2.6
            LIST	list-2.7
         */
        assertThat(config().toString(), both(startsWith("["))
                .and(endsWith(key() + "] LIST (elements: 8)")));
    }

    //
    // helper
    //

    private <T> void getConfigAndExpectException(Function<Config,T> op) {
        getConfigAndExpectException("", op);
    }

    private <T> void getConfigAndExpectException(String key, Function<Config,T> op) {
        Config config = config().get(key);
        ConfigMappingException ex = assertThrows(ConfigMappingException.class, () -> {
            op.apply(config);
                });
        assertTrue(ex.getMessage().contains("'" + config.key() + "'"));
    }

    protected Config config() {
        return config("");
    }

    private Config.Key key() {
        return config().key();
    }

    private String nodeName() {
        return nodeName(config());
    }

    private ObjectConfigBean[] expectedObjectConfigBeans() {
        return CollectionsHelper.listOf(new ObjectConfigBean("fromConfig", "key:0@VALUE"),
                       new ObjectConfigBean("fromConfig", "key:1@OBJECT"),
                       new ObjectConfigBean("fromConfig", "key:2@LIST"),
                       new ObjectConfigBean("fromConfig", "key:3@VALUE"),
                       new ObjectConfigBean("fromConfig", "key:4@VALUE"),
                       new ObjectConfigBean("fromConfig", "key:5@VALUE"),
                       new ObjectConfigBean("fromConfig", "key:6@VALUE"),
                       new ObjectConfigBean("fromConfig", "key:7@LIST"))
                .toArray(new ObjectConfigBean[0]);
    }

}
