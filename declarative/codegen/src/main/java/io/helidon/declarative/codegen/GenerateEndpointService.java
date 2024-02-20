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

package io.helidon.declarative.codegen;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.spi.HttpParameterCodegenProvider;
import io.helidon.service.codegen.ServiceCodegenTypes;

import static io.helidon.codegen.CodegenUtil.toConstantName;
import static io.helidon.declarative.codegen.WebServerCodegenExtension.GENERATOR;
import static io.helidon.declarative.codegen.WebServerCodegenTypes.COMMON_CONTEXT;
import static io.helidon.declarative.codegen.WebServerCodegenTypes.HTTP_METHOD;
import static io.helidon.declarative.codegen.WebServerCodegenTypes.HTTP_STATUS;
import static io.helidon.declarative.codegen.WebServerCodegenTypes.HTTP_STATUS_ANNOTATION;
import static io.helidon.declarative.codegen.WebServerCodegenTypes.INJECT_SCOPE;
import static io.helidon.declarative.codegen.WebServerCodegenTypes.SERVER_HTTP_ROUTING_BUILDER;
import static io.helidon.declarative.codegen.WebServerCodegenTypes.SERVER_HTTP_RULES;
import static io.helidon.declarative.codegen.WebServerCodegenTypes.SERVER_REQUEST;
import static io.helidon.declarative.codegen.WebServerCodegenTypes.SERVER_RESPONSE;
import static io.helidon.declarative.codegen.WebServerCodegenTypes.SERVICE_CONTEXT;
import static io.helidon.declarative.codegen.WebServerCodegenTypes.SERVICE_HEADERS;
import static io.helidon.declarative.codegen.WebServerCodegenTypes.SERVICE_PROLOGUE;
import static io.helidon.declarative.codegen.WebServerCodegenTypes.SERVICE_SERVER_REQUEST;
import static io.helidon.declarative.codegen.WebServerCodegenTypes.SERVICE_SERVER_RESPONSE;

final class GenerateEndpointService {

    private static final String REQUEST_PARAM_NAME = "helidonDeclarative__server_req";
    private static final String RESPONSE_PARAM_NAME = "helidonDeclarative__server_res";
    private static final String METHOD_RESPONSE_NAME = "helidonDeclarative__response";

    private static final List<HttpParameterCodegenProvider> PARAM_PROVIDERS =
            HelidonServiceLoader.builder(ServiceLoader.load(HttpParameterCodegenProvider.class))
                    .addService(new HttpEntityParamProvider())
                    .addService(new HttpHeaderParamProvider())
                    .addService(new HttpPathParamProvider())
                    .addService(new HttpQueryParamProvider())
                    .addService(new ServerReqResParamProvider())
                    .build()
                    .asList();

    private GenerateEndpointService() {
    }

    static void generate(CodegenContext ctx, TypeInfo endpoint, String classNameBase, String path, List<MethodDef> httpMethods) {
        String endpointServiceName = classNameBase + "__HttpFeature";

        TypeName generatedType = TypeName.builder()
                .packageName(endpoint.typeName().packageName())
                .className(endpointServiceName)
                .build();

        ClassModel.Builder endpointService = ClassModel.builder()
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
                .addAnnotation(Annotation.create(ServiceCodegenTypes.INJECTION_SINGLETON))
                .addInterface(WebServerCodegenTypes.SERVER_HTTP_FEATURE);

        boolean singleton = endpoint.hasAnnotation(ServiceCodegenTypes.INJECTION_SINGLETON);

        // the endpoint itself
        addFields(endpointService, endpoint.typeName(), singleton);
        // constructor injecting the fields
        addConstructor(endpointService, endpoint.typeName(), singleton);
        // HttpFeature.setup(HttpRouting.Builder routing)
        addSetupMethod(endpointService, path);
        // private void routing(HttpRules rules)
        addRoutingMethod(endpointService, httpMethods);

        // now each method
        int methodIndex = 0;
        for (MethodDef httpMethod : httpMethods) {
            addEndpointMethod(endpointService, singleton, httpMethod, methodIndex);
            methodIndex++;
        }

        ctx.filer()
                .writeSourceFile(endpointService.build(), endpoint.originatingElement().orElseGet(endpoint::typeName));
    }

