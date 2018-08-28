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

import java.util.Collections;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.Config.Value;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.stringContainsInOrder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link ConfigMapperManager}.
 */
public class ConfigMapperManagerTest {

    @Test
    public void testUnknownMapper() {
        ConfigMapperManager manager = BuilderImpl.buildMappers(false, Collections.emptyMap());

        Assertions.assertThrows(ConfigMappingException.class, () -> {
            manager.map(NoPublicConstructorBean.class, mock(Config.class));
        });
    }

    public static class NoPublicConstructorBean {
        private NoPublicConstructorBean() {
        }
    }

    @Test
    public void testTransientPublicConstructor() {
        ConfigMapperManager manager = BuilderImpl.buildMappers(false, Collections.emptyMap());

        Assertions.assertThrows(ConfigMappingException.class, () -> {
            manager.map(TransientPublicConstructorBean.class, mock(Config.class));
        });
    }

    public static class TransientPublicConstructorBean {
        @Config.Transient
        public TransientPublicConstructorBean() {
        }
    }

    @Test
    public void testTransientPublicConstructorWithConfig() {
        ConfigMapperManager manager = BuilderImpl.buildMappers(false, Collections.emptyMap());

        Assertions.assertThrows(ConfigMappingException.class, () -> {
            manager.map(TransientPublicConstructorWithConfigBean.class, mock(Config.class));
        });
    }

    public static class TransientPublicConstructorWithConfigBean {
        @Config.Transient
        public TransientPublicConstructorWithConfigBean(Config config) {
        }
    }

    @Test
    public void testTransientFromWithConfig() {
        ConfigMapperManager manager = BuilderImpl.buildMappers(false, Collections.emptyMap());

        TransientFromWithConfigBean bean = manager.map(TransientFromWithConfigBean.class, mock(Config.class));

        assertThat(bean.isOk(), is(true));
    }

    public static class TransientFromWithConfigBean extends TestingBean {
        private TransientFromWithConfigBean(boolean ok) {
            super(ok);
        }

        public TransientFromWithConfigBean() {
            this(true);
        }

        @Config.Transient //ignore from method
        public static TransientFromWithConfigBean from(Config config) {
            return new TransientFromWithConfigBean(false);
        }
    }

    @Test
    public void testBadTypeFromWithConfig() {
        ConfigMapperManager manager = BuilderImpl.buildMappers(false, Collections.emptyMap());

        BadTypeFromWithConfigBean bean = manager.map(BadTypeFromWithConfigBean.class, mock(Config.class));

        assertThat(bean.isOk(), is(true));
    }

    public static class BadTypeFromWithConfigBean extends TestingBean {
        public BadTypeFromWithConfigBean() {
            super(true);
        }

        public static String from(Config config) {
            return "bad-return-type";
        }
    }

    @Test
    public void testNotStaticFromWithConfig() {
        ConfigMapperManager manager = BuilderImpl.buildMappers(false, Collections.emptyMap());

        NotStaticFromWithConfigBean bean = manager.map(NotStaticFromWithConfigBean.class, mock(Config.class));

        assertThat(bean.isOk(), is(true));
    }

    public static class NotStaticFromWithConfigBean extends TestingBean {
        private NotStaticFromWithConfigBean(boolean ok) {
            super(ok);
        }

        public NotStaticFromWithConfigBean() {
            this(true);
        }

        public NotStaticFromWithConfigBean from(Config config) {
            return new NotStaticFromWithConfigBean(false);
        }
    }

    @Test
    public void testSubclassFromWithConfig() {
        ConfigMapperManager manager = BuilderImpl.buildMappers(false, Collections.emptyMap());

        SubclassFromWithConfigBean bean = manager.map(SubclassFromWithConfigBean.class, mock(Config.class));

        assertThat(bean.isOk(), is(true));
        assertThat(bean, instanceOf(SubclassFromWithConfigBeanImpl.class));
    }

    public static class SubclassFromWithConfigBean extends TestingBean {
        private SubclassFromWithConfigBean(boolean ok) {
            super(ok);
        }

        private SubclassFromWithConfigBean() {
            this(false);
        }

        public static SubclassFromWithConfigBeanImpl from(Config config) {
            return new SubclassFromWithConfigBeanImpl();
        }
    }

    public static class SubclassFromWithConfigBeanImpl extends SubclassFromWithConfigBean {
        private SubclassFromWithConfigBeanImpl() {
            super(true);
        }
    }

    //
    // complex tests
    //

    private void assertMapper(Class<?> type, boolean leaf, String expectedValue) {
        Config config = Config.builder()
                .sources(ConfigSources.from(CollectionsHelper.mapOf(
                        "value", "val1-val2",
                        "aaa", "val1",
                        "bbb", "val2")))
                .build();
        if (leaf) {
            config = config.get("value");
        }

        assertThat(config.as(type).toString(), is(expectedValue));
    }

