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

import java.net.URI;
import java.util.List;

import io.helidon.config.FactoryMethodConfigMapperTest.CustomType;
import io.helidon.config.FactoryMethodConfigMapperTest.DefaultCustomTypeSupplier;
import io.helidon.config.FactoryMethodConfigMapperTest.DefaultCustomTypesSupplier;
import io.helidon.config.FactoryMethodConfigMapperTest.DefaultNumbersSupplier;
import io.helidon.config.FactoryMethodConfigMapperTest.DefaultUrisSupplier;
import io.helidon.config.FactoryMethodConfigMapperTest.JavaBean;
import io.helidon.config.spi.ConfigNode;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ConfigMappers} with focus on builder pattern,
 * see {@link BuilderConfigMapper}.
 */
public class BuilderConfigMapperTest {

    @Test
    public void testBuilderNoBuildMethod() {
        Config config = Config.empty();

        BuilderNoBuildMethodBean bean = config.as(BuilderNoBuildMethodBean.class);

        assertThat(bean.isOk(), is(true));
    }

    @Test
    public void testBuilderBadTypeBuildMethod() {
        Config config = Config.empty();

        BadTypeBuilderBean bean = config.as(BadTypeBuilderBean.class);

        assertThat(bean.isOk(), is(true));
    }

    @Test
    public void testTransientBuilder() {
        Config config = Config.empty();

        TransientBuilderBean bean = config.as(TransientBuilderBean.class);

        assertThat(bean.isOk(), is(true));
    }

    @Test
    public void testTransientBuildMethod() {
        Config config = Config.empty();

        TransientBuildMethodBean bean = config.as(TransientBuildMethodBean.class);

        assertThat(bean.isOk(), is(true));
    }

    @Test
    public void testSettersBuilder() {
        Config config = Config.from(ConfigSources.from(
                ConfigNode.ObjectNode.builder()
                        .addValue("app.number", "1")
                        .addValue("app.uri", "this:is/my?uri")
                        .addValue("app.path", "/this/is/my.path")
                        .addList("app.numbers", ConfigNode.ListNode.builder()
                                .addValue("1")
                                .addValue("2")
                                .build())
                        .addList("app.uris", ConfigNode.ListNode.builder()
                                .addValue("this:is/my?uri")
                                .addValue("http://another/uri")
                                .build())
                        .addList("app.paths", ConfigNode.ListNode.builder()
                                .addValue("/this/is/my.path")
                                .addValue("/and/another.path")
                                .build())
                        .build()));

        SettersBuilderBean bean = config.get("app")
                .as(SettersBuilderBean.class);

        assertThat(bean.getNumber(), is(1));
        assertThat(bean.getUri(), is(URI.create("this:is/my?uri")));
        assertThat(bean.getCustom(), is(CustomType.from("/this/is/my.path")));
        assertThat(bean.getNumbers(), contains(1, 2));
        assertThat(bean.getUris(), contains(URI.create("this:is/my?uri"), URI.create("http://another/uri")));
        assertThat(bean.getCustoms(), contains(CustomType.from("/this/is/my.path"), CustomType.from("/and/another.path")));
    }

    @Test
    public void testFieldsBuilder() {
        Config config = Config.from(ConfigSources.from(
                ConfigNode.ObjectNode.builder()
                        .addValue("app.number", "1")
                        .addValue("app.uri", "this:is/my?uri")
                        .addValue("app.path", "/this/is/my.path")
                        .addList("app.numbers", ConfigNode.ListNode.builder()
                                .addValue("1")
                                .addValue("2")
                                .build())
                        .addList("app.uris", ConfigNode.ListNode.builder()
                                .addValue("this:is/my?uri")
                                .addValue("http://another/uri")
                                .build())
                        .addList("app.paths", ConfigNode.ListNode.builder()
                                .addValue("/this/is/my.path")
                                .addValue("/and/another.path")
                                .build())
                        .build()));

        FieldsBuilderBean bean = config.get("app")
                .as(FieldsBuilderBean.class);

        assertThat(bean.getNumber(), is(1));
        assertThat(bean.getUri(), is(URI.create("this:is/my?uri")));
        assertThat(bean.getCustom(), is(CustomType.from("/this/is/my.path")));
        assertThat(bean.getNumbers(), contains(1, 2));
        assertThat(bean.getUris(), contains(URI.create("this:is/my?uri"), URI.create("http://another/uri")));
        assertThat(bean.getCustoms(), contains(CustomType.from("/this/is/my.path"), CustomType.from("/and/another.path")));
    }

