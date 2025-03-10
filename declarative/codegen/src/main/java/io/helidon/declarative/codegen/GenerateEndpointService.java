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

package io.helidon.declarative.codegen;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
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
import io.helidon.service.codegen.FieldHandler;

import static io.helidon.codegen.CodegenUtil.toConstantName;
import static io.helidon.declarative.codegen.DeclarativeCodegenTypes.COMMON_CONTEXT;
import static io.helidon.declarative.codegen.DeclarativeCodegenTypes.HTTP_METHOD;
import static io.helidon.declarative.codegen.DeclarativeCodegenTypes.HTTP_STATUS;
import static io.helidon.declarative.codegen.DeclarativeCodegenTypes.HTTP_STATUS_ANNOTATION;
import static io.helidon.declarative.codegen.DeclarativeCodegenTypes.INJECT_SCOPE;
import static io.helidon.declarative.codegen.DeclarativeCodegenTypes.MEDIA_TYPE;
import static io.helidon.declarative.codegen.DeclarativeCodegenTypes.MEDIA_TYPES;
import static io.helidon.declarative.codegen.DeclarativeCodegenTypes.SERVER_HTTP_ROUTE;
import static io.helidon.declarative.codegen.DeclarativeCodegenTypes.SERVER_HTTP_ROUTING_BUILDER;
import static io.helidon.declarative.codegen.DeclarativeCodegenTypes.SERVER_HTTP_RULES;
import static io.helidon.declarative.codegen.DeclarativeCodegenTypes.SERVER_REQUEST;
import static io.helidon.declarative.codegen.DeclarativeCodegenTypes.SERVER_RESPONSE;
import static io.helidon.declarative.codegen.DeclarativeCodegenTypes.SERVICE_CONTEXT;
import static io.helidon.declarative.codegen.DeclarativeCodegenTypes.SERVICE_HEADERS;
import static io.helidon.declarative.codegen.DeclarativeCodegenTypes.SERVICE_PROLOGUE;
import static io.helidon.declarative.codegen.DeclarativeCodegenTypes.SERVICE_SERVER_REQUEST;
import static io.helidon.declarative.codegen.DeclarativeCodegenTypes.SERVICE_SERVER_RESPONSE;
import static io.helidon.declarative.codegen.WebServerCodegenExtension.GENERATOR;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_SINGLETON;

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

    static void generate(CodegenContext ctx,
                         TypeInfo endpoint,
                         String classNameBase,
                         String path,
                         List<MethodDef> httpMethods) {
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
                .addAnnotation(Annotation.create(SERVICE_ANNOTATION_SINGLETON))
                .addInterface(DeclarativeCodegenTypes.SERVER_HTTP_FEATURE);

        boolean singleton = endpoint.hasAnnotation(SERVICE_ANNOTATION_SINGLETON);

        // the endpoint itself
        addFields(endpointService, endpoint.typeName(), singleton);
        // constructor injecting the fields
        Constructor.Builder constructor = addConstructor(endpoint.typeName(), singleton);

        FieldHandler fieldHandler = FieldHandler.create(endpointService, constructor);
        // HttpFeature.setup(HttpRouting.Builder routing)
        addSetupMethod(endpointService, path);
        // private void routing(HttpRules rules)
        addRoutingMethod(endpointService, fieldHandler, httpMethods);

        // now each method
        int methodIndex = 0;
        for (MethodDef httpMethod : httpMethods) {
            addEndpointMethod(endpoint.typeName(),
                              endpointService,
                              fieldHandler,
                              singleton,
                              httpMethod,
                              methodIndex);
            methodIndex++;
        }

        endpointService.addConstructor(constructor);

        ctx.filer()
                .writeSourceFile(endpointService.build(), endpoint.originatingElement().orElseGet(endpoint::typeName));
    }

    private static Constructor.Builder addConstructor(TypeName endpoint, boolean singleton) {
        return Constructor.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addAnnotation(Annotation.create(SERVICE_ANNOTATION_INJECT))
                .addParameter(param -> param
                        .type(singleton ? endpoint : supplierOf(endpoint))
                        .name("endpoint"))
                .addContentLine("this.endpoint = endpoint;");
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

    private static void addEndpointMethod(TypeName endpointType,
                                          ClassModel.Builder endpointService,
                                          FieldHandler fieldHandler,
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
                .update(it -> endpointMethodBody(endpointType,
                                                 endpointService,
                                                 fieldHandler,
                                                 singleton,
                                                 it,
                                                 httpMethod,
                                                 methodIndex)));
    }

    private static void endpointMethodBody(TypeName endpointType,
                                           ClassModel.Builder classModel,
                                           FieldHandler fieldHandler,
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
            invokeParamHandler(endpointType, classModel, fieldHandler, method, httpMethod, param, methodIndex, paramIndex);
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

        if (httpMethod.producesMediaTypes().size() == 1) {
            String mediaType = httpMethod.producesMediaTypes().getFirst();
            if (!"*/*".equals(mediaType)) {
                String constantName = ensureMediaTypeConstant(fieldHandler, mediaType);
                method.addContent(RESPONSE_PARAM_NAME)
                        .addContent(".headers().contentType(")
                        .addContent(constantName)
                        .addContentLine(");");
            }
        }

        if (elementInfo.hasAnnotation(HTTP_STATUS_ANNOTATION)) {
            Annotation statusAnnotation = elementInfo.annotation(HTTP_STATUS_ANNOTATION);
            int status = statusAnnotation
                    .intValue()
                    .orElse(200);
            String reason = statusAnnotation.stringValue("reason").filter(Predicate.not(String::isBlank))
                    .orElse(null);

            String constantName = fieldHandler.constant("STATUS_" + status,
                                                        HTTP_STATUS,
                                                        status + (reason == null ? "" : "-" + reason),
                                                        content -> {
                                                            content.addContent(HTTP_STATUS)
                                                                    .addContent(".create(")
                                                                    .addContent(String.valueOf(status));

                                                            if (reason != null) {
                                                                content.addContent(", \"" + reason + "\"");
                                                            }
                                                            content.addContent(")");
                                                        });

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

    private static void invokeParamHandler(TypeName endpointType,
                                           ClassModel.Builder classModel,
                                           FieldHandler fieldHandler,
                                           Method.Builder method,
                                           MethodDef httpMethod,
                                           ParamDef param,
                                           int methodIndex,
                                           int paramIndex) {
        for (HttpParameterCodegenProvider paramProvider : PARAM_PROVIDERS) {
            try {
                if (paramProvider.codegen(new ParamCodegenContextImpl(fieldHandler,
                                                                      param.qualifiers(),
                                                                      param.annotations(),
                                                                      param.type(),
                                                                      classModel,
                                                                      method,
                                                                      REQUEST_PARAM_NAME,
                                                                      RESPONSE_PARAM_NAME,
                                                                      endpointType,
                                                                      httpMethod.methodName(),
                                                                      param.name(),
                                                                      methodIndex,
                                                                      paramIndex))) {
                    return;
                }
            } catch (Exception e) {
                throw new CodegenException("Failed to process parameter '" + param.type().resolvedName() + " " + param.name()
                                                   + "' that is " + (paramIndex + 1) + " parameter of method "
                                                   + endpointType.fqName() + "." + httpMethod.methodName()
                                                   + ", as the parameter handler ("
                                                   + paramProvider.getClass().getName() + ") threw an exception.",
                                           e);
            }
        }
        throw new CodegenException("Failed to process parameter '" + param.type().resolvedName() + " " + param.name()
                                           + "' that is " + (paramIndex + 1) + ". parameter of method "
                                           + endpointType.fqName() + "." + httpMethod.methodName()
                                           + ", as there is no parameter handler registered for it.");
    }

    private static void addRoutingMethod(ClassModel.Builder endpointService,
                                         FieldHandler fieldHandler,
                                         List<MethodDef> httpMethods) {

        endpointService.addMethod(routing -> routing
                .accessModifier(AccessModifier.PRIVATE)
                .name("routing")
                .addParameter(rules -> rules
                        .type(SERVER_HTTP_RULES)
                        .name("rules"))
                .update(it -> httpMethods.forEach(methodDef -> addRoutingMethodContent(endpointService,
                                                                                       fieldHandler,
                                                                                       it,
                                                                                       methodDef)))
        );
    }

    private static void addRoutingMethodContent(ClassModel.Builder endpointService,
                                                FieldHandler fieldHandler,
                                                Method.Builder routing,
                                                MethodDef methodDef) {

        if (methodDef.producesMediaTypes().isEmpty() && methodDef.consumesMediaTypes().isEmpty()) {
            addSimpleRoute(endpointService, fieldHandler, routing, methodDef);
        } else {
            addHttpRoute(endpointService, fieldHandler, routing, methodDef);
        }
    }

    private static void addHttpRoute(ClassModel.Builder endpointService,
                                     FieldHandler fieldHandler,
                                     Method.Builder routing,
                                     MethodDef methodDef) {
        routing.addContent("rules.route(")
                .addContent(SERVER_HTTP_ROUTE)
                .addContentLine(".builder()")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(".methods(");

        String httpMethod = methodDef.httpMethod();
        switch (httpMethod) {
        case "GET", "POST", "PUT", "DELETE", "TRACE", "HEAD":
            routing.addContent(HTTP_METHOD)
                    .addContent(".")
                    .addContent(httpMethod);
            break;
        default:
            String methodConstant = ensureHttpMethodConstant(fieldHandler, httpMethod);
            routing.addContent(methodConstant);
            break;
        }
        routing.addContentLine(")"); // end of methods(Method.GET)

        boolean consumesExists = !methodDef.consumesMediaTypes().isEmpty();

        routing.addContent(".headers(headers -> ");

        if (consumesExists) {
            routing.addContent("headers.testContentType(");
            routing.addContent(methodDef.consumesMediaTypes()
                                       .stream()
                                       .map(it -> ensureMediaTypeConstant(fieldHandler, it))
                                       .collect(Collectors.joining(", ")));
            routing.addContent(")");
        }

        if (!methodDef.producesMediaTypes().isEmpty()) {
            if (consumesExists) {
                routing.addContent(" && ");
            }
            routing.addContent("headers.isAccepted(");
            routing.addContent(methodDef.producesMediaTypes()
                                       .stream()
                                       .map(it -> ensureMediaTypeConstant(fieldHandler, it))
                                       .collect(Collectors.joining(", ")));
            routing.addContent(")");
        }

        routing.addContentLine(")");

        String path = methodDef.path().orElse("/");
        routing.addContent(".path(\"")
                .addContent(path)
                .addContentLine("\")")
                .addContent(".handler(this::")
                .addContent(methodDef.serviceMethod())
                .addContentLine(")");

        routing.addContentLine(".build());")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private static void addSimpleRoute(ClassModel.Builder endpointService,
                                       FieldHandler fieldHandler,
                                       Method.Builder routing,
                                       MethodDef methodDef) {
        routing.addContent("rules.");

        String httpMethod = methodDef.httpMethod();
        switch (httpMethod) {
        case "GET", "POST", "PUT", "DELETE", "TRACE", "HEAD":
            routing.addContent(httpMethod.toLowerCase(Locale.ROOT))
                    .addContent("(");
            break;
        default:
            String methodConstant = ensureHttpMethodConstant(fieldHandler, httpMethod);
            routing.addContent("route(" + methodConstant + ", ");
            break;
        }

        String path = methodDef.path().orElse("/");

        routing.addContent("\"")
                .addContent(path)
                .addContent("\", ");

        routing.addContent("this::")
                .addContent(methodDef.serviceMethod())
                .addContentLine(");");
    }

    private static String ensureMediaTypeConstant(FieldHandler fieldHandler,
                                                  String mediaType) {

        return fieldHandler.constant("MEDIA_TYPE",
                                     MEDIA_TYPE,
                                     mediaType,
                                     content -> content
                                             .addContent(MEDIA_TYPES)
                                             .addContent(".create(\"")
                                             .addContent(mediaType)
                                             .addContent("\")"));
    }

    private static String ensureHttpMethodConstant(FieldHandler fieldHandler,
                                                   String httpMethod) {
        String constantName = toConstantName("ENDPOINT_METHOD_" + httpMethod);
        return fieldHandler.constant(constantName,
                                     HTTP_METHOD,
                                     httpMethod.toUpperCase(Locale.ROOT),
                                     content -> content.addContent(HTTP_METHOD)
                                             .addContent(".create(\"")
                                             .addContent(httpMethod)
                                             .addContent("\""));
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
