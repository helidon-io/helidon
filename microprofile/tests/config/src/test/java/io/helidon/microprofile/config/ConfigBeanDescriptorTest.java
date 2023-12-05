/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.microprofile.config;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Map;

import io.helidon.config.mp.MpConfigSources;
import io.helidon.microprofile.config.testsubjects.TestConfiguredBean;

import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.InjectionPoint;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lower level testing for {@link io.helidon.microprofile.config.ConfigBeanDescriptor}.
 */
class ConfigBeanDescriptorTest {

    private static MutableMpTest.MutableSource source;
    private static Config config;
    private static io.helidon.config.Config emptyConfig;

    @BeforeAll
    static void initClass() {
        config = createTestConfiguredBeanConfig();

        // we need to ensure empty config is initialized before running other tests,
        // as this messes up the mapping service counter
        emptyConfig = io.helidon.config.Config.empty();

        source = new MutableMpTest.MutableSource("initial");

        // register config
        ConfigProviderResolver configProvider = ConfigProviderResolver.instance();
        configProvider.registerConfig(config, Thread.currentThread().getContextClassLoader());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProduceListFields() throws Exception {
        // setup for the test
        AnnotatedType<?> annotatedType = mock(AnnotatedType.class);
        when(annotatedType.getJavaClass()).thenReturn((Class) TestConfiguredBean.class);

        Field strListField = TestConfiguredBean.class.getField("strList");
        Type strListType = strListField.getGenericType();
        Field intListField = TestConfiguredBean.class.getField("intList");
        Type intListType = intListField.getGenericType();

        ConfigProperties configProperties = TestConfiguredBean.class.getAnnotation(ConfigProperties.class);
        ConfigBeanDescriptor descriptor = ConfigBeanDescriptor.create(annotatedType, configProperties);

        InjectionPoint strListInjectionPoint = mock(InjectionPoint.class);
        when(strListInjectionPoint.getType()).thenReturn(strListType);

        InjectionPoint intListInjectionPoint = mock(InjectionPoint.class);
        when(intListInjectionPoint.getType()).thenReturn(intListType);

        // the target of the test starts here:
        Object instance = descriptor.produce(strListInjectionPoint, config);
        assertThat(((TestConfiguredBean) instance).strList, contains("a", "b", "c"));

        instance = descriptor.produce(intListInjectionPoint, config);
        assertThat(((TestConfiguredBean) instance).intList, contains(1, 2, 3));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProduceGenericTypesNotSupported() throws Exception {
        AnnotatedType<?> annotatedType = mock(AnnotatedType.class);
        when(annotatedType.getJavaClass()).thenReturn((Class) TestConfiguredBean.Unsupported.class);
        ConfigProperties configProperties = TestConfiguredBean.class.getAnnotation(ConfigProperties.class);
        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
                                                       () -> ConfigBeanDescriptor.create(annotatedType, configProperties));
        assertThat(e.getMessage(), is("No idea how to handle ?"));
    }

    static Config createTestConfiguredBeanConfig() {
        return ConfigProviderResolver.instance()
                .getBuilder()
                .withSources(MpConfigSources.create(Map.of(
                        "strList", "a,b,c",
                        "intList", "1,2,3",
                        "untypedList", "a,b,c")))
                .build();
    }

}