    @Test
    public void testInterfaceBuilder() {
        Config config = Config.from(ConfigSources.from(
                ConfigNode.ObjectNode.builder()
                        .addValue("app.number", "1")
                        .addValue("app.uri", "this:is/my?uri")
                        .addValue("app.path", "/this/is/my.path")
                        .addList("app.numbers", ConfigNode.ListNode.builder()
                                .addValue("1")
                                .addValue("2")
                                .build())
                        .addList("app.uris", ConfigNode.ListNode.builder()
                                .addValue("this:is/my?uri")
                                .addValue("http://another/uri")
                                .build())
                        .addList("app.paths", ConfigNode.ListNode.builder()
                                .addValue("/this/is/my.path")
                                .addValue("/and/another.path")
                                .build())
                        .build()));

        InterfaceBuilderBean bean = config.get("app")
                .as(InterfaceBuilderBean.class);

        assertThat(bean.getNumber(), is(1));
        assertThat(bean.getUri(), is(URI.create("this:is/my?uri")));
        assertThat(bean.getCustom(), is(CustomType.from("/this/is/my.path")));
        assertThat(bean.getNumbers(), contains(1, 2));
        assertThat(bean.getUris(), contains(URI.create("this:is/my?uri"), URI.create("http://another/uri")));
        assertThat(bean.getCustoms(), contains(CustomType.from("/this/is/my.path"), CustomType.from("/and/another.path")));
    }

    @Test
    public void testDefaultsSettersBuilder() {
        Config config = Config.empty();

        DefaultsSettersBuilderBean bean = config.as(DefaultsSettersBuilderBean.class);

        assertThat(bean.getNumber(), is(42));
        assertThat(bean.getUri(), is(URI.create("default:uri")));
        assertThat(bean.getCustom(), is(CustomType.from("/tmp/default")));
        assertThat(bean.getNumbers(), contains(23, 42));
        assertThat(bean.getUris(), contains(URI.create("default:uri"), URI.create("default:another:uri")));
        assertThat(bean.getCustoms(), contains(CustomType.from("/tmp/default"), CustomType.from("/tmp/another/default")));
    }

    @Test
    public void testDefaultsFieldsBuilder() {
        Config config = Config.empty();

        DefaultsFieldsBuilderBean bean = config.as(DefaultsFieldsBuilderBean.class);

        assertThat(bean.getNumber(), is(42));
        assertThat(bean.getUri(), is(URI.create("default:uri")));
        assertThat(bean.getCustom(), is(CustomType.from("/tmp/default")));
        assertThat(bean.getNumbers(), contains(23, 42));
        assertThat(bean.getUris(), contains(URI.create("default:uri"), URI.create("default:another:uri")));
        assertThat(bean.getCustoms(), contains(CustomType.from("/tmp/default"), CustomType.from("/tmp/another/default")));
    }

    @Test
    public void testDefaultsInterfaceBuilder() {
        Config config = Config.empty();

        DefaultsInterfaceBuilderBean bean = config.as(DefaultsInterfaceBuilderBean.class);

        assertThat(bean.getNumber(), is(42));
        assertThat(bean.getUri(), is(URI.create("default:uri")));
        assertThat(bean.getCustom(), is(CustomType.from("/tmp/default")));
        assertThat(bean.getNumbers(), contains(23, 42));
        assertThat(bean.getUris(), contains(URI.create("default:uri"), URI.create("default:another:uri")));
        assertThat(bean.getCustoms(), contains(CustomType.from("/tmp/default"), CustomType.from("/tmp/another/default")));
    }

    //
    // test beans
    //

    public static class SettersBuilderBean extends JavaBean {
        private SettersBuilderBean(int number, URI uri, CustomType custom,
                                   List<Integer> numbers, List<URI> uris, List<CustomType> customs) {
            super(number, uri, custom, numbers, uris, customs);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private int number;
            private URI uri;
            private CustomType custom;
            private List<Integer> numbers;
            private List<URI> uris;
            private List<CustomType> customs;

            private Builder() {
            }

            public void setNumber(int number) {
                this.number = number;
            }

            public void setUri(URI uri) {
                this.uri = uri;
            }

            @Config.Value(key = "path")
            public void setCustom(CustomType custom) {
                this.custom = custom;
            }

            public void setNumbers(List<Integer> numbers) {
                this.numbers = numbers;
            }

            public void setUris(List<URI> uris) {
                this.uris = uris;
            }

            @Config.Value(key = "paths")
            public void setCustoms(List<CustomType> customs) {
                this.customs = customs;
            }

            public SettersBuilderBean build() {
                return new SettersBuilderBean(number, uri, custom, numbers, uris, customs);
            }
        }
    }

