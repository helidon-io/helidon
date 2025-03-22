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

package io.helidon.declarative.codegen.http.restclient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
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
import static io.helidon.declarative.codegen.DeclarativeTypes.CONFIG_EXCEPTION;
import static io.helidon.declarative.codegen.DeclarativeTypes.SINGLETON_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_ENTITY_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_HEADER_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_HEADER_VALUES;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_METHOD_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_PATH_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_QUERY_PARAM_ANNOTATION;
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

        return findMetaAnnotated(metaAnnotation, annotations)
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
        if (Annotations.findFirst(HTTP_ENTITY_PARAM_ANNOTATION, annotations).isPresent()) {
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

        Map<TypeName, String> headerProducerFields = headerProducers(fieldHandler, endpoint);

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
                                Map<TypeName, String> headerProducers) {

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
                                             Map<TypeName, String> headerProducers) {
        method.parameters()
                .forEach(param -> it.addParameter(newParam -> newParam
                        .name(param.name())
                        .type(param.typeName())));

        if (method.pathParameters().isEmpty()) {
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

            for (RestMethodParameter pathParameter : method.pathParameters()) {
                String paramName = pathParameter.name();
                String pathParam = pathParamNameFromPathParam(pathParameter);
                pathParamNameToParameterName.put(pathParam, paramName);
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
        // now each header value, header producer, and header parameter
        for (HeaderValue header : method.headers()) {
            it.addContent("declarative__builder.header(")
                    .addContent(HttpFields.ensureHeaderValueConstant(fieldHandler, header))
                    .addContentLine(");");
        }
        for (ComputedHeader computedHeader : method.computedHeaders()) {
            String headerNameConstant = HttpFields.ensureHeaderNameConstant(fieldHandler, computedHeader.name());

            it.addContent(headerProducers.get(computedHeader.producer()))
                    .addContent(".produceHeader(")
                    .addContent(headerNameConstant)
                    .addContent(").ifPresent(declarative__it -> declarative__builder.header(")
                    .addContent(headerNameConstant)
                    .addContentLine(", declarative__it));");
        }
        for (RestMethodParameter headerParameter : method.headerParameters()) {
            it.addContent("declarative__builder.header(")
                    .addContent(HTTP_HEADER_VALUES)
                    .addContent(".create(")
                    .addContent(HttpFields.ensureHeaderNameConstant(fieldHandler, headerNameFromHeaderParam(headerParameter)))
                    .addContent(", ")
                    .addContent(headerParameter.name())
                    .addContentLine("));");
        }
        // query parameter
        for (RestMethodParameter parameter : method.queryParameters()) {
            boolean optional = parameter.typeName().isOptional();
            String queryParamName = queryParameterName(parameter);
            if (optional) {
                it.addContent(parameter.name())
                        .addContent(".ifPresent(declarative__it -> declarative__builder.queryParam(\"")
                        .addContent(queryParamName)
                        .addContent("\", String.valueOf(declarative__it)));");
            } else {
                it.addContent("declarative__builder.queryParam(\"")
                        .addContent(queryParamName)
                        .addContent("\", String.valueOf(")
                        .addContent(parameter.name())
                        .addContentLine(")));");
            }

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

        boolean hasEntity = method.entityParameter().isPresent();
        boolean hasResponse = !(
                method.returnType().equals(TypeNames.BOXED_VOID)
                        || method.returnType().equals(TypeNames.PRIMITIVE_VOID));

        /*
        neither - call request() without any parameters, try with resources
        hasEntity - call submit(entity)
        hasResponse - call request(Type.class)
        both - call submit(entity, Type.class)
         */
        if (hasEntity && hasResponse) {
            it.addContent("var declarative__response = declarative__builder.submit(")
                    .addContent(method.entityParameter().get().name())
                    .addContent(", ")
                    .addContent(method.returnType())
                    .addContentLine(".class);");
        } else if (hasEntity) {
            it.addContent("try (var declarative__response = declarative__builder.submit(")
                    .addContent(method.entityParameter().get().name())
                    .addContentLine(")) {");
        } else if (hasResponse) {
            it.addContent("var declarative__response = declarative__builder.request(")
                    .addContent(method.returnType())
                    .addContentLine(".class);");
        } else {
            it.addContentLine("try (var declarative__response = declarative__builder.request()) {");
        }

        it.addContentLine("var declarative__headers = declarative__builder.headers();");

        if (hasResponse) {
            it.addContent("errorHandling.handle(declarative__uri, declarative__headers, declarative__response, ")
                    .addContent(method.returnType())
                    .addContentLine(".class);");
        } else {
            it.addContentLine("errorHandling.handle(declarative__uri, declarative__headers, declarative__response);");
        }

        if (hasResponse) {
            it.addContentLine("return declarative__response.entity();");
        } else {
            it.addContentLine("}");
        }
    }

    private String queryParameterName(RestMethodParameter parameter) {
        return Annotations.findFirst(HTTP_QUERY_PARAM_ANNOTATION,
                                     parameter.annotations())
                .flatMap(Annotation::value)
                .orElseThrow(() -> new CodegenException("Parameter is recognized as @Http.QueryParam yet it is not annotated: "
                                                                + parameter.name(), parameter.parameter()
                                                                .originatingElementValue()));
    }

    private String pathParamNameFromPathParam(RestMethodParameter parameter) {
        return Annotations.findFirst(HTTP_PATH_PARAM_ANNOTATION,
                                     parameter.annotations())
                .flatMap(Annotation::value)
                .orElseThrow(() -> new CodegenException("Parameter is recognized as @Http.HeaderParam yet it is not annotated: "
                                                                + parameter.name(), parameter.parameter()
                                                                .originatingElementValue()));
    }

    private String headerNameFromHeaderParam(RestMethodParameter parameter) {
        for (Annotation annotation : parameter.annotations()) {
            if (annotation.typeName().equals(HTTP_HEADER_PARAM_ANNOTATION)) {
                return annotation.value().orElseThrow();
            }
        }
        throw new CodegenException("Parameter is recognized as @Http.HeaderParam yet it is not annotated: "
                                           + parameter.name(), parameter.parameter()
                                           .originatingElementValue());
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
                .addContentLine("")
                .addContent("var endpointConfig = config.get(\"")
                .addContent(endpoint.configKey())
                .addContentLine("\");")
                .addContentLine("var clientConfig = endpointConfig.get(\"client\");")
                .addContentLine("if (clientConfig.exists()) {")
                .addContent("this.client = ")
                .addContent(WEB_CLIENT)
                .addContentLine(".builder().config(clientConfig).build();")
                .decreaseContentPadding()
                .addContentLine("} else {")
                .addContent("this.client = registryClient.get().orElseGet(")
                .addContent(WEB_CLIENT)
                .addContentLine("::create);")
                .addContentLine("}")
                .update(it -> constructorUriHandling(it, endpoint));
    }

    private void constructorUriHandling(Constructor.Builder ctr, ClientEndpoint endpoint) {
        ctr.addContent(String.class)
                .addContentLine(" uri = endpointConfig.get(\"uri\")")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContentLine(".asString()");

        if (endpoint.uri().isPresent()) {
            ctr.addContent(".orElse(\"")
                    .addContent(endpoint.uri().get())
                    .addContentLine("\");");
        } else {
            ctr.addContent(".orElseThrow(() -> new ")
                    .addContent(CONFIG_EXCEPTION)
                    .addContent("(\"Configuration key \\\"")
                    .addContent(endpoint.configKey())
                    .addContent(".uri\\\" does not exist, and there is no default URI defined in")
                    .addContent(" @")
                    .addContent(REST_CLIENT_ENDPOINT.classNameWithEnclosingNames())
                    .addContent(" for ")
                    .addContent(endpoint.type().typeName().fqName())
                    .addContentLine("\"));");
        }
        ctr.decreaseContentPadding()
                .decreaseContentPadding();

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
