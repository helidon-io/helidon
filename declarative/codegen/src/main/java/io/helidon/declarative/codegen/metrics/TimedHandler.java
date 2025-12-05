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

package io.helidon.declarative.codegen.metrics;

import java.util.List;
import java.util.concurrent.Callable;

import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementSignature;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.DeclarativeTypes;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceCodegenTypes;

import static io.helidon.declarative.codegen.metrics.MetricsExtension.GENERATOR;
import static io.helidon.declarative.codegen.metrics.MetricsTypes.ANNOTATION_TIMED;
import static io.helidon.declarative.codegen.metrics.MetricsTypes.METER_REGISTRY;
import static io.helidon.declarative.codegen.metrics.MetricsTypes.TIMER;

class TimedHandler {
    private final RegistryRoundContext ctx;

    TimedHandler(RegistryRoundContext ctx) {
        this.ctx = ctx;
    }

    void handle(TypeInfo typeInfo, TypedElementInfo element, int counter) {
        TypeName type = typeInfo.typeName();
        String className = type.className() + "__TimedInterceptor" + (counter != 0 ? "_" + counter : "");

        TypeName generatedType = TypeName.builder()
                .packageName(type.packageName())
                .className(className)
                .build();

        Annotation named = Annotation.builder()
                .typeName(ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED)
                .value(type.fqName() + "." + element.signature().text())
                .build();

        var classModel = ClassModel.builder()
                .type(generatedType)
                .copyright(CodegenUtil.copyright(GENERATOR, GENERATOR, generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR, GENERATOR, generatedType, "0", ""))
                .addAnnotation(DeclarativeTypes.SINGLETON_ANNOTATION)
                .addAnnotation(named)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addInterface(ServiceCodegenTypes.INTERCEPTION_ELEMENT_INTERCEPTOR);

        classModel.addField(timerField -> timerField
                .type(TIMER)
                .name("timer")
                .isFinal(true)
                .accessModifier(AccessModifier.PRIVATE));

        var annotation = element.annotation(ANNOTATION_TIMED);

        String name = MetricsExtension.name(annotation, type, element);
        List<MetricsExtension.Tag> tags = MetricsExtension.tags(ctx, type, element, annotation);
        String scope = MetricsExtension.scope(annotation);
        String description = MetricsExtension.description(annotation, ANNOTATION_TIMED, type, element);
        String unit = annotation.stringValue("unit").orElse("none");

        var ctr = Constructor.builder()
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT))
                .addParameter(registry -> registry
                        .type(METER_REGISTRY)
                        .name("meterRegistry")
                )
                .addContent("this.timer = meterRegistry.getOrCreate(")
                .addContent(TIMER)
                .addContent(".builder(")
                .addContentLiteral(name)
                .addContentLine(")")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(".description(")
                .addContentLiteral(description)
                .addContentLine(")")
                .addContent(".scope(")
                .addContentLiteral(scope)
                .addContentLine(")")
                .addContent(".baseUnit(")
                .addContentLiteral(unit)
                .addContentLine(")");

        MetricsExtension.addTagsToBuilder(ctr, tags);

        ctr.decreaseContentPadding()
                .decreaseContentPadding()
                .addContentLine(");");

        classModel.addConstructor(ctr);

        TypeName typeV = TypeName.createFromGenericDeclaration("V");
        TypeName chainType = TypeName.builder(ServiceCodegenTypes.INTERCEPTION_CHAIN)
                .addTypeArgument(typeV)
                .build();

        classModel.addMethod(proceed -> proceed
                .addGenericArgument(TypeArgument.create(typeV))
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(typeV)
                .name("proceed")
                .addParameter(ctx -> ctx
                        .name("ctx")
                        .type(ServiceCodegenTypes.INTERCEPTION_CONTEXT)
                )
                .addParameter(chain -> chain
                        .name("chain")
                        .type(chainType)
                )
                .addParameter(args -> args
                        .name("args")
                        .type(TypeName.builder(TypeNames.OBJECT)
                                      .vararg(true)
                                      .build())
                )
                .addThrows(thr -> thr.type(Exception.class))
                .addContent("return timer.record((")
                .addContent(Callable.class)
                .addContentLine("<V>) () -> chain.proceed(args));")
        );

        addToString(classModel, type, element.signature());

        ctx.addGeneratedType(generatedType, classModel, GENERATOR);
    }

    private void addToString(ClassModel.Builder classModel,
                             TypeName serviceType,
                             ElementSignature signature) {
        classModel.addMethod(toString -> toString
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(TypeNames.STRING)
                .name("toString")
                .addAnnotation(Annotations.OVERRIDE)
                .addContent("return \"Timed interceptor for ")
                .addContent(serviceType.fqName())
                .addContent(".")
                .addContent(signature.text())
                .addContentLine("\";")
        );
    }
}
