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

import io.helidon.builder.Builder;
import io.helidon.builder.Singular;
import io.helidon.builder.config.ConfigBean;
import io.helidon.builder.config.spi.ConfigBeanInfo;
import io.helidon.common.types.AnnotationAndValueDefault;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeInfoDefault;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNameDefault;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.common.types.TypedElementInfoDefault;

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
        TypeName implTypeName = TypeNameDefault.create(getClass());
        TypeInfo typeInfo = TypeInfoDefault.builder()
                .typeKind(TypeInfo.KIND_CLASS)
                .typeName(TypeNameDefault.create(getClass()))
                .build();
        AnnotationAndValueDefault configBeanAnno = AnnotationAndValueDefault.builder()
                .typeName(TypeNameDefault.create(ConfigBean.class))
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
        TypeName implTypeName = TypeNameDefault.create(getClass());
        TypeInfo typeInfo = TypeInfoDefault.builder()
                .typeKind(TypeInfo.KIND_INTERFACE)
                .typeName(TypeNameDefault.create(getClass()))
                .build();
        AnnotationAndValueDefault configBeanAnno = AnnotationAndValueDefault.builder()
                .typeName(TypeNameDefault.create(ConfigBean.class))
                .values(Map.of(
                        ConfigBeanInfo.TAG_LEVEL_TYPE, ConfigBean.LevelType.NESTED.name(),
                        ConfigBeanInfo.TAG_DRIVES_ACTIVATION, "true"))
                .build();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                                               () -> creator.preValidate(implTypeName, typeInfo, configBeanAnno));
        assertThat(e.getMessage(),
                   equalTo("Only levelType {ROOT} config beans can drive activation for: " + getClass().getName()));
    }

    @Test
    void preValidateConfigBeansMustBeRootToHaveDefaults() {
        TypeName implTypeName = TypeNameDefault.create(getClass());
        TypeInfo typeInfo = TypeInfoDefault.builder()
                .typeKind(TypeInfo.KIND_INTERFACE)
                .typeName(TypeNameDefault.create(getClass()))
                .build();
        AnnotationAndValueDefault configBeanAnno = AnnotationAndValueDefault.builder()
                .typeName(TypeNameDefault.create(ConfigBean.class))
                .values(Map.of(
                        ConfigBeanInfo.TAG_LEVEL_TYPE, ConfigBean.LevelType.NESTED.name(),
                        ConfigBeanInfo.TAG_DRIVES_ACTIVATION, "false",
                        ConfigBeanInfo.TAG_WANT_DEFAULT_CONFIG_BEAN, "true"))
                .build();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                                               () -> creator.preValidate(implTypeName, typeInfo, configBeanAnno));
        assertThat(e.getMessage(),
                   equalTo("Only levelType {ROOT} config beans can have a default bean for: " + getClass().getName()));
    }

    @Test
    void preValidateConfigBeansMustNotHaveDuplicateSingularNames() {
        TypedElementInfo method1 = TypedElementInfoDefault.builder()
                .elementName("socket")
                .typeName(String.class)
                .build();
        TypedElementInfo method2 = TypedElementInfoDefault.builder()
                .elementName("socketSet")
                .typeName(String.class)
                .addAnnotation(AnnotationAndValueDefault.create(Singular.class, "socket"))
                .build();
        TypeName implTypeName = TypeNameDefault.create(getClass());
        TypeInfo typeInfo = TypeInfoDefault.builder()
                .typeKind(TypeInfo.KIND_INTERFACE)
                .typeName(TypeNameDefault.create(getClass()))
                .interestingElementInfo(Set.of(method1, method2))
                .build();
        AnnotationAndValueDefault configBeanAnno = AnnotationAndValueDefault.builder()
                .typeName(TypeNameDefault.create(ConfigBean.class))
                .values(Map.of(
                        ConfigBeanInfo.TAG_LEVEL_TYPE, ConfigBean.LevelType.NESTED.name(),
                        ConfigBeanInfo.TAG_DRIVES_ACTIVATION, "false",
                        ConfigBeanInfo.TAG_WANT_DEFAULT_CONFIG_BEAN, "false"))
                .build();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                                               () -> creator.preValidate(implTypeName, typeInfo, configBeanAnno));
        assertThat(e.getMessage(),
                   startsWith("Duplicate methods are using the same names [socket] for: "));
    }

    @Test
    void preValidateConfigBeansMustHaveMapTypesWithNestedConfigBeans() {
        TypedElementInfo method1 = TypedElementInfoDefault.builder()
                .elementName("socket")
                .typeName(TypeNameDefault.builder()
                                  .type(Map.class)
                                  .typeArguments(List.of(
                                          TypeNameDefault.create(String.class),
                                          TypeNameDefault.create(String.class)))
                                  .build())
                .build();
        TypeName implTypeName = TypeNameDefault.create(getClass());
        TypeInfo typeInfo = TypeInfoDefault.builder()
                .typeKind(TypeInfo.KIND_INTERFACE)
                .typeName(TypeNameDefault.create(getClass()))
                .interestingElementInfo(Set.of(method1))
                .build();
        AnnotationAndValueDefault configBeanAnno = AnnotationAndValueDefault.builder()
                .typeName(TypeNameDefault.create(ConfigBean.class))
                .values(Map.of(
                        ConfigBeanInfo.TAG_LEVEL_TYPE, ConfigBean.LevelType.NESTED.name(),
                        ConfigBeanInfo.TAG_DRIVES_ACTIVATION, "false",
                        ConfigBeanInfo.TAG_WANT_DEFAULT_CONFIG_BEAN, "false"))
                .build();
        // Map<String, any non-generic> is supported
//        IllegalStateException e = assertThrows(IllegalStateException.class,
//                                               () -> creator.preValidate(implTypeName, typeInfo, configBeanAnno));
//        assertThat(e.getMessage(), startsWith(
//                "[java.util.Map<java.lang.String, java.lang.String> socket]: only methods returning Map<String, "
//                        + "<any-non-generic-type>> are supported for: "));
        creator.preValidate(implTypeName, typeInfo, configBeanAnno);

        // now we will validate the exceptions when ConfigBeans are attempted to be embedded
        TypedElementInfo method2 = TypedElementInfoDefault.builder()
                .elementName("unsupported1")
                .typeName(TypeNameDefault.builder()
                                  .type(Map.class)
                                  // here we register a known config bean value (see below)
                                  .typeArguments(List.of(
                                          TypeNameDefault.create(String.class),
                                          TypeNameDefault.create(getClass())))
                                  .build())
                .build();
        TypedElementInfo method3 = TypedElementInfoDefault.builder()
                .elementName("unsupported2")
                .typeName(TypeNameDefault.builder()
                                  .type(Map.class)
                        // here we will just leave it generic, and this should fail
//                                  .typeArguments(List.of(
//                                          TypeNameDefault.create(String.class),
//                                          TypeNameDefault.create(getClass())))
                                  .build())
                .build();
        TypeInfo typeInfo2 = TypeInfoDefault.builder()
                .typeKind(TypeInfo.KIND_INTERFACE)
                .typeName(TypeNameDefault.create(getClass()))
                .interestingElementInfo(List.of(method2, method3))
                .referencedTypeNamesToAnnotations(Map.of(TypeNameDefault.create(getClass()),
                                                         List.of(AnnotationAndValueDefault.create(Builder.class))))
                .build();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                                               () -> creator.preValidate(implTypeName, typeInfo2, configBeanAnno));
        assertThat(e.getMessage(), startsWith(
                "[java.util.Map unsupported2]: only methods returning Map<String, <any-non-generic-type>> are supported for: "));
    }

}
