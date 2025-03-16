/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
import java.util.UUID;
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
import static io.helidon.declarative.codegen.faulttolerance.FtTypes.CIRCUIT_BREAKER;
import static io.helidon.declarative.codegen.faulttolerance.FtTypes.CIRCUIT_BREAKER_ANNOTATION;
import static io.helidon.declarative.codegen.faulttolerance.FtTypes.CIRCUIT_BREAKER_CONFIG;

class CircuitBreakerHandler extends FtHandler {

    CircuitBreakerHandler(RegistryCodegenContext ctx) {
        super(ctx, CIRCUIT_BREAKER_ANNOTATION);
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
        classModel.addInterface(FtTypes.CIRCUIT_BREAKER_GENERATED_METHOD);

        // generate the class body
        circuitBreakerBody(classModel,
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

    private void circuitBreakerBody(ClassModel.Builder classModel,
                                    TypeName enclosingTypeName,
                                    TypedElementInfo element,
                                    TypeName generatedType,
                                    String methodName,
                                    Annotation annotation) {
        addErrorChecker(classModel, annotation);

        classModel.addField(circuitBreaker -> circuitBreaker
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(CIRCUIT_BREAKER)
                .name("breaker"));

        String name = annotation.stringValue("name").filter(Predicate.not(String::isBlank))
                .orElse(null);

        /*
        Constructor (may inject named CircuitBreaker)
         */
        var ctr = Constructor.builder()
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);

        if (name == null) {
            ctr.addContentLine("this.breaker = produceBreaker();");
        } else {
            // named, inject
            ctr.addParameter(namedCircuitBreaker -> namedCircuitBreaker
                            .name("namedBreaker")
                            .type(TypeName.builder()
                                          .from(TypeNames.OPTIONAL)
                                          .addTypeArgument(CIRCUIT_BREAKER)
                                          .build())
                            .addAnnotation(namedAnnotation(name)))
                    .addContent("this.breaker = namedBreaker.orElseGet(")
                    .addContent(generatedType)
                    .addContentLine("::produceBreaker);");
        }

        classModel.addConstructor(ctr);

        /*
        CircuitBreaker method (implementing interface)
         */
        classModel.addMethod(circuitBreaker -> circuitBreaker
                .name("circuitBreaker")
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(CIRCUIT_BREAKER)
                .accessModifier(AccessModifier.PUBLIC)
                .addContentLine("return breaker;")
        );

        /*
        Produce circuitBreaker method (from annotation values)
         */
        String customName;
        if (name == null) {
            // this is not fully random, but we may only get conflict on the same type, same method name
            customName = enclosingTypeName.fqName()
                    + "." + methodName + "-"
                    + System.identityHashCode(enclosingTypeName);
        } else {
            customName = name + "-" + UUID.randomUUID();
        }

        classModel.addMethod(produceCircuitBreaker -> produceCircuitBreaker
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .returnType(CIRCUIT_BREAKER)
                .name("produceBreaker")
                .update(builder -> produceBreakerMethodBody(enclosingTypeName, element, builder, annotation, customName))
        );
    }

    private void produceBreakerMethodBody(TypeName typeName,
                                          TypedElementInfo element,
                                          Method.Builder builder,
                                          Annotation annotation,
                                          String customName) {
        String delayDuration = validateDuration(typeName,
                                                element,
                                                CIRCUIT_BREAKER_ANNOTATION,
                                                "delay",
                                                annotation.stringValue("delay").orElse("PT5S"));
        int errorRatio = annotation.intValue("errorRatio").orElse(60);
        int volume = annotation.intValue("volume").orElse(10);
        int successThreshold = annotation.intValue("successThreshold").orElse(1);

        builder.addContent("return ")
                .addContent(CIRCUIT_BREAKER_CONFIG)
                .addContentLine(".builder()")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContentLine(".applyOn(APPLY_ON)")
                .addContentLine(".skipOn(SKIP_ON)")
                .addContent(".delay(")
                .addContent(Duration.class)
                .addContent(".parse(\"")
                .addContent(delayDuration)
                .addContentLine("\"))")
                .addContent(".name(\"")
                .addContent(customName)
                .addContentLine("\")")
                .addContent(".errorRatio(")
                .addContent(String.valueOf(errorRatio))
                .addContentLine(")")
                .addContent(".volume(")
                .addContent(String.valueOf(volume))
                .addContentLine(")")
                .addContent(".successThreshold(")
                .addContent(String.valueOf(successThreshold))
                .addContentLine(")")
                .addContentLine(".build();")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }
}
