/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.http.restclient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.TypeHierarchy;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.Parameter;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.DeclarativeTypes;
import io.helidon.declarative.codegen.DeclarativeUtils;
import io.helidon.declarative.codegen.DelcarativeConfigSupport;
import io.helidon.declarative.codegen.http.HttpCodegenValidation;
import io.helidon.declarative.codegen.http.HttpFields;
import io.helidon.declarative.codegen.http.RestExtensionBase;
import io.helidon.declarative.codegen.model.http.ClientEndpoint;
import io.helidon.declarative.codegen.model.http.ComputedHeader;
import io.helidon.declarative.codegen.model.http.HeaderValue;
import io.helidon.declarative.codegen.model.http.RestMethod;
import io.helidon.declarative.codegen.model.http.RestMethodParameter;
import io.helidon.service.codegen.FieldHandler;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

import static io.helidon.declarative.codegen.DeclarativeTypes.CONFIG;
import static io.helidon.declarative.codegen.DeclarativeTypes.SINGLETON_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_COOKIE_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_ENTITY_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_FORM_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_HEADER_NAMES;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_HEADER_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_METHOD_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_PATH_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_QUERY_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_REQUEST_PARAMS_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_SUPPORT;
import static io.helidon.declarative.codegen.http.restclient.RestClientTypes.REST_CLIENT_COMPUTED_HEADER;
import static io.helidon.declarative.codegen.http.restclient.RestClientTypes.REST_CLIENT_COMPUTED_HEADERS;
import static io.helidon.declarative.codegen.http.restclient.RestClientTypes.REST_CLIENT_ENDPOINT;
import static io.helidon.declarative.codegen.http.restclient.RestClientTypes.REST_CLIENT_ERROR_HANDLING;
import static io.helidon.declarative.codegen.http.restclient.RestClientTypes.REST_CLIENT_HEADER;
import static io.helidon.declarative.codegen.http.restclient.RestClientTypes.REST_CLIENT_HEADERS;
import static io.helidon.declarative.codegen.http.restclient.RestClientTypes.REST_CLIENT_QUALIFIER_INSTANCE;
import static io.helidon.declarative.codegen.http.restclient.RestClientTypes.WEB_CLIENT;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED;
import static java.util.function.Predicate.not;

class RestClientExtension extends RestExtensionBase implements RegistryCodegenExtension {
    static final TypeName GENERATOR = TypeName.create(RestClientExtension.class);

    private final RegistryCodegenContext ctx;

    RestClientExtension(RegistryCodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RegistryRoundContext roundContext) {
        // for each @RestClient.Endpoint generate a service that implements it
        Collection<TypeInfo> clientApis = roundContext.annotatedTypes(RestClientTypes.REST_CLIENT_ENDPOINT);

        List<ClientEndpoint> endpoints = clientApis.stream()
                .map(this::toEndpoint)
                .collect(Collectors.toUnmodifiableList());

        for (ClientEndpoint endpoint : endpoints) {
            process(roundContext, endpoint);
        }
    }