    public static class InterfaceBuilderBean extends JavaBean {
        private InterfaceBuilderBean(int number, URI uri, CustomType custom,
                                     List<Integer> numbers, List<URI> uris, List<CustomType> customs) {
            super(number, uri, custom, numbers, uris, customs);
        }

        public static Builder builder() {
            return new BuilderImpl();
        }

        public interface Builder {
            void setNumber(int number);

            void setUri(URI uri);

            @Config.Value(key = "path")
            void setCustom(CustomType custom);

            void setNumbers(List<Integer> numbers);

            void setUris(List<URI> uris);

            @Config.Value(key = "paths")
            void setCustoms(List<CustomType> customs);

            InterfaceBuilderBean build();
        }

        private static class BuilderImpl implements Builder {
            private int number;
            private URI uri;
            private CustomType custom;
            private List<Integer> numbers;
            private List<URI> uris;
            private List<CustomType> customs;

            private BuilderImpl() {
            }

            public void setNumber(int number) {
                this.number = number;
            }

            public void setUri(URI uri) {
                this.uri = uri;
            }

            public void setCustom(CustomType custom) {
                this.custom = custom;
            }

            public void setNumbers(List<Integer> numbers) {
                this.numbers = numbers;
            }

            public void setUris(List<URI> uris) {
                this.uris = uris;
            }

            public void setCustoms(List<CustomType> customs) {
                this.customs = customs;
            }

            public InterfaceBuilderBean build() {
                return new InterfaceBuilderBean(number, uri, custom, numbers, uris, customs);
            }
        }
    }

    public static class FieldsBuilderBean extends JavaBean {
        private FieldsBuilderBean(int number, URI uri, CustomType custom,
                                  List<Integer> numbers, List<URI> uris, List<CustomType> customs) {
            super(number, uri, custom, numbers, uris, customs);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            public int number;
            public URI uri;
            @Config.Value(key = "path")
            public CustomType custom;
            public List<Integer> numbers;
            public List<URI> uris;
            @Config.Value(key = "paths")
            public List<CustomType> customs;

            private Builder() {
            }

            public FieldsBuilderBean build() {
                return new FieldsBuilderBean(number, uri, custom, numbers, uris, customs);
            }
        }
    }

    public static class DefaultsSettersBuilderBean extends JavaBean {
        private DefaultsSettersBuilderBean(int number, URI uri, CustomType custom,
                                           List<Integer> numbers, List<URI> uris, List<CustomType> customs) {
            super(number, uri, custom, numbers, uris, customs);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private int number;
            private URI uri;
            private CustomType custom;
            private List<Integer> numbers;
            private List<URI> uris;
            private List<CustomType> customs;

            private Builder() {
            }

            @Config.Value(withDefault = "42")
            public void setNumber(int number) {
                this.number = number;
            }

            @Config.Value(withDefault = "default:uri")
            public void setUri(URI uri) {
                this.uri = uri;
            }

            @Config.Value(key = "path", withDefaultSupplier = DefaultCustomTypeSupplier.class)
            public void setCustom(CustomType custom) {
                this.custom = custom;
            }

            @Config.Value(withDefaultSupplier = DefaultNumbersSupplier.class)
            public void setNumbers(List<Integer> numbers) {
                this.numbers = numbers;
            }

            @Config.Value(withDefaultSupplier = DefaultUrisSupplier.class)
            public void setUris(List<URI> uris) {
                this.uris = uris;
            }

            @Config.Value(withDefaultSupplier = DefaultCustomTypesSupplier.class)
            public void setCustoms(List<CustomType> customs) {
                this.customs = customs;
            }

            public DefaultsSettersBuilderBean build() {
                return new DefaultsSettersBuilderBean(number, uri, custom, numbers, uris, customs);
            }
        }
    }

    public static class DefaultsInterfaceBuilderBean extends JavaBean {
        private DefaultsInterfaceBuilderBean(int number, URI uri, CustomType custom,
                                             List<Integer> numbers, List<URI> uris, List<CustomType> customs) {
            super(number, uri, custom, numbers, uris, customs);
        }

