/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.helidon.config.Config.Type.LIST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

/**
 * Tests {@link Config} API in case the node is {@link Config.Type#LIST} type, i.e. {@link ConfigListImpl}.
 */
public class ConfigListImplTest extends ConfigComplexImplTest {
    private static final String[] ITEM_NAMES = {
            "0@VALUE",
            "1@OBJECT",
            "2@LIST",
            "3@VALUE",
            "4@VALUE",
            "5@VALUE",
            "6@VALUE",
            "7@LIST"};

    static Stream<TestContext> initParams() {
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
    @MethodSource("initParams")
    @ParameterizedTest
    public void testValue(TestContext context) {
        init(context);
        getConfigAndAssertEmpty(config -> config.asString().asOptional());
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalListFromString(TestContext context) {
        init(context);
        getConfigAndExpectMappingException(config -> config.asList(ValueConfigBean::fromConfig).get());
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
    public void testTraverseWithPredicate(TestContext context) {
        init(context);
        List<Config.Key> allSubKeys = config()
                // ignore whole list nodes
                .traverse(node -> node.type() != Config.Type.OBJECT)
                .map(Config::key)
                .collect(Collectors.toList());

        assertThat(allSubKeys, hasSize(subNodesNoObjects()));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testAsOptionalListFromStringSupplier(TestContext context) {
        init(context);
        getConfigAndExpectMappingException(config -> config.asList(ValueConfigBean.class).get());
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
    public void testTraverseWithPredicateSupplier(TestContext context) {
        init(context);
        List<Config.Key> allSubKeys = config()
                .asNode()
                .get()
                // ignore whole list nodes
                .traverse(node -> node.type() != Config.Type.OBJECT)
                .map(Config::key)
                .collect(Collectors.toList());

        assertThat(allSubKeys, hasSize(subNodesNoObjects()));
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
        assertThat(config().asNode().optionalSupplier().get().get().toString(), both(startsWith("["))
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

    @Override
    protected String[] itemNames() {
        return ITEM_NAMES;
    }

    @Override
    protected int subNodes() {
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

    @Override
    protected int subLeafs() {
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

    @Override
    protected ObjectConfigBean[] expectedObjectConfigBeans() {
        return List.of(new ObjectConfigBean("fromConfig", "key:0@VALUE"),
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
