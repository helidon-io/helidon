/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.helidon.config.Config.Type.MISSING;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests {@link Config} API in case the node is {@link Config.Type#MISSING} type, i.e. {@link ConfigMissingImpl}.
 */
public class ConfigMissingImplTest extends AbstractConfigImplTestBase {

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
        assertThat(config().asString(), is(ConfigValues.empty()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAs(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.as(ValueConfigBean.class).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsList(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asList(ValueConfigBean.class).get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsString(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asString().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsBoolean(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asBoolean().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsInt(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asInt().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLong(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asLong().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDouble(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asDouble().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeList(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asNodeList().get());
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
    public void testAsNode(TestContext context) {
        init(context);
        config()
                .asNode()
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
    public void testAsMap(TestContext context) {
        init(context);
        getConfigAndExpectException(config -> config.asMap().get());
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

    @Disabled("Makes no sense for missing config")
    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTypeExistsSupplier(TestContext context) {
        init(context);
    }

    @Disabled("Makes no sense for missing config")
    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTypeIsLeafSupplier(TestContext context) {
        init(context);
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
        config().asNode()
                .optionalSupplier()
                .get()
                .ifPresent(node -> fail("Node unexpectedly present"));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testIfExistsSupplier(TestContext context) {
        init(context);
        config().ifExists(node -> fail("Config unexpectedly exists"));
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

    private <T> void getConfigAndExpectException(Function<Config, T> op) {
        Config config = config();
        MissingValueException ex = assertThrows(MissingValueException.class, () -> {
            op.apply(config);
        });
        assertThat(ex.getMessage(), containsString("'" + config.key() + "'"));
    }

}
