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

package io.helidon.declarative.codegen.faulttolerance;

import java.time.Duration;
import java.util.function.Predicate;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceCodegenTypes;

import static io.helidon.codegen.CodegenValidator.validateDuration;
import static io.helidon.declarative.codegen.DeclarativeTypes.WEIGHT;
import static io.helidon.declarative.codegen.faulttolerance.FtTypes.TIMEOUT;
import static io.helidon.declarative.codegen.faulttolerance.FtTypes.TIMEOUT_ANNOTATION;

final class TimeoutHandler extends FtHandler {

    TimeoutHandler(RegistryCodegenContext ctx) {
        super(ctx, TIMEOUT_ANNOTATION);
    }

    @Override
    void process(RegistryRoundContext roundContext,
                 TypeInfo enclosingType,
                 TypedElementInfo element,
                 Annotation annotation,
                 TypeName generatedType,
                 ClassModel.Builder classModel) {
        TypeName enclosingTypeName = enclosingType.typeName();

        // class definition
        classModel.superType(FtTypes.TIMEOUT_GENERATED_METHOD)
                .addAnnotation(Annotation.builder()
                                       .typeName(WEIGHT)
                                       .putValue("value", InterceptorWeights.WEIGHT_TIMEOUT)
                                       .build());

        // generate the class body
        timeoutBody(classModel,
                    enclosingTypeName,
                    element,
                    generatedType,
                    element.elementName(),
                    annotation);

        // add type to context
        addType(roundContext,
                generatedType,
                classModel,
                enclosingTypeName,
                element);
    }

    private void timeoutBody(ClassModel.Builder classModel,
                             TypeName enclosingTypeName,
                             TypedElementInfo element,
                             TypeName generatedType,
                             String methodName,
                             Annotation annotation) {

        classModel.addField(timeout -> timeout
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(TIMEOUT)
                .name("timeout"));

        String name = annotation.stringValue("name")
                .filter(Predicate.not(String::isBlank))
                .orElse(null);

        /*
        Constructor (may inject named Timeout)
         */
        var ctr = Constructor.builder()
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);

        if (name == null) {
            ctr.addContentLine("this.timeout = produceTimeout();");
        } else {
            // named, inject
            ctr.addParameter(namedTimeout -> namedTimeout
                            .name("namedTimeout")
                            .type(TypeName.builder()
                                          .from(TypeNames.OPTIONAL)
                                          .addTypeArgument(TIMEOUT)
                                          .build())
                            .addAnnotation(namedAnnotation(name)))
                    .addContent("this.timeout = namedTimeout.orElseGet(")
                    .addContent(generatedType)
                    .addContentLine("::produceTimeout);");
        }

        classModel.addConstructor(ctr);

        /*
        Timeout method (implementing abstract method)
         */
        classModel.addMethod(timeout -> timeout
                .name("timeout")
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(TIMEOUT)
                .accessModifier(AccessModifier.PROTECTED)
                .addContentLine("return timeout;")
        );

        /*
        Produce timeout method (from annotation values)
         */
        String customName;
        if (name == null) {
            customName = enclosingTypeName.fqName()
                    + "." + element.signature().text();
        } else {
            // as the named instance was not found, use the name and our unique signature
            customName = name + "-" + enclosingTypeName.fqName()
                    + "." + element.signature().text();
        }

        classModel.addMethod(produceTimeout -> produceTimeout
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .returnType(TIMEOUT)
                .name("produceTimeout")
                .update(builder -> produceTimeoutMethodBody(enclosingTypeName,
                                                            element,
                                                            builder,
                                                            annotation,
                                                            customName))
        );
    }

    private void produceTimeoutMethodBody(TypeName typeName,
                                          TypedElementInfo element,
                                          Method.Builder builder,
                                          Annotation annotation,
                                          String customName) {

        String timeout = validateDuration(typeName,
                                          element,
                                          TIMEOUT_ANNOTATION,
                                          "time",
                                          annotation.stringValue("time").orElse("PT10S"));
        boolean currentThread = annotation.booleanValue("currentThread")
                .orElse(false);

        builder.addContent("return ")
                .addContent(TIMEOUT)
                .addContentLine(".builder()")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(".name(\"")
                .addContent(customName)
                .addContentLine("\")")
                .addContent(".timeout(")
                .addContent(Duration.class)
                .addContent(".parse(\"")
                .addContent(timeout)
                .addContentLine("\"))")
                .addContent(".currentThread(")
                .addContent(String.valueOf(currentThread))
                .addContentLine(")")
                .addContentLine(".build();")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }
}
