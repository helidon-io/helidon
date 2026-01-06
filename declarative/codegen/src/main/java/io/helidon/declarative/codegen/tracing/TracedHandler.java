/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.tracing;

import java.util.List;
import java.util.Map;

import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementSignature;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.DeclarativeTypes;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceCodegenTypes;

import static io.helidon.declarative.codegen.tracing.TracingExtension.GENERATOR;
import static io.helidon.declarative.codegen.tracing.TracingTypes.SPAN_KIND;
import static io.helidon.declarative.codegen.tracing.TracingTypes.TRACER;

class TracedHandler {
    private final RegistryRoundContext ctx;

    TracedHandler(RegistryRoundContext ctx) {
        this.ctx = ctx;
    }

    public void handle(TypeName serviceType,
                       TypedElementInfo element,
                       int index,
                       String spanName,
                       String spanKind,
                       Map<String, String> tags, List<TracingExtension.TagParam> tagParams) {

        String className = serviceType.className()
                + "__TracedInterceptor"
                + (index != 0 ? "_" + index : "");

        TypeName generatedType = TypeName.builder()
                .packageName(serviceType.packageName())
                .className(className)
                .build();

        Annotation named = Annotation.builder()
                .typeName(ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED)
                .value(serviceType.fqName() + "." + element.signature().text())
                .build();

        var classModel = ClassModel.builder()
                .type(generatedType)
                .copyright(CodegenUtil.copyright(GENERATOR, GENERATOR, generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR, GENERATOR, generatedType, "0", ""))
                .addAnnotation(DeclarativeTypes.SINGLETON_ANNOTATION)
                .addAnnotation(named)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addInterface(ServiceCodegenTypes.INTERCEPTION_ELEMENT_INTERCEPTOR);

        classModel.addField(counterField -> counterField
                .type(TRACER)
                .name("tracer")
                .isFinal(true)
                .accessModifier(AccessModifier.PRIVATE));

        var ctr = Constructor.builder()
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT))
                .addParameter(tracer -> tracer
                        .type(TRACER)
                        .name("tracer")
                )
                .addContentLine("this.tracer = tracer;");

        classModel.addConstructor(ctr);

        TypeName typeV = TypeName.createFromGenericDeclaration("V");
        TypeName chainType = TypeName.builder(ServiceCodegenTypes.INTERCEPTION_CHAIN)
                .addTypeArgument(typeV)
                .build();

        var proceedMethod = Method.builder()
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
                .addThrows(thr -> thr.type(Exception.class));

        for (TracingExtension.TagParam tagParam : tagParams) {
            proceedMethod.addContent("var ")
                    .addContent(tagParam.name())
                    .addContent(" = (")
                    .addContent(tagParam.type())
                    .addContent(") args[")
                    .addContent(String.valueOf(tagParam.index()))
                    .addContentLine("];");
        }

        proceedMethod.addContentLine()
                .addContent("var span = tracer.spanBuilder(")
                .addContentLiteral(spanName)
                .addContentLine(")")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(".kind(")
                .addContent(SPAN_KIND)
                .addContent(".")
                .addContent(spanKind)
                .addContentLine(")");

        for (Map.Entry<String, String> tag : tags.entrySet()) {
            proceedMethod.addContent(".tag(")
                    .addContentLiteral(tag.getKey())
                    .addContent(", ")
                    .addContentLiteral(tag.getValue())
                    .addContentLine(")");
        }

        for (TracingExtension.TagParam tagParam : tagParams) {
            proceedMethod.addContent(".tag(")
                    .addContentLiteral(tagParam.name())
                    .addContent(", ")
                    .addContent(tagParam.name())
                    .addContentLine(")");
        }

        proceedMethod.addContentLine(".start();")
                .decreaseContentPadding()
                .decreaseContentPadding()
                .addContentLine("try {")
                .addContentLine("var tracing__response = chain.proceed(args);")
                .addContentLine("span.end();")
                .addContentLine("return tracing__response;")
                .addContent("} catch (")
                .addContent(Exception.class)
                .addContentLine(" e) {")
                .addContentLine("span.end(e);")
                .addContentLine("throw e;")
                .addContentLine("}");

        classModel.addMethod(proceedMethod);

        addToString(classModel, serviceType, element.signature());

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
                .addContent("return \"Tracing interceptor for ")
                .addContent(serviceType.fqName())
                .addContent(".")
                .addContent(signature.text())
                .addContentLine("\";")
        );
    }
}
