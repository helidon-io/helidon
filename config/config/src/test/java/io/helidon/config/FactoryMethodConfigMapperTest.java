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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.Config.Transient;
import io.helidon.config.Config.Value;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.stringContainsInOrder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ConfigMappers} with focus on factory method and constructor initialization,
 * see {@link FactoryMethodConfigMapper}.
 */
public class FactoryMethodConfigMapperTest {

    //
    // constructor
    //

    @Test
    public void testAmbiguousConstructors() {
        Config config = Config.empty();
        ConfigMappingException ex = Assertions.assertThrows(ConfigMappingException.class, () -> {
            config.as(AmbiguousConstructorsBean.class);
        });
        Assertions.assertTrue(stringContainsInOrder(CollectionsHelper.listOf("no compatible config value mapper found")).matches(ex.getMessage()));
    }

    @Test
    public void testTransientConstructor() {
        Config config = Config.from(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("app.number", "1")
                        .addValue("app.uri", "this:is/my?uri")
                        .addValue("app.path", "/this/is/my.path")
                        .addValue("app.unused", "true")
                        .addList("app.numbers", ListNode.builder()
                                .addValue("1")
                                .addValue("2")
                                .build())
                        .addList("app.uris", ListNode.builder()
                                .addValue("this:is/my?uri")
                                .addValue("http://another/uri")
                                .build())
                        .addList("app.paths", ListNode.builder()
                                .addValue("/this/is/my.path")
                                .addValue("/and/another.path")
                                .build())
                        .build()));

        ConstructorBean bean = config.get("app")
                .as(ConstructorBean.class);

