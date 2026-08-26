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

package io.helidon.integrations.langchain4j.codegen;

import java.util.ArrayList;
import java.util.List;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.AnnotationProperty;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ModelTypeTest {
    private static final String TEST_PACKAGE = "io.helidon.integrations.langchain4j.codegen.test.";

    @Test
    void traversesDeepSuperclassHierarchyOnce() {
        TypeInfo current = classType("Level0", null);
        List<TypeName> expected = new ArrayList<>();
        expected.add(current.typeName());

        for (int i = 1; i <= 16; i++) {
            current = classType("Level" + i, current);
            expected.addFirst(current.typeName());
        }

        List<TypeInfo> lineage = new ArrayList<>();
        ModelType.allParents(current, lineage);

        assertEquals(expected, lineage.stream().map(TypeInfo::typeName).toList());
    }

    @Test
    void selectsMostSpecificOverrideAcrossInterfaceDiamond() {
        TypedElementInfo sharedOption = configuredMethod("endpoint");
        TypedElementInfo rightOption = configuredMethod("endpoint");
        TypeInfo sharedFromLeft = interfaceType("Shared", List.of(), List.of(sharedOption));
        TypeInfo sharedFromRight = interfaceType("Shared", List.of(), List.of(configuredMethod("endpoint")));
        TypeInfo left = interfaceType("Left", List.of(sharedFromLeft), List.of());
        TypeInfo right = interfaceType("Right", List.of(sharedFromRight), List.of(rightOption));
        TypeInfo provider = interfaceType("DiamondLc4jProvider", List.of(left, right), List.of());

        List<TypeInfo> lineage = new ArrayList<>();
        ModelType.allParents(provider, lineage);

        assertEquals(List.of(provider.typeName(), left.typeName(), right.typeName(), sharedFromLeft.typeName()),
                     lineage.stream().map(TypeInfo::typeName).toList());

        TypeInfo modelType = interfaceType("TestModel", List.of(), List.of());
        TypeInfo builderType = classType("TestModelBuilder", null);
        Annotation modelAnnotation = Annotation.builder()
                .typeName(LangchainTypes.MODEL_CONFIG_TYPE)
                .putProperty("value", AnnotationProperty.create(modelType.typeName()))
                .build();
        LlmModelBlueprintBuilder blueprintBuilder = new LlmModelBlueprintBuilder(null,
                                                                                 provider,
                                                                                 modelType,
                                                                                 builderType,
                                                                                 "test",
                                                                                 modelAnnotation);

        assertSame(rightOption, blueprintBuilder.resolveOverriddenProperties().get("endpoint"));
    }

    private static TypedElementInfo configuredMethod(String name) {
        return TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .elementName(name)
                .typeName(TypeNames.STRING)
                .addAnnotation(Annotation.create(LangchainTypes.OPT_CONFIGURED))
                .build();
    }

    private static TypeInfo classType(String name, TypeInfo superType) {
        TypeInfo.Builder builder = TypeInfo.builder()
                .typeName(TypeName.create(TEST_PACKAGE + name))
                .kind(ElementKind.CLASS);
        if (superType != null) {
            builder.superTypeInfo(superType);
        }
        return builder.build();
    }

    private static TypeInfo interfaceType(String name, List<TypeInfo> parents, List<TypedElementInfo> methods) {
        return TypeInfo.builder()
                .typeName(TypeName.create(TEST_PACKAGE + name))
                .kind(ElementKind.INTERFACE)
                .interfaceTypeInfo(parents)
                .elementInfo(methods)
                .build();
    }
}
