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
import static io.helidon.config.Config.Type.OBJECT;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
 * Tests {@link Config} API in case the node is {@link Config.Type#OBJECT} type,
 * i.e. {@link ConfigObjectImpl}.
 */
public class ConfigObjectImplTest extends AbstractConfigImplTest {

    public static Stream<TestContext> initParams() {
        return Stream.of(
                // use root config properties
                new TestContext("", 1, true),
                new TestContext("", 1, false),
                // and object properties
                new TestContext("object-1", 2, true),
                new TestContext("object-1", 2, false),
                // and sub-object properties
                new TestContext("object-1.object-2", 3, true),
                new TestContext("object-1.object-2", 3, false),
                // and object in a list properties
                new TestContext("list-1.1", 3, true),
                new TestContext("list-1.1", 3, false)
        );
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTypeExists(TestContext context) {
        init(context);
        assertThat(config().exists(), is(true));
        assertThat(config().type().exists(), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTypeIsLeaf(TestContext context) {
        init(context);
        assertThat(config().isLeaf(), is(false));
        assertThat(config().type().isLeaf(), is(false));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testValue(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.value());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalString(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asOptionalString());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testNodeList(TestContext context) {
        init(context);
        assertThat(nodeNames(config().nodeList().get()),
                containsInAnyOrder(objectNames()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptional(TestContext context) {
        init(context);
        assertThat(config().asOptional(ObjectConfigBean.class).get(),
                is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalList(TestContext context) {
        init(context);
        assertThat(config().asOptionalList(ObjectConfigBean.class).get(),
                containsInAnyOrder(expectedObjectConfigBeans()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalStringList(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asOptionalStringList());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalWithFunction(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.mapOptional(ValueConfigBean::fromString));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalWithConfigMapper(TestContext context) {
        init(context);
        assertThat(config().mapOptional(ObjectConfigBean::fromConfig).get(),
                is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
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
    public void testAsOptionalBoolean(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asOptionalBoolean());
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
        assertThat(config().as(ObjectConfigBean.class), is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
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
        getConfigAndExpectException(config -> config.asStringList());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsStringListWithDefault(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asStringList(CollectionsHelper.listOf("default", "value")));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeList(TestContext context) {
        init(context);
        assertThat(nodeNames(config().asNodeList()),
                containsInAnyOrder(objectNames()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeListWithDefault(TestContext context) {
        init(context);
        assertThat(nodeNames(config().asNodeList(CollectionsHelper.listOf(Config.empty()))),
                containsInAnyOrder(objectNames()));
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
        assertThat(detached.type(), is(OBJECT));
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
                () -> fail("Expected config not found"));

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
                .traverse(node -> node.type() != Config.Type.LIST)
                .map(Config::key)
                .collect(Collectors.toList());

        assertThat(allSubKeys, hasSize(subNodesNoLists()));
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
                containsInAnyOrder(objectNames()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalSupplier(TestContext context) {
        init(context);
        assertThat(config().asOptionalSupplier(ObjectConfigBean.class).get().get(),
                is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalListSupplier(TestContext context) {
        init(context);
        assertThat(config().asOptionalListSupplier(ObjectConfigBean.class).get().get(),
                containsInAnyOrder(expectedObjectConfigBeans()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalStringListSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asOptionalStringListSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalWithFunctionSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.mapOptionalSupplier(ValueConfigBean::fromString).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalWithConfigMapperSupplier(TestContext context) {
        init(context);
        assertThat(config().mapOptionalSupplier(ObjectConfigBean::fromConfig).get().get(),
                is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalBooleanSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asOptionalBooleanSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalIntSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asOptionalIntSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalLongSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asOptionalLongSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalDoubleSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asOptionalDoubleSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalListWithFunctionSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.mapOptionalListSupplier(ValueConfigBean::fromString).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalListWithConfigMapperSupplier(TestContext context) {
        init(context);
        assertThat(config().mapOptionalListSupplier(ObjectConfigBean::fromConfig).get().get(),
                containsInAnyOrder(expectedObjectConfigBeans()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsSupplier(TestContext context) {
        init(context);
        assertThat(config().asSupplier(ObjectConfigBean.class).get(),
                is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsWithDefaultSupplier(TestContext context) {
        init(context);
        assertThat(config().asSupplier(ObjectConfigBean.class, ObjectConfigBean.EMPTY).get(),
                is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithFunctionSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.mapSupplier(ValueConfigBean::fromString).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithFunctionAndDefaultSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.mapSupplier(ValueConfigBean::fromString, ObjectConfigBean.EMPTY).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithConfigMapperSupplier(TestContext context) {
        init(context);
        assertThat(config().mapSupplier(ObjectConfigBean::fromConfig).get(),
                is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithConfigMapperAndDefaultSupplier(TestContext context) {
        init(context);
        assertThat(config().mapSupplier(ObjectConfigBean::fromConfig, ObjectConfigBean.EMPTY).get(),
                is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsListSupplier(TestContext context) {
        init(context);
        assertThat(config().asListSupplier(ObjectConfigBean.class).get(),
                containsInAnyOrder(expectedObjectConfigBeans()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsListWithDefaultSupplier(TestContext context) {
        init(context);
        assertThat(config().asListSupplier(ObjectConfigBean.class, CollectionsHelper.listOf(ObjectConfigBean.EMPTY)).get(),
                containsInAnyOrder(expectedObjectConfigBeans()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithFunctionSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.mapListSupplier(ValueConfigBean::fromString).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithFunctionAndDefaultSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.mapListSupplier(ValueConfigBean::fromString, CollectionsHelper.listOf(ValueConfigBean.EMPTY)).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithConfigMapperSupplier(TestContext context) {
        init(context);
        assertThat(config().mapListSupplier(ObjectConfigBean::fromConfig).get(),
                containsInAnyOrder(expectedObjectConfigBeans()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithConfigMapperAndDefaultSupplier(TestContext context) {
        init(context);
        assertThat(config().mapListSupplier(ObjectConfigBean::fromConfig, CollectionsHelper.listOf(ObjectConfigBean.EMPTY)).get(),
                containsInAnyOrder(expectedObjectConfigBeans()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsStringSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asStringSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsStringWithDefaultSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asStringSupplier("default value").get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsBooleanSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asBooleanSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsBooleanWithDefaultSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asBooleanSupplier(true).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsIntSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asIntSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsIntWithDefaultSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asIntSupplier(42).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLongSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asLongSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLongWithDefaultSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asLongSupplier(23).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDoubleSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asDoubleSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDoubleWithDefaultSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asDoubleSupplier(Math.PI).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsStringListSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asStringListSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsStringListWithDefaultSupplier(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asStringListSupplier(CollectionsHelper.listOf("default", "value")).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeListSupplier(TestContext context) {
        init(context);
        assertThat(nodeNames(config().asNodeListSupplier().get()),
                containsInAnyOrder(objectNames()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeListWithDefaultSupplier(TestContext context) {
        init(context);
        assertThat(nodeNames(config().asNodeListSupplier(CollectionsHelper.listOf(Config.empty())).get()),
                containsInAnyOrder(objectNames()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTimestampSupplier(TestContext context) {
        init(context);
        testTimestamp(config().nodeSupplier().get().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testDetachSupplier(TestContext context) {
        init(context);
        Config detached = config().detach().nodeSupplier().get().get();
        assertThat(detached.type(), is(OBJECT));
        assertThat(detached.key().toString(), is(""));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testNodeSupplier(TestContext context) {
        init(context);
        AtomicBoolean called = new AtomicBoolean(false);
        config()
                .nodeSupplier()
                .get()
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
        config()
                .nodeSupplier()
                .get()
                .get()
                .ifExists(
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
        config()
                .nodeSupplier()
                .get()
                .get()
                .ifExistsOrElse(
                        node -> {
                            assertThat(node.key(), is(key()));
                            called.set(true);
                        },
                        () -> fail("Expected config not found"));

        assertThat(called.get(), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalMapSupplier(TestContext context) {
        init(context);
        assertThat(config().asOptionalMapSupplier().get().get().entrySet(), hasSize(subLeafs()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsMapSupplier(TestContext context) {
        init(context);
        assertThat(config().asMapSupplier().get().entrySet(), hasSize(subLeafs()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsMapWithDefaultSupplier(TestContext context) {
        init(context);
        assertThat(config().asMapSupplier(CollectionsHelper.mapOf()).get().entrySet(), hasSize(subLeafs()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTraverseWithPredicateSupplier(TestContext context) {
        init(context);
        List<Config.Key> allSubKeys = config()
                .nodeSupplier()
                .get()
                .get()
                // ignore whole list nodes
                .traverse(node -> node.type() != Config.Type.LIST)
                .map(Config::key)
                .collect(Collectors.toList());

        assertThat(allSubKeys, hasSize(subNodesNoLists()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTraverseSupplier(TestContext context) {
        init(context);
        List<Config.Key> allSubKeys = config()
                .nodeSupplier()
                .get()
                .get()
                .traverse()
                .map(Config::key)
                .collect(Collectors.toList());

        assertThat(allSubKeys, hasSize(subNodes()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testToStringSupplier(TestContext context) {
        init(context);
        /*
            VALUE	object-2.double-3
            OBJECT	object-2.object-3
            LIST	object-2.str-list-3
            VALUE	object-2.long-3
            VALUE	object-2.text-3
            VALUE	object-2.bool-3
            LIST	object-2.list-3
            VALUE	object-2.int-3
         */
        assertThat(config().nodeSupplier().get().get().toString(), both(startsWith("["))
                .and(endsWith(key() + "] OBJECT (members: 8)")));
    }

    /**
     * Count number of sub-nodes.
     *
     * @param levelCount number of nodes on single level
     * @param includeLists if count also LIST trees
     * @return summary count
     */
    private int subCount(int levelCount, boolean includeLists) {
        //TODO improved "computation"
        switch (level()) {
            case MAX_LEVELS:
                return levelCount;
            case MAX_LEVELS - 1:
                return (includeLists ? 3 : 2) * levelCount;
            case MAX_LEVELS - 2:
                return (includeLists ? 7 : 3) * levelCount;
            default:
                break;
        }
        return -1;
    }

    private int subNodes() {
        /*
            VALUE	object-2.double-3
            OBJECT	object-2.object-3
            LIST	object-2.str-list-3
            VALUE	object-2.str-list-3.0
            VALUE	object-2.str-list-3.1
            VALUE	object-2.str-list-3.2
            VALUE	object-2.long-3
            VALUE	object-2.text-3
            VALUE	object-2.bool-3
            LIST	object-2.list-3
            VALUE	object-2.int-3
         */
        return subCount(11, true);
    }

    private int subNodesNoLists() {
        /*
            VALUE	object-2.double-3
            OBJECT	object-2.object-3
            VALUE	object-2.long-3
            VALUE	object-2.text-3
            VALUE	object-2.bool-3
            VALUE	object-2.int-3
         */
        return subCount(6, false);
    }

    private int subLeafs() {
        /*
            VALUE	object-2.double-3
            VALUE	object-2.str-list-3.0
            VALUE	object-2.str-list-3.1
            VALUE	object-2.str-list-3.2
            VALUE	object-2.long-3
            VALUE	object-2.text-3
            VALUE	object-2.bool-3
            VALUE	object-2.int-3
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

        assertThat(allSubKeys, hasSize(subNodes()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testToString(TestContext context) {
        init(context);
        /*
            VALUE	object-2.double-3
            OBJECT	object-2.object-3
            LIST	object-2.str-list-3
            VALUE	object-2.long-3
            VALUE	object-2.text-3
            VALUE	object-2.bool-3
            LIST	object-2.list-3
            VALUE	object-2.int-3
         */
        assertThat(config().toString(), both(startsWith("["))
                .and(endsWith(key() + "] OBJECT (members: 8)")));
    }

    //
    // helper
    //
    private <T> void getConfigAndExpectException(Function<Config, T> op) {
        Config config = config();
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

    private String[] objectNames() {
        return objectNames(level()).toArray(new String[0]);
    }

    private String nodeName() {
        return nodeName(config());
    }

    private ObjectConfigBean[] expectedObjectConfigBeans() {
        return CollectionsHelper.listOf(
                new ObjectConfigBean("fromConfig", "key:double-" + level() + "@VALUE"),
                new ObjectConfigBean("fromConfig", "key:bool-" + level() + "@VALUE"),
                new ObjectConfigBean("fromConfig", "key:object-" + level() + "@OBJECT"),
                new ObjectConfigBean("fromConfig", "key:int-" + level() + "@VALUE"),
                new ObjectConfigBean("fromConfig", "key:text-" + level() + "@VALUE"),
                new ObjectConfigBean("fromConfig", "key:str-list-" + level() + "@LIST"),
                new ObjectConfigBean("fromConfig", "key:long-" + level() + "@VALUE"),
                new ObjectConfigBean("fromConfig", "key:list-" + level() + "@LIST"))
                .toArray(new ObjectConfigBean[0]);
    }

}
