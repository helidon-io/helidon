/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Methods common to {@link ConfigObjectImplTest} and {@link ConfigListImplTest}.
 */
public abstract class ConfigComplexImplTest extends AbstractConfigImplTestBase {
    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNode(TestContext context) {
        init(context);
        AtomicBoolean called = new AtomicBoolean(false);
        config()
                .asNode()
                .ifPresent(node -> {
                    assertThat(node.key(), is(key()));
                    called.set(true);
                });
        assertThat(called.get(), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsNodeList(TestContext context) {
        init(context);
        assertThat(nodeNames(config().asNodeList().get()),
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
        assertThat(config().asNode().supplier().get().exists(), is(true));
        assertThat(config().asNode().supplier().get().type().exists(), is(true));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTypeIsLeafSupplier(TestContext context) {
        init(context);
        assertThat(config().asNode().supplier().get().isLeaf(), is(false));
        assertThat(config().asNode().supplier().get().type().isLeaf(), is(false));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAs(TestContext context) {
        init(context);
        assertThat(config().as(ObjectConfigBean::fromConfig).get(),
                   is(new ObjectConfigBean("fromConfig", "key:" + nodeName())));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsString(TestContext context) {
        init(context);
        getConfigAndExpectMissingException(config -> config.asString().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsBoolean(TestContext context) {
        init(context);
        getConfigAndExpectMissingException(config -> config.asBoolean().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsInt(TestContext context) {
        init(context);

        getConfigAndExpectMissingException(config -> config.asInt().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsLong(TestContext context) {
        init(context);
        getConfigAndExpectMissingException(config -> config.asLong().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsDouble(TestContext context) {
        init(context);
        getConfigAndExpectMissingException(config -> config.asDouble().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsList(TestContext context) {
        init(context);
        assertThat(config().asList(ObjectConfigBean::fromConfig).get(),
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
    public void testAsMap(TestContext context) {
        init(context);
        assertThat(config().asMap().get().entrySet(), hasSize(subLeafs()));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTimestampSupplier(TestContext context) {
        init(context);
        testTimestamp(config().asNode().supplier().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testNodeSupplier(TestContext context) {
        init(context);
        AtomicBoolean called = new AtomicBoolean(false);
        config()
                .asNode()
                .optionalSupplier()
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
                .asNode()
                .optionalSupplier()
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
    public void testTraverseSupplier(TestContext context) {
        init(context);
        List<Config.Key> allSubKeys = config()
                .asNode()
                .optionalSupplier()
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