    private void assertLeafMapper(Class<?> type, String expectedValue) {
        assertMapper(type, true, expectedValue);
    }

    private void assertObjectMapper(Class<?> type, String expectedValue) {
        assertMapper(type, false, expectedValue);
    }

    @Test
    public void testFailingPrivateMappers() {
        ConfigMappingException ex = Assertions.assertThrows(ConfigMappingException.class, () -> {
            assertLeafMapper(Bean0Private.class, "n/a");
        });
        Assertions.assertTrue(
                stringContainsInOrder(CollectionsHelper.listOf("Unsupported Java type", "no compatible config value mapper found")).matches(ex.getMessage()));
    }

    @Test
    public void testFailingTransientMappers() {
        ConfigMappingException ex = Assertions.assertThrows(ConfigMappingException.class, () -> {
            assertLeafMapper(Bean0Transient.class, "n/a");
        });
        Assertions.assertTrue(
                stringContainsInOrder(CollectionsHelper.listOf("Unsupported Java type", "no compatible config value mapper found")).matches(ex.getMessage()));
    }

    @Test
    public void testAddedMapperFromStringHavePrecedence() {
        String key = "my-key";
        Config config = Config.builder()
                .sources(ConfigSources.from(CollectionsHelper.mapOf(key, "val1-val2")))
                .addMapper(Bean0ExtraMappers.class, Bean0ExtraMappers::extraFromString)
                .build();
        Config node = config.get(key);

        assertThat(node.as(Bean0ExtraMappers.class).toString(), is("function:val1-val2"));
    }

    @Test
    public void testAddedMappersFromConfigHavePrecedence() {
        String key = "my-key";
        Config config = Config.builder()
                .sources(ConfigSources.from(CollectionsHelper.mapOf(key, "val1-val2")))
                .addMapper(Bean0ExtraMappers.class, Bean0ExtraMappers::extraFromConfig)
                .build();
        Config node = config.get(key);

        assertThat(node.as(Bean0ExtraMappers.class).toString(), is("mapper:val1-val2"));
    }

    @Test
    public void testFromConfigParamMethod() {
        assertLeafMapper(Bean1.class, "from:Config:val1-val2");
    }

    @Test
    public void testConfigConstructor() {
        assertLeafMapper(Bean2.class, "new:Config:val1-val2");
    }

    @Test
    public void testValueOfConfigMethod() {
        assertLeafMapper(Bean3.class, "valueOf:Config:val1-val2");
    }

    @Test
    public void testFromConfigMethod() {
        assertLeafMapper(Bean4.class, "fromConfig:val1-val2");
    }

    @Test
    public void testFromStringParamMethod() {
        assertLeafMapper(Bean5.class, "from:String:val1-val2");
    }

    @Test
    public void testStringConstructor() {
        assertLeafMapper(Bean6.class, "new:String:val1-val2");
    }

    @Test
    public void testValueOfStringMethod() {
        assertLeafMapper(Bean7.class, "valueOf:String:val1-val2");
    }

    @Test
    public void testFromStringMethod() {
        assertLeafMapper(Bean8.class, "fromString:val1-val2");
    }

    @Test
    public void testBuilder() {
        assertObjectMapper(Bean9.class, "builder:val1-val2");
    }

    @Test
    public void testFactoryFrom() {
        assertObjectMapper(Bean10.class, "from:factory:val1-val2");
    }

    @Test
    public void testFactoryConstructor() {
        assertObjectMapper(Bean11.class, "new:factory:val1-val2");
    }

    @Test
    public void testDeserialization() {
        assertObjectMapper(Bean12.class, "new:val1-val2");
    }

    @Test
    public void testParseStringMethod() {
        assertLeafMapper(Bean13.class, "val1-val2");
    }

    @Test
    public void testParseCharSequenceMethod() {
        assertLeafMapper(Bean14.class, "val1-val2");
    }

    @Test
    public void testEnum1() {
        String key = "my-key";
        Config config = Config.builder()
                .sources(ConfigSources.from(CollectionsHelper.mapOf(key, "A"))) //"a" should fail
                .build();
        Config node = config.get(key);

        assertThat(node.as(Enum1.class).toString(), is("A"));
    }

    @Test
    public void testEnum2() {
        String key = "my-key";
        Config config = Config.builder()
                .sources(ConfigSources.from(CollectionsHelper.mapOf(key, "a")))
                .build();
        Config node = config.get(key);

        assertThat(node.as(Enum2.class).toString(), is("A"));
    }

