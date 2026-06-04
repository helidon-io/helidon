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

package io.helidon.declarative.codegen.http.webserver;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.DeclarativeTypes;
import io.helidon.declarative.codegen.http.HttpCodegenValidation;
import io.helidon.declarative.codegen.http.HttpFields;
import io.helidon.declarative.codegen.http.RestExtensionBase;
import io.helidon.declarative.codegen.http.webserver.spi.HttpParameterCodegenProvider;
import io.helidon.declarative.codegen.model.http.ComputedHeader;
import io.helidon.declarative.codegen.model.http.HeaderValue;
import io.helidon.declarative.codegen.model.http.HttpMethod;
import io.helidon.declarative.codegen.model.http.HttpStatus;
import io.helidon.declarative.codegen.model.http.RestMethod;
import io.helidon.declarative.codegen.model.http.RestMethodParameter;
import io.helidon.declarative.codegen.model.http.ServerEndpoint;
import io.helidon.service.codegen.FieldHandler;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceCodegenTypes;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

import static io.helidon.codegen.CodegenUtil.toConstantName;
import static io.helidon.declarative.codegen.DeclarativeTypes.SINGLETON_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_ENTITY_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_FORM_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_METHOD;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_REQUEST_PARAMS_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_SUPPORT;

/*
Generates:
- Endpoint__HttpFeature.java
 */
class RestServerExtension extends RestExtensionBase implements RegistryCodegenExtension {
    private static final TypeName GENERATOR = TypeName.create(RestServerExtension.class);
    private static final TypeName PARAMETERS = TypeName.create("io.helidon.common.parameters.Parameters");
    private static final String REQUEST_PARAM_NAME = "req";
    private static final String RESPONSE_PARAM_NAME = "res";
    private static final String METHOD_RESPONSE_NAME = "response";

    private final RegistryCodegenContext ctx;
    private final List<HttpParameterCodegenProvider> paramProviders;
    private final ServerEndpointAnalyzer endpointAnalyzer;

    RestServerExtension(RegistryCodegenContext ctx) {
        this.ctx = ctx;
        this.paramProviders = loadParamProviders(RestServerExtension.class.getClassLoader(), ctx);
        this.endpointAnalyzer = new ServerEndpointAnalyzer(ctx);
    }

    @Override
    public void process(RegistryRoundContext roundContext) {
        // for each @RestServer.Endpoint generate a service that implements it
        List<ServerEndpoint> endpoints = endpointAnalyzer.endpoints(roundContext);

        for (ServerEndpoint endpoint : endpoints) {
            process(roundContext, endpoint);
        }
    }

    static List<HttpParameterCodegenProvider> loadParamProviders(ClassLoader classLoader) {
        return loadParamProviders(classLoader, null);
    }

    static List<HttpParameterCodegenProvider> loadParamProviders(ClassLoader classLoader,
                                                                 RegistryCodegenContext ctx) {
        var builder = HelidonServiceLoader.builder(ServiceLoader.load(HttpParameterCodegenProvider.class, classLoader))
                .addService(new ParamProviderHttpEntity())
                .addService(new ParamProviderHttpHeader())
                .addService(new ParamProviderHttpCookie())
                .addService(new ParamProviderHttpPathParam())
                .addService(new ParamProviderHttpQuery())
                .addService(new ParamProviderHttpForm())
                .addService(new ParamProviderHttpReqRes())
                .addService(new ParamProviderSecurityContext())
                .addService(new ParamProviderContext());

        List<HttpParameterCodegenProvider> providers = builder.build().asList();
        if (ctx == null) {
            return providers;
        }

        var result = new ArrayList<HttpParameterCodegenProvider>();
        result.add(new ParamProviderHttpRequestParams(ctx, providers));
        result.addAll(providers);
        return List.copyOf(result);
    }

    private static String userVariableName(String userName) {
        return "u_" + userName;
    }

