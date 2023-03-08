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

import java.util.Map;

import io.helidon.builder.config.ConfigBean;
import io.helidon.builder.config.spi.ConfigBeanInfo;
import io.helidon.common.types.DefaultAnnotationAndValue;
import io.helidon.common.types.DefaultTypeInfo;
import io.helidon.common.types.DefaultTypeName;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
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

}