    private static void addConstructor(ClassModel.Builder endpointService, TypeName endpoint, boolean singleton) {
        endpointService.addConstructor(ctr -> ctr
                .addAnnotation(Annotation.create(ServiceCodegenTypes.INJECTION_INJECT))
                .addParameter(param -> param
                        .type(singleton ? endpoint : supplierOf(endpoint))
                        .name("endpoint"))
                .addContentLine("this.endpoint = endpoint;")
        );
    }

    private static void addFields(ClassModel.Builder endpointService, TypeName endpointType, boolean singleton) {
        endpointService.addField(endpointField -> endpointField
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(singleton ? endpointType : supplierOf(endpointType))
                .name("endpoint")
        );
    }

    private static TypeName supplierOf(TypeName endpointType) {
        return TypeName.builder(TypeNames.SUPPLIER)
                .addTypeArgument(endpointType)
                .build();
    }

    private static void addEndpointMethod(ClassModel.Builder endpointService,
                                          boolean singleton,
                                          MethodDef httpMethod,
                                          int methodIndex) {
        endpointService.addMethod(method -> method
                .accessModifier(AccessModifier.PRIVATE)
                .name(httpMethod.serviceMethod())
                .addParameter(req -> req
                        .type(SERVER_REQUEST)
                        .name(REQUEST_PARAM_NAME))
                .addParameter(res -> res
                        .type(SERVER_RESPONSE)
                        .name(RESPONSE_PARAM_NAME))
                .update(it -> httpMethod.methodElement().throwsChecked()
                        .forEach(checked -> it.addThrows(thrown -> thrown.type(checked))))
                .update(it -> endpointMethodBody(endpointService, singleton, it, httpMethod, methodIndex)));
    }

    private static void endpointMethodBody(ClassModel.Builder classModel,
                                           boolean singleton,
                                           Method.Builder method,
                                           MethodDef httpMethod,
                                           int methodIndex) {
        TypedElementInfo elementInfo = httpMethod.methodElement();

        // first get all parameters
        int paramIndex = 0;
        for (ParamDef param : httpMethod.params()) {
            String paramName = param.name();
            method.addContent("var ")
                    .addContent(paramName)
                    .addContent(" = ");
            invokeParamHandler(classModel, method, param, methodIndex, paramIndex);
            method.addContentLine("");
            paramIndex++;
        }

        boolean hasResponse = false;
        if (!elementInfo.typeName().boxed().equals(TypeNames.BOXED_VOID)) {
            method.addContent("var ")
                    .addContent(METHOD_RESPONSE_NAME)
                    .addContent(" = ");
            hasResponse = true;
        }

        List<ParamDef> params = httpMethod.params();
        if (singleton) {
            method.addContent("this.endpoint.");
        } else {
            method.addContent("this.endpoint.get().");
        }
        method.addContent(httpMethod.methodName())
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

            String constantName = toConstantName(httpMethod.methodName() + "Status_" + methodIndex);
            classModel.addField(statusField -> statusField
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
            method.addContent(RESPONSE_PARAM_NAME)
                    .addContent(".status(")
                    .addContent(constantName)
                    .addContentLine(");");
        }

        method.addContent(RESPONSE_PARAM_NAME)
                .addContent(".send(");
        if (hasResponse) {
            // we consider the response to be an entity to be sent (unmodified) over the response
            method.addContent(METHOD_RESPONSE_NAME);
        }
        method.addContentLine(");");

    }

    private static void invokeParamHandler(ClassModel.Builder classModel,
                                           Method.Builder method,
                                           ParamDef param,
                                           int methodIndex,
                                           int paramIndex) {
        for (HttpParameterCodegenProvider paramProvider : PARAM_PROVIDERS) {
            if (paramProvider.codegen(new ParamCodegenContextImpl(param.qualifiers(),
                                                                  param.type(),
                                                                  classModel,
                                                                  method,
                                                                  REQUEST_PARAM_NAME,
                                                                  RESPONSE_PARAM_NAME,
                                                                  methodIndex,
                                                                  paramIndex))) {
                return;
            }
        }
    }

