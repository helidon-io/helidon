/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.config.processor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.builder.Singular;
import io.helidon.builder.config.ConfigBean;
import io.helidon.builder.config.spi.ConfigBeanInfo;
import io.helidon.common.types.DefaultAnnotationAndValue;
import io.helidon.common.types.DefaultTypeInfo;
import io.helidon.common.types.DefaultTypeName;
import io.helidon.common.types.DefaultTypedElementName;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementName;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigBeanBuilderCreatorTest {
    final ConfigBeanBuilderCreator creator = new ConfigBeanBuilderCreator();

    @Test
    void supportedAnnotationTypes() {
        assertThat(creator.supportedAnnotationTypes().toString(), creator.supportedAnnotationTypes().size(),
                   is(1));
        assertThat(creator.supportedAnnotationTypes().iterator().next(),
                   equalTo(ConfigBean.class));
    }

    @Test
    void preValidateConfigBeansMustBeInterfaces() {
        TypeName implTypeName = DefaultTypeName.create(getClass());
        TypeInfo typeInfo = DefaultTypeInfo.builder()
                .typeKind(TypeInfo.KIND_CLASS)
                .typeName(DefaultTypeName.create(getClass()))
                .build();
        DefaultAnnotationAndValue configBeanAnno = DefaultAnnotationAndValue.builder()
                .typeName(DefaultTypeName.create(ConfigBean.class))
                .values(Map.of(
                        ConfigBeanInfo.TAG_LEVEL_TYPE, ConfigBean.LevelType.ROOT.name(),
                        ConfigBeanInfo.TAG_DRIVES_ACTIVATION, "true"))
                .build();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                                               () -> creator.preValidate(implTypeName, typeInfo, configBeanAnno));
        assertThat(e.getMessage(),
                   equalTo("@ConfigBean is only supported on interface types: " + getClass().getName()));
    }

    @Test
    void preValidateConfigBeansMustBeRootToDriveActivation() {
        TypeName implTypeName = DefaultTypeName.create(getClass());
        TypeInfo typeInfo = DefaultTypeInfo.builder()
                .typeKind(TypeInfo.KIND_INTERFACE)
                .typeName(DefaultTypeName.create(getClass()))
                .build();
        DefaultAnnotationAndValue configBeanAnno = DefaultAnnotationAndValue.builder()
                .typeName(DefaultTypeName.create(ConfigBean.class))
                .values(Map.of(
                        ConfigBeanInfo.TAG_LEVEL_TYPE, ConfigBean.LevelType.NESTED.name(),
                        ConfigBeanInfo.TAG_DRIVES_ACTIVATION, "true"))
                .build();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                                               () -> creator.preValidate(implTypeName, typeInfo, configBeanAnno));
        assertThat(e.getMessage(),
                   equalTo("only levelType {ROOT} config beans can drive activation for: " + getClass().getName()));
    }

    @Test
    void preValidateConfigBeansMustBeRootToHaveDefaults() {
        TypeName implTypeName = DefaultTypeName.create(getClass());
        TypeInfo typeInfo = DefaultTypeInfo.builder()
                .typeKind(TypeInfo.KIND_INTERFACE)
                .typeName(DefaultTypeName.create(getClass()))
                .build();
        DefaultAnnotationAndValue configBeanAnno = DefaultAnnotationAndValue.builder()
                .typeName(DefaultTypeName.create(ConfigBean.class))
                .values(Map.of(
                        ConfigBeanInfo.TAG_LEVEL_TYPE, ConfigBean.LevelType.NESTED.name(),
                        ConfigBeanInfo.TAG_DRIVES_ACTIVATION, "false",
                        ConfigBeanInfo.TAG_WANT_DEFAULT_CONFIG_BEAN, "true"))
                .build();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                                               () -> creator.preValidate(implTypeName, typeInfo, configBeanAnno));
        assertThat(e.getMessage(),
                   equalTo("only levelType {ROOT} config beans can have a default bean for: " + getClass().getName()));
    }

    @Test
    void preValidateConfigBeansMustNotHaveDuplicateSingularNames() {
        TypedElementName method1 = DefaultTypedElementName.builder()
                .elementName("socket")
                .typeName(String.class)
                .build();
        TypedElementName method2 = DefaultTypedElementName.builder()
                .elementName("socketSet")
                .typeName(String.class)
                .addAnnotation(DefaultAnnotationAndValue.create(Singular.class, "socket"))
                .build();
        TypeName implTypeName = DefaultTypeName.create(getClass());
        TypeInfo typeInfo = DefaultTypeInfo.builder()
                .typeKind(TypeInfo.KIND_INTERFACE)
                .typeName(DefaultTypeName.create(getClass()))
                .elementInfo(Set.of(method1, method2))
                .build();
        DefaultAnnotationAndValue configBeanAnno = DefaultAnnotationAndValue.builder()
                .typeName(DefaultTypeName.create(ConfigBean.class))
                .values(Map.of(
                        ConfigBeanInfo.TAG_LEVEL_TYPE, ConfigBean.LevelType.NESTED.name(),
                        ConfigBeanInfo.TAG_DRIVES_ACTIVATION, "false",
                        ConfigBeanInfo.TAG_WANT_DEFAULT_CONFIG_BEAN, "false"))
                .build();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                                               () -> creator.preValidate(implTypeName, typeInfo, configBeanAnno));
        assertThat(e.getMessage(),
                   startsWith("duplicate methods are using the same names [socket] for: "));
    }

    /**
     * Enhancement tracked in https://github.com/helidon-io/helidon/issues/6382
     */
    @Test
    void preValidateConfigBeansMustNotHaveMapTypesWithNestedConfigBeans() {
        TypedElementName method1 = DefaultTypedElementName.builder()
                .elementName("socket")
                .typeName(DefaultTypeName.builder()
                                  .type(Map.class)
                                  .typeArguments(List.of(
                                          DefaultTypeName.create(String.class),
                                          DefaultTypeName.create(String.class)))
                                  .build())
                .build();
        TypeName implTypeName = DefaultTypeName.create(getClass());
        TypeInfo typeInfo = DefaultTypeInfo.builder()
                .typeKind(TypeInfo.KIND_INTERFACE)
                .typeName(DefaultTypeName.create(getClass()))
                .elementInfo(Set.of(method1))
                .build();
        DefaultAnnotationAndValue configBeanAnno = DefaultAnnotationAndValue.builder()
                .typeName(DefaultTypeName.create(ConfigBean.class))
                .values(Map.of(
                        ConfigBeanInfo.TAG_LEVEL_TYPE, ConfigBean.LevelType.NESTED.name(),
                        ConfigBeanInfo.TAG_DRIVES_ACTIVATION, "false",
                        ConfigBeanInfo.TAG_WANT_DEFAULT_CONFIG_BEAN, "false"))
                .build();
        // the above should be fine since we declared a simple Map type with no nested configbean components on the map decl
        creator.preValidate(implTypeName, typeInfo, configBeanAnno);

        // now we will validate the exceptions when ConfigBeans are attempted to be embedded
        TypedElementName method2 = DefaultTypedElementName.builder()
                .elementName("unsupported1")
                .typeName(DefaultTypeName.builder()
                                  .type(Map.class)
                                  .typeArguments(List.of(
                                          DefaultTypeName.create(String.class),
                                          DefaultTypeName.create(getClass())))
                                  .build())
                .build();
        TypedElementName method3 = DefaultTypedElementName.builder()
                .elementName("unsupported2")
                .typeName(DefaultTypeName.builder()
                                  .type(Map.class)
                        // here we will just leave it generic, and this should fail also
//                                  .typeArguments(List.of(
//                                          DefaultTypeName.create(String.class),
//                                          DefaultTypeName.create(getClass())))
                                  .build())
                .build();
        TypeInfo typeInfo2 = DefaultTypeInfo.builder()
                .typeKind(TypeInfo.KIND_INTERFACE)
                .typeName(DefaultTypeName.create(getClass()))
                .elementInfo(List.of(method1, method2, method3))
                .build();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                                               () -> creator.preValidate(implTypeName, typeInfo2, configBeanAnno));
        assertThat(e.getMessage(),
                   startsWith("methods returning Map<...sub ConfigBean...> [java.util.Map<java.lang.String, io.helidon.builder"
                                   + ".config.processor.ConfigBeanBuilderCreatorTest> unsupported1, java.util.Map unsupported2] "
                                   + "are not supported for: "));
    }

}
