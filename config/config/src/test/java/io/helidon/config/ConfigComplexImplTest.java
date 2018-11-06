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
package io.helidon.config;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.common.CollectionsHelper;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Methods common to {@link ConfigObjectImplTest} and {@link ConfigListImplTest}.
 */
public abstract class ConfigComplexImplTest extends AbstractConfigImplTest {
    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testNodeList(TestContext context) {
        init(context);
        assertThat(nodeNames(config().nodeList().get()),
                   containsInAnyOrder(itemNames()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeList(TestContext context) {
        init(context);
        assertThat(nodeNames(config().asNodeList()),
                   containsInAnyOrder(itemNames()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeListWithDefault(TestContext context) {
        init(context);
        assertThat(nodeNames(config().asNodeList(CollectionsHelper.listOf(Config.empty()))),
                   containsInAnyOrder(itemNames()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testNodeListSupplier(TestContext context) {
        init(context);
        assertThat(nodeNames(config().asNodeListSupplier().get()),
                   containsInAnyOrder(itemNames()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeListSupplier(TestContext context) {
        init(context);
        assertThat(nodeNames(config().asNodeListSupplier().get()),
                   containsInAnyOrder(itemNames()));
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
    public void testAsOptional(TestContext context) {
        init(context);
        assertThat(config().asOptional(ObjectConfigBean.class).get(),
                   is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalWithFunction(TestContext context) {
        init(context);
        getConfigAndExpectMissingException(config -> config.mapOptional(ValueConfigBean::fromString));
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
    public void testMapOptionalListWithFunction(TestContext context) {
        init(context);
        getConfigAndExpectMappingException(config -> config.mapOptionalList(ValueConfigBean::fromString));
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
    public void testAsString(TestContext context) {
        init(context);
        getConfigAndExpectMissingException(Config::asString);
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsStringWithDefault(TestContext context) {
        init(context);
        String defaultValue = "default value";

        assertThat(config().asString(defaultValue), is(defaultValue));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsBoolean(TestContext context) {
        init(context);
        getConfigAndExpectMissingException(Config::asBoolean);
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsBooleanWithDefault(TestContext context) {
        init(context);

        assertThat(config().asBoolean(true), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsInt(TestContext context) {
        init(context);

        getConfigAndExpectMissingException(Config::asInt);
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsIntWithDefault(TestContext context) {
        init(context);

        assertThat(config().asInt(42), is(42));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLong(TestContext context) {
        init(context);
        getConfigAndExpectMissingException(Config::asLong);
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLongWithDefault(TestContext context) {
        init(context);

        assertThat(config().asLong(23), is(23L));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDouble(TestContext context) {
        init(context);
        getConfigAndExpectMissingException(Config::asDouble);
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDoubleWithDefault(TestContext context) {
        init(context);
        assertThat(config().asDouble(Math.PI), is(Math.PI));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalBoolean(TestContext context) {
        init(context);
        getConfigAndAssertEmpty(Config::asOptionalBoolean);
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsBooleanSupplier(TestContext context) {
        init(context);
        getConfigAndExpectMissingException(config -> config.asBooleanSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsBooleanWithDefaultSupplier(TestContext context) {
        init(context);
        assertThat(config().asBooleanSupplier(true).get(), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalInt(TestContext context) {
        init(context);

        assertThat(config().asOptionalInt(), is(OptionalInt.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsIntSupplier(TestContext context) {
        init(context);
        getConfigAndExpectMissingException(config -> config.asIntSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsIntWithDefaultSupplier(TestContext context) {
        init(context);
        assertThat(config().asIntSupplier(42).get(), is(42));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalLong(TestContext context) {
        init(context);

        assertThat(config().asOptionalLong(), is(OptionalLong.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLongSupplier(TestContext context) {
        init(context);
        getConfigAndExpectMissingException(config -> config.asLongSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLongWithDefaultSupplier(TestContext context) {
        init(context);
        assertThat(config().asLongSupplier(23).get(), is(23L));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalDouble(TestContext context) {
        init(context);
        assertThat(config().asOptionalDouble(), is(OptionalDouble.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDoubleSupplier(TestContext context) {
        init(context);
        getConfigAndExpectMissingException(config -> config.asDoubleSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDoubleWithDefaultSupplier(TestContext context) {
        init(context);
        assertThat(config().asDoubleSupplier(Math.PI).get(), is(Math.PI));
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
    public void testAsOptionalIntSupplier(TestContext context) {
        init(context);
        assertThat(config().asOptionalIntSupplier().get(), is(OptionalInt.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalBooleanSupplier(TestContext context) {
        init(context);
        assertThat(config().asOptionalBooleanSupplier().get(), is(Optional.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalLongSupplier(TestContext context) {
        init(context);
        assertThat(config().asOptionalLongSupplier().get(), is(OptionalLong.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalDoubleSupplier(TestContext context) {
        init(context);
        assertThat(config().asOptionalDoubleSupplier().get(), is(OptionalDouble.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithFunctionSupplier(TestContext context) {
        init(context);

        getConfigAndExpectMissingException(config -> config.mapSupplier(ValueConfigBean::fromString).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithFunction(TestContext context) {
        init(context);
        getConfigAndExpectMissingException(config -> config.map(ValueConfigBean::fromString));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithFunctionAndDefault(TestContext context) {
        init(context);
        getConfigAndExpectMissingException(config -> config.map(ValueConfigBean::fromString, ObjectConfigBean.EMPTY));
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
        getConfigAndExpectMappingException(config -> config.mapList(ValueConfigBean::fromString));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithFunctionAndDefault(TestContext context) {
        init(context);
        getConfigAndExpectMappingException(config -> config
                .mapList(ValueConfigBean::fromString, CollectionsHelper.listOf(ValueConfigBean.EMPTY)));
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
    public void testTimestamp(TestContext context) {
        init(context);
        testTimestamp(config());
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
                () -> fail("Expected node does not exist"));

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
    public void testAsOptionalSupplier(TestContext context) {
        init(context);
        assertThat(config().asOptionalSupplier(ObjectConfigBean.class).get().get(),
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
    public void testAsOptionalListSupplier(TestContext context) {
        init(context);
        assertThat(config().asOptionalListSupplier(ObjectConfigBean.class).get().get(),
                   containsInAnyOrder(expectedObjectConfigBeans()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalWithFunctionSupplier(TestContext context) {
        init(context);
        getConfigAndExpectMissingException(config -> config.mapOptionalSupplier(ValueConfigBean::fromString).get());
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
    public void testMapOptionalListWithFunctionSupplier(TestContext context) {
        init(context);
        getConfigAndExpectMappingException(config -> config.mapOptionalListSupplier(ValueConfigBean::fromString).get());
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
    public void testTimestampSupplier(TestContext context) {
        init(context);
        testTimestamp(config().nodeSupplier().get().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithFunctionAndDefaultSupplier(TestContext context) {
        init(context);
        getConfigAndExpectMissingException(config -> config.mapSupplier(ValueConfigBean::fromString, ObjectConfigBean.EMPTY)
                .get());
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
        getConfigAndExpectMappingException(config -> config.mapListSupplier(ValueConfigBean::fromString).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithFunctionAndDefaultSupplier(TestContext context) {
        init(context);
        getConfigAndExpectMappingException(config -> config
                .mapListSupplier(ValueConfigBean::fromString, CollectionsHelper.listOf(ValueConfigBean.EMPTY)).get());
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
                        () -> {
                            fail("Expected config does not exists");
                        });

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
    public void testTraverse(TestContext context) {
        init(context);
        List<Config.Key> allSubKeys = config()
                .traverse()
                .map(Config::key)
                .collect(Collectors.toList());

        assertThat(allSubKeys, hasSize(subNodes()));
    }

    protected <T> void getConfigAndAssertEmpty(Function<Config, Optional<T>> op) {
        Config config = config().get("");
        Optional<T> apply = op.apply(config);

        assertThat(apply, is(Optional.empty()));
    }

    protected <T> void getConfigAndExpectMissingException(Function<Config, T> op) {
        Config config = config();

        MissingValueException ex = assertThrows(MissingValueException.class, () -> {
            op.apply(config);
        });

        ex.printStackTrace();
        assertThat(ex.getMessage(), containsString("'" + config.key() + "'"));
    }

    protected <T> void getConfigAndExpectMappingException(Function<Config, T> op) {
        Config config = config();

        ConfigMappingException ex = assertThrows(ConfigMappingException.class, () -> {
            op.apply(config);
        });

        assertThat(ex.getMessage(), containsString("'" + config.key() + "'"));
    }

    protected Config config() {
        return config("");
    }

    protected Config.Key key() {
        return config().key();
    }

    protected String nodeName() {
        return nodeName(config());
    }

    protected abstract ObjectConfigBean[] expectedObjectConfigBeans();

    protected abstract int subLeafs();

    protected abstract int subNodes();

    protected abstract String[] itemNames();
}