    private static void addRoutingMethod(ClassModel.Builder endpointService, List<MethodDef> httpMethods) {
        // we must add each constant just once, this is to keep track of them
        Set<String> addedHttpMethods = new HashSet<>();
        endpointService.addMethod(routing -> routing
                .accessModifier(AccessModifier.PRIVATE)
                .name("routing")
                .addParameter(rules -> rules
                        .type(SERVER_HTTP_RULES)
                        .name("rules"))
                .update(it -> httpMethods.forEach(methodDef -> addRoutingMethodContent(endpointService,
                                                                                       it,
                                                                                       methodDef,
                                                                                       addedHttpMethods)))
        );
    }

    private static void addRoutingMethodContent(ClassModel.Builder endpointService,
                                                Method.Builder routing,
                                                MethodDef methodDef,
                                                Set<String> addedHttpMethods) {

        routing.addContent("rules.");

        String httpMethod = methodDef.httpMethod();
        switch (httpMethod) {
        case "GET", "POST", "PUT", "DELETE", "TRACE", "HEAD":
            routing.addContent(httpMethod.toLowerCase(Locale.ROOT))
                    .addContent("(");
            break;
        default:
            if (addedHttpMethods.add(httpMethod)) {
                endpointService.addField(httpMethodField -> httpMethodField
                        .accessModifier(AccessModifier.PRIVATE)
                        .isStatic(true)
                        .isFinal(true)
                        .type(HTTP_METHOD)
                        .name("ENDPOINT_METHOD_" + httpMethod)
                        .addContent(HTTP_METHOD)
                        .addContent(".create(\"")
                        .addContent(httpMethod)
                        .addContent("\"")
                );
            }
            routing.addContent("route(ENDPOINT_METHOD_" + httpMethod + ", ");
            break;
        }

        if (methodDef.path().isPresent()) {
            routing.addContent("\"")
                    .addContent(methodDef.path().get())
                    .addContent("\", ");
        }

        routing.addContent("this::")
                .addContent(methodDef.serviceMethod())
                .addContentLine(");");
    }

    private static void addSetupMethod(ClassModel.Builder endpointService, String path) {
        endpointService.addMethod(setup -> setup
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .name("setup")
                .addParameter(routing -> routing
                        .name("routing")
                        .type(SERVER_HTTP_ROUTING_BUILDER))
                .addContent("routing.register(\"")
                .addContent(path)
                .addContentLine("\", this::routing);"));
    }

    private static void addStartRequestScopeMethod(ClassModel.Builder endpointService) {
        // this method is always the same in all generated endpoints, we can investigate if there is a good place
        // to put it as a shared method
        endpointService.addMethod(method -> method
                .accessModifier(AccessModifier.PRIVATE)
                .returnType(INJECT_SCOPE)
                .name("startRequestScope")
                .addParameter(req -> req
                        .name("req")
                        .type(SERVER_REQUEST))
                .addParameter(req -> req
                        .name("res")
                        .type(SERVER_RESPONSE))
                .addContentLine("return requestCtrl.startRequestScope(\"http_\" + req.id(),")
                .increaseContentPadding()
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(TypeNames.MAP)
                .addContentLine(".of(")
                .increaseContentPadding()
                .addContent(SERVICE_CONTEXT)
                .addContent(".INSTANCE, (")
                .addContent(TypeNames.SUPPLIER)
                .addContent("<")
                .addContent(COMMON_CONTEXT)
                .addContentLine(">) req::context,")
                .update(it -> addInitialBinding(it, SERVICE_PROLOGUE, "req.prologue(),"))
                .update(it -> addInitialBinding(it, SERVICE_HEADERS, "req.headers(),"))
                .update(it -> addInitialBinding(it, SERVICE_SERVER_REQUEST, "req,"))
                .update(it -> addInitialBinding(it, SERVICE_SERVER_RESPONSE, "res"))
                .decreaseContentPadding()
                .addContentLine("));")
        );
    }

    private static void addInitialBinding(Method.Builder method, TypeName serviceTypeName, String content) {
        method.addContent(serviceTypeName)
                .addContent(".INSTANCE, ")
                .addContentLine(content);
    }
}
