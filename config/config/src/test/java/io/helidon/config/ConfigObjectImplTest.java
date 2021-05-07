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

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.helidon.config.Config.Type.OBJECT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

/**
 * Tests {@link Config} API in case the node is {@link Config.Type#OBJECT} type,
 * i.e. {@link ConfigObjectImpl}.
 */
public class ConfigObjectImplTest extends ConfigComplexImplTest {

    static Stream<TestContext> initParams() {
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
    public void testDetach(TestContext context) {
        init(context);
        Config detached = config().detach();
        assertThat(detached.type(), is(OBJECT));
        assertThat(detached.key().toString(), is(""));
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
    public void testValue(TestContext context) {
        init(context);
        assertValue(key -> config(key).asString().get());
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testDetachSupplier(TestContext context) {
        init(context);
        Config detached = config().detach().asNode().supplier().get();
        assertThat(detached.type(), is(OBJECT));
        assertThat(detached.key().toString(), is(""));
    }

    @Override
    @MethodSource("initParams")
    @ParameterizedTest
    public void testTraverseWithPredicateSupplier(TestContext context) {
        init(context);
        List<Config.Key> allSubKeys = config()
                .asNode()
                .supplier()
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
        assertThat(config().asNode().supplier().get().toString(), both(startsWith("["))
                .and(endsWith(key() + "] OBJECT (members: 8)")));
    }

    /**
     * Count number of sub-nodes.
     *
     * @param levelCount   number of nodes on single level
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

    @Override
    protected int subNodes() {
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

    @Override
    protected int subLeafs() {
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

    @Override
    protected String[] itemNames() {
        return objectNames(level()).toArray(new String[0]);
    }

    @Override
    protected ObjectConfigBean[] expectedObjectConfigBeans() {
        return List.of(
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

}