    @Test
    public void testExternalBuilderMapperWrongBuilder() {
        ConfigException ce = Assertions.assertThrows(ConfigException.class, () -> {
            ConfigMappers.from(IfaceA.class, WrongBuilderA.class);
        });
        Assertions.assertTrue(stringContainsInOrder(
                CollectionsHelper.listOf("WrongBuilderA", "does not provide accessible 'build()'", "IfaceA instance")).matches(ce.getMessage()));
    }

    @Test
    public void testExternalBuilderMapperCallMap() {
        Config config = Config.builder()
                .sources(ConfigSources.from(CollectionsHelper.mapOf(
                        "aaa", "val1",
                        "bbb", "val2")))
                .build();

        IfaceA a = config.map(ConfigMappers.from(IfaceA.class, GenericBuilderA.class));

        assertThat(a.getAaa(), is("val1"));
        assertThat(a.getBbb(), is("val2"));
    }

    @Test
    public void testExternalBuilderMapperRegisterMapper() {
        Config config = Config.builder()
                .sources(ConfigSources.from(CollectionsHelper.mapOf(
                        "aaa", "val1",
                        "bbb", "val2")))
                .addMapper(IfaceA.class, ConfigMappers.from(IfaceA.class, FromConfigBuilderA.class))
                .build();

        IfaceA a = config.as(IfaceA.class);

        assertThat(a.getAaa(), is("val1"));
        assertThat(a.getBbb(), is("val2"));
    }

    //
    // testing types
    //

    private abstract static class AbstractBean {
        private String init;
        private String aaa;
        private String bbb;

        private AbstractBean(String aaa, String bbb, String init) {
            this.aaa = aaa;
            this.bbb = bbb;
            this.init = init;
        }

        protected void setAaa(String aaa) {
            this.aaa = aaa;
        }

        protected void setBbb(String bbb) {
            this.bbb = bbb;
        }

        @Override
        public String toString() {
            return init + ":" + aaa + "-" + bbb;
        }
    }

    /**
     * All constructors and methods are private, i.e. not accessible.
     * Contains same constructors and methods as {@link Bean1} BUT private.
     */
    public static class Bean0Private extends AbstractBean {
        private static Bean0Private from(Config config) {
            return new Bean0Private(config.asString().split("-")[0], config.asString().split("-")[1], "from:Config");
        }

        private Bean0Private(Config config) {
            this(config.asString().split("-")[0], config.asString().split("-")[1], "new:Config");
        }

        private static Bean0Private valueOf(Config config) {
            return new Bean0Private(config.asString().split("-")[0], config.asString().split("-")[1], "valueOf:Config");
        }

        private static Bean0Private fromConfig(Config config) {
            return new Bean0Private(config.asString().split("-")[0], config.asString().split("-")[1], "fromConfig");
        }

        private static Bean0Private from(String raw) {
            return new Bean0Private(raw.split("-")[0], raw.split("-")[1], "from:String");
        }

        private Bean0Private(String raw) {
            this(raw.split("-")[0], raw.split("-")[1], "new:String");
        }

        private static Bean0Private valueOf(String raw) {
            return new Bean0Private(raw.split("-")[0], raw.split("-")[1], "valueOf:String");
        }

        private static Bean0Private fromString(String raw) {
            return new Bean0Private(raw.split("-")[0], raw.split("-")[1], "fromString");
        }

        private static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String aaa;
            private String bbb;

            private Builder() {
            }

            @Value
            public Builder setAaa(String aaa) {
                this.aaa = aaa;
                return this;
            }

            @Value
            public Builder setBbb(String bbb) {
                this.bbb = bbb;
                return this;
            }

            public Bean0Private build() {
                return new Bean0Private(aaa, bbb, "builder");
            }
        }

        private static Bean0Private from(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            return new Bean0Private(aaa, bbb, "from:factory");
        }

        private Bean0Private(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            this(aaa, bbb, "new:factory");
        }

        private Bean0Private(String aaa, String bbb, String init) {
            super(aaa, bbb, init);
        }

        private Bean0Private() {
            super("", "", "new");
        }

        @Override
        public void setAaa(String aaa) {
            super.setAaa(aaa);
        }