        assertThat(bean.getNumber(), is(1));
        assertThat(bean.getUri(), is(URI.create("this:is/my?uri")));
        assertThat(bean.getCustom(), is(CustomType.from("/this/is/my.path")));
        assertThat(bean.getNumbers(), contains(1, 2));
        assertThat(bean.getUris(), contains(URI.create("this:is/my?uri"), URI.create("http://another/uri")));
        assertThat(bean.getCustoms(), contains(CustomType.from("/this/is/my.path"), CustomType.from("/and/another.path")));
    }

    @Test
    public void testNoConfigValueConstructor() {
        Config config = Config.from(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("app.arg0", "1")
                        .addValue("app.arg1", "this:is/my?uri")
                        .addValue("app.arg2", "/this/is/my.path")
                        .addList("app.arg3", ListNode.builder()
                                .addValue("1")
                                .addValue("2")
                                .build())
                        .addList("app.arg4", ListNode.builder()
                                .addValue("this:is/my?uri")
                                .addValue("http://another/uri")
                                .build())
                        .addList("app.arg5", ListNode.builder()
                                .addValue("/this/is/my.path")
                                .addValue("/and/another.path")
                                .build())
                        .build()));

        NoConfigValueConstructorBean bean = config.get("app")
                .as(NoConfigValueConstructorBean.class);

        assertThat(bean.getNumber(), is(1));
        assertThat(bean.getUri(), is(URI.create("this:is/my?uri")));
        assertThat(bean.getCustom(), is(CustomType.from("/this/is/my.path")));
        assertThat(bean.getNumbers(), contains(1, 2));
        assertThat(bean.getUris(), contains(URI.create("this:is/my?uri"), URI.create("http://another/uri")));
        assertThat(bean.getCustoms(), contains(CustomType.from("/this/is/my.path"), CustomType.from("/and/another.path")));
    }

    @Test
    public void testMissingParamsConstructor() {
        Config config = Config.from(ConfigSources.from(CollectionsHelper.mapOf(
                "app.number", "1"
        )));

        ConfigMappingException ex = Assertions.assertThrows(ConfigMappingException.class, () -> {
            config.get("app")
                .as(ConstructorBean.class);
        });
        Assertions.assertTrue(stringContainsInOrder(CollectionsHelper.listOf(
                "'app'", "ConstructorBean", "Missing value for parameter 'uri'.")).matches(ex.getMessage()));
    }

    @Test
    public void testDefaultsConstructor() {
        Config config = Config.from(ConfigSources.from(CollectionsHelper.mapOf(
                "app.number", "1"
        )));

        DefaultsConstructorBean bean = config.get("app")
                .as(DefaultsConstructorBean.class);

        assertThat(bean.getNumber(), is(1));
        assertThat(bean.getUri(), is(URI.create("default:uri")));
        assertThat(bean.getCustom(), is(CustomType.from("/tmp/default")));
        assertThat(bean.getNumbers(), contains(23, 42));
        assertThat(bean.getUris(), contains(URI.create("default:uri"), URI.create("default:another:uri")));
        assertThat(bean.getCustoms(), contains(CustomType.from("/tmp/default"), CustomType.from("/tmp/another/default")));
    }

    //
    // static from method
    //

    @Test
    public void testAmbiguousFromMethods() {
        Config config = Config.empty();

        ConfigMappingException ex = Assertions.assertThrows(ConfigMappingException.class, () -> {
            config.as(AmbiguousFromMethodsBean.class);
        });
        Assertions.assertTrue(stringContainsInOrder(CollectionsHelper.listOf("no compatible config value mapper found")).matches(ex.getMessage()));
    }

    @Test
    public void testTransientFromMethod() {
        Config config = Config.from(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("app.number", "1")
                        .addValue("app.uri", "this:is/my?uri")
                        .addValue("app.path", "/this/is/my.path")
                        .addValue("app.unused", "true")
                        .addList("app.numbers", ListNode.builder()
                                .addValue("1")
                                .addValue("2")
                                .build())
                        .addList("app.uris", ListNode.builder()
                                .addValue("this:is/my?uri")
                                .addValue("http://another/uri")
                                .build())
                        .addList("app.paths", ListNode.builder()
                                .addValue("/this/is/my.path")
                                .addValue("/and/another.path")
                                .build())
                        .build()));

        FromMethodBean bean = config.get("app")
                .as(FromMethodBean.class);

        assertThat(bean.getNumber(), is(1));
        assertThat(bean.getUri(), is(URI.create("this:is/my?uri")));
        assertThat(bean.getCustom(), is(CustomType.from("/this/is/my.path")));
        assertThat(bean.getNumbers(), contains(1, 2));
        assertThat(bean.getUris(), contains(URI.create("this:is/my?uri"), URI.create("http://another/uri")));
        assertThat(bean.getCustoms(), contains(CustomType.from("/this/is/my.path"), CustomType.from("/and/another.path")));
    }

    @Test
    public void testNoConfigValueFromMethod() {
        Config config = Config.from(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("app.arg0", "1")
                        .addValue("app.arg1", "this:is/my?uri")
                        .addValue("app.arg2", "/this/is/my.path")
                        .addList("app.arg3", ListNode.builder()
                                .addValue("1")
                                .addValue("2")
                                .build())
                        .addList("app.arg4", ListNode.builder()
                                .addValue("this:is/my?uri")
                                .addValue("http://another/uri")
                                .build())
                        .addList("app.arg5", ListNode.builder()
                                .addValue("/this/is/my.path")
                                .addValue("/and/another.path")
                                .build())
                        .build()));

        NoConfigValueFromMethodBean bean = config.get("app")
                .as(NoConfigValueFromMethodBean.class);

        assertThat(bean.getNumber(), is(1));
        assertThat(bean.getUri(), is(URI.create("this:is/my?uri")));
        assertThat(bean.getCustom(), is(CustomType.from("/this/is/my.path")));
        assertThat(bean.getNumbers(), contains(1, 2));
        assertThat(bean.getUris(), contains(URI.create("this:is/my?uri"), URI.create("http://another/uri")));
        assertThat(bean.getCustoms(), contains(CustomType.from("/this/is/my.path"), CustomType.from("/and/another.path")));
    }

    @Test
    public void testMissingParamsFromMethod() {
        Config config = Config.from(ConfigSources.from(CollectionsHelper.mapOf(
                "app.number", "1"
        )));

        ConfigMappingException ex = Assertions.assertThrows(ConfigMappingException.class, () -> {
            config.get("app")
                .as(FromMethodBean.class);
        });
        Assertions.assertTrue(stringContainsInOrder(CollectionsHelper.listOf(
                "'app'", "FromMethodBean", "Missing value for parameter 'uri'.")).matches(ex.getMessage()));
    }

    @Test
    public void testDefaultsFromMethod() {
        Config config = Config.from(ConfigSources.from(CollectionsHelper.mapOf(
                "app.number", "1"
        )));

        DefaultsFromMethodBean bean = config.get("app")
                .as(DefaultsFromMethodBean.class);

        assertThat(bean.getNumber(), is(1));
        assertThat(bean.getUri(), is(URI.create("default:uri")));
        assertThat(bean.getCustom(), is(CustomType.from("/tmp/default")));
        assertThat(bean.getNumbers(), contains(23, 42));
        assertThat(bean.getUris(), contains(URI.create("default:uri"), URI.create("default:another:uri")));
        assertThat(bean.getCustoms(), contains(CustomType.from("/tmp/default"), CustomType.from("/tmp/another/default")));
    }

    //
    // test beans
    //

    public abstract static class JavaBean {
        private final int number;
        private final URI uri;
        private final CustomType custom;
        private final List<Integer> numbers;
        private final List<URI> uris;
        private final List<CustomType> customs;

        protected JavaBean(int number, URI uri, CustomType custom,
                           List<Integer> numbers, List<URI> uris, List<CustomType> customs) {
            this.number = number;
            this.uri = uri;
            this.custom = custom;
            this.numbers = numbers;
            this.uris = uris;
            this.customs = customs;
        }

        public int getNumber() {
            return number;
        }

        public URI getUri() {
            return uri;
        }

        public CustomType getCustom() {
            return custom;
        }

        public List<Integer> getNumbers() {
            return numbers;
        }

        public List<URI> getUris() {
            return uris;
        }

        public List<CustomType> getCustoms() {
            return customs;
        }
    }

    public static class CustomType {
        private final Path path;

        private CustomType(Path path) {
            this.path = path;
        }

        public Path getPath() {
            return path;
        }

        public static CustomType from(String value) {
            return new CustomType(Paths.get(value));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CustomType that = (CustomType) o;
            return Objects.equals(path, that.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path);
        }
    }

    public static class AmbiguousFromMethodsBean extends JavaBean {
        private AmbiguousFromMethodsBean(int number, URI uri, CustomType custom,
                                         List<Integer> numbers, List<URI> uris, List<CustomType> customs) {
            super(number, uri, custom, numbers, uris, customs);
        }

        public static AmbiguousFromMethodsBean from(int number, URI uri, CustomType custom) {
            return new AmbiguousFromMethodsBean(number, uri, custom, null, null, null);
        }

        public static AmbiguousFromMethodsBean from(int number, URI uri, CustomType custom,
                                                    List<Integer> numbers, List<URI> uris, List<CustomType> customs) {
            return new AmbiguousFromMethodsBean(number, uri, custom, numbers, uris, customs);
        }
    }

    public static class AmbiguousConstructorsBean extends JavaBean {
        public AmbiguousConstructorsBean(int number, URI uri, CustomType custom) {
            super(number, uri, custom, null, null, null);
        }

        public AmbiguousConstructorsBean(int number, URI uri, CustomType custom,
                                         List<Integer> numbers, List<URI> uris, List<CustomType> customs) {
            super(number, uri, custom, numbers, uris, customs);
        }
    }

    public static class FromMethodBean extends JavaBean {
        private FromMethodBean(int number, URI uri, CustomType custom,
                               List<Integer> numbers, List<URI> uris, List<CustomType> customs) {
            super(number, uri, custom, numbers, uris, customs);
        }

        @Transient
        public static FromMethodBean from(int number, URI uri, CustomType custom) {
            return new FromMethodBean(number, uri, custom, null, null, null);
        }

        public static FromMethodBean from(@Value(key = "number") int number,
                                          @Value(key = "uri") URI uri,
                                          @Value(key = "path") CustomType custom,
                                          @Value(key = "numbers") List<Integer> numbers,
                                          @Value(key = "uris") List<URI> uris,
                                          @Value(key = "paths") List<CustomType> customs) {
            return new FromMethodBean(number, uri, custom, numbers, uris, customs);
        }
    }

    public static class NoConfigValueFromMethodBean extends JavaBean {
        private NoConfigValueFromMethodBean(int number, URI uri, CustomType custom,
                                            List<Integer> numbers, List<URI> uris, List<CustomType> customs) {
            super(number, uri, custom, numbers, uris, customs);
        }

        public static NoConfigValueFromMethodBean from(int number,
                                                       URI uri,
                                                       CustomType custom,
                                                       List<Integer> numbers,
                                                       List<URI> uris,
                                                       List<CustomType> customs) {
            return new NoConfigValueFromMethodBean(number, uri, custom, numbers, uris, customs);
        }
    }

    public static class DefaultsFromMethodBean extends JavaBean {
        public DefaultsFromMethodBean() {
            super(-1, null, null, null, null, null);
            throw new IllegalStateException("The constructor should be ignored.");
        }

        @Transient
        public DefaultsFromMethodBean(Config config) {
            super(-1, null, null, null, null, null);
            throw new IllegalStateException("The constructor should be ignored.");
        }

        private DefaultsFromMethodBean(int number, URI uri, CustomType custom,
                                       List<Integer> numbers, List<URI> uris, List<CustomType> customs) {
            super(number, uri, custom, numbers, uris, customs);
        }

        public static DefaultsFromMethodBean from(
                @Value(key = "number", withDefault = "42") int number,
                @Value(key = "uri", withDefault = "default:uri") URI uri,
                @Value(key = "path", withDefaultSupplier = DefaultCustomTypeSupplier.class) CustomType custom,
                @Value(key = "numbers", withDefaultSupplier = DefaultNumbersSupplier.class) List<Integer> numbers,
                @Value(key = "uris", withDefaultSupplier = DefaultUrisSupplier.class) List<URI> uris,
                @Value(key = "paths", withDefaultSupplier = DefaultCustomTypesSupplier.class) List<CustomType> customs) {
            return new DefaultsFromMethodBean(number, uri, custom, numbers, uris, customs);
        }

        @Transient
        public DefaultsFromMethodBean from(Config config) {
            throw new IllegalStateException("The method should be ignored.");
        }
    }

    public static class ConstructorBean extends JavaBean {
        @Transient
        public ConstructorBean(int number, URI uri, CustomType custom) {
            super(number, uri, custom, null, null, null);
        }

        public ConstructorBean(@Value(key = "number") int number,
                               @Value(key = "uri") URI uri,
                               @Value(key = "path") CustomType custom,
                               @Value(key = "numbers") List<Integer> numbers,
                               @Value(key = "uris") List<URI> uris,
                               @Value(key = "paths") List<CustomType> customs) {
            super(number, uri, custom, numbers, uris, customs);
        }

    }

    public static class NoConfigValueConstructorBean extends JavaBean {
        public NoConfigValueConstructorBean(int number,
                                            URI uri,
                                            CustomType custom,
                                            List<Integer> numbers,
                                            List<URI> uris,
                                            List<CustomType> customs) {
            super(number, uri, custom, numbers, uris, customs);
        }

    }

    public static class DefaultsConstructorBean extends JavaBean {
        @Transient
        public DefaultsConstructorBean(Config config) {
            super(-1, null, null, null, null, null);
            throw new IllegalStateException("The constructor should be ignored.");
        }

        public DefaultsConstructorBean(
                @Value(key = "number", withDefault = "42") int number,
                @Value(key = "uri", withDefault = "default:uri") URI uri,
                @Value(key = "path", withDefaultSupplier = DefaultCustomTypeSupplier.class) CustomType custom,
                @Value(key = "numbers", withDefaultSupplier = DefaultNumbersSupplier.class) List<Integer> numbers,
                @Value(key = "uris", withDefaultSupplier = DefaultUrisSupplier.class) List<URI> uris,
                @Value(key = "paths", withDefaultSupplier = DefaultCustomTypesSupplier.class) List<CustomType> customs) {
            super(number, uri, custom, numbers, uris, customs);
        }

        public DefaultsConstructorBean() {
            super(-1, null, null, null, null, null);
            throw new IllegalStateException("The constructor should be ignored.");
        }

        @Transient
        public DefaultsConstructorBean from(Config config) {
            throw new IllegalStateException("The method should be ignored.");
        }
    }

    public static class DefaultCustomTypeSupplier implements Supplier<CustomType> {
        @Override
        public CustomType get() {
            return CustomType.from("/tmp/default");
        }
    }

    public static class DefaultCustomTypesSupplier implements Supplier<List<CustomType>> {
        @Override
        public List<CustomType> get() {
            return CollectionsHelper.listOf(CustomType.from("/tmp/default"), CustomType.from("/tmp/another/default"));
        }
    }

    public static class DefaultUrisSupplier implements Supplier<List<URI>> {
        @Override
        public List<URI> get() {
            return CollectionsHelper.listOf(URI.create("default:uri"), URI.create("default:another:uri"));
        }
    }

    public static class DefaultNumbersSupplier implements Supplier<List<Integer>> {
        @Override
        public List<Integer> get() {
            return CollectionsHelper.listOf(23, 42);
        }
    }

}