        public static Builder builder() {
            return new BuilderImpl();
        }

        public interface Builder {
            @Config.Value(withDefault = "42")
            void setNumber(int number);

            @Config.Value(withDefault = "default:uri")
            void setUri(URI uri);

            @Config.Value(key = "path", withDefaultSupplier = DefaultCustomTypeSupplier.class)
            void setCustom(CustomType custom);

            @Config.Value(withDefaultSupplier = DefaultNumbersSupplier.class)
            void setNumbers(List<Integer> numbers);

            @Config.Value(withDefaultSupplier = DefaultUrisSupplier.class)
            void setUris(List<URI> uris);

            @Config.Value(withDefaultSupplier = DefaultCustomTypesSupplier.class)
            void setCustoms(List<CustomType> customs);

            DefaultsInterfaceBuilderBean build();
        }

        private static class BuilderImpl implements Builder {
            private int number;
            private URI uri;
            private CustomType custom;
            private List<Integer> numbers;
            private List<URI> uris;
            private List<CustomType> customs;

            private BuilderImpl() {
            }

            public void setNumber(int number) {
                this.number = number;
            }

            public void setUri(URI uri) {
                this.uri = uri;
            }

            public void setCustom(CustomType custom) {
                this.custom = custom;
            }

            public void setNumbers(List<Integer> numbers) {
                this.numbers = numbers;
            }

            public void setUris(List<URI> uris) {
                this.uris = uris;
            }

            public void setCustoms(List<CustomType> customs) {
                this.customs = customs;
            }

            public DefaultsInterfaceBuilderBean build() {
                return new DefaultsInterfaceBuilderBean(number, uri, custom, numbers, uris, customs);
            }
        }
    }

    public static class DefaultsFieldsBuilderBean extends JavaBean {
        private DefaultsFieldsBuilderBean(int number, URI uri, CustomType custom,
                                          List<Integer> numbers, List<URI> uris, List<CustomType> customs) {
            super(number, uri, custom, numbers, uris, customs);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            @Config.Value(withDefault = "42")
            public int number;
            @Config.Value(withDefault = "default:uri")
            public URI uri;
            @Config.Value(withDefaultSupplier = DefaultCustomTypeSupplier.class)
            public CustomType custom;
            @Config.Value(withDefaultSupplier = DefaultNumbersSupplier.class)
            public List<Integer> numbers;
            @Config.Value(withDefaultSupplier = DefaultUrisSupplier.class)
            public List<URI> uris;
            @Config.Value(withDefaultSupplier = DefaultCustomTypesSupplier.class)
            public List<CustomType> customs;

            private Builder() {
            }

            public DefaultsFieldsBuilderBean build() {
                return new DefaultsFieldsBuilderBean(number, uri, custom, numbers, uris, customs);
            }
        }
    }

    public abstract static class TestingBean {
        private final boolean ok;

        protected TestingBean(boolean ok) {
            this.ok = ok;
        }

        public boolean isOk() {
            return ok;
        }
    }

    public static class BuilderNoBuildMethodBean extends TestingBean {
        private BuilderNoBuildMethodBean(boolean ok) {
            super(ok);
        }

        public BuilderNoBuildMethodBean() {
            this(true);
        }

        public static NotBuilder builder() {
            return new NotBuilder();
        }

        public static class NotBuilder {
            private NotBuilder() {
            }

            private BuilderNoBuildMethodBean build() { //ignored
                return new BuilderNoBuildMethodBean(false);
            }
        }
    }

    public static class BadTypeBuilderBean extends TestingBean {
        public BadTypeBuilderBean() {
            super(true);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Builder() {
            }

            public String build() {
                return "bad-return-type";
            }
        }
    }

    public static class TransientBuilderBean extends TestingBean {
        private TransientBuilderBean(boolean ok) {
            super(ok);
        }

        public TransientBuilderBean() {
            this(true);
        }

        @Config.Transient
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Builder() {
            }

            public TransientBuilderBean build() {
                return new TransientBuilderBean(false);
            }
        }
    }

    public static class TransientBuildMethodBean extends TestingBean {
        private TransientBuildMethodBean(boolean ok) {
            super(ok);
        }

        public TransientBuildMethodBean() {
            this(true);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Builder() {
            }

            @Config.Transient
            public TransientBuildMethodBean build() {
                return new TransientBuildMethodBean(false);
            }
        }
    }

}
