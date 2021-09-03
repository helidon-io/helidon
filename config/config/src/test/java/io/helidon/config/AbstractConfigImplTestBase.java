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

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;


/**
 * General tests of {@link Config} to be extended by test classes for missing, value, object and list node types.
 */
public abstract class AbstractConfigImplTestBase {

    private Config config;

    protected static final int MAX_LEVELS = 3;
    private static final Config CONFIG = ConfigTest.createTestConfig(MAX_LEVELS);
    
    private TestContext context = null;

    protected static class TestContext {
        private final String key;
        private final int level;
        private final boolean detached;
        
        TestContext(String key, int level, boolean detached) {
            this.key = key;
            this.level = level;
            this.detached = detached;
        }
    }
    
    protected void init(TestContext context) {
        this.context = context;
        this.config = context.detached ? CONFIG.get(context.key).detach() : CONFIG.get(context.key);
    }
    
    protected void init(boolean detached) {
        context = new TestContext("", 0, detached);
        config = detached
                      ? ConfigTest.createTestConfig(1).detach()
                      : ConfigTest.createTestConfig(1);
    }
    
    protected int level() {
        return context.level;
    }
    
    protected AbstractConfigImplTestBase() {
    }

    protected Config config(String key) {
        return config.get(key);
    }

    protected Config configViaSupplier(String key) {
        return config.get(key).asNode().supplier().get();
    }

    protected void testTimestamp(Config config) {
        Instant timestamp = config.timestamp();
        assertThat(timestamp, greaterThan(Instant.now().minusSeconds(60)));
        assertThat(timestamp, lessThan(Instant.now()));
    }

    @Test
    void testMappingManagerConvert() {
        // TODO implement
    }

    @Test
    void testConfigValueConvert() {
        // TODO implement
    }

    public abstract void testTimestamp(TestContext context);

    public abstract void testDetach(TestContext context);

    public abstract void testTypeExists(TestContext context);

    public abstract void testTypeIsLeaf(TestContext context);

    public abstract void testAsNode(TestContext context);

    public abstract void testIfExists(TestContext context);

    public abstract void testValue(TestContext context);

    public abstract void testAs(TestContext context);

    public abstract void testAsList(TestContext context);

    public abstract void testAsString(TestContext context);

    public abstract void testAsBoolean(TestContext context);

    public abstract void testAsInt(TestContext context);

    public abstract void testAsLong(TestContext context);

    public abstract void testAsDouble(TestContext context);

    public abstract void testAsNodeList(TestContext context);

    public abstract void testAsMap(TestContext context);

    public abstract void testTraverse(TestContext context);

    public abstract void testTraverseWithPredicate(TestContext context);

    public abstract void testToString(TestContext context);

    public abstract void testTimestampSupplier(TestContext context);

    public abstract void testDetachSupplier(TestContext context);

    public abstract void testTypeExistsSupplier(TestContext context);

    public abstract void testTypeIsLeafSupplier(TestContext context);

    public abstract void testNodeSupplier(TestContext context);

    public abstract void testIfExistsSupplier(TestContext context);

    public abstract void testTraverseSupplier(TestContext context);

    public abstract void testTraverseWithPredicateSupplier(TestContext context);

    public abstract void testToStringSupplier(TestContext context);

    //
    // helpers
    //

    public static List<String> objectNames(int level) {
        return List.of("text-" + level + "@VALUE",
                       "object-" + level + "@OBJECT",
                       "list-" + level + "@LIST",
                       "bool-" + level + "@VALUE",
                       "double-" + level + "@VALUE",
                       "int-" + level + "@VALUE",
                       "long-" + level + "@VALUE",
                       "str-list-" + level + "@LIST");
    }


    public static List<String> nodeNames(List<Config> nodeList) {
        return nodeList.stream()
                .map(AbstractConfigImplTestBase::nodeName)
                .collect(Collectors.toList());
    }

    public static String nodeName(Config node) {
        return node.name() + "@" + node.type();
    }

    public static class ValueConfigBean {
        static final ValueConfigBean EMPTY = new ValueConfigBean("EMPTY", "");

        private final String meta;
        private final String text;

        public ValueConfigBean(String meta, String text) {
            this.meta = meta;
            this.text = text;
        }

        public String getText() {
            return text;
        }

        static ValueConfigBean empty() {
            return EMPTY;
        }

        public static ValueConfigBean fromString(String string) {
            return new ValueConfigBean("fromString", string);
        }

        // unit test support to build for comparison
        public static ValueConfigBean utFromConfig(String string) {
            return new ValueConfigBean("fromConfig", string);
        }

        public static ValueConfigBean fromConfig(Config config) {
            return new ValueConfigBean("fromConfig", config.asString().get());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ValueConfigBean that = (ValueConfigBean) o;

            if (!meta.equals(that.meta)) {
                return false;
            }
            return text.equals(that.text);
        }

        @Override
        public int hashCode() {
            int result = meta.hashCode();
            result = 31 * result + text.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "ValueConfigBean{" +
                    "meta='" + meta + '\'' +
                    ", text='" + text + '\'' +
                    '}';
        }
    }

    public static class ObjectConfigBean {
        static final ObjectConfigBean EMPTY = new ObjectConfigBean("EMPTY", "");

        private final String meta;
        private final String text;

        public ObjectConfigBean(String meta, String text) {
            this.meta = meta;
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public static ObjectConfigBean empty() {
            return EMPTY;
        }

        public static ObjectConfigBean fromConfig(Config config) {
            return new ObjectConfigBean("fromConfig", "key:" + nodeName(config));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ObjectConfigBean that = (ObjectConfigBean) o;

            if (!meta.equals(that.meta)) {
                return false;
            }
            return text.equals(that.text);
        }

        @Override
        public int hashCode() {
            int result = meta.hashCode();
            result = 31 * result + text.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "ObjectConfigBean{" +
                    "meta='" + meta + '\'' +
                    ", text='" + text + '\'' +
                    '}';
        }
    }

    public static class NoMapperConfigBean {
        private NoMapperConfigBean() {
        }
    }

}
