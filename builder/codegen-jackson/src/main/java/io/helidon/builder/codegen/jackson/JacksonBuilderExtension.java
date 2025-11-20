/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.builder.codegen.jackson;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.codegen.GeneratedMethod;
import io.helidon.builder.codegen.OptionInfo;
import io.helidon.builder.codegen.OptionMethodType;
import io.helidon.builder.codegen.PrototypeInfo;
import io.helidon.builder.codegen.spi.BuilderCodegenExtension;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.codegen.jackson.JacksonTypes.JSON_DESERIALIZE;
import static io.helidon.builder.codegen.jackson.JacksonTypes.JSON_POJO_BUILDER;

class JacksonBuilderExtension implements BuilderCodegenExtension {
    public static final String JACKSON_PACKAGE_PREFIX = "com.fasterxml.jackson";
    private final boolean serialize;
    private final boolean deserialize;

    JacksonBuilderExtension(boolean serialize, boolean deserialize) {
        this.serialize = serialize;
        this.deserialize = deserialize;
    }

    @Override
    public Optional<GeneratedMethod> method(OptionInfo optionInfo, GeneratedMethod method, OptionMethodType methodType) {
        // serialization (of object to String) uses the implementation type getters
        if (methodType == OptionMethodType.PROTOTYPE_GETTER) {
            return Optional.of(updateImplGetter(optionInfo, method));
        }
        if (methodType == OptionMethodType.BUILDER_SETTER) {
            if (serialize) {
                return Optional.of(updateBuilderSetter(optionInfo, method));
            }
        }

        return Optional.of(method);
    }

    @Override
    public void updatePrototype(PrototypeInfo prototypeInfo, List<OptionInfo> options, ClassModel.Builder classModel) {
        if (!deserialize) {
            return;
        }
        var builderType = TypeName.builder(prototypeInfo.prototypeType())
                .className("Builder")
                .addEnclosingName(prototypeInfo.prototypeType().className())
                .build();

        classModel.addAnnotation(Annotation.builder()
                                         .typeName(JSON_DESERIALIZE)
                                         .putValue("builder", builderType)
                                         .build());
        classModel.addAnnotation(Annotation.builder()
                                         .typeName(JSON_POJO_BUILDER)
                                         .putValue("withPrefix", prototypeInfo.recordStyle() ? "" : "set")
                                         .build());
    }

    private GeneratedMethod updateBuilderSetter(OptionInfo optionInfo, GeneratedMethod method) {
        if (optionInfo.interfaceMethod().isEmpty()) {
            // no declaration
            return method;
        }
        TypedElementInfo ifaceMethod = optionInfo.interfaceMethod().get();
        // add any annotation related to Jackson
        var gmBuilder = GeneratedMethod.builder(method);
        var methodBuilder = TypedElementInfo.builder(method.method());

        for (Annotation annotation : ifaceMethod.annotations()) {
            if (annotation.typeName().packageName().startsWith(JACKSON_PACKAGE_PREFIX)) {
                methodBuilder.addAnnotation(annotation);
            }
        }

        return gmBuilder.method(methodBuilder)
                .build();
    }

    private GeneratedMethod updateImplGetter(OptionInfo optionInfo, GeneratedMethod method) {
        if (optionInfo.interfaceMethod().isEmpty()) {
            // no declaration
            return method;
        }
        TypedElementInfo ifaceMethod = optionInfo.interfaceMethod().get();
        // add any annotation related to Jackson
        var gmBuilder = GeneratedMethod.builder(method);
        var methodBuilder = TypedElementInfo.builder(method.method());

        for (Annotation annotation : ifaceMethod.annotations()) {
            if (annotation.typeName().packageName().startsWith(JACKSON_PACKAGE_PREFIX)) {
                methodBuilder.addAnnotation(annotation);
            }
        }

        return gmBuilder.method(methodBuilder)
                .build();
    }
}
