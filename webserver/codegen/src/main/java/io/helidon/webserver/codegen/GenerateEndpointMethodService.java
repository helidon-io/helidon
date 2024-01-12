package io.helidon.webserver.codegen;

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.inject.codegen.InjectCodegenTypes;

import static io.helidon.codegen.CodegenUtil.toConstantName;
import static io.helidon.webserver.codegen.WebServerCodegenExtension.GENERATOR;
import static io.helidon.webserver.codegen.WebServerCodegenTypes.HTTP_STATUS;
import static io.helidon.webserver.codegen.WebServerCodegenTypes.HTTP_STATUS_ANNOTATION;
import static io.helidon.webserver.codegen.WebServerCodegenTypes.SERVER_RESPONSE;

class GenerateEndpointMethodService {
    public static void generate(CodegenContext ctx, TypeInfo endpoint, MethodDef httpMethod) {
        TypeName generatedType = TypeName.builder()
                .packageName(endpoint.typeName().packageName())
                .className(httpMethod.className())
                .build();

        ClassModel.Builder methodService = ClassModel.builder()
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 endpoint.typeName(),
                                                 generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               endpoint.typeName(),
                                                               generatedType,
                                                               "1",
                                                               ""))
                .type(generatedType)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addAnnotation(Annotation.create(InjectCodegenTypes.INJECTION_REQUEST_SCOPE));

        // the endpoint and method parameters
        addFields(methodService, endpoint, httpMethod);
        // constructor injecting all the fields
        addConstructor(methodService, endpoint, httpMethod);
        addInvokeMethod(methodService, httpMethod);

        ctx.filer()
                .writeSourceFile(methodService.build(), endpoint.originatingElement().orElseGet(endpoint::typeName));
    }

    private static void addInvokeMethod(ClassModel.Builder methodService, MethodDef httpMethod) {
        methodService.addMethod(invoke -> invoke
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .name("invoke")
                .addParameter(res -> res
                        .type(SERVER_RESPONSE)
                        .name("helidonInject__serverResponse"))
                .update(it -> invokeMethodBody(methodService, it, httpMethod))
        );
    }

    private static void invokeMethodBody(ClassModel.Builder methodService, Method.Builder method, MethodDef httpMethod) {
        TypedElementInfo elementInfo = httpMethod.methodElement();

        if (!elementInfo.throwsChecked().isEmpty()) {
            elementInfo.throwsChecked().forEach(it -> method.addThrows(thrown -> thrown.type(it)));
        }

        boolean hasResponse = false;
        if (!elementInfo.typeName().boxed().equals(TypeNames.BOXED_VOID)) {
            method.addContent("var helidonInject__response = ");
            hasResponse = true;
        }

        List<ParamDef> params = httpMethod.params();
        method.addContent("helidonInject__endpoint.")
                    .addContent(httpMethod.methodName())
                .addContent("(");
        if (params.isEmpty()) {
            method.addContentLine(");");
        } else if (params.size() == 1) {
            method.addContent(params.getFirst().name())
                    .addContentLine(");");
        } else {
            // more than one parameter, multiline
            method.addContentLine("")
                    .increaseContentPadding()
                    .increaseContentPadding();
            Iterator<ParamDef> iterator = params.iterator();
            while (iterator.hasNext()) {
                ParamDef next = iterator.next();
                method.addContent(next.name());
                if (iterator.hasNext()) {
                    method.addContentLine(",");
                }
            }
            method.addContentLine(");")
                    .decreaseContentPadding()
                    .decreaseContentPadding();
        }

        if (elementInfo.hasAnnotation(HTTP_STATUS_ANNOTATION)) {
            Annotation statusAnnotation = elementInfo.annotation(HTTP_STATUS_ANNOTATION);
            int status = statusAnnotation
                    .intValue()
                    .orElse(200);
            String reason = statusAnnotation.stringValue("reason").filter(Predicate.not(String::isBlank))
                    .orElse(null);

            String constantName = toConstantName(httpMethod.methodName() + "Status");
            methodService.addField(statusField -> statusField
                    .accessModifier(AccessModifier.PRIVATE)
                    .isStatic(true)
                    .isFinal(true)
                    .type(HTTP_STATUS)
                    .name(constantName)
                    .addContent(HTTP_STATUS)
                    .addContent(".create(")
                    .addContent(String.valueOf(status))
                    .update(it -> {
                        if (reason != null) {
                            it.addContent(", \"" + reason + "\"");
                        }
                    })
                    .addContent(")")
            );
            method.addContent("helidonInject__serverResponse.status(")
                    .addContent(constantName)
                    .addContentLine(");");
        }

        method.addContent("helidonInject__serverResponse.send(");
        if (hasResponse) {
            // we consider the response to be an entity to be sent (unmodified) over the response
            method.addContent("helidonInject__response");
        }
        method.addContentLine(");");
    }

    private static void addConstructor(ClassModel.Builder methodService, TypeInfo endpoint, MethodDef httpMethod) {
        methodService.addConstructor(ctr -> ctr
                .addAnnotation(Annotation.create(InjectCodegenTypes.INJECTION_INJECT))
                .addParameter(param -> param
                        .type(endpoint.typeName())
                        .name("helidonInject__endpoint"))
                .addContentLine("this.helidonInject__endpoint = helidonInject__endpoint;")
                .update(it -> {
                    for (ParamDef param : httpMethod.params()) {
                        String paramName = param.name();
                        it.addParameter(methodParam -> methodParam
                                .type(param.type())
                                .name(paramName)
                                .update(ctrParam -> param.qualifiers().forEach(ctrParam::addAnnotation)));
                        it.addContent("this.")
                                .addContent(paramName)
                                .addContent(" = ")
                                .addContent(paramName)
                                .addContentLine(";");

                    }
                })
        );
    }

    private static void addFields(ClassModel.Builder methodService, TypeInfo endpoint, MethodDef httpMethod) {
        methodService.addField(endpointField -> endpointField
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(endpoint.typeName())
                .name("helidonInject__endpoint")
        );

        for (ParamDef param : httpMethod.params()) {
            methodService.addField(field -> field
                    .accessModifier(AccessModifier.PRIVATE)
                    .isFinal(true)
                    .type(param.type())
                    .name(param.name())
            );
        }
    }
}