        @Override
        public void setBbb(String bbb) {
            super.setBbb(bbb);
        }
    }

    /**
     * All constructors and methods are annotated by {@link Config.Transient}, i.e. not accessible.
     * Contains same constructors and methods as {@link Bean1} BUT {@link Config.Transient}.
     */
    public static class Bean0Transient extends AbstractBean {
        @Config.Transient
        public static Bean0Transient from(Config config) {
            return new Bean0Transient(config.asString().split("-")[0], config.asString().split("-")[1], "from:Config");
        }

        @Config.Transient
        public Bean0Transient(Config config) {
            this(config.asString().split("-")[0], config.asString().split("-")[1], "new:Config");
        }

        @Config.Transient
        public static Bean0Transient valueOf(Config config) {
            return new Bean0Transient(config.asString().split("-")[0], config.asString().split("-")[1], "valueOf:Config");
        }

        @Config.Transient
        public static Bean0Transient fromConfig(Config config) {
            return new Bean0Transient(config.asString().split("-")[0], config.asString().split("-")[1], "fromConfig");
        }

        @Config.Transient
        public static Bean0Transient from(String raw) {
            return new Bean0Transient(raw.split("-")[0], raw.split("-")[1], "from:String");
        }

        @Config.Transient
        public Bean0Transient(String raw) {
            this(raw.split("-")[0], raw.split("-")[1], "new:String");
        }

        @Config.Transient
        public static Bean0Transient valueOf(String raw) {
            return new Bean0Transient(raw.split("-")[0], raw.split("-")[1], "valueOf:String");
        }

        @Config.Transient
        public static Bean0Transient fromString(String raw) {
            return new Bean0Transient(raw.split("-")[0], raw.split("-")[1], "fromString");
        }

        @Config.Transient
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String aaa;
            private String bbb;

            private Builder() {
            }

            @Value
            public Builder setAaa(String aaa) {
                this.aaa = aaa;
                return this;
            }

            @Value
            public Builder setBbb(String bbb) {
                this.bbb = bbb;
                return this;
            }

            public Bean0Transient build() {
                return new Bean0Transient(aaa, bbb, "builder");
            }
        }

        @Config.Transient
        public static Bean0Transient from(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            return new Bean0Transient(aaa, bbb, "from:factory");
        }

        @Config.Transient
        public Bean0Transient(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            this(aaa, bbb, "new:factory");
        }

        private Bean0Transient(String aaa, String bbb, String init) {
            super(aaa, bbb, init);
        }

        @Config.Transient
        public Bean0Transient() {
            super("", "", "new");
        }

        @Override
        public void setAaa(String aaa) {
            super.setAaa(aaa);
        }

        @Override
        public void setBbb(String bbb) {
            super.setBbb(bbb);
        }
    }

    /**
     * Extra mappers registered in {@link Config.Builder} has precedence.
     * Contains same constructors and methods as {@link Bean1}.
     */
    public static class Bean0ExtraMappers extends AbstractBean {
        public static Bean0ExtraMappers extraFromString(String raw) {
            return new Bean0ExtraMappers(raw.split("-")[0], raw.split("-")[1], "function");
        }

        public static Bean0ExtraMappers extraFromConfig(Config config) {
            return new Bean0ExtraMappers(config.asString().split("-")[0], config.asString().split("-")[1], "mapper");
        }

        public static Bean0ExtraMappers from(Config config) {
            return new Bean0ExtraMappers(config.asString().split("-")[0], config.asString().split("-")[1], "from:Config");
        }

        public Bean0ExtraMappers(Config config) {
            this(config.asString().split("-")[0], config.asString().split("-")[1], "new:Config");
        }

        public static Bean0ExtraMappers valueOf(Config config) {
            return new Bean0ExtraMappers(config.asString().split("-")[0], config.asString().split("-")[1], "valueOf:Config");
        }

        public static Bean0ExtraMappers fromConfig(Config config) {
            return new Bean0ExtraMappers(config.asString().split("-")[0], config.asString().split("-")[1], "fromConfig");
        }

        public static Bean0ExtraMappers from(String raw) {
            return new Bean0ExtraMappers(raw.split("-")[0], raw.split("-")[1], "from:String");
        }

        public Bean0ExtraMappers(String raw) {
            this(raw.split("-")[0], raw.split("-")[1], "new:String");
        }

        public static Bean0ExtraMappers valueOf(String raw) {
            return new Bean0ExtraMappers(raw.split("-")[0], raw.split("-")[1], "valueOf:String");
        }

        public static Bean0ExtraMappers fromString(String raw) {
            return new Bean0ExtraMappers(raw.split("-")[0], raw.split("-")[1], "fromString");
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String aaa;
            private String bbb;

            private Builder() {
            }

            @Value
            public Builder setAaa(String aaa) {
                this.aaa = aaa;
                return this;
            }

            @Value
            public Builder setBbb(String bbb) {
                this.bbb = bbb;
                return this;
            }

            public Bean0ExtraMappers build() {
                return new Bean0ExtraMappers(aaa, bbb, "builder");
            }
        }

        public static Bean0ExtraMappers from(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            return new Bean0ExtraMappers(aaa, bbb, "from:factory");
        }

        public Bean0ExtraMappers(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            this(aaa, bbb, "new:factory");
        }

        private Bean0ExtraMappers(String aaa, String bbb, String init) {
            super(aaa, bbb, init);
        }

        public Bean0ExtraMappers() {
            super("", "", "new");
        }

        @Override
        public void setAaa(String aaa) {
            super.setAaa(aaa);
        }

        @Override
        public void setBbb(String bbb) {
            super.setBbb(bbb);
        }
    }

    /**
     * "Extends" {@link Bean2} by top priority "from(Config) method".
     */
    public static class Bean1 extends AbstractBean {
        public static Bean1 from(Config config) {
            return new Bean1(config.asString().split("-")[0], config.asString().split("-")[1], "from:Config");
        }

        public Bean1(Config config) {
            this(config.asString().split("-")[0], config.asString().split("-")[1], "new:Config");
        }

        public static Bean1 valueOf(Config config) {
            return new Bean1(config.asString().split("-")[0], config.asString().split("-")[1], "valueOf:Config");
        }

        public static Bean1 fromConfig(Config config) {
            return new Bean1(config.asString().split("-")[0], config.asString().split("-")[1], "fromConfig");
        }

        public static Bean1 from(String raw) {
            return new Bean1(raw.split("-")[0], raw.split("-")[1], "from:String");
        }

        public Bean1(String raw) {
            this(raw.split("-")[0], raw.split("-")[1], "new:String");
        }

        public static Bean1 valueOf(String raw) {
            return new Bean1(raw.split("-")[0], raw.split("-")[1], "valueOf:String");
        }

        public static Bean1 fromString(String raw) {
            return new Bean1(raw.split("-")[0], raw.split("-")[1], "fromString");
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String aaa;
            private String bbb;

            private Builder() {
            }

            @Value
            public Builder setAaa(String aaa) {
                this.aaa = aaa;
                return this;
            }

            @Value
            public Builder setBbb(String bbb) {
                this.bbb = bbb;
                return this;
            }

            public Bean1 build() {
                return new Bean1(aaa, bbb, "builder");
            }
        }

        public static Bean1 from(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            return new Bean1(aaa, bbb, "from:factory");
        }

        public Bean1(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            this(aaa, bbb, "new:factory");
        }

        private Bean1(String aaa, String bbb, String init) {
            super(aaa, bbb, init);
        }

        public Bean1() {
            super("", "", "new");
        }

        @Override
        public void setAaa(String aaa) {
            super.setAaa(aaa);
        }

        @Override
        public void setBbb(String bbb) {
            super.setBbb(bbb);
        }
    }

    /**
     * "Extends" {@link Bean3} by top priority "Config constructor".
     */
    public static class Bean2 extends AbstractBean {
        public Bean2(Config config) {
            this(config.asString().split("-")[0], config.asString().split("-")[1], "new:Config");
        }

        public static Bean2 valueOf(Config config) {
            return new Bean2(config.asString().split("-")[0], config.asString().split("-")[1], "valueOf:Config");
        }

        public static Bean2 fromConfig(Config config) {
            return new Bean2(config.asString().split("-")[0], config.asString().split("-")[1], "fromConfig");
        }

        public static Bean2 from(String raw) {
            return new Bean2(raw.split("-")[0], raw.split("-")[1], "from:String");
        }

        public Bean2(String raw) {
            this(raw.split("-")[0], raw.split("-")[1], "new:String");
        }

        public static Bean2 valueOf(String raw) {
            return new Bean2(raw.split("-")[0], raw.split("-")[1], "valueOf:String");
        }

        public static Bean2 fromString(String raw) {
            return new Bean2(raw.split("-")[0], raw.split("-")[1], "fromString");
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String aaa;
            private String bbb;

            private Builder() {
            }

            @Value
            public Builder setAaa(String aaa) {
                this.aaa = aaa;
                return this;
            }

            @Value
            public Builder setBbb(String bbb) {
                this.bbb = bbb;
                return this;
            }

            public Bean2 build() {
                return new Bean2(aaa, bbb, "builder");
            }
        }

        public static Bean2 from(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            return new Bean2(aaa, bbb, "from:factory");
        }

        public Bean2(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            this(aaa, bbb, "new:factory");
        }

        private Bean2(String aaa, String bbb, String init) {
            super(aaa, bbb, init);
        }

        public Bean2() {
            super("", "", "new");
        }

        @Override
        public void setAaa(String aaa) {
            super.setAaa(aaa);
        }

        @Override
        public void setBbb(String bbb) {
            super.setBbb(bbb);
        }
    }

    /**
     * "Extends" {@link Bean4} by top priority "valueOf(Config) method".
     */
    public static class Bean3 extends AbstractBean {
        public static Bean3 valueOf(Config config) {
            return new Bean3(config.asString().split("-")[0], config.asString().split("-")[1], "valueOf:Config");
        }

        public static Bean3 fromConfig(Config config) {
            return new Bean3(config.asString().split("-")[0], config.asString().split("-")[1], "fromConfig");
        }

        public static Bean3 from(String raw) {
            return new Bean3(raw.split("-")[0], raw.split("-")[1], "from:String");
        }

        public Bean3(String raw) {
            this(raw.split("-")[0], raw.split("-")[1], "new:String");
        }

        public static Bean3 valueOf(String raw) {
            return new Bean3(raw.split("-")[0], raw.split("-")[1], "valueOf:String");
        }

        public static Bean3 fromString(String raw) {
            return new Bean3(raw.split("-")[0], raw.split("-")[1], "fromString");
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String aaa;
            private String bbb;

            private Builder() {
            }

            @Value
            public Builder setAaa(String aaa) {
                this.aaa = aaa;
                return this;
            }

            @Value
            public Builder setBbb(String bbb) {
                this.bbb = bbb;
                return this;
            }

            public Bean3 build() {
                return new Bean3(aaa, bbb, "builder");
            }
        }

        public static Bean3 from(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            return new Bean3(aaa, bbb, "from:factory");
        }

        public Bean3(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            this(aaa, bbb, "new:factory");
        }

        private Bean3(String aaa, String bbb, String init) {
            super(aaa, bbb, init);
        }

        public Bean3() {
            super("", "", "new");
        }

        @Override
        public void setAaa(String aaa) {
            super.setAaa(aaa);
        }

        @Override
        public void setBbb(String bbb) {
            super.setBbb(bbb);
        }
    }

    /**
     * "Extends" {@link Bean5} by top priority "fromConfig(Config) method".
     */
    public static class Bean4 extends AbstractBean {
        public static Bean4 fromConfig(Config config) {
            return new Bean4(config.asString().split("-")[0], config.asString().split("-")[1], "fromConfig");
        }

        public static Bean4 from(String raw) {
            return new Bean4(raw.split("-")[0], raw.split("-")[1], "from:String");
        }

        public Bean4(String raw) {
            this(raw.split("-")[0], raw.split("-")[1], "new:String");
        }

        public static Bean4 valueOf(String raw) {
            return new Bean4(raw.split("-")[0], raw.split("-")[1], "valueOf:String");
        }

        public static Bean4 fromString(String raw) {
            return new Bean4(raw.split("-")[0], raw.split("-")[1], "fromString");
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String aaa;
            private String bbb;

            private Builder() {
            }

            @Value
            public Builder setAaa(String aaa) {
                this.aaa = aaa;
                return this;
            }

            @Value
            public Builder setBbb(String bbb) {
                this.bbb = bbb;
                return this;
            }

            public Bean4 build() {
                return new Bean4(aaa, bbb, "builder");
            }
        }

        public static Bean4 from(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            return new Bean4(aaa, bbb, "from:factory");
        }

        public Bean4(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            this(aaa, bbb, "new:factory");
        }

        private Bean4(String aaa, String bbb, String init) {
            super(aaa, bbb, init);
        }

        public Bean4() {
            super("", "", "new");
        }

        @Override
        public void setAaa(String aaa) {
            super.setAaa(aaa);
        }

        @Override
        public void setBbb(String bbb) {
            super.setBbb(bbb);
        }
    }

    /**
     * "Extends" {@link Bean6} by top priority "from(String) method".
     */
    public static class Bean5 extends AbstractBean {
        public static Bean5 from(String raw) {
            return new Bean5(raw.split("-")[0], raw.split("-")[1], "from:String");
        }

        public Bean5(String raw) {
            this(raw.split("-")[0], raw.split("-")[1], "new:String");
        }

        public static Bean5 valueOf(String raw) {
            return new Bean5(raw.split("-")[0], raw.split("-")[1], "valueOf:String");
        }

        public static Bean5 fromString(String raw) {
            return new Bean5(raw.split("-")[0], raw.split("-")[1], "fromString");
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String aaa;
            private String bbb;

            private Builder() {
            }

            @Value
            public Builder setAaa(String aaa) {
                this.aaa = aaa;
                return this;
            }

            @Value
            public Builder setBbb(String bbb) {
                this.bbb = bbb;
                return this;
            }

            public Bean5 build() {
                return new Bean5(aaa, bbb, "builder");
            }
        }

        public static Bean5 from(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            return new Bean5(aaa, bbb, "from:factory");
        }

        public Bean5(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            this(aaa, bbb, "new:factory");
        }

        private Bean5(String aaa, String bbb, String init) {
            super(aaa, bbb, init);
        }

        public Bean5() {
            super("", "", "new");
        }

        @Override
        public void setAaa(String aaa) {
            super.setAaa(aaa);
        }

        @Override
        public void setBbb(String bbb) {
            super.setBbb(bbb);
        }
    }

    /**
     * "Extends" {@link Bean7} by top priority "String constructor".
     */
    public static class Bean6 extends AbstractBean {
        public Bean6(String raw) {
            this(raw.split("-")[0], raw.split("-")[1], "new:String");
        }

        public static Bean6 valueOf(String raw) {
            return new Bean6(raw.split("-")[0], raw.split("-")[1], "valueOf:String");
        }

        public static Bean6 fromString(String raw) {
            return new Bean6(raw.split("-")[0], raw.split("-")[1], "fromString");
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String aaa;
            private String bbb;

            private Builder() {
            }

            @Value
            public Builder setAaa(String aaa) {
                this.aaa = aaa;
                return this;
            }

            @Value
            public Builder setBbb(String bbb) {
                this.bbb = bbb;
                return this;
            }

            public Bean6 build() {
                return new Bean6(aaa, bbb, "builder");
            }
        }

        public static Bean6 from(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            return new Bean6(aaa, bbb, "from:factory");
        }

        public Bean6(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            this(aaa, bbb, "new:factory");
        }

        private Bean6(String aaa, String bbb, String init) {
            super(aaa, bbb, init);
        }

        public Bean6() {
            super("", "", "new");
        }

        @Override
        public void setAaa(String aaa) {
            super.setAaa(aaa);
        }

        @Override
        public void setBbb(String bbb) {
            super.setBbb(bbb);
        }
    }

    /**
     * "Extends" {@link Bean8} by top priority "valueOf(String) method".
     */
    public static class Bean7 extends AbstractBean {
        public static Bean7 valueOf(String raw) {
            return new Bean7(raw.split("-")[0], raw.split("-")[1], "valueOf:String");
        }

        public static Bean7 fromString(String raw) {
            return new Bean7(raw.split("-")[0], raw.split("-")[1], "fromString");
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String aaa;
            private String bbb;

            private Builder() {
            }

            @Value
            public Builder setAaa(String aaa) {
                this.aaa = aaa;
                return this;
            }

            @Value
            public Builder setBbb(String bbb) {
                this.bbb = bbb;
                return this;
            }

            public Bean7 build() {
                return new Bean7(aaa, bbb, "builder");
            }
        }

        public static Bean7 from(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            return new Bean7(aaa, bbb, "from:factory");
        }

        public Bean7(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            this(aaa, bbb, "new:factory");
        }

        private Bean7(String aaa, String bbb, String init) {
            super(aaa, bbb, init);
        }

        public Bean7() {
            super("", "", "new");
        }

        @Override
        public void setAaa(String aaa) {
            super.setAaa(aaa);
        }

        @Override
        public void setBbb(String bbb) {
            super.setBbb(bbb);
        }
    }

    /**
     * "Extends" {@link Bean9} by top priority "fromString(String) method".
     */
    public static class Bean8 extends AbstractBean {
        public static Bean8 fromString(String raw) {
            return new Bean8(raw.split("-")[0], raw.split("-")[1], "fromString");
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String aaa;
            private String bbb;

            private Builder() {
            }

            @Value
            public Builder setAaa(String aaa) {
                this.aaa = aaa;
                return this;
            }

            @Value
            public Builder setBbb(String bbb) {
                this.bbb = bbb;
                return this;
            }

            public Bean8 build() {
                return new Bean8(aaa, bbb, "builder");
            }
        }

        public static Bean8 from(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            return new Bean8(aaa, bbb, "from:factory");
        }

        public Bean8(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            this(aaa, bbb, "new:factory");
        }

        private Bean8(String aaa, String bbb, String init) {
            super(aaa, bbb, init);
        }

        public Bean8() {
            super("", "", "new");
        }

        @Override
        public void setAaa(String aaa) {
            super.setAaa(aaa);
        }

        @Override
        public void setBbb(String bbb) {
            super.setBbb(bbb);
        }
    }

    /**
     * "Extends" {@link Bean10} by top priority "static builder()".
     */
    public static class Bean9 extends AbstractBean {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String aaa;
            private String bbb;

            private Builder() {
            }

            @Value
            public Builder setAaa(String aaa) {
                this.aaa = aaa;
                return this;
            }

            @Value
            public Builder setBbb(String bbb) {
                this.bbb = bbb;
                return this;
            }

            public Bean9 build() {
                return new Bean9(aaa, bbb, "builder");
            }
        }

        public static Bean9 from(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            return new Bean9(aaa, bbb, "from:factory");
        }

        public Bean9(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            this(aaa, bbb, "new:factory");
        }

        private Bean9(String aaa, String bbb, String init) {
            super(aaa, bbb, init);
        }

        public Bean9() {
            super("", "", "new");
        }

        @Override
        public void setAaa(String aaa) {
            super.setAaa(aaa);
        }

        @Override
        public void setBbb(String bbb) {
            super.setBbb(bbb);
        }
    }

    /**
     * "Extends" {@link Bean11} by top priority "static T from(param1, params...)".
     */
    public static class Bean10 extends AbstractBean {
        public static Bean10 from(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            return new Bean10(aaa, bbb, "from:factory");
        }

        public Bean10(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            this(aaa, bbb, "new:factory");
        }

        private Bean10(String aaa, String bbb, String init) {
            super(aaa, bbb, init);
        }

        public Bean10() {
            super("", "", "new");
        }

        @Override
        public void setAaa(String aaa) {
            super.setAaa(aaa);
        }

        @Override
        public void setBbb(String bbb) {
            super.setBbb(bbb);
        }
    }

    /**
     * "Extends" {@link Bean12} by top priority "(param1, params...) constructor".
     */
    public static class Bean11 extends AbstractBean {
        public Bean11(@Value(key = "aaa") String aaa, @Value(key = "bbb") String bbb) {
            this(aaa, bbb, "new:factory");
        }

        private Bean11(String aaa, String bbb, String init) {
            super(aaa, bbb, init);
        }

        public Bean11() {
            super("", "", "new");
        }

        @Override
        public void setAaa(String aaa) {
            super.setAaa(aaa);
        }

        @Override
        public void setBbb(String bbb) {
            super.setBbb(bbb);
        }
    }

    /**
     * Generic "deserialization" ready bean.
     */
    public static class Bean12 extends AbstractBean {
        private Bean12(String aaa, String bbb, String init) {
            super(aaa, bbb, init);
        }

        public Bean12() {
            this("", "", "new");
        }

        @Override
        public void setAaa(String aaa) {
            super.setAaa(aaa);
        }

        @Override
        public void setBbb(String bbb) {
            super.setBbb(bbb);
        }
    }

    /**
     * parse String bean
     */
    public static class Bean13 {
        private final String param;

        private Bean13(String param) {
            this.param = param;
        }

        public static Bean13 parse(String param) {
            return new Bean13(param);
        }

        @Override
        public String toString() {
            return param;
        }
    }

    /**
     * parse CharSequence bean
     */
    public static class Bean14 {
        private final CharSequence param;

        private Bean14(CharSequence param) {
            this.param = param;
        }

        public static Bean14 parse(CharSequence  param) {
            return new Bean14(param);
        }

        @Override
        public String toString() {
            return String.valueOf(param);
        }
    }

    public enum Enum1 {
        A("aaa"),
        B("bbb"),
        C("ccc");

        public String value;

        Enum1(String value) {
            this.value = value;
        }

    }

    public enum Enum2 {
        A("aaa"),
        B("bbb"),
        C("ccc");

        private String value;

        Enum2(String value) {
            this.value = value;
        }

        public static Enum2 valueOf(Config config) {
            return Enum2.valueOf(config.asString().toUpperCase());
        }

    }

    private abstract static class TestingBean {
        private final boolean ok;

        protected TestingBean(boolean ok) {
            this.ok = ok;
        }

        public boolean isOk() {
            return ok;
        }
    }

    public interface IfaceA {
        String getAaa();

        String getBbb();
    }

    public static class ImplA implements IfaceA {
        private final String aaa;
        private final String bbb;

        private ImplA(String aaa, String bbb) {
            this.aaa = aaa;
            this.bbb = bbb;
        }

        @Override
        public String getAaa() {
            return aaa;
        }

        @Override
        public String getBbb() {
            return bbb;
        }
    }

    public static class WrongBuilderA {
        public WrongBuilderA() {
        }

        public Object build() { //expected IfaceA
            return null;
        }
    }

    public static class GenericBuilderA {
        private String aaa;
        private String bbb;

        public GenericBuilderA() {
        }

        @Value
        public GenericBuilderA aaa(String aaa) {
            this.aaa = aaa;
            return this;
        }

        @Value
        public GenericBuilderA bbb(String bbb) {
            this.bbb = bbb;
            return this;
        }

        public IfaceA build() {
            return new ImplA(aaa, bbb);
        }
    }

    public static class FromConfigBuilderA {
        private String aaa;
        private String bbb;

        private FromConfigBuilderA() {
        }

        public FromConfigBuilderA aaa(String aaa) {
            this.aaa = aaa;
            return this;
        }

        public FromConfigBuilderA bbb(String bbb) {
            this.bbb = bbb;
            return this;
        }

        public static FromConfigBuilderA from(Config config) {
            return new FromConfigBuilderA()
                    .aaa(config.get("aaa").asString())
                    .bbb(config.get("bbb").asString());
        }

        public IfaceA build() {
            return new ImplA(aaa, bbb);
        }
    }

}