    private static void addSetupMethod(ClassModel.Builder endpointService, String path) {
        endpointService.addMethod(setup -> setup
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .name("setup")
                .addParameter(routing -> routing
                        .name("routing")
                        .type(WebServerCodegenTypes.SERVER_HTTP_ROUTING_BUILDER))
                .addContent("routing.register(\"")
                .addContent(path)
                .addContentLine("\", this::routing);"));
    }

    private Constructor.Builder constructor(TypeName endpoint,
                                            boolean singleton) {
        var constructor = Constructor.builder();
        constructor.accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT))
                .addParameter(param -> param
                        .type(singleton ? endpoint : supplierOf(endpoint))
                        .name("endpoint"))
                .addContentLine("this.endpoint = endpoint;");
        return constructor;
    }

    private void methodHandlers(ClassModel.Builder classModel,
                                Constructor.Builder constructor,
                                TypeName descriptorType,
                                List<RestMethod> methods) {
        // create constants with method metadata
        // create fields for each handler
        // create handlers in constructor

        constructor.addParameter(httpEntryPoints -> httpEntryPoints
                .type(WebServerCodegenTypes.DECLARATIVE_ENTRY_POINTS)
                .name("entryPoints")
        );

        constructor
                .addContent("var descriptor = ")
                .addContent(descriptorType)
                .addContentLine(".INSTANCE;");
        constructor
                .addContent("var annotations = ")
                .addContent(descriptorType)
                .addContentLine(".ANNOTATIONS;")
                .addContentLine();

        for (RestMethod method : methods) {
            String uniqueName = method.uniqueName();
            String constant = toConstantName("METHOD_" + uniqueName);
            String field = "handler_" + uniqueName;

            classModel.addField(handlerField -> handlerField
                    .accessModifier(AccessModifier.PRIVATE)
                    .isFinal(true)
                    .type(WebServerCodegenTypes.SERVER_HTTP_HANDLER)
                    .name(field)
            );

            constructor.addContent("this.")
                    .addContent(field)
                    .addContentLine(" = entryPoints.handler(")
                    .increaseContentPadding()
                    .increaseContentPadding()
                    .addContentLine("descriptor,")
                    .addContentLine("descriptor.qualifiers(),")
                    .addContentLine("annotations,")
                    .addContent(descriptorType)
                    .addContent(".")
                    .addContent(constant)
                    .addContentLine(",")
                    .addContent("this::")
                    .addContent(method.uniqueName())
                    .addContentLine(");")
                    .decreaseContentPadding()
                    .decreaseContentPadding();
        }
    }

    private void addFields(ClassModel.Builder endpointService, TypeName endpointType, boolean singleton) {
        endpointService.addField(endpointField -> endpointField
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(singleton ? endpointType : supplierOf(endpointType))
                .name("endpoint")
        );
    }

    private void process(RegistryRoundContext roundContext, ServerEndpoint endpoint) {
        TypeInfo type = endpoint.type();
        if (type.kind() == ElementKind.INTERFACE) {
            // interfaces are ignored, we must have an implementation
            return;
        }
        TypeName endpointTypeName = type.typeName();

        // we have all the necessary constants, time to build the implementation
        String classNameBase = endpointTypeName.classNameWithEnclosingNames().replace('.', '_');
        String className = classNameBase + "__HttpFeature";
        TypeName generatedType = TypeName.builder()
                .packageName(endpointTypeName.packageName())
                .className(className)
                .build();

        ClassModel.Builder classModel = ClassModel.builder()
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 endpointTypeName,
                                                 generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               endpointTypeName,
                                                               generatedType,
                                                               "1",
                                                               ""))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .type(generatedType)
                .addAnnotation(SINGLETON_ANNOTATION)
                .addAnnotation(DeclarativeTypes.SUPPRESS_API)
                .addInterface(WebServerCodegenTypes.SERVER_HTTP_FEATURE);

        boolean singleton = type.hasAnnotation(ServiceCodegenTypes.SERVICE_ANNOTATION_SINGLETON);
        // adds the endpoint field (may be a supplier)
        addFields(classModel, endpointTypeName, singleton);

        // constructor injecting the field(s)
        var constructor = constructor(endpointTypeName, singleton);

        FieldHandler fieldHandler = FieldHandler.create(classModel, constructor);

        methodHandlers(classModel, constructor, ctx.descriptorType(type.typeName()), endpoint.methods());
        // HttpFeature.setup(HttpRouting.Builder routing)
        addSetupMethod(classModel, endpoint.path().orElse("/"));
        // socket() and socketRequired()
        addSocketMethods(classModel, endpoint);
        // private void routing(HttpRules rules)
        addRoutingMethod(fieldHandler, classModel, endpoint);
        int methodIndex = 0;

        Map<String, String> headerProducerFields = headerProducers(fieldHandler, endpoint);
        for (RestMethod restMethod : endpoint.methods()) {
            addEndpointMethod(fieldHandler,
                              endpointTypeName,
                              classModel,
                              singleton,
                              restMethod,
                              headerProducerFields,
                              methodIndex);
            methodIndex++;
        }

        classModel.addConstructor(constructor);
        roundContext.addGeneratedType(generatedType, classModel, endpointTypeName, type.originatingElementValue());
    }

    private void addSocketMethods(ClassModel.Builder classModel, ServerEndpoint endpoint) {
        Optional<String> listener = endpoint.listener();
        if (listener.isEmpty()) {
            return;
        }
        classModel.addMethod(socket -> socket
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(TypeNames.STRING)
                .name("socket")
                .addAnnotation(Annotations.OVERRIDE)
                .addContent("return \"")
                .addContent(listener.get())
                .addContentLine("\";"));

        if (endpoint.listenerRequired()) {
            classModel.addMethod(socket -> socket
                    .accessModifier(AccessModifier.PUBLIC)
                    .returnType(TypeNames.PRIMITIVE_BOOLEAN)
                    .name("socketRequired")
                    .addAnnotation(Annotations.OVERRIDE)
                    .addContentLine("return true;"));
        }
    }

    @SuppressWarnings("checkstyle:ParameterNumber") // this is a private method
    private void addEndpointMethod(FieldHandler fieldHandler,
                                   TypeName endpointTypeName,
                                   ClassModel.Builder classModel,
                                   boolean singleton,
                                   RestMethod restMethod,
                                   Map<String, String> headerProducers,
                                   int methodIndex) {

        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PRIVATE)
                .name(restMethod.uniqueName())
                .addParameter(req -> req
                        .type(WebServerCodegenTypes.SERVER_REQUEST)
                        .name(REQUEST_PARAM_NAME))
                .addParameter(res -> res
                        .type(WebServerCodegenTypes.SERVER_RESPONSE)
                        .name(RESPONSE_PARAM_NAME))
                .update(it -> restMethod.method().throwsChecked()
                        .forEach(checked -> it.addThrows(thrown -> thrown.type(checked))))
                .update(it -> endpointMethodBody(
                        fieldHandler,
                        endpointTypeName,
                        classModel,
                        singleton,
                        it,
                        restMethod,
                        headerProducers,
                        methodIndex)));
    }

    @SuppressWarnings("checkstyle:ParameterNumber") // this is a private method
    private void endpointMethodBody(FieldHandler fieldHandler,
                                    TypeName endpointType,
                                    ClassModel.Builder classModel,
                                    boolean singleton,
                                    Method.Builder method,
                                    RestMethod restMethod,
                                    Map<String, String> headerProducers,
                                    int methodIndex) {
        validateBodyParameters(restMethod);
        if (methodUsesFormParams(restMethod)) {
            method.addContent("var ")
                    .addContent(ParamProviderHttpForm.FORM_PARAMS)
                    .addContent(" = ")
                    .addContent(HTTP_SUPPORT)
                    .addContentLine(".lazyFormParams(() -> req.content()")
                    .increaseContentPadding()
                    .increaseContentPadding()
                    .addContent(".asOptional(")
                    .addContent(PARAMETERS)
                    .addContentLine(".GENERIC_TYPE));")
                    .decreaseContentPadding()
                    .decreaseContentPadding();
        }

        // parameters
        for (RestMethodParameter parameter : restMethod.parameters()) {
            String paramName = userVariableName(parameter.name());
            method.addContent("var ")
                    .addContent(paramName)
                    .addContent(" = ");
            invokeParamHandler(fieldHandler,
                               endpointType,
                               classModel,
                               method,
                               restMethod,
                               parameter,
                               methodIndex);
            method.addContentLine();
        }

        boolean hasResponse = false;
        if (!restMethod.returnType().boxed().equals(TypeNames.BOXED_VOID)) {
            method.addContent("var ")
                    .addContent(METHOD_RESPONSE_NAME)
                    .addContent(" = ");
            hasResponse = true;
        }

        List<RestMethodParameter> params = restMethod.parameters();
        if (singleton) {
            method.addContent("this.endpoint.");
        } else {
            method.addContent("this.endpoint.get().");
        }
        method.addContent(restMethod.name())
                .addContent("(");
        if (params.isEmpty()) {
            method.addContentLine(");");
        } else if (params.size() == 1) {
            method.addContent(userVariableName(params.getFirst().name()))
                    .addContentLine(");");
        } else {
            // more than one parameter, multiline
            method.addContentLine()
                    .increaseContentPadding()
                    .increaseContentPadding();
            Iterator<RestMethodParameter> iterator = params.iterator();
            while (iterator.hasNext()) {
                RestMethodParameter next = iterator.next();
                method.addContent(userVariableName(next.name()));
                if (iterator.hasNext()) {
                    method.addContentLine(",");
                }
            }
            method.addContentLine(");")
                    .decreaseContentPadding()
                    .decreaseContentPadding();
        }

        if (restMethod.produces().size() == 1) {
            String mediaType = restMethod.produces().getFirst();
            if (!"*/*".equals(mediaType)) {
                method.addContent(RESPONSE_PARAM_NAME)
                        .addContent(".headers().contentType(")
                        .addContent(HttpFields.ensureHttpMediaTypeConstant(fieldHandler, mediaType))
                        .addContentLine(");");
            }
        }

        if (restMethod.status().isPresent()) {
            HttpStatus httpStatus = restMethod.status().get();

            method.addContent(RESPONSE_PARAM_NAME)
                    .addContent(".status(")
                    .addContent(HttpFields.ensureHttpStatusConstant(fieldHandler, httpStatus))
                    .addContentLine(");");
        }

        // now each header value, header producer, and header parameter
        for (HeaderValue header : restMethod.headers()) {
            method.addContent(RESPONSE_PARAM_NAME)
                    .addContent(".header(")
                    .addContent(HttpFields.ensureHeaderValueConstant(fieldHandler, header))
                    .addContentLine(");");
        }
        for (ComputedHeader computedHeader : restMethod.computedHeaders()) {
            String headerNameConstant = HttpFields.ensureHeaderNameConstant(fieldHandler, computedHeader.headerName());

            method.addContent(headerProducers.get(computedHeader.serviceName()))
                    .addContent(".apply(")
                    .addContent(headerNameConstant)
                    .addContent(").ifPresent(it -> ")
                    .addContent(RESPONSE_PARAM_NAME)
                    .addContent(".header(it));");
        }

        method.addContent(RESPONSE_PARAM_NAME)
                .addContent(".send(");
        if (hasResponse) {
            // we consider the response to be an entity to be sent (unmodified) over the response
            method.addContent(METHOD_RESPONSE_NAME);
        }
        method.addContentLine(");");
    }

    @SuppressWarnings("checkstyle:ParameterNumber") // this is a private method
    private void invokeParamHandler(FieldHandler fieldHandler,
                                    TypeName endpointType,
                                    ClassModel.Builder classModel,
                                    Method.Builder method,
                                    RestMethod restMethod,
                                    RestMethodParameter param,
                                    int methodIndex) {
        for (HttpParameterCodegenProvider paramProvider : paramProviders) {
            try {
                if (paramProvider.codegen(new ParamCodegenContextImpl(fieldHandler,
                                                                      param.annotations(),
                                                                      param.typeName(),
                                                                      classModel,
                                                                      method,
                                                                      REQUEST_PARAM_NAME,
                                                                      RESPONSE_PARAM_NAME,
                                                                      endpointType,
                                                                      restMethod.name(),
                                                                      param.name(),
                                                                      methodIndex,
                                                                      param.index()))) {
                    return;
                }
            } catch (Exception e) {
                throw new CodegenException("Failed to process parameter '" + param.typeName().resolvedName() + " "
                                                   + param.name() + "' that is " + (param.index() + 1) + " parameter of method "
                                                   + endpointType.fqName() + "." + restMethod.name()
                                                   + ", as the parameter handler ("
                                                   + paramProvider.getClass().getName() + ") threw an exception: "
                                                   + e.getMessage(),
                                           e);
            }
        }
        throw new CodegenException("Failed to process parameter '" + param.typeName().resolvedName() + " "
                                           + param.name() + "' that is " + (param.index() + 1) + " parameter of method "
                                           + endpointType.fqName() + "." + restMethod.name()
                                           + ", as there is no parameter handler registered for it.");
    }

    private void addRoutingMethod(FieldHandler fieldHandler,
                                  ClassModel.Builder classModel,
                                  ServerEndpoint endpoint) {

        classModel.addMethod(routing -> routing
                .accessModifier(AccessModifier.PRIVATE)
                .name("routing")
                .addParameter(rules -> rules
                        .type(WebServerCodegenTypes.SERVER_HTTP_RULES)
                        .name("rules"))
                .update(it -> routingMethodBody(
                        fieldHandler,
                        it,
                        endpoint)));
    }

    private void routingMethodBody(FieldHandler fieldHandler,
                                   Method.Builder method,
                                   ServerEndpoint endpoint) {

        for (RestMethod restMethod : endpoint.methods()) {
            if (restMethod.produces().isEmpty() && restMethod.consumes().isEmpty()) {
                addSimpleRoute(fieldHandler, method, restMethod);
            } else {
                addHttpRoute(fieldHandler, method, restMethod);
            }
        }
    }

    private void validateBodyParameters(RestMethod restMethod) {
        BodyParameters bodyParameters = bodyParameters(restMethod);
        if (bodyParameters.entityCount() > 1) {
            throw new CodegenException("Only one @Http.Entity parameter is supported on declarative server method "
                                               + restMethod.type().typeName().fqName() + "." + restMethod.name() + "().",
                                       bodyParameters.firstOriginatingElement());
        }
        if (bodyParameters.entityCount() > 0 && bodyParameters.formCount() > 0) {
            throw new CodegenException("@Http.Entity and @Http.FormParam cannot be combined on declarative server method "
                                               + restMethod.type().typeName().fqName() + "." + restMethod.name() + "().",
                                       bodyParameters.firstOriginatingElement());
        }
    }

    private boolean methodUsesFormParams(RestMethod restMethod) {
        return bodyParameters(restMethod).formCount() > 0;
    }

    private BodyParameters bodyParameters(RestMethod restMethod) {
        int entityCount = 0;
        int formCount = 0;
        Object firstOriginatingElement = restMethod.method().originatingElementValue();

        for (RestMethodParameter parameter : restMethod.parameters()) {
            if (HttpCodegenValidation.hasAnnotation(parameter.annotations(), HTTP_ENTITY_ANNOTATION)) {
                entityCount++;
                firstOriginatingElement = parameter.parameter().originatingElementValue();
            }
            if (HttpCodegenValidation.hasAnnotation(parameter.annotations(), HTTP_FORM_PARAM_ANNOTATION)) {
                formCount++;
                firstOriginatingElement = parameter.parameter().originatingElementValue();
            }
            if (HttpCodegenValidation.hasAnnotation(parameter.annotations(), HTTP_REQUEST_PARAMS_ANNOTATION)) {
                TypeInfo requestParamsType = HttpCodegenValidation.requestParamsRecordType(
                        ctx::typeInfo,
                        parameter.typeName(),
                        parameter.parameter().originatingElementValue());
                HttpCodegenValidation.validateRequestParamsBodyComponents(requestParamsType);
                for (TypedElementInfo component : HttpCodegenValidation.requestParamsComponents(requestParamsType)) {
                    if (HttpCodegenValidation.hasAnnotation(component.annotations(), HTTP_ENTITY_ANNOTATION)) {
                        entityCount++;
                        firstOriginatingElement = component.originatingElementValue();
                    }
                    if (HttpCodegenValidation.hasAnnotation(component.annotations(), HTTP_FORM_PARAM_ANNOTATION)) {
                        formCount++;
                        firstOriginatingElement = component.originatingElementValue();
                    }
                }
            }
        }

        return new BodyParameters(entityCount, formCount, firstOriginatingElement);
    }

    private void addSimpleRoute(FieldHandler fieldHandler, Method.Builder routing, RestMethod restMethod) {
        routing.addContent("rules.");

        HttpMethod httpMethod = restMethod.httpMethod();
        if (httpMethod.builtIn()) {
            routing.addContent(httpMethod.name().toLowerCase(Locale.ROOT))
                    .addContent("(");
        } else {
            routing.addContent("route(" + HttpFields.ensureHttpMethodConstant(fieldHandler, httpMethod.name()) + ", ");
        }

        String path = restMethod.path().orElse("/");

        routing.addContent("\"")
                .addContent(path)
                .addContent("\", ");

        routing.addContent("handler_")
                .addContent(restMethod.uniqueName())
                .addContentLine(");");
    }

    private void addHttpRoute(FieldHandler fieldHandler,
                              Method.Builder routing,
                              RestMethod restMethod) {
        routing.addContent("rules.route(")
                .addContent(WebServerCodegenTypes.SERVER_HTTP_ROUTE)
                .addContentLine(".builder()")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(".methods(");

        HttpMethod httpMethod = restMethod.httpMethod();
        if (httpMethod.builtIn()) {
            routing.addContent(HTTP_METHOD)
                    .addContent(".")
                    .addContent(httpMethod.name());
        } else {
            routing.addContent(HttpFields.ensureHttpMethodConstant(fieldHandler, httpMethod.name()));
        }

        routing.addContentLine(")"); // end of methods(Method.GET)

        boolean consumesExists = !restMethod.consumes().isEmpty();

        routing.addContent(".headers(headers -> ");

        if (consumesExists) {
            routing.addContent("headers.testContentType(");
            routing.addContent(restMethod.consumes()
                                       .stream()
                                       .map(it -> HttpFields.ensureHttpMediaTypeConstant(fieldHandler, it))
                                       .collect(Collectors.joining(", ")));
            routing.addContent(")");
        }

        if (!restMethod.produces().isEmpty()) {
            if (consumesExists) {
                routing.addContent(" && ");
            }
            routing.addContent("headers.isAccepted(");
            routing.addContent(restMethod.produces()
                                       .stream()
                                       .map(it -> HttpFields.ensureHttpMediaTypeConstant(fieldHandler, it))
                                       .collect(Collectors.joining(", ")));
            routing.addContent(")");
        }

        routing.addContentLine(")");

        String path = restMethod.path().orElse("/");
        routing.addContent(".path(\"")
                .addContent(path)
                .addContentLine("\")")
                .addContent(".handler(handler_")
                .addContent(restMethod.uniqueName())
                .addContentLine(")");

        routing.addContentLine(".build());")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private record BodyParameters(int entityCount,
                                  int formCount,
                                  Object firstOriginatingElement) {
    }
}