    private ClientEndpoint toEndpoint(TypeInfo typeInfo) {
        var builder = ClientEndpoint.builder()
                .type(typeInfo);

        Set<Annotation> typeAnnotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx, typeInfo));
        builder.annotations(typeAnnotations);

        Annotations.findFirst(REST_CLIENT_ENDPOINT, typeAnnotations)
                .ifPresent(endpoint -> {
                    endpoint.stringValue()
                            .filter(not(String::isBlank))
                            .ifPresent(builder::uri);
                    builder.configKey(endpoint.stringValue("configKey")
                                              .filter(not(String::isBlank))
                                              .orElseGet(() -> typeInfo.typeName().fqName()));

                    endpoint.stringValue("clientName")
                            .filter(not(String::isBlank))
                            .ifPresent(builder::clientName);
                });

        path(typeAnnotations, builder);
        produces(typeAnnotations, builder);
        consumes(typeAnnotations, builder);
        headers(typeAnnotations, builder, REST_CLIENT_HEADERS, REST_CLIENT_HEADER);
        computedHeaders(typeAnnotations, builder, REST_CLIENT_COMPUTED_HEADERS, REST_CLIENT_COMPUTED_HEADER);

        Map<MethodSignature, MethodOrigin> discoveredMethods = new LinkedHashMap<>();

        typeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(not(ElementInfoPredicates::isStatic))
                .filter(not(ElementInfoPredicates::isDefault))
                .forEach(it -> discoveredMethods.put(MethodSignature.create(it), new MethodOrigin(typeInfo, it)));

        typeInfo.interfaceTypeInfo()
                .forEach(iface -> iface.elementInfo()
                        .stream()
                        .filter(ElementInfoPredicates::isMethod)
                        .filter(not(ElementInfoPredicates::isPrivate))
                        .filter(not(ElementInfoPredicates::isStatic))
                        .filter(not(ElementInfoPredicates::isDefault))
                        .forEach(it -> discoveredMethods.putIfAbsent(MethodSignature.create(it),
                                                                     new MethodOrigin(iface, it))));

        discoveredMethods.forEach((sig, origin) -> {
            builder.addMethod(toEndpointMethod(origin.type(), origin.method(), builder));
        });

        return builder.build();
    }

    private RestMethod toEndpointMethod(TypeInfo typeInfo, TypedElementInfo method, ClientEndpoint.Builder endpointBuilder) {
        Set<Annotation> annotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx, typeInfo, method));
        var builder = RestMethod.builder()
                .returnType(method.typeName())
                .type(typeInfo)
                .name(method.elementName())
                .uniqueName(method.elementName()) // this is not unique, but we do not need it in client
                .method(method)
                .annotations(annotations)
                .httpMethod(httpMethodFromAnnotation(method,
                                                     getMetaAnnotated(method, HTTP_METHOD_ANNOTATION, annotations)));

        path(annotations, builder);
        consumes(annotations, builder);
        produces(annotations, builder);
        headers(annotations, builder, REST_CLIENT_HEADERS, REST_CLIENT_HEADER);
        computedHeaders(annotations, builder, REST_CLIENT_COMPUTED_HEADERS, REST_CLIENT_COMPUTED_HEADER);

        if (builder.consumes().isEmpty()) {
            builder.consumes(endpointBuilder.consumes());
        }
        if (builder.produces().isEmpty()) {
            builder.produces(endpointBuilder.produces());
        }
        builder.addHeaders(endpointBuilder.headers());
        builder.addComputedHeaders(endpointBuilder.computedHeaders());

        int index = 0;
        for (TypedElementInfo parameterInfo : method.parameterArguments()) {
            processEndpointParameter(typeInfo, method, parameterInfo, builder, index);
            index++;
        }

        return builder.build();
    }

    private Annotation getMetaAnnotated(TypedElementInfo method,
                                        TypeName metaAnnotation,
                                        Set<Annotation> annotations) {

        return DeclarativeUtils.findMetaAnnotated(annotations, metaAnnotation)
                .orElseThrow(() -> new CodegenException("Cannot find meta annotation " + metaAnnotation.fqName() + " on method",
                                                        method.originatingElementValue()));
    }

    private void processEndpointParameter(TypeInfo typeInfo,
                                          TypedElementInfo methodInfo,
                                          TypedElementInfo parameterInfo,
                                          RestMethod.Builder method,
                                          int index) {
        Set<Annotation> annotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx,
                                                                                       typeInfo,
                                                                                       methodInfo,
                                                                                       parameterInfo,
                                                                                       index));
        HttpCodegenValidation.validateMethodParameterAnnotationCount(
                annotations,
                "Parameter '" + parameterInfo.elementName() + "' of declarative client method "
                        + typeInfo.typeName().fqName() + "." + methodInfo.elementName()
                        + "() must have at most one supported request parameter annotation.",
                parameterInfo.originatingElementValue());
        var parameter = RestMethodParameter.builder()
                .annotations(annotations)
                .name(parameterInfo.elementName())
                .typeName(parameterInfo.typeName())
                .index(index)
                .method(methodInfo)
                .type(typeInfo)
                .parameter(parameterInfo)
                .build();

        method.addParameter(parameter);
        if (Annotations.findFirst(HTTP_HEADER_PARAM_ANNOTATION, annotations).isPresent()) {
            method.addHeaderParameter(parameter);
        }
        if (Annotations.findFirst(HTTP_QUERY_PARAM_ANNOTATION, annotations).isPresent()) {
            method.addQueryParameter(parameter);
        }
        if (Annotations.findFirst(HTTP_PATH_PARAM_ANNOTATION, annotations).isPresent()) {
            method.addPathParameter(parameter);
        }
        if (Annotations.findFirst(HTTP_ENTITY_ANNOTATION, annotations).isPresent()) {
            method.entityParameter(parameter);
        }
    }

    private void process(RegistryRoundContext roundContext, ClientEndpoint endpoint) {
        TypeInfo type = endpoint.type();
        if (type.kind() != ElementKind.INTERFACE) {
            throw new CodegenException("Types annotated with "
                                               + RestClientTypes.REST_CLIENT_ENDPOINT.classNameWithEnclosingNames()
                                               + " must be interfaces. This type is: " + type.kind(),
                                       type.originatingElementValue());
        }

        String classNameBase = type.typeName().classNameWithEnclosingNames().replace('.', '_');
        String className = classNameBase + "__DeclarativeClient";
        TypeName generatedType = TypeName.builder()
                .packageName(type.typeName().packageName())
                .className(className)
                .build();

        /*
        @Injection.Singleton
        @RestClient.Client
        class MyEndpoint__DeclarativeClient implements MyEndpoint
         */
        var classModel = ClassModel.builder()
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 type.typeName(),
                                                 generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               type.typeName(),
                                                               generatedType,
                                                               "1",
                                                               ""))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .type(generatedType)
                .addInterface(type.typeName())
                .addAnnotation(SINGLETON_ANNOTATION)
                .addAnnotation(DeclarativeTypes.SUPPRESS_API)
                .addAnnotation(REST_CLIENT_QUALIFIER_INSTANCE);

        var constructor = constructor(classModel, endpoint);

        FieldHandler fieldHandler = FieldHandler.create(classModel, constructor);

        /*
        Now add the fields to handle requests
         */
        classModel.addField(webClient -> webClient
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(WEB_CLIENT)
                .name("client"));
        classModel.addField(webClient -> webClient
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(String.class)
                .name("baseUri"));
        classModel.addField(webClient -> webClient
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(REST_CLIENT_ERROR_HANDLING)
                .name("errorHandling"));

        Map<String, String> headerProducerFields = headerProducers(fieldHandler, endpoint);

        if (hasParameterValueHelper(endpoint)) {
            addParameterValueHelper(classModel);
        }

        for (RestMethod method : endpoint.methods()) {
            generateMethod(fieldHandler,
                           classModel,
                           method,
                           headerProducerFields);
        }

        classModel.addConstructor(constructor);

        roundContext.addGeneratedType(generatedType, classModel, type.typeName(), type.originatingElementValue());
    }

    private void generateMethod(FieldHandler fieldHandler,
                                ClassModel.Builder classModel,
                                RestMethod method,
                                Map<String, String> headerProducers) {

        classModel.addMethod(restMethod -> restMethod
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .name(method.name())
                .returnType(method.returnType())
                .update(it -> generateMethodParamsAndBody(
                        fieldHandler,
                        it,
                        method,
                        headerProducers)));
    }

    // readability is better if the whole method is generated here, and it is a private method
    @SuppressWarnings({"checkstyle:MethodLength", "checkstyle:ParameterNumber"})
    private void generateMethodParamsAndBody(FieldHandler fieldHandler,
                                             Method.Builder it,
                                             RestMethod method,
                                             Map<String, String> headerProducers) {
        validateMethodParameters(method);

        method.parameters()
                .forEach(param -> it.addParameter(newParam -> newParam
                        .name(param.name())
                        .type(param.typeName())));
        addRequestParamsNullChecks(it, method);

        List<ClientParameter> clientParameters = clientParameters(method);
        List<ClientParameter> pathParameters = annotatedParameters(clientParameters, HTTP_PATH_PARAM_ANNOTATION);
        List<ClientParameter> formParameters = annotatedParameters(clientParameters, HTTP_FORM_PARAM_ANNOTATION);
        List<ClientParameter> entityParameters = annotatedParameters(clientParameters, HTTP_ENTITY_ANNOTATION);
        if (entityParameters.size() > 1) {
            throw new CodegenException("Only one @Http.Entity parameter is supported on declarative client method "
                                               + method.type().typeName().fqName() + "." + method.name() + "().",
                                       entityParameters.getFirst().originatingElement().originatingElementValue());
        }
        if (!entityParameters.isEmpty() && !formParameters.isEmpty()) {
            throw new CodegenException("@Http.Entity and @Http.FormParam cannot be combined on declarative client method "
                                               + method.type().typeName().fqName() + "." + method.name() + "().",
                                       entityParameters.getFirst().originatingElement().originatingElementValue());
        }

        if (pathParameters.isEmpty()) {
            String path = method.path().orElse(null);
            it.addContent("var declarative__uri = baseUri");
            if (path == null) {
                it.addContentLine(";");
            } else {
                it.addContent(" + \"")
                        .addContent(path)
                        .addContentLine("\";");
            }
        } else {
            // path can be something like "/{pathParam1}/{pathParam2}
            String path = method.path().orElseThrow(() -> new CodegenException("@Http.PathParameter defined on a method,"
                                                                                       + "yet it does not have a @Http.Path "
                                                                                       + "annotation.",
                                                                               method.method().originatingElementValue()));

            List<PathParamPart> parts = new ArrayList<>();
            StringBuilder currentPart = new StringBuilder();
            boolean currentPartIsName = false;
            for (char c : path.toCharArray()) {
                if (c == '{') {
                    if (!currentPart.isEmpty()) {
                        if (currentPartIsName) {
                            parts.add(new NamePart(currentPart.toString()));
                        } else {
                            parts.add(new StringPart(currentPart.toString()));
                        }
                        currentPart.setLength(0);
                    }
                    currentPartIsName = true;
                } else if (c == '}') {
                    parts.add(new NamePart(currentPart.toString()));
                    currentPart.setLength(0);
                    currentPartIsName = false;
                } else {
                    currentPart.append(c);
                }
            }
            if (!currentPart.isEmpty()) {
                parts.add(new StringPart(currentPart.toString()));
            }

            var pathParamNameToParameterName = new HashMap<String, String>();

            for (ClientParameter pathParameter : pathParameters) {
                String pathParam = pathParamNameFromPathParam(pathParameter);
                pathParamNameToParameterName.put(pathParam, valueExpression(pathParameter));
            }

            it.addContent("var declarative__uri = baseUri")
                    .addContent(parts.stream()
                                        .map(part -> part.codegen(pathParamNameToParameterName))
                                        .collect(Collectors.joining(" + ", " + ", "")));
            it.addContentLine(";");
        }

        it.addContent("var declarative__builder = client.");
        if (method.httpMethod().builtIn()) {
            it.addContent(method.httpMethod().name().toLowerCase(Locale.ROOT))
                    .addContentLine("(declarative__uri);");
        } else {
            it.addContent("method(")
                    .addContent(HttpFields.ensureHttpMethodConstant(fieldHandler, method.httpMethod().name()))
                    .addContentLine(").path(declarative__uri);");
        }

        List<ClientParameter> headerParameters = annotatedParameters(clientParameters, HTTP_HEADER_PARAM_ANNOTATION);
        List<ClientParameter> cookieParameters = annotatedParameters(clientParameters, HTTP_COOKIE_PARAM_ANNOTATION);
        boolean hasCookieHeaders = hasCookieHeaders(method, headerParameters, cookieParameters);
        if (hasCookieHeaders) {
            it.addContentLine("var declarative__cookies = new java.util.ArrayList<String>();");
        }

        // now each header value, header producer, and header parameter
        for (HeaderValue header : method.headers()) {
            if (isCookieHeader(header.name())) {
                it.addContent("declarative__cookies.add(")
                        .addContentLiteral(header.value())
                        .addContentLine(");");
            } else {
                it.addContent("declarative__builder.header(")
                        .addContent(HttpFields.ensureHeaderValueConstant(fieldHandler, header))
                        .addContentLine(");");
            }
        }
        for (ComputedHeader computedHeader : method.computedHeaders()) {
            String headerNameConstant = HttpFields.ensureHeaderNameConstant(fieldHandler, computedHeader.headerName());

            if (isCookieHeader(computedHeader.headerName())) {
                it.addContent(headerProducers.get(computedHeader.serviceName()))
                        .addContent(".apply(")
                        .addContent(headerNameConstant)
                        .addContentLine(")")
                        .increaseContentPadding()
                        .increaseContentPadding()
                        .addContentLine(".ifPresent(declarative__it -> "
                                                + "declarative__cookies.addAll(declarative__it.allValues()));")
                        .decreaseContentPadding()
                        .decreaseContentPadding();
            } else {
                it.addContent(headerProducers.get(computedHeader.serviceName()))
                        .addContent(".apply(")
                        .addContent(headerNameConstant)
                        .addContent(").ifPresent(declarative__it -> declarative__builder.header(declarative__it));");
            }
        }
        for (ClientParameter headerParameter : headerParameters) {
            if (isCookieHeader(headerNameFromHeaderParam(headerParameter))) {
                addCookieHeaderParameter(it, headerParameter);
            } else {
                addHeaderParameter(it, fieldHandler, headerParameter);
            }
        }
        if (!cookieParameters.isEmpty()) {
            for (ClientParameter cookieParameter : cookieParameters) {
                addCookieParameter(it, cookieParameter);
            }
        }
        if (hasCookieHeaders) {
            it.addContentLine("if (!declarative__cookies.isEmpty()) {")
                    .increaseContentPadding()
                    .addContent("declarative__builder.header(")
                    .addContent(HTTP_HEADER_NAMES)
                    .addContent(".COOKIE, ")
                    .addContent(HTTP_SUPPORT)
                    .addContentLine(".cookieHeader(declarative__cookies));")
                    .decreaseContentPadding()
                    .addContentLine("}");
        }
        // query parameter
        for (ClientParameter parameter : annotatedParameters(clientParameters, HTTP_QUERY_PARAM_ANNOTATION)) {
            addQueryParameter(it, parameter);
        }

        List<String> produces = method.produces();
        if (!produces.isEmpty()) {
            it.addContent("declarative__builder.accept(");
            it.addContent(produces.stream()
                                  .map(mediaType -> HttpFields.ensureHttpMediaTypeConstant(fieldHandler, mediaType))
                                  .collect(Collectors.joining(", ")));
            it.addContentLine(");");
        }

        List<String> consumes = method.consumes();
        if (consumes.size() == 1) {
            it.addContent("declarative__builder.contentType(")
                    .addContent(HttpFields.ensureHttpMediaTypeConstant(fieldHandler, consumes.getFirst()))
                    .addContentLine(");");
        }

        if (!formParameters.isEmpty()) {
            it.addContentLine("var declarative__formParams = io.helidon.common.parameters.Parameters.builder(\"form-params\");");
            for (ClientParameter formParameter : formParameters) {
                addFormParameter(it, formParameter);
            }
        }

        boolean hasEntity = !entityParameters.isEmpty() || !formParameters.isEmpty();
        boolean hasResponse = !(
                method.returnType().equals(TypeNames.BOXED_VOID)
                        || method.returnType().equals(TypeNames.PRIMITIVE_VOID));
        var returnType = method.returnType();
        boolean useGenericType;
        String genericTypeField;
        if (hasResponse && !returnType.typeArguments().isEmpty()) {
            // we have a return type, and it has type arguments
            useGenericType = true;
            TypeName genType = TypeName.builder(TypeNames.GENERIC_TYPE)
                    .addTypeArgument(returnType)
                    .build();
            genericTypeField = fieldHandler.constant("GTYPE", genType, ResolvedType.create(genType), constant -> {
                constant.addContent("new ")
                        .addContent(TypeNames.GENERIC_TYPE)
                        .addContent("<")
                        .addContent(returnType)
                        .addContent(">() {}");
            });
        } else {
            useGenericType = false;
            genericTypeField = null;
        }

        /*
        neither - call request() without any parameters, try with resources
        hasEntity - call submit(entity)
        hasResponse - call request(Type.class)
        both - call submit(entity, Type.class)
         */
        if (hasEntity && hasResponse) {
            it.addContent("var declarative__response = declarative__builder.submit(")
                    .addContent(entityExpression(entityParameters, formParameters))
                    .addContent(", ");
            if (useGenericType) {
                it.addContent(genericTypeField);
            } else {
                it.addContent(method.returnType())
                        .addContent(".class");
            }
            it.addContentLine(");");
        } else if (hasEntity) {
            it.addContent("try (var declarative__response = declarative__builder.submit(")
                    .addContent(entityExpression(entityParameters, formParameters))
                    .addContentLine(")) {");
        } else if (hasResponse) {
            it.addContent("var declarative__response = declarative__builder.request(");
            if (useGenericType) {
                it.addContent(genericTypeField);
            } else {
                it.addContent(method.returnType())
                        .addContent(".class");
            }
            it.addContentLine(");");
        } else {
            it.addContentLine("try (var declarative__response = declarative__builder.request()) {");
        }

        it.addContentLine("var declarative__headers = declarative__builder.headers();");

        if (hasResponse) {
            if (useGenericType) {
                it.addContent("errorHandling.handle(declarative__uri, declarative__headers, declarative__response, ")
                        .addContent(genericTypeField)
                        .addContentLine(");");
            } else {
                it.addContent("errorHandling.handle(declarative__uri, declarative__headers, declarative__response, ")
                        .addContent(method.returnType())
                        .addContentLine(".class);");
            }
        } else {
            it.addContentLine("errorHandling.handle(declarative__uri, declarative__headers, declarative__response);");
        }

        if (hasResponse) {
            it.addContentLine("return declarative__response.entity();");
        } else {
            it.addContentLine("}");
        }
    }

    private void validateMethodParameters(RestMethod method) {
        for (RestMethodParameter parameter : method.parameters()) {
            if (!HttpCodegenValidation.hasMethodParameterAnnotation(parameter.annotations())) {
                throw new CodegenException("Parameter '" + parameter.name() + "' of declarative client method "
                                                   + method.type().typeName().fqName() + "." + method.name()
                                                   + "() must be annotated with a supported request parameter annotation.",
                                           parameter.parameter().originatingElementValue());
            }
        }
    }

    private List<ClientParameter> clientParameters(RestMethod method) {
        List<ClientParameter> result = new ArrayList<>();
        for (RestMethodParameter parameter : method.parameters()) {
            if (Annotations.findFirst(HTTP_REQUEST_PARAMS_ANNOTATION, parameter.annotations()).isPresent()) {
                addRequestParamsComponents(result, parameter);
            } else {
                result.add(ClientParameter.create(parameter));
            }
        }
        return result;
    }

    private void addRequestParamsComponents(List<ClientParameter> result, RestMethodParameter parameter) {
        TypeInfo requestParamsType = HttpCodegenValidation.requestParamsRecordType(
                ctx::typeInfo,
                parameter.typeName(),
                parameter.parameter().originatingElementValue());

        HttpCodegenValidation.validateRequestParamsBodyComponents(requestParamsType);

        for (TypedElementInfo component : HttpCodegenValidation.requestParamsComponents(requestParamsType)) {
            Set<Annotation> annotations = new HashSet<>(component.annotations());
            if (HttpCodegenValidation.firstRequestParamsComponentAnnotation(annotations).isEmpty()) {
                throw new CodegenException("Record component '" + component.elementName()
                                                   + "' of @Http.RequestParams type " + parameter.typeName().fqName()
                                                   + " is not annotated with a supported request parameter annotation.",
                                           component.originatingElementValue());
            }
            result.add(ClientParameter.create(component,
                                              parameter.name() + "." + component.elementName() + "()",
                                              annotations));
        }
    }

    private List<ClientParameter> annotatedParameters(List<ClientParameter> parameters, TypeName annotation) {
        return parameters.stream()
                .filter(it -> Annotations.findFirst(annotation, it.annotations()).isPresent())
                .toList();
    }

    private boolean hasParameterValueHelper(ClientEndpoint endpoint) {
        return endpoint.methods()
                .stream()
                .map(this::clientParameters)
                .flatMap(List::stream)
                .anyMatch(this::isValueParameter);
    }

    private boolean isValueParameter(ClientParameter parameter) {
        return Annotations.findFirst(HTTP_HEADER_PARAM_ANNOTATION, parameter.annotations()).isPresent()
                || Annotations.findFirst(HTTP_QUERY_PARAM_ANNOTATION, parameter.annotations()).isPresent()
                || Annotations.findFirst(HTTP_PATH_PARAM_ANNOTATION, parameter.annotations()).isPresent()
                || Annotations.findFirst(HTTP_COOKIE_PARAM_ANNOTATION, parameter.annotations()).isPresent()
                || Annotations.findFirst(HTTP_FORM_PARAM_ANNOTATION, parameter.annotations()).isPresent();
    }

    private void addRequestParamsNullChecks(Method.Builder method, RestMethod restMethod) {
        restMethod.parameters()
                .stream()
                .filter(parameter -> Annotations.findFirst(HTTP_REQUEST_PARAMS_ANNOTATION, parameter.annotations()).isPresent())
                .forEach(parameter -> method.addContent(Objects.class)
                        .addContent(".requireNonNull(")
                        .addContent(parameter.name())
                        .addContent(", ")
                        .addContentLiteral("@Http.RequestParams parameter " + parameter.name() + " must not be null.")
                        .addContentLine(");"));
    }

    private boolean hasCookieHeaders(RestMethod method,
                                     List<ClientParameter> headerParameters,
                                     List<ClientParameter> cookieParameters) {
        return !cookieParameters.isEmpty()
                || method.headers()
                        .stream()
                        .map(HeaderValue::name)
                        .anyMatch(this::isCookieHeader)
                || method.computedHeaders()
                        .stream()
                        .map(ComputedHeader::headerName)
                        .anyMatch(this::isCookieHeader)
                || headerParameters.stream()
                        .map(this::headerNameFromHeaderParam)
                        .anyMatch(this::isCookieHeader);
    }

    private boolean isCookieHeader(String headerName) {
        return "cookie".equals(headerName.toLowerCase(Locale.ROOT));
    }

    private void addParameterValueHelper(ClassModel.Builder classModel) {
        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .returnType(TypeNames.STRING)
                .name("declarative__parameterValue")
                .addParameter(param -> param
                        .type(TypeNames.STRING)
                        .name("source"))
                .addParameter(param -> param
                        .type(TypeNames.STRING)
                        .name("name"))
                .addParameter(param -> param
                        .type(TypeNames.OBJECT)
                        .name("value"))
                .addContent("return String.valueOf(")
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(value, source + \" \" + name + \" must not be null.\"));"));
    }

    private void addHeaderParameter(Method.Builder method, FieldHandler fieldHandler, ClientParameter parameter) {
        String headerName = headerNameFromHeaderParam(parameter);
        addBuilderParameter(method,
                            parameter,
                            "Header",
                            headerName,
                            "declarative__builder.header("
                                    + HttpFields.ensureHeaderNameConstant(fieldHandler, headerName) + ", ",
                            ")");
    }

    private void addCookieHeaderParameter(Method.Builder method, ClientParameter parameter) {
        String headerName = headerNameFromHeaderParam(parameter);
        TypeName type = parameter.typeName();
        if (type.isOptional()) {
            TypeName optionalType = type.typeArguments().getFirst();
            if (optionalType.isList()) {
                addRequireNonNull(method, "Header", headerName, parameter.expression());
                method
                        .addContent(".ifPresent(declarative__it -> declarative__it.stream()")
                        .addContent(".map(declarative__value -> ");
                addParameterValue(method, "Header", headerName, "declarative__value");
                method.addContent(")")
                        .addContentLine(".forEach(declarative__cookies::add));");
            } else {
                addRequireNonNull(method, "Header", headerName, parameter.expression());
                method
                        .addContent(".ifPresent(declarative__it -> declarative__cookies.add(");
                addParameterValue(method, "Header", headerName, "declarative__it");
                method.addContent("))")
                        .addContentLine(";");
            }
        } else if (type.isList()) {
            addRequireNonNull(method, "Header", headerName, parameter.expression());
            method.addContent(".stream().map(declarative__value -> ");
            addParameterValue(method, "Header", headerName, "declarative__value");
            method.addContent(")")
                    .addContentLine(".forEach(declarative__cookies::add);");
        } else {
            method.addContent("declarative__cookies.add(");
            addParameterValue(method, "Header", headerName, parameter.expression());
            method.addContentLine(");");
        }
    }

    private void addCookieParameter(Method.Builder method, ClientParameter parameter) {
        String cookieName = cookieParamName(parameter);
        TypeName type = parameter.typeName();
        if (type.isOptional()) {
            TypeName optionalType = type.typeArguments().getFirst();
            if (optionalType.isList()) {
                addRequireNonNull(method, "Cookie parameter", cookieName, parameter.expression());
                method
                        .addContent(".ifPresent(declarative__it -> declarative__it.stream()")
                        .addContent(".map(declarative__value -> ")
                        .addContent(HTTP_SUPPORT)
                        .addContent(".cookie(")
                        .addContentLiteral(cookieName)
                        .addContent(", ");
                addParameterValue(method, "Cookie parameter", cookieName, "declarative__value");
                method.addContent("))")
                        .addContentLine(".forEach(declarative__cookies::add));");
            } else {
                addRequireNonNull(method, "Cookie parameter", cookieName, parameter.expression());
                method
                        .addContent(".ifPresent(declarative__it -> declarative__cookies.add(")
                        .addContent(HTTP_SUPPORT)
                        .addContent(".cookie(")
                        .addContentLiteral(cookieName)
                        .addContent(", ");
                addParameterValue(method, "Cookie parameter", cookieName, "declarative__it");
                method.addContentLine(")));");
            }
        } else if (type.isList()) {
            method.addContent("if (")
                    .update(it -> addRequireNonNull(it, "Cookie parameter", cookieName, parameter.expression()))
                    .addContentLine(".isEmpty()) {")
                    .increaseContentPadding()
                    .addContent("throw new IllegalArgumentException(")
                    .addContentLiteral("Cookie parameter " + cookieName + " has no values.")
                    .addContentLine(");")
                    .decreaseContentPadding()
                    .addContentLine("}");
            addRequireNonNull(method, "Cookie parameter", cookieName, parameter.expression());
            method
                    .addContent(".stream().map(declarative__it -> ")
                    .addContent(HTTP_SUPPORT)
                    .addContent(".cookie(")
                    .addContentLiteral(cookieName)
                    .addContent(", ");
            addParameterValue(method, "Cookie parameter", cookieName, "declarative__it");
            method.addContent("))")
                    .addContentLine(".forEach(declarative__cookies::add);");
        } else {
            method.addContent("declarative__cookies.add(")
                    .addContent(HTTP_SUPPORT)
                    .addContent(".cookie(")
                    .addContentLiteral(cookieName)
                    .addContent(", ");
            addParameterValue(method, "Cookie parameter", cookieName, parameter.expression());
            method
                    .addContentLine("));");
        }
    }

    private void addQueryParameter(Method.Builder method, ClientParameter parameter) {
        String queryName = queryParameterName(parameter);
        TypeName type = parameter.typeName();
        if (type.isOptional()) {
            TypeName optionalType = type.typeArguments().getFirst();
            if (optionalType.isList()) {
                addRequireNonNull(method, "Query parameter", queryName, parameter.expression());
                method.addContent(".ifPresent(declarative__it -> ");
                addQueryParameterStart(method, queryName);
                method.addContent("declarative__it.stream().map(declarative__value -> ");
                addParameterValue(method, "Query parameter", queryName, "declarative__value");
                method.addContent(").toArray(String[]::new))")
                        .addContentLine(");");
            } else {
                addRequireNonNull(method, "Query parameter", queryName, parameter.expression());
                method.addContent(".ifPresent(declarative__it -> ");
                addQueryParameterStart(method, queryName);
                addParameterValue(method, "Query parameter", queryName, "declarative__it");
                method.addContentLine("));");
            }
        } else if (type.isList()) {
            addQueryParameterStart(method, queryName);
            addRequireNonNull(method, "Query parameter", queryName, parameter.expression());
            method.addContent(".stream().map(declarative__value -> ");
            addParameterValue(method, "Query parameter", queryName, "declarative__value");
            method.addContent(").toArray(String[]::new))")
                    .addContentLine(";");
        } else {
            addQueryParameterStart(method, queryName);
            addParameterValue(method, "Query parameter", queryName, parameter.expression());
            method.addContentLine(");");
        }
    }

    private void addQueryParameterStart(Method.Builder method, String queryName) {
        method.addContent("declarative__builder.queryParam(")
                .addContentLiteral(queryName)
                .addContent(", ");
    }

    private void addFormParameter(Method.Builder method, ClientParameter parameter) {
        String formName = formParamName(parameter);
        TypeName type = parameter.typeName();
        if (type.isOptional()) {
            TypeName optionalType = type.typeArguments().getFirst();
            if (optionalType.isList()) {
                addRequireNonNull(method, "Form parameter", formName, parameter.expression());
                method
                        .addContent(".ifPresent(declarative__it -> declarative__formParams.add(")
                        .addContentLiteral(formName)
                        .addContent(", declarative__it.stream()")
                        .addContent(".map(declarative__value -> declarative__parameterValue(\"Form parameter\", ")
                        .addContentLiteral(formName)
                        .addContent(", declarative__value))")
                        .addContentLine(".toArray(String[]::new)));");
            } else {
                addRequireNonNull(method, "Form parameter", formName, parameter.expression());
                method
                        .addContent(".ifPresent(declarative__it -> declarative__formParams.add(")
                        .addContentLiteral(formName)
                        .addContent(", declarative__parameterValue(\"Form parameter\", ")
                        .addContentLiteral(formName)
                        .addContentLine(", declarative__it)));");
            }
        } else if (type.isList()) {
            method.addContent("declarative__formParams.add(")
                    .addContentLiteral(formName)
                    .addContent(", ")
                    .update(it -> addRequireNonNull(it, "Form parameter", formName, parameter.expression()))
                    .addContent(".stream().map(declarative__it -> declarative__parameterValue(\"Form parameter\", ")
                    .addContentLiteral(formName)
                    .addContent(", declarative__it))")
                    .addContentLine(".toArray(String[]::new));");
        } else {
            method.addContent("declarative__formParams.add(")
                    .addContentLiteral(formName)
                    .addContent(", declarative__parameterValue(\"Form parameter\", ")
                    .addContentLiteral(formName)
                    .addContent(", ")
                    .addContent(parameter.expression())
                    .addContentLine("));");
        }
    }

    private void addBuilderParameter(Method.Builder method,
                                     ClientParameter parameter,
                                     String source,
                                     String name,
                                     String prefix,
                                     String suffix) {
        TypeName type = parameter.typeName();
        if (type.isOptional()) {
            TypeName optionalType = type.typeArguments().getFirst();
            if (optionalType.isList()) {
                addRequireNonNull(method, source, name, parameter.expression());
                method
                        .addContent(".ifPresent(declarative__it -> ")
                        .addContent(prefix)
                        .addContent("declarative__it.stream().map(declarative__value -> ");
                addParameterValue(method, source, name, "declarative__value");
                method
                        .addContent(").toArray(String[]::new)")
                        .addContent(suffix)
                        .addContentLine(");");
            } else {
                addRequireNonNull(method, source, name, parameter.expression());
                method
                        .addContent(".ifPresent(declarative__it -> ")
                        .addContent(prefix);
                addParameterValue(method, source, name, "declarative__it");
                method
                        .addContent(suffix)
                        .addContentLine(");");
            }
        } else if (type.isList()) {
            method.addContent(prefix)
                    .update(it -> addRequireNonNull(it, source, name, parameter.expression()))
                    .addContent(".stream().map(declarative__value -> ");
            addParameterValue(method, source, name, "declarative__value");
            method
                    .addContent(").toArray(String[]::new)")
                    .addContent(suffix)
                    .addContentLine(";");
        } else {
            method.addContent(prefix);
            addParameterValue(method, source, name, parameter.expression());
            method
                    .addContent(suffix)
                    .addContentLine(";");
        }
    }

    private void addParameterValue(Method.Builder method, String source, String name, String valueExpression) {
        method.addContent("declarative__parameterValue(")
                .addContentLiteral(source)
                .addContent(", ")
                .addContentLiteral(name)
                .addContent(", ")
                .addContent(valueExpression)
                .addContent(")");
    }

    private void addRequireNonNull(Method.Builder method, String source, String name, String valueExpression) {
        method.addContent(Objects.class)
                .addContent(".requireNonNull(")
                .addContent(valueExpression)
                .addContent(", ")
                .addContentLiteral(source + " " + name + " must not be null.")
                .addContent(")");
    }

    private String entityExpression(List<ClientParameter> entityParameters, List<ClientParameter> formParameters) {
        if (!formParameters.isEmpty()) {
            return "declarative__formParams.build()";
        }
        return entityParameters.getFirst().expression();
    }

    private String valueExpression(ClientParameter parameter) {
        String name = pathParamNameFromPathParam(parameter);
        if (parameter.typeName().isOptional()) {
            return parameterValueExpression("Path parameter",
                                            name,
                                            requireNonNullExpression("Path parameter",
                                                                     name,
                                                                     parameter.expression()) + ".orElseThrow()");
        }
        return parameterValueExpression("Path parameter", name, parameter.expression());
    }

    private String parameterValueExpression(String source, String name, String valueExpression) {
        return "declarative__parameterValue(" + javaStringLiteral(source) + ", " + javaStringLiteral(name) + ", "
                + valueExpression + ")";
    }

    private String requireNonNullExpression(String source, String name, String valueExpression) {
        return "Objects.requireNonNull(" + valueExpression + ", " + javaStringLiteral(source + " " + name
                + " must not be null.") + ")";
    }

    private String javaStringLiteral(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String queryParameterName(ClientParameter parameter) {
        return annotationValue(parameter, HTTP_QUERY_PARAM_ANNOTATION, "@Http.QueryParam");
    }

    private String pathParamNameFromPathParam(ClientParameter parameter) {
        return annotationValue(parameter, HTTP_PATH_PARAM_ANNOTATION, "@Http.PathParam");
    }

    private String headerNameFromHeaderParam(ClientParameter parameter) {
        return annotationValue(parameter, HTTP_HEADER_PARAM_ANNOTATION, "@Http.HeaderParam");
    }

    private String cookieParamName(ClientParameter parameter) {
        String cookieParamName = annotationValue(parameter, HTTP_COOKIE_PARAM_ANNOTATION, "@Http.CookieParam");
        HttpCodegenValidation.validateCookieName(cookieParamName,
                                                 "@Http.CookieParam",
                                                 parameter.originatingElement().originatingElementValue());
        return cookieParamName;
    }

    private String formParamName(ClientParameter parameter) {
        return annotationValue(parameter, HTTP_FORM_PARAM_ANNOTATION, "@Http.FormParam");
    }

    private String annotationValue(ClientParameter parameter, TypeName annotationType, String annotationName) {
        return Annotations.findFirst(annotationType, parameter.annotations())
                .flatMap(Annotation::value)
                .orElseThrow(() -> new CodegenException("Parameter is recognized as " + annotationName
                                                                + " yet it is not annotated: " + parameter.name(),
                                                        parameter.originatingElement().originatingElementValue()));
    }

    private Constructor.Builder constructor(ClassModel.Builder classModel,
                                            ClientEndpoint endpoint) {
        return Constructor.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addParameter(errorHandling -> errorHandling
                        .name("errorHandling")
                        .type(REST_CLIENT_ERROR_HANDLING))
                .addParameter(config -> config
                        .name("config")
                        .type(CONFIG))
                .addParameter(registryClient -> registryClient
                        .name("registryClient")
                        .update(it -> registryClientParameter(it, endpoint)))
                .addContentLine("this.errorHandling = errorHandling;")
                .addContentLine()
                .addContent("this.client = registryClient.get().orElseGet(")
                .addContent(WEB_CLIENT)
                .addContentLine("::create);")
                .update(it -> constructorUriHandling(it, endpoint));
    }

    private void constructorUriHandling(Constructor.Builder ctr, ClientEndpoint endpoint) {
        DelcarativeConfigSupport.assignResolveExpression(ctr,
                                                         "config",
                                                         "uri",
                                                         endpoint.uri());

        String path = endpoint.path().orElse("/");
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        ctr.addContentLine("if (!uri.endsWith(\"/\")) {")
                .addContentLine("uri = uri + \"/\";")
                .addContentLine("}")
                .addContent("this.baseUri = uri");
        if (!path.isBlank()) {
            ctr.addContent(" + \"")
                    .addContent(path)
                    .addContent("\";");
        }
        ctr.addContentLine(";");

    }

    private void registryClientParameter(Parameter.Builder param, ClientEndpoint endpoint) {
        if (endpoint.clientName().isPresent()) {
            param.addAnnotation(Annotation.create(SERVICE_ANNOTATION_NAMED, endpoint.clientName().get()));
        }
        TypeName optionalWebClient = TypeName.builder()
                .from(TypeNames.OPTIONAL)
                .addTypeArgument(WEB_CLIENT)
                .build();

        param.type(TypeName.builder()
                           .from(TypeNames.SUPPLIER)
                           .addTypeArgument(optionalWebClient)
                           .build());
    }

    private interface PathParamPart {
        String codegen(Map<String, String> pathParamToMethodParam);
    }

    private record MethodSignature(String name, List<TypeName> parameterTypes) {
        public static MethodSignature create(TypedElementInfo element) {
            return new MethodSignature(element.elementName(),
                                       element.parameterArguments()
                                               .stream()
                                               .map(TypedElementInfo::typeName)
                                               .toList());
        }
    }

    private record MethodOrigin(TypeInfo type, TypedElementInfo method) { }

    private record ClientParameter(Set<Annotation> annotations,
                                   String name,
                                   String expression,
                                   TypeName typeName,
                                   TypedElementInfo originatingElement) {
        static ClientParameter create(RestMethodParameter parameter) {
            return new ClientParameter(parameter.annotations(),
                                       parameter.name(),
                                       parameter.name(),
                                       parameter.typeName(),
                                       parameter.parameter());
        }

        static ClientParameter create(TypedElementInfo component,
                                      String expression,
                                      Set<Annotation> annotations) {
            return new ClientParameter(annotations,
                                       component.elementName(),
                                       expression,
                                       component.typeName(),
                                       component);
        }
    }

    private record StringPart(String value) implements PathParamPart {

        @Override
        public String codegen(Map<String, String> pathParamToMethodParam) {
            return "\"" + value + "\"";
        }
    }

    private record NamePart(String name) implements PathParamPart {
        @Override
        public String codegen(Map<String, String> pathParamToMethodParam) {
            String methodParameter = pathParamToMethodParam.get(name);
            if (methodParameter == null) {
                throw new CodegenException("There is no method parameter defined for path parameter: " + name);
            }
            return methodParameter;
        }
    }
}
