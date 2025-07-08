/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
import static io.helidon.declarative.codegen.faulttolerance.FtTypes.RETRY;
import static io.helidon.declarative.codegen.faulttolerance.FtTypes.RETRY_ANNOTATION;
import static io.helidon.declarative.codegen.faulttolerance.FtTypes.RETRY_CONFIG;

final class RetryHandler extends FtHandler {

    RetryHandler(RegistryCodegenContext ctx) {
        super(ctx, RETRY_ANNOTATION);
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
        classModel.superType(FtTypes.RETRY_GENERATED_METHOD)
                .addAnnotation(Annotation.builder()
                                       .typeName(WEIGHT)
                                       .putValue("value", InterceptorWeights.WEIGHT_RETRY)
                                       .build());

        // generate the class body
        retryBody(classModel,
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

    private void retryBody(ClassModel.Builder classModel,
                           TypeName enclosingTypeName,
                           TypedElementInfo element,
                           TypeName generatedType,
                           String methodName,
                           Annotation annotation) {
        addErrorChecker(classModel, annotation, false);

        classModel.addField(retry -> retry
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(RETRY)
                .name("retry"));

        String name = annotation.stringValue("name").filter(Predicate.not(String::isBlank))
                .orElse(null);

        /*
        Constructor (may inject named Retry)
         */
        var ctr = Constructor.builder()
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);

        if (name == null) {
            ctr.addContentLine("this.retry = produceRetry();");
        } else {
            // named, inject
            ctr.addParameter(namedRetry -> namedRetry
                            .name("namedRetry")
                            .type(TypeName.builder()
                                          .from(TypeNames.OPTIONAL)
                                          .addTypeArgument(RETRY)
                                          .build())
                            .addAnnotation(namedAnnotation(name)))
                    .addContent("this.retry = namedRetry.orElseGet(")
                    .addContent(generatedType)
                    .addContentLine("::produceRetry);");
        }

        classModel.addConstructor(ctr);

        /*
        Retry method (implementing interface)
         */
        classModel.addMethod(retry -> retry
                .name("retry")
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(RETRY)
                .accessModifier(AccessModifier.PROTECTED)
                .addContentLine("return retry;")
        );

        /*
        Produce retry method (from annotation values)
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

        classModel.addMethod(produceRetry -> produceRetry
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .returnType(RETRY)
                .name("produceRetry")
                .update(builder -> produceRetryMethodBody(enclosingTypeName,
                                                          element,
                                                          builder,
                                                          annotation,
                                                          customName))
        );
    }

    private void produceRetryMethodBody(TypeName typeName,
                                        TypedElementInfo element,
                                        Method.Builder builder,
                                        Annotation annotation,
                                        String customName) {

        int calls = annotation.intValue("calls").orElse(3);
        String delayDuration = validateDuration(typeName,
                                                element,
                                                RETRY_ANNOTATION,
                                                "delay",
                                                annotation.stringValue("delay").orElse("PT0.2S"));
        double delayFactor = annotation.doubleValue("delayFactor").orElse(-1.0);
        String jitterDuration = validateDuration(typeName,
                                                 element,
                                                 RETRY_ANNOTATION,
                                                 "jitter",
                                                 annotation.stringValue("jitter").orElse("PT-1S"));
        String overallTimeoutDuration = validateDuration(typeName,
                                                         element,
                                                         RETRY_ANNOTATION,
                                                         "overallTimeout",
                                                         annotation.stringValue("overallTimeout").orElse("PT1S"));

        /*
        First build the retry policy
         */
        builder.addContent("var policy = ")
                .addContent(RETRY)
                .addContent(".");

        if (jitterDuration.equals("PT-1S") || delayFactor > 0) {
            delayFactor = delayFactor > 0 ? delayFactor : 2.0;
            // delaying
            builder.addContentLine("DelayingRetryPolicy.builder()")
                    .increaseContentPadding()
                    .increaseContentPadding()
                    .addContent(".delayFactor(")
                    .addContent(String.valueOf(delayFactor))
                    .addContentLine(")");
        } else {
            // jittery
            builder.addContentLine("JitterRetryPolicy.builder()")
                    .increaseContentPadding()
                    .increaseContentPadding()
                    .addContent(".jitter(")
                    .addContent(Duration.class)
                    .addContent(".parse(\"")
                    .addContent(jitterDuration)
                    .addContentLine("\"))");
        }
        builder.addContent(".calls(")
                .addContent(String.valueOf(calls))
                .addContentLine(")");
        builder.addContent(".delay(")
                .addContent(Duration.class)
                .addContent(".parse(\"")
                .addContent(delayDuration)
                .addContentLine("\"))");
        builder.addContentLine(".build();")
                .decreaseContentPadding()
                .decreaseContentPadding();
        builder.addContentLine("");

        /*
        Now build the retry itself
         */
        builder.addContent("return ")
                .addContent(RETRY_CONFIG)
                .addContentLine(".builder()")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContentLine(".applyOn(APPLY_ON)")
                .addContentLine(".skipOn(SKIP_ON)")
                .addContentLine(".retryPolicy(policy)")
                .addContent(".overallTimeout(")
                .addContent(Duration.class)
                .addContent(".parse(\"")
                .addContent(overallTimeoutDuration)
                .addContentLine("\"))")
                .addContent(".name(\"")
                .addContent(customName)
                .addContentLine("\")")
                .addContentLine(".build();")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }
}
