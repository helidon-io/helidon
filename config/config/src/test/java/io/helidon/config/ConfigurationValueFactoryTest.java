/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.Default;
import io.helidon.common.GenericType;
import io.helidon.common.mapper.DefaultsResolver;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigurationValueFactoryTest {
    private static final TypeName TEST_DESCRIPTOR = TypeName.create(ConfigurationValueFactoryTest.class);
    private static final TypeName TEST_SERVICE = TypeName.create(TestService.class);
    private static final TypeName DEFAULT_VALUE = TypeName.create(Default.Value.class);

    @Test
    void literalKeyUsesDirectLookup() {
        ConfigurationValueFactory factory = factory(Map.of("app.greeting", "Ahoj"));

        List<Service.QualifiedInstance<Object>> values = factory.list(Qualifier.create(Configuration.Value.class,
                                                                                      "app.greeting"),
                                                                      Lookup.create(String.class),
                                                                      asObjectType(GenericType.STRING));

        assertThat(values, hasSize(1));
        assertThat(values.getFirst().get(), is("Ahoj"));
    }

    @Test
    void literalKeyUsesGenericTypeMapper() {
        GenericType<Map<String, Integer>> mapType = new GenericType<Map<String, Integer>>() { };
        Config config = configWithMapMapper(mapType, ConfigSources.create(Map.of("app.limit", "23")));
        ConfigurationValueFactory factory = new ConfigurationValueFactory(() -> defaultsResolver(config), () -> config);

        assertThat(config.get("app").as(mapType).get().get("limit"), is(23));

        List<Service.QualifiedInstance<Object>> values = factory.list(Qualifier.create(Configuration.Value.class,
                                                                                      "app"),
                                                                      Lookup.create(Map.class),
                                                                      asObjectType(mapType));

        assertThat(values, hasSize(1));
        Map<?, ?> value = (Map<?, ?>) values.getFirst().get();
        assertThat(value.get("limit"), is(23));
    }

    @Test
    void literalMapStringStringUsesClassMapperFallback() {
        GenericType<Map<String, String>> mapType = new GenericType<Map<String, String>>() { };
        Config config = configWithRawMapMapper(ConfigSources.create(Map.of("app.limit", "23")));
        ConfigurationValueFactory factory = new ConfigurationValueFactory(() -> defaultsResolver(config), () -> config);

        assertThat(config.get("app").as(mapType).get(), is(Map.of("raw-limit", "23")));

        List<Service.QualifiedInstance<Object>> values = factory.list(Qualifier.create(Configuration.Value.class,
                                                                                      "app"),
                                                                      Lookup.create(Map.class),
                                                                      asObjectType(mapType));

        assertThat(values, hasSize(1));
        assertThat(values.getFirst().get(), is(Map.of("raw-limit", "23")));
    }

    @Test
    void literalMapStringIntegerDoesNotUseClassMapperFallback() {
        GenericType<Map<String, Integer>> mapType = new GenericType<Map<String, Integer>>() { };
        Config config = configWithRawMapMapper(ConfigSources.create(Map.of("app.limit", "23")));
        ConfigurationValueFactory factory = new ConfigurationValueFactory(() -> defaultsResolver(config), () -> config);

        assertThrows(ConfigMappingException.class,
                     () -> factory.list(Qualifier.create(Configuration.Value.class,
                                                         "app"),
                                        Lookup.create(Map.class),
                                        asObjectType(mapType)));
    }

    @Test
    void listKeyUsesGenericTypeMapperForElements() {
        GenericType<Map<String, Integer>> mapType = new GenericType<Map<String, Integer>>() { };
        ConfigNode.ObjectNode first = ConfigNode.ObjectNode.builder()
                .addValue("limit", "23")
                .build();
        ConfigNode.ObjectNode second = ConfigNode.ObjectNode.builder()
                .addValue("limit", "42")
                .build();
        ConfigNode.ListNode list = ConfigNode.ListNode.builder()
                .addObject(first)
                .addObject(second)
                .build();
        ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder()
                .addList("app", list)
                .build();
        Config config = configWithMapMapper(mapType, ConfigSources.create(root));
        ConfigurationValueFactory factory = new ConfigurationValueFactory(() -> defaultsResolver(config), () -> config);

        assertThat(config.get("app").isList(), is(true));
        assertThat(config.get("app").asNodeList().get().getFirst().as(mapType).get().get("limit"), is(23));

        List<Service.QualifiedInstance<Object>> values = factory.list(Qualifier.create(Configuration.Value.class,
                                                                                      "app"),
                                                                      Lookup.create(Map.class),
                                                                      asObjectType(mapType));

        assertThat(values, hasSize(2));
        assertThat(values.stream()
                           .map(Service.QualifiedInstance::get)
                           .toList(),
                   is(List.of(Map.of("limit", 23),
                              Map.of("limit", 42))));
    }

    @Test
    void listMapStringStringUsesClassMapperFallbackForElements() {
        GenericType<Map<String, String>> mapType = new GenericType<Map<String, String>>() { };
        ConfigNode.ObjectNode first = ConfigNode.ObjectNode.builder()
                .addValue("limit", "23")
                .build();
        ConfigNode.ObjectNode second = ConfigNode.ObjectNode.builder()
                .addValue("limit", "42")
                .build();
        ConfigNode.ListNode list = ConfigNode.ListNode.builder()
                .addObject(first)
                .addObject(second)
                .build();
        ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder()
                .addList("app", list)
                .build();
        Config config = configWithRawMapMapper(ConfigSources.create(root));
        ConfigurationValueFactory factory = new ConfigurationValueFactory(() -> defaultsResolver(config), () -> config);

        assertThat(config.get("app").asNodeList().get().getFirst().as(mapType).get(), is(Map.of("raw-limit", "23")));

        List<Service.QualifiedInstance<Object>> values = factory.list(Qualifier.create(Configuration.Value.class,
                                                                                      "app"),
                                                                      Lookup.create(Map.class),
                                                                      asObjectType(mapType));

        assertThat(values, hasSize(2));
        assertThat(values.stream()
                           .map(Service.QualifiedInstance::get)
                           .toList(),
                   is(List.of(Map.of("raw-limit", "23"),
                              Map.of("raw-limit", "42"))));
    }

    @Test
    void listMapStringIntegerDoesNotUseClassMapperFallbackForElements() {
        GenericType<Map<String, Integer>> mapType = new GenericType<Map<String, Integer>>() { };
        ConfigNode.ObjectNode first = ConfigNode.ObjectNode.builder()
                .addValue("limit", "23")
                .build();
        ConfigNode.ObjectNode second = ConfigNode.ObjectNode.builder()
                .addValue("limit", "42")
                .build();
        ConfigNode.ListNode list = ConfigNode.ListNode.builder()
                .addObject(first)
                .addObject(second)
                .build();
        ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder()
                .addList("app", list)
                .build();
        Config config = configWithRawMapMapper(ConfigSources.create(root));
        ConfigurationValueFactory factory = new ConfigurationValueFactory(() -> defaultsResolver(config), () -> config);

        assertThrows(ConfigMappingException.class,
                     () -> factory.list(Qualifier.create(Configuration.Value.class,
                                                         "app"),
                                        Lookup.create(Map.class),
                                        asObjectType(mapType)));
    }

    @Test
    void wholeExpressionUsesConfiguredValueWhenPresent() {
        ConfigurationValueFactory factory = factory(Map.of("app.limit", "23"));

        List<Service.QualifiedInstance<Object>> values = factory.list(Qualifier.create(Configuration.Value.class,
                                                                                      "${app.limit:13}"),
                                                                      Lookup.create(Integer.class),
                                                                      asObjectType(GenericType.create(Integer.class)));

        assertThat(values, hasSize(1));
        assertThat(values.getFirst().get(), is(23));
    }

    @Test
    void wholeExpressionUsesInlineDefaultWhenConfigIsMissing() {
        ConfigurationValueFactory factory = factory(Map.of());

        List<Service.QualifiedInstance<Object>> values = factory.list(Qualifier.create(Configuration.Value.class,
                                                                                      "${app.limit:13}"),
                                                                      Lookup.create(Integer.class),
                                                                      asObjectType(GenericType.create(Integer.class)));

        assertThat(values, hasSize(1));
        assertThat(values.getFirst().get(), is(13));
    }

    @Test
    void wholeExpressionUsesInlineDefaultWhenConfigIsMissingForPrimitiveTarget() {
        ConfigurationValueFactory factory = factory(Map.of());

        List<Service.QualifiedInstance<Object>> values = factory.list(Qualifier.create(Configuration.Value.class,
                                                                                      "${declarative.ignore-incubating:false}"),
                                                                      Lookup.create(boolean.class),
                                                                      asObjectType(GenericType.create(boolean.class)));

        assertThat(values, hasSize(1));
        assertThat(values.getFirst().get(), is(false));
    }

    @Test
    void wholeExpressionFallsBackToDefaultAnnotation() {
        ConfigurationValueFactory factory = factory(Map.of());

        List<Service.QualifiedInstance<Object>> values = factory.list(Qualifier.create(Configuration.Value.class,
                                                                                      "${app.greeting}"),
                                                                      lookupWithDefault(GenericType.STRING,
                                                                                        Annotation.create(Default.Value.class,
                                                                                                          "Ciao")),
                                                                      asObjectType(GenericType.STRING));

        assertThat(values, hasSize(1));
        assertThat(values.getFirst().get(), is("Ciao"));
    }

    @Test
    void compositeExpressionResolvesConfiguredValues() {
        ConfigurationValueFactory factory = factory(Map.of("app.name", "Joe"));

        List<Service.QualifiedInstance<Object>> values = factory.list(Qualifier.create(Configuration.Value.class,
                                                                                      "Hello ${app.name:World}"),
                                                                      Lookup.create(String.class),
                                                                      asObjectType(GenericType.STRING));

        assertThat(values, hasSize(1));
        assertThat(values.getFirst().get(), is("Hello Joe"));
    }

    @Test
    void compositeExpressionStartingAndEndingWithExpressionUsesCompositeResolution() {
        ConfigurationValueFactory factory = factory(Map.of("host", "example.com"));

        List<Service.QualifiedInstance<Object>> values = factory.list(Qualifier.create(Configuration.Value.class,
                                                                                      "${host:localhost}:${port:80}"),
                                                                      Lookup.create(String.class),
                                                                      asObjectType(GenericType.STRING));

        assertThat(values, hasSize(1));
        assertThat(values.getFirst().get(), is("example.com:80"));
    }

    @Test
    void escapedExpressionIsTreatedAsLiteralCompositeString() {
        ConfigurationValueFactory factory = factory(Map.of());

        List<Service.QualifiedInstance<Object>> values = factory.list(Qualifier.create(Configuration.Value.class,
                                                                                      "Hello \\${app.name}"),
                                                                      Lookup.create(String.class),
                                                                      asObjectType(GenericType.STRING));

        assertThat(values, hasSize(1));
        assertThat(values.getFirst().get(), is("Hello ${app.name}"));
    }

    private static ConfigurationValueFactory factory(Map<String, String> values) {
        Config config = Config.builder()
                .sources(ConfigSources.create(values))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .build();

        return new ConfigurationValueFactory(() -> defaultsResolver(config), () -> config);
    }

    private static Config configWithMapMapper(GenericType<Map<String, Integer>> mapType,
                                              Supplier<? extends ConfigSource> source) {
        return Config.builder()
                .sources(source)
                .addMapper(mapType, node -> node.asMap()
                        .orElseThrow()
                        .entrySet()
                        .stream()
                        .collect(Collectors.toUnmodifiableMap(entry -> leaf(entry.getKey()),
                                                              entry -> Integer.parseInt(entry.getValue()))))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .build();
    }

    private static Config configWithRawMapMapper(Supplier<? extends ConfigSource> source) {
        return Config.builder()
                .sources(source)
                .addMapper(Map.class, node -> ConfigMappers.toMap(node.detach())
                        .entrySet()
                        .stream()
                        .collect(Collectors.toUnmodifiableMap(entry -> "raw-" + leaf(entry.getKey()),
                                                              Map.Entry::getValue)))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .build();
    }

    private static String leaf(String key) {
        return key.substring(key.lastIndexOf('.') + 1);
    }

    private static DefaultsResolver defaultsResolver(Config config) {
        return (annotations, expectedType, name) -> annotations.stream()
                .filter(it -> DEFAULT_VALUE.equals(it.typeName()))
                .findFirst()
                .flatMap(Annotation::stringValues)
                .stream()
                .flatMap(List::stream)
                .map(it -> config.mapper().map(it, expectedType.rawType(), name))
                .toList();
    }

    private static Lookup lookupWithDefault(GenericType<?> type, Annotation... annotations) {
        Dependency dependency = Dependency.builder()
                .contract(TypeName.create(type.rawType()))
                .contractType(type)
                .descriptor(TEST_DESCRIPTOR)
                .descriptorConstant("TEST_DEP")
                .name("value")
                .service(TEST_SERVICE)
                .typeName(TypeName.create(type.rawType()))
                .annotations(Set.of(annotations))
                .build();

        return Lookup.create(dependency);
    }

    @SuppressWarnings("unchecked")
    private static GenericType<Object> asObjectType(GenericType<?> type) {
        return (GenericType<Object>) type;
    }

    private static final class TestService {
        private TestService() {
        }
    }
}
