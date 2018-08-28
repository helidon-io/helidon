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

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import io.helidon.common.CollectionsHelper;
import static io.helidon.config.Config.Type.MISSING;
import java.util.function.Function;
import java.util.stream.Stream;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests {@link Config} API in case the node is {@link Config.Type#MISSING} type, i.e. {@link ConfigMissingImpl}.
 */
public class ConfigMissingImplTest extends AbstractConfigImplTest {

    public static Stream<TestContext> initParams() {
        return Stream.of(
                // use root config properties
                new TestContext("", 0, false),
                new TestContext("", 0, true)
        );
    }

    protected Config config() {
        Config config = config(key());
        assertThat(config.type(), is(MISSING));
        return config;
    }

    protected String key() {
        return "this.is.missing.node";
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTypeExists(TestContext context) {
        init(context);
        assertThat(config().exists(), is(false));
        assertThat(config().type().exists(), is(false));
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
        assertThat(config().value(), is(Optional.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalString(TestContext context) {
        init(context);
	assertThat(config().asOptionalString(), is(Optional.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testNodeList(TestContext context) {
        init(context);
	assertThat(config().nodeList(), is(Optional.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptional(TestContext context) {
        init(context);
	assertThat(config().asOptional(ValueConfigBean.class), is(Optional.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalList(TestContext context) {
        init(context);
	assertThat(config().asOptionalList(ValueConfigBean.class), is(Optional.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalStringList(TestContext context) {
        init(context);
	assertThat(config().asOptionalStringList(), is(Optional.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalWithFunction(TestContext context) {
        init(context);
	assertThat(config().mapOptional(ValueConfigBean::fromString), is(Optional.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalWithConfigMapper(TestContext context) {
        init(context);
	assertThat(config().mapOptional(ValueConfigBean::fromConfig), is(Optional.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalBoolean(TestContext context) {
        init(context);
	assertThat(config().asOptionalBoolean(), is(Optional.empty()));
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
    public void testAsOptionalLong(TestContext context) {
        init(context);
	assertThat(config().asOptionalLong(), is(OptionalLong.empty()));
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
    public void testMapOptionalListWithFunction(TestContext context) {
        init(context);
	assertThat(config().mapOptionalList(ValueConfigBean::fromString), is(Optional.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalListWithConfigMapper(TestContext context) {
        init(context);
	assertThat(config().mapOptionalList(ValueConfigBean::fromConfig), is(Optional.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAs(TestContext context) {
        init(context);
	getConfigAndExpectException(config -> config.as(ValueConfigBean.class));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsWithDefault(TestContext context) {
        init(context);
	assertThat(config().as(ValueConfigBean.class, ValueConfigBean.EMPTY), is(ValueConfigBean.empty()));
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
	assertThat(config().map(ValueConfigBean::fromString, ValueConfigBean.EMPTY), is(ValueConfigBean.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithConfigMapper(TestContext context) {
        init(context);
	getConfigAndExpectException(config -> config.map(ValueConfigBean::fromConfig));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithConfigMapperAndDefault(TestContext context) {
        init(context);
	assertThat(config().map(ValueConfigBean::fromConfig, ValueConfigBean.EMPTY), is(ValueConfigBean.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsList(TestContext context) {
        init(context);
	getConfigAndExpectException(config -> config.asList(ValueConfigBean.class));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsListWithDefault(TestContext context) {
        init(context);
	assertThat(config().asList(ValueConfigBean.class, CollectionsHelper.listOf(ValueConfigBean.EMPTY)), contains(ValueConfigBean.empty()));
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
	assertThat(config().mapList(ValueConfigBean::fromString, CollectionsHelper.listOf(ValueConfigBean.EMPTY)),
                   contains(ValueConfigBean.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithConfigMapper(TestContext context) {
        init(context);
	getConfigAndExpectException(config -> config.mapList(ValueConfigBean::fromConfig));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithConfigMapperAndDefault(TestContext context) {
        init(context);
	assertThat(config().mapList(ValueConfigBean::fromConfig, CollectionsHelper.listOf(ValueConfigBean.EMPTY)),
                   contains(ValueConfigBean.empty()));
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
	assertThat(config().asString("default value"), is("default value"));
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
	assertThat(config().asBoolean(true), is(true));
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
	assertThat(config().asInt(42), is(42));
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
	assertThat(config().asLong(23), is(23L));
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
	assertThat(config().asDouble(Math.PI), is(Math.PI));
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
	assertThat(config().asStringList(CollectionsHelper.listOf("default", "value")), contains("default", "value"));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeList(TestContext context) {
        init(context);
	getConfigAndExpectException(config -> config.asNodeList());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeListWithDefault(TestContext context) {
        init(context);
	assertThat(config().asNodeList(CollectionsHelper.listOf(Config.empty())), contains(Config.empty()));
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
        assertThat(detached.type(), is(MISSING));
        assertThat(detached.key().toString(), is(""));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testNode(TestContext context) {
        init(context);
	config()
                .node()
                .ifPresent(node -> fail("Node unexpectedly present"));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testIfExists(TestContext context) {
        init(context);
	config()
                .ifExists(node -> fail("Config unexpectedly exists"));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testIfExistsOrElse(TestContext context) {
        init(context);
	AtomicBoolean called = new AtomicBoolean(false);
        config()
                .ifExistsOrElse(node -> fail("Config unexpectedly exists"),
                                () -> called.set(true));
        assertThat(called.get(), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalMap(TestContext context) {
        init(context);
	assertThat(config().asOptionalMap(),
                   is(Optional.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsMap(TestContext context) {
        init(context);
	getConfigAndExpectException(config -> config.asMap());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsMapWithDefault(TestContext context) {
        init(context);
	assertThat(config().asMap(CollectionsHelper.mapOf("k1", "v1")),
                   is(CollectionsHelper.mapOf("k1", "v1")));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTraverseWithPredicate(TestContext context) {
        init(context);
	assertThat(config()
                           .traverse((node) -> false)
                           .collect(Collectors.toList()),
                   is(empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTraverse(TestContext context) {
        init(context);
	assertThat(config()
                           .traverse()
                           .collect(Collectors.toList()),
                   is(empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testToString(TestContext context) {
        init(context);
	assertThat(config().toString(), both(startsWith("["))
                .and(endsWith(key() + "] MISSING")));
    }

    @Disabled
    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTypeExistsSupplier(TestContext context) {
        init(context);
    }

    @Disabled
    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTypeIsLeafSupplier(TestContext context) {
        init(context);
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testOptionalStringSupplier(TestContext context) {
        init(context);
	assertThat(config().asOptionalStringSupplier().get(), is(Optional.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testNodeListSupplier(TestContext context) {
        init(context);
	assertThat(config().asOptionalNodeListSupplier().get(), is(Optional.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalSupplier(TestContext context) {
        init(context);
	assertThat(config().asOptionalSupplier(ValueConfigBean.class).get(), is(Optional.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalListSupplier(TestContext context) {
        init(context);
	assertThat(config().asOptionalListSupplier(ValueConfigBean.class).get(), is(Optional.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalStringListSupplier(TestContext context) {
        init(context);
	assertThat(config().asOptionalStringListSupplier().get(), is(Optional.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalWithFunctionSupplier(TestContext context) {
        init(context);
	assertThat(config().mapOptionalSupplier(ValueConfigBean::fromString).get(), is(Optional.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalWithConfigMapperSupplier(TestContext context) {
        init(context);
	assertThat(config().mapOptionalSupplier(ValueConfigBean::fromConfig).get(), is(Optional.empty()));
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
    public void testAsOptionalIntSupplier(TestContext context) {
        init(context);
	assertThat(config().asOptionalIntSupplier().get(), is(OptionalInt.empty()));
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
    public void testMapOptionalListWithFunctionSupplier(TestContext context) {
        init(context);
	assertThat(config().mapOptionalListSupplier(ValueConfigBean::fromString).get(), is(Optional.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapOptionalListWithConfigMapperSupplier(TestContext context) {
        init(context);
	assertThat(config().mapOptionalListSupplier(ValueConfigBean::fromConfig).get(), is(Optional.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsSupplier(TestContext context) {
        init(context);
	getConfigAndExpectException(config -> config.asSupplier(ValueConfigBean.class).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsWithDefaultSupplier(TestContext context) {
        init(context);
	assertThat(config().asSupplier(ValueConfigBean.class, ValueConfigBean.EMPTY).get(), is(ValueConfigBean.empty()));
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
	assertThat(config().mapSupplier(ValueConfigBean::fromString, ValueConfigBean.EMPTY).get(),
                   is(ValueConfigBean.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithConfigMapperSupplier(TestContext context) {
        init(context);
	getConfigAndExpectException(config -> config.mapSupplier(ValueConfigBean::fromConfig).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapWithConfigMapperAndDefaultSupplier(TestContext context) {
        init(context);
	assertThat(config().mapSupplier(ValueConfigBean::fromConfig, ValueConfigBean.EMPTY).get(),
                   is(ValueConfigBean.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsListSupplier(TestContext context) {
        init(context);
	getConfigAndExpectException(config -> config.asListSupplier(ValueConfigBean.class).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsListWithDefaultSupplier(TestContext context) {
        init(context);
	assertThat(config().asListSupplier(ValueConfigBean.class, CollectionsHelper.listOf(ValueConfigBean.EMPTY)).get(),
                   contains(ValueConfigBean.empty()));
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
	assertThat(config().mapListSupplier(ValueConfigBean::fromString, CollectionsHelper.listOf(ValueConfigBean.EMPTY)).get(),
                   contains(ValueConfigBean.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithConfigMapperSupplier(TestContext context) {
        init(context);
	getConfigAndExpectException(config -> config.mapListSupplier(ValueConfigBean::fromConfig).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testMapListWithConfigMapperAndDefaultSupplier(TestContext context) {
        init(context);
	assertThat(config().mapListSupplier(ValueConfigBean::fromConfig, CollectionsHelper.listOf(ValueConfigBean.EMPTY)).get(),
                   contains(ValueConfigBean.empty()));
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
	assertThat(config().asStringSupplier("default value").get(), is("default value"));
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
	assertThat(config().asBooleanSupplier(true).get(), is(true));
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
	assertThat(config().asIntSupplier(42).get(), is(42));
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
	assertThat(config().asLongSupplier(23).get(), is(23L));
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
	assertThat(config().asDoubleSupplier(Math.PI).get(), is(Math.PI));
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
	assertThat(config().asStringListSupplier(CollectionsHelper.listOf("default", "value")).get(), contains("default", "value"));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeListSupplier(TestContext context) {
        init(context);
	getConfigAndExpectException(config -> config.asNodeListSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeListWithDefaultSupplier(TestContext context) {
        init(context);
	assertThat(config().asNodeListSupplier(CollectionsHelper.listOf(Config.empty())).get(), contains(Config.empty()));
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
        assertThat(detached.type(), is(MISSING));
        assertThat(detached.key().toString(), is(""));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testNodeSupplier(TestContext context) {
        init(context);
	config()
                .nodeSupplier()
                .get()
                .ifPresent(node -> fail("Node unexpectedly present"));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testIfExistsSupplier(TestContext context) {
        init(context);
	config()
                .ifExists(node -> fail("Config unexpectedly exists"));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testIfExistsOrElseSupplier(TestContext context) {
        init(context);
	AtomicBoolean called = new AtomicBoolean(false);
        config()
                .ifExistsOrElse(node -> fail("Config unexpectedly exists"),
                                () -> called.set(true));
        assertThat(called.get(), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalMapSupplier(TestContext context) {
        init(context);
	assertThat(config().asOptionalMapSupplier().get(),
                   is(Optional.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsMapSupplier(TestContext context) {
        init(context);
	getConfigAndExpectException(config -> config.asMapSupplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsMapWithDefaultSupplier(TestContext context) {
        init(context);
	assertThat(config().asMapSupplier(CollectionsHelper.mapOf("k1", "v1")).get(),
                   is(CollectionsHelper.mapOf("k1", "v1")));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTraverseWithPredicateSupplier(TestContext context) {
        init(context);
	assertThat(config()
                           .traverse((node) -> false)
                           .collect(Collectors.toList()),
                   is(empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTraverseSupplier(TestContext context) {
        init(context);
	assertThat(config()
                           .traverse()
                           .collect(Collectors.toList()),
                   is(empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testToStringSupplier(TestContext context) {
        init(context);
	assertThat(config().toString(), both(startsWith("["))
                .and(endsWith(key() + "] MISSING")));
    }

    //
    // helper
    //

    private <T> void getConfigAndExpectException(Function<Config,T> op) {
        Config config = config();
        MissingValueException ex = assertThrows(MissingValueException.class, () -> {
            op.apply(config);
                });
        assertTrue(ex.getMessage().contains("'" + config.key() + "'"));
    }

}
