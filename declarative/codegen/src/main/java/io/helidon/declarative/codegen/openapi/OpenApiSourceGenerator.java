/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.openapi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.TypeHierarchy;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
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
import io.helidon.declarative.codegen.model.http.ComputedHeader;
import io.helidon.declarative.codegen.model.http.HeaderValue;
import io.helidon.declarative.codegen.model.http.RestMethod;
import io.helidon.declarative.codegen.model.http.RestMethodParameter;
import io.helidon.declarative.codegen.model.http.ServerEndpoint;
import io.helidon.service.codegen.DefaultsCodegen;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;

import static io.helidon.declarative.codegen.DeclarativeTypes.SINGLETON_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_COOKIE_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_ENTITY_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_FORM_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_HEADER_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_PATH_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_QUERY_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_REQUEST_PARAMS_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.JSON_SCHEMA_PROVIDER;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.JSON_STRING;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_CONTACT_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_DOCUMENT_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_DOCUMENT_BUILDER;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_DOCUMENT_CONTEXT;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_DOCUMENT_CONTEXT_SUPPORT;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_DOCUMENT_EXAMPLE;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_EXTENSIONS_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_EXTENSION_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_EXTERNAL_DOCS_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_HIDDEN_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_INFO_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_LICENSE_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_OPERATION_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_PARAMETERS_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_PARAMETER_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_REQUEST_BODY_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_RESPONSES_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_RESPONSE_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SECURITY_REQUIREMENTS_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SECURITY_REQUIREMENT_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SECURITY_SCHEME_REQUIREMENT_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SERVERS_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SERVER_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SOURCE_BASE;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_TAGS_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_TAG_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.WEB_SERVER;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED_BY_TYPE;
import static java.util.function.Predicate.not;

final class OpenApiSourceGenerator {
    private static final TypeName GENERATOR = TypeName.create(OpenApiSourceGenerator.class);
    private static final TypeName VOID = TypeName.create(Void.class);
    private static final String DEFAULT_MEDIA_TYPE = "application/json";

    private final OpenApiAnnotationValidator validator = new OpenApiAnnotationValidator();
    private final OpenApiSourceExpressions expressions = new OpenApiSourceExpressions(validator);
    private final OpenApiSecuritySchemeCodegen securitySchemeCodegen = new OpenApiSecuritySchemeCodegen(validator,
                                                                                                        expressions);
    private final OpenApiSchemaCodegen schemas = new OpenApiSchemaCodegen(expressions, this::examplesExpression);
    private final OpenApiFormRequestBodyCodegen formRequestBodies = new OpenApiFormRequestBodyCodegen(
            validator,
            expressions,
            schemas,
            this::examplesExpression,
            this::formParameterRequired,
            parameter -> parameterName(parameter, "form"));
    private final RegistryCodegenContext ctx;

    OpenApiSourceGenerator(RegistryCodegenContext ctx) {
        this.ctx = ctx;
    }

    void processDocuments(RegistryRoundContext roundContext) {
        Collection<TypeInfo> documents = roundContext.annotatedTypes(OPENAPI_DOCUMENT_ANNOTATION);
        for (TypeInfo document : documents) {
            processDocument(roundContext, document);
        }
    }

    void processEndpoints(RegistryRoundContext roundContext, List<ServerEndpoint> endpoints) {
        for (ServerEndpoint endpoint : endpoints) {
            if (endpoint.type().kind() != ElementKind.INTERFACE
                    && !hasAnnotation(endpoint.annotations(), OPENAPI_HIDDEN_ANNOTATION)) {
                processEndpoint(roundContext, endpoint);
            }
        }
    }

    private void processDocument(RegistryRoundContext roundContext, TypeInfo typeInfo) {
        TypeName generatedType = generatedType(typeInfo.typeName(), "OpenApiDocument");
        ClassModel.Builder classModel = sourceClass(typeInfo.typeName(), generatedType);
        classModel.addAnnotation(Annotation.builder()
                                         .typeName(SERVICE_ANNOTATION_NAMED_BY_TYPE)
                                         .property("value", typeInfo.typeName())
                                         .build());

        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .name("describe")
                .addParameter(context -> context
                        .type(OPENAPI_DOCUMENT_CONTEXT)
                        .name("context"))
                .addParameter(document -> document
                        .type(OPENAPI_DOCUMENT_BUILDER)
                        .name("document"))
                .update(it -> documentSourceBody(typeInfo, it)));

        roundContext.addGeneratedType(generatedType,
                                      classModel,
                                      typeInfo.typeName(),
                                      typeInfo.originatingElementValue());
    }

    private void processEndpoint(RegistryRoundContext roundContext, ServerEndpoint endpoint) {
        TypeInfo typeInfo = endpoint.type();
        TypeName generatedType = generatedType(typeInfo.typeName(), "OpenApiEndpoint");
        List<OpenApiSchemaBinding> schemaBindings = schemaBindings(endpoint);
        ClassModel.Builder classModel = sourceClass(typeInfo.typeName(), generatedType);
        addSchemaInjection(classModel, schemaBindings);
        addSupports(classModel, endpoint.listener());

        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .name("describe")
                .addParameter(context -> context
                        .type(OPENAPI_DOCUMENT_CONTEXT)
                        .name("context"))
                .addParameter(document -> document
                        .type(OPENAPI_DOCUMENT_BUILDER)
                        .name("document"))
                .update(it -> endpointSourceBody(endpoint, schemaBindings, it)));

        roundContext.addGeneratedType(generatedType,
                                      classModel,
                                      typeInfo.typeName(),
                                      typeInfo.originatingElementValue());
    }

    private void addSupports(ClassModel.Builder classModel, Optional<String> listener) {
        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeNames.PRIMITIVE_BOOLEAN)
                .name("supports")
                .addParameter(context -> context
                        .type(OPENAPI_DOCUMENT_CONTEXT)
                        .name("context"))
                .addContent("return ")
                .update(it -> listener.ifPresentOrElse(explicit -> it.addContent(expressions.stringLiteral(explicit)),
                                                        () -> it.addContent(WEB_SERVER)
                                                                .addContent(".DEFAULT_SOCKET_NAME")))
                .addContentLine(".equals(context.listener());"));
    }

    private ClassModel.Builder sourceClass(TypeName sourceType, TypeName generatedType) {
        return ClassModel.builder()
                .copyright(CodegenUtil.copyright(GENERATOR, sourceType, generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               sourceType,
                                                               generatedType,
                                                               "1",
                                                               ""))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .type(generatedType)
                .addAnnotation(SINGLETON_ANNOTATION)
                .addAnnotation(DeclarativeTypes.SUPPRESS_API)
                .superType(OPENAPI_SOURCE_BASE);
    }

    private void documentSourceBody(TypeInfo typeInfo, Method.Builder method) {
        Set<Annotation> annotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx, typeInfo));
        Annotation document = Annotations.findFirst(OPENAPI_DOCUMENT_ANNOTATION, annotations)
                .orElseThrow(() -> new CodegenException("Missing @OpenApi.Document on "
                                                                + typeInfo.typeName().fqName()));

        document.stringValue("self")
                .filter(not(String::isBlank))
                .ifPresent(self -> method.addContent("document.self(")
                        .addContent(expressions.stringExpression(self))
                        .addContentLine(");"));
        document.stringValue("jsonSchemaDialect")
                .filter(not(String::isBlank))
                .ifPresent(dialect -> method.addContent("document.jsonSchemaDialect(")
                        .addContent(expressions.stringExpression(dialect))
                        .addContentLine(");"));

        Annotation info = Annotations.findFirst(OPENAPI_INFO_ANNOTATION, annotations)
                .orElseThrow(() -> new CodegenException("@OpenApi.Document on " + typeInfo.typeName().fqName()
                                                                + " requires @OpenApi.Info"));
        addInfo(method, info, annotations);

        String owner = typeInfo.typeName().fqName();
        List<Annotation> servers = repeatableAnnotations(annotations,
                                                         OPENAPI_SERVERS_ANNOTATION,
                                                         OPENAPI_SERVER_ANNOTATION);
        validator.validateServers(owner, servers);
        servers.forEach(server -> writeServer(method, "document.server", true, server));
        List<Annotation> tags = repeatableAnnotations(annotations, OPENAPI_TAGS_ANNOTATION, OPENAPI_TAG_ANNOTATION);
        validator.validateTags(owner, tags);
        tags.forEach(tag -> writeTag(method, tag));
        Annotations.findFirst(OPENAPI_EXTERNAL_DOCS_ANNOTATION, annotations)
                .ifPresent(externalDocs -> writeExternalDocs(method, "document.externalDocs", true, externalDocs));
        List<Annotation> extensions = repeatableAnnotations(annotations,
                                                            OPENAPI_EXTENSIONS_ANNOTATION,
                                                            OPENAPI_EXTENSION_ANNOTATION);
        validator.validateExtensions(owner, extensions);
        extensions.forEach(extension -> writeExtension(method, "document.extension", true, extension));
        List<OpenApiSecurityScheme> securitySchemes = securitySchemeCodegen.securitySchemes(annotations);
        validator.validateSecuritySchemes(owner, securitySchemes);
        securitySchemes.forEach(scheme -> securitySchemeCodegen.writeSecurityScheme(method, owner, scheme));
        List<OpenApiSecurityRequirement> securityRequirements = securityRequirements(owner, annotations);
        validator.validateSecurityRequirements(owner, securityRequirements);
        securityRequirements.forEach(requirement -> writeSecurityRequirement(method,
                                                                            "document.securityRequirement",
                                                                            true,
                                                                            requirement));
    }

    private void endpointSourceBody(ServerEndpoint endpoint,
                                    List<OpenApiSchemaBinding> schemaBindings,
                                    Method.Builder method) {
        String endpointTag = endpointName(endpoint.type().typeName());
        Map<TypeName, String> componentNames = schemas.componentNames(schemaBindings);
        Set<Annotation> endpointAnnotations = endpoint.annotations();
        boolean endpointClearsSecurity = hasEmptySecurityRequirements(endpointAnnotations);
        List<OpenApiSecurityRequirement> endpointSecurityRequirements = securityRequirements(endpoint.type().typeName()
                                                                                                     .fqName(),
                                                                                             endpointAnnotations);
        if (!endpointClearsSecurity) {
            validator.validateSecurityRequirements(endpoint.type().typeName().fqName(), endpointSecurityRequirements);
        }
        for (OpenApiSchemaBinding schemaBinding : schemaBindings) {
            schemas.addSchemaComponent(method, schemaBinding);
        }
        for (RestMethod restMethod : endpoint.methods()) {
            if (hasAnnotation(restMethod.annotations(), OPENAPI_HIDDEN_ANNOTATION)) {
                continue;
            }
            addOperation(method,
                         endpoint,
                         restMethod,
                         endpointTag,
                         componentNames,
                         endpointSecurityRequirements,
                         endpointClearsSecurity);
        }
    }

    private void addInfo(Method.Builder method, Annotation info, Set<Annotation> documentAnnotations) {
        String title = info.stringValue("title")
                .filter(Predicate.not(String::isBlank))
                .orElseThrow(() -> new CodegenException("@OpenApi.Info title is required"));
        String version = info.stringValue("version")
                .filter(Predicate.not(String::isBlank))
                .orElseThrow(() -> new CodegenException("@OpenApi.Info version is required"));
        method.addContent("document.info(info -> info.title(")
                .addContent(expressions.stringExpression(title))
                .addContent(")")
                .addContentLine()
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(".version(")
                .addContent(expressions.stringExpression(version))
                .addContentLine(")");
        info.stringValue("summary")
                .filter(not(String::isBlank))
                .ifPresent(summary -> method.addContent(".summary(")
                        .addContent(expressions.stringExpression(summary))
                        .addContentLine(")"));
        info.stringValue("description")
                .filter(not(String::isBlank))
                .ifPresent(description -> method.addContent(".description(")
                        .addContent(expressions.stringExpression(description))
                        .addContentLine(")"));
        info.stringValue("termsOfService")
                .filter(not(String::isBlank))
                .ifPresent(terms -> method.addContent(".termsOfService(")
                        .addContent(expressions.stringExpression(terms))
                        .addContentLine(")"));
        Annotations.findFirst(OPENAPI_CONTACT_ANNOTATION, documentAnnotations)
                .ifPresent(contact -> addContact(method, contact));
        Annotations.findFirst(OPENAPI_LICENSE_ANNOTATION, documentAnnotations)
                .ifPresent(license -> addLicense(method, license));
        method.addContentLine(");")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void addContact(Method.Builder method, Annotation contact) {
        if (!hasStringValue(contact, "value") && !hasStringValue(contact, "url") && !hasStringValue(contact, "email")) {
            method.addContentLine(".contact(contact -> { })");
            return;
        }
        method.addContent(".contact(contact -> contact")
                .addContentLine()
                .increaseContentPadding()
                .increaseContentPadding();
        contact.stringValue()
                .filter(not(String::isBlank))
                .ifPresent(name -> method.addContent(".name(")
                        .addContent(expressions.stringExpression(name))
                        .addContentLine(")"));
        contact.stringValue("url")
                .filter(not(String::isBlank))
                .ifPresent(url -> method.addContent(".url(")
                        .addContent(expressions.stringExpression(url))
                        .addContentLine(")"));
        contact.stringValue("email")
                .filter(not(String::isBlank))
                .ifPresent(email -> method.addContent(".email(")
                        .addContent(expressions.stringExpression(email))
                        .addContentLine(")"));
        method.addContentLine(")")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void addLicense(Method.Builder method, Annotation license) {
        String name = license.stringValue()
                .filter(not(String::isBlank))
                .orElseThrow(() -> new CodegenException("@OpenApi.License value is required"));
        method.addContent(".license(license -> license.name(")
                .addContent(expressions.stringExpression(name))
                .addContentLine(")")
                .increaseContentPadding()
                .increaseContentPadding();
        license.stringValue("url")
                .filter(not(String::isBlank))
                .ifPresent(url -> method.addContent(".url(")
                        .addContent(expressions.stringExpression(url))
                        .addContentLine(")"));
        license.stringValue("identifier")
                .filter(not(String::isBlank))
                .ifPresent(identifier -> method.addContent(".identifier(")
                        .addContent(expressions.stringExpression(identifier))
                        .addContentLine(")"));
        method.addContentLine(")")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void writeServer(Method.Builder method, String call, boolean statement, Annotation server) {
        String url = server.stringValue()
                .filter(not(String::isBlank))
                .orElseThrow(() -> new CodegenException("@OpenApi.Server value is required"));
        method.addContent(call)
                .addContent("(server -> server.url(")
                .addContent(expressions.stringExpression(url))
                .addContentLine(")")
                .increaseContentPadding()
                .increaseContentPadding();
        server.stringValue("description")
                .filter(not(String::isBlank))
                .ifPresent(description -> method.addContent(".description(")
                        .addContent(expressions.stringExpression(description))
                        .addContentLine(")"));
        server.stringValue("name")
                .filter(not(String::isBlank))
                .ifPresent(name -> method.addContent(".name(")
                        .addContent(expressions.stringExpression(name))
                        .addContentLine(")"));
        method.addContentLine(statement ? ");" : ")")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void writeTag(Method.Builder method, Annotation tag) {
        String name = tag.stringValue()
                .filter(not(String::isBlank))
                .orElseThrow(() -> new CodegenException("@OpenApi.Tag value is required"));
        method.addContent("document.tag(tag -> tag.name(")
                .addContent(expressions.validatedStringExpression(name))
                .addContentLine(")")
                .increaseContentPadding()
                .increaseContentPadding();
        tag.stringValue("description")
                .filter(not(String::isBlank))
                .ifPresent(description -> method.addContent(".description(")
                        .addContent(expressions.stringExpression(description))
                        .addContentLine(")"));
        tag.stringValue("summary")
                .filter(not(String::isBlank))
                .ifPresent(summary -> method.addContent(".summary(")
                        .addContent(expressions.stringExpression(summary))
                        .addContentLine(")"));
        tag.stringValue("parent")
                .filter(not(String::isBlank))
                .ifPresent(parent -> method.addContent(".parent(")
                        .addContent(expressions.stringExpression(parent))
                        .addContentLine(")"));
        tag.stringValue("kind")
                .filter(not(String::isBlank))
                .ifPresent(kind -> method.addContent(".kind(")
                        .addContent(expressions.stringExpression(kind))
                        .addContentLine(")"));
        method.addContentLine(");")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void writeExternalDocs(Method.Builder method, String call, boolean statement, Annotation externalDocs) {
        String url = externalDocs.stringValue()
                .filter(not(String::isBlank))
                .orElseThrow(() -> new CodegenException("@OpenApi.ExternalDocs value is required"));
        method.addContent(call)
                .addContent("(externalDocs -> externalDocs.url(")
                .addContent(expressions.stringExpression(url))
                .addContentLine(")")
                .increaseContentPadding()
                .increaseContentPadding();
        externalDocs.stringValue("description")
                .filter(not(String::isBlank))
                .ifPresent(description -> method.addContent(".description(")
                        .addContent(expressions.stringExpression(description))
                        .addContentLine(")"));
        method.addContentLine(statement ? ");" : ")")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void writeExtension(Method.Builder method, String call, boolean statement, Annotation extension) {
        String name = extension.stringValue("name")
                .filter(not(String::isBlank))
                .orElseThrow(() -> new CodegenException("@OpenApi.Extension name is required"));
        String extensionName = validator.expressionDefaultValue(name);
        if (!extensionName.startsWith("x-")) {
            throw new CodegenException("@OpenApi.Extension name must start with x-: " + extensionName);
        }
        String value = extension.stringValue("value")
                .orElseThrow(() -> new CodegenException("@OpenApi.Extension value is required"));
        method.addContent(call)
                .addContent("(")
                .addContent(expressions.validatedStringExpression(name))
                .addContent(", ")
                .addContent(JSON_STRING)
                .addContent(".create(")
                .addContent(expressions.stringExpression(value))
                .addContentLine(statement ? "));" : "))");
    }

    private void writeSecurityRequirement(Method.Builder method,
                                          String call,
                                          boolean statement,
                                          OpenApiSecurityRequirement requirement) {
        List<Annotation> schemes = requirement.schemes();
        if (schemes.isEmpty()) {
            method.addContent(call)
                    .addContentLine(statement ? "(security -> { });" : "(security -> { })");
            return;
        }
        method.addContent(call)
                .addContent("(security -> security")
                .addContentLine()
                .increaseContentPadding()
                .increaseContentPadding();
        schemes.forEach(scheme -> {
            List<String> scopes = scheme.stringValues("scopes").orElseGet(List::of);
            method.addContent(".scheme(")
                    .addContent(expressions.validatedStringExpression(scheme.stringValue().orElseThrow()))
                    .addContent(", ")
                    .addContent(expressions.validatedStringListExpression(scopes))
                    .addContentLine(")");
        });
        method.addContentLine(statement ? ");" : ")")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void addOperation(Method.Builder method,
                              ServerEndpoint endpoint,
                              RestMethod restMethod,
                              String endpointTag,
                              Map<TypeName, String> componentNames,
                              List<OpenApiSecurityRequirement> endpointSecurityRequirements,
                              boolean endpointClearsSecurity) {
        Optional<Annotation> operation = operationAnnotation(restMethod);
        List<RestMethodParameter> pathParameters = pathParameters(restMethod);
        method.addContent("document.path(")
                .addContent(expressions.stringLiteral(OpenApiPathSupport.openApiPath(endpoint,
                                                                                     restMethod,
                                                                                     operation,
                                                                                     pathParameters)))
                .addContentLine(",")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent("path -> path.operation(")
                .addContent(expressions.stringLiteral(restMethod.httpMethod().name().toLowerCase(Locale.ROOT)))
                .addContentLine(",")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContentLine("operation -> operation")
                .increaseContentPadding()
                .increaseContentPadding();

        operation.ifPresentOrElse(
                operationAnnotation -> addExplicitOperation(method, operationAnnotation, restMethod, endpointTag),
                () -> addInferredOperation(method, restMethod, endpointTag));
        addOperationMetadata(method, restMethod, endpointSecurityRequirements, endpointClearsSecurity);
        addParameters(method, restMethod, componentNames);
        addRequestBody(method, restMethod, componentNames);
        addResponses(method, restMethod, componentNames);

        method.addContentLine("));")
                .decreaseContentPadding()
                .decreaseContentPadding()
                .decreaseContentPadding()
                .decreaseContentPadding()
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void addExplicitOperation(Method.Builder method,
                                      Annotation operation,
                                      RestMethod restMethod,
                                      String endpointTag) {
        operation.stringValue()
                .filter(not(String::isBlank))
                .ifPresent(summary -> method.addContent(".summary(")
                        .addContent(expressions.stringExpression(summary))
                        .addContentLine(")"));
        operation.stringValue("description")
                .filter(not(String::isBlank))
                .ifPresent(description -> method.addContent(".description(")
                        .addContent(expressions.stringExpression(description))
                        .addContentLine(")"));
        Optional<String> operationId = operation.stringValue("operationId")
                .filter(not(String::isBlank))
                .or(() -> Optional.of(operationId(restMethod, endpointTag)));
        addOperationId(method, restMethod, operationId.orElseThrow());
        List<String> tags = operation.stringValues("tags").orElseGet(List::of);
        if (tags.isEmpty()) {
            method.addContent(".tag(")
                    .addContent(expressions.stringLiteral(endpointTag))
                    .addContentLine(")");
        } else {
            validator.validateOperationTags(restMethodDescription(restMethod), tags);
            tags.forEach(tag -> method.addContent(".tag(")
                    .addContent(expressions.validatedStringExpression(tag))
                    .addContentLine(")"));
        }
        operation.booleanValue("deprecated")
                .filter(Boolean::booleanValue)
                .ifPresent(deprecated -> method.addContentLine(".deprecated(true)"));
    }

    private void addOperationMetadata(Method.Builder method,
                                      RestMethod restMethod,
                                      List<OpenApiSecurityRequirement> endpointSecurityRequirements,
                                      boolean endpointClearsSecurity) {
        Set<Annotation> annotations = restMethod.annotations();
        List<Annotation> servers = repeatableAnnotations(annotations,
                                                         OPENAPI_SERVERS_ANNOTATION,
                                                         OPENAPI_SERVER_ANNOTATION);
        validator.validateServers(restMethodDescription(restMethod), servers);
        servers.forEach(server -> writeServer(method, ".server", false, server));
        Annotations.findFirst(OPENAPI_EXTERNAL_DOCS_ANNOTATION, annotations)
                .ifPresent(externalDocs -> writeExternalDocs(method, ".externalDocs", false, externalDocs));
        List<Annotation> extensions = repeatableAnnotations(annotations,
                                                            OPENAPI_EXTENSIONS_ANNOTATION,
                                                            OPENAPI_EXTENSION_ANNOTATION);
        validator.validateExtensions(restMethodDescription(restMethod), extensions);
        extensions.forEach(extension -> writeExtension(method, ".extension", false, extension));
        Set<Annotation> securityAnnotations = operationSecurityAnnotations(restMethod);
        if (hasEmptySecurityRequirements(securityAnnotations)) {
            method.addContentLine(".security(java.util.List.of())");
            return;
        }
        List<OpenApiSecurityRequirement> securityRequirements = securityRequirements(restMethodDescription(restMethod),
                                                                                    securityAnnotations);
        if (!securityRequirements.isEmpty()) {
            validator.validateSecurityRequirements(restMethodDescription(restMethod), securityRequirements);
            securityRequirements.forEach(requirement -> writeSecurityRequirement(method,
                                                                                ".securityRequirement",
                                                                                false,
                                                                                requirement));
            return;
        }
        if (endpointClearsSecurity) {
            method.addContentLine(".security(java.util.List.of())");
            return;
        }
        endpointSecurityRequirements.forEach(requirement -> writeSecurityRequirement(method,
                                                                                    ".securityRequirement",
                                                                                    false,
                                                                                    requirement));
    }

    private Set<Annotation> operationSecurityAnnotations(RestMethod restMethod) {
        Set<Annotation> directAnnotations = new HashSet<>(restMethod.method().annotations());
        if (hasSecurityRequirementAnnotations(directAnnotations)) {
            return directAnnotations;
        }
        return restMethod.annotations();
    }

    private boolean hasSecurityRequirementAnnotations(Set<Annotation> annotations) {
        return hasAnnotation(annotations, OPENAPI_SECURITY_SCHEME_REQUIREMENT_ANNOTATION)
                || hasAnnotation(annotations, OPENAPI_SECURITY_REQUIREMENT_ANNOTATION)
                || hasAnnotation(annotations, OPENAPI_SECURITY_REQUIREMENTS_ANNOTATION);
    }

    private void addInferredOperation(Method.Builder method, RestMethod restMethod, String endpointTag) {
        addOperationId(method, restMethod, operationId(restMethod, endpointTag));
        method.addContent(".tag(")
                .addContent(expressions.stringLiteral(endpointTag))
                .addContentLine(")");
    }

    private void addOperationId(Method.Builder method, RestMethod restMethod, String operationId) {
        method.addContent(".operationId(")
                .addContent(OPENAPI_DOCUMENT_CONTEXT_SUPPORT)
                .addContent(".operationId(context, ")
                .addContent(expressions.stringLiteral(operationSignature(restMethod)))
                .addContent(", ")
                .addContent(expressions.stringExpression(operationId))
                .addContentLine("))");
    }

    private String operationId(RestMethod restMethod, String endpointTag) {
        return endpointTag
                + CodegenUtil.capitalize(restMethod.httpMethod().name().toLowerCase(Locale.ROOT))
                + CodegenUtil.capitalize(restMethod.uniqueName());
    }

    private String operationSignature(RestMethod restMethod) {
        return restMethod.type().typeName().fqName() + "#" + restMethod.method().signature().text();
    }

    private void addParameters(Method.Builder method, RestMethod restMethod, Map<TypeName, String> componentNames) {
        List<Annotation> methodParameters = new ArrayList<>(methodParameterAnnotations(restMethod));
        List<RestMethodParameter> pathParameters = pathParameters(restMethod);
        List<RestMethodParameter> queryParameters = queryParameters(restMethod);
        List<RestMethodParameter> headerParameters = headerParameters(restMethod)
                .stream()
                .filter(parameter -> !isSpecialHeader(parameterName(parameter, "header")))
                .toList();
        List<RestMethodParameter> cookieParameters = cookieParameters(restMethod);
        validator.validateMethodParameters(restMethodDescription(restMethod), methodParameters);
        OpenApiParameterValidation.validateGeneratedParameters(restMethodDescription(restMethod),
                                                              pathParameters,
                                                              queryParameters,
                                                              headerParameters,
                                                              cookieParameters,
                                                              this::parameterName);
        for (RestMethodParameter parameter : pathParameters) {
            addParameter(method,
                         restMethod,
                         parameter,
                         "path",
                         matchingMethodParameters(methodParameters, parameter, "path"),
                         componentNames);
        }
        for (RestMethodParameter parameter : queryParameters) {
            addParameter(method,
                         restMethod,
                         parameter,
                         "query",
                         matchingMethodParameters(methodParameters, parameter, "query"),
                         componentNames);
        }
        for (RestMethodParameter parameter : headerParameters) {
            addParameter(method,
                         restMethod,
                         parameter,
                         "header",
                         matchingMethodParameters(methodParameters, parameter, "header"),
                         componentNames);
        }
        for (RestMethodParameter parameter : cookieParameters) {
            addParameter(method,
                         restMethod,
                         parameter,
                         "cookie",
                         matchingMethodParameters(methodParameters, parameter, "cookie"),
                         componentNames);
        }
        if (!methodParameters.isEmpty()) {
            throw unmatchedMethodParameter(restMethod, methodParameters.getFirst());
        }
    }

    private void addParameter(Method.Builder method,
                              RestMethod restMethod,
                              RestMethodParameter parameter,
                              String in,
                              List<Annotation> methodAnnotations,
                              Map<TypeName, String> componentNames) {
        TypeName type = parameter.typeName();
        TypeName schemaType = schemas.schemaType(type);
        String parameterName = parameterName(parameter, in);
        List<Annotation> parameterAnnotations = repeatableAnnotations(parameter.annotations(),
                                                                      OPENAPI_PARAMETERS_ANNOTATION,
                                                                      OPENAPI_PARAMETER_ANNOTATION);
        validator.validateParameterAnnotations(restMethodDescription(restMethod),
                                               in,
                                               parameterName,
                                               parameterAnnotations);
        List<Annotation> annotations = new ArrayList<>(methodAnnotations);
        annotations.addAll(parameterAnnotations);
        Optional<String> configuredLocation = validatedExplicitStringValue(annotations, "in");
        validateParameterLocation(restMethod, in, configuredLocation);
        String location = configuredLocation.orElse(in);
        Optional<String> configuredName = validatedExplicitStringValue(annotations, "name");
        validateParameterName(restMethod, in, parameterName, configuredName);
        String name = configuredName.orElse(parameterName);
        List<Annotation> contentAnnotations = annotationValues(annotations, "content");
        boolean hasExplicitContent = !contentAnnotations.isEmpty();
        Optional<String> configuredStyle = style(annotations);
        Optional<Boolean> configuredExplode = explode(annotations);
        validator.validateParameterContent(restMethodDescription(restMethod), in, name, contentAnnotations);
        validateParameterContentSerialization(restMethod, in, name, hasExplicitContent, configuredStyle, configuredExplode);
        validateParameterSerialization(restMethod, in, schemaType, configuredStyle, configuredExplode);
        boolean allowReserved = booleanFlag(annotations, "allowReserved");
        validateParameterAllowReserved(restMethod, in, allowReserved);
        Optional<String> example = explicitStringValue(annotations, "example");
        List<Annotation> examples = annotationValues(annotations, "examples");
        validator.validateParameterExamples(restMethodDescription(restMethod), in, name, example, examples);

        method.addContent(".parameter(parameter -> parameter.name(")
                .addContent(expressions.validatedStringExpression(name))
                .addContent(")")
                .addContentLine()
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(".in(")
                .addContent(expressions.validatedStringExpression(location))
                .addContentLine(")")
                .addContent(".required(")
                .addContent(Boolean.toString(required(restMethod, parameter, in, type, schemaType, annotations)))
                .addContentLine(")");
        if (hasExplicitContent) {
            for (Annotation content : contentAnnotations) {
                addContent(method, List.of(DEFAULT_MEDIA_TYPE), content, schemaType, true, componentNames);
            }
        } else {
            method.addContent(".schema(")
                    .addContent(schemas.schemaExpression(schemaType, componentNames))
                    .addContentLine(")");
        }

        explicitStringValue(annotations)
                .ifPresent(description -> method.addContent(".description(")
                        .addContent(expressions.stringExpression(description))
                        .addContentLine(")"));
        configuredStyle
                .or(() -> hasExplicitContent ? Optional.empty() : inferredStyle(schemaType, location))
                .ifPresent(style -> method.addContent(".style(")
                        .addContent(expressions.stringExpression(style))
                        .addContentLine(")"));
        configuredExplode
                .or(() -> hasExplicitContent ? Optional.empty() : inferredExplode(schemaType, location))
                .ifPresent(explode -> method.addContent(".explode(")
                        .addContent(Boolean.toString(explode))
                        .addContentLine(")"));
        if (allowReserved) {
            method.addContentLine(".allowReserved(true)");
        }
        if (booleanFlag(annotations, "deprecated")) {
            method.addContentLine(".deprecated(true)");
        }
        example.ifPresent(it -> method.addContent(".example(")
                        .addContent("exampleValue(")
                        .addContent(expressions.stringExpression(it))
                        .addContentLine("))"));
        addExamples(method, examples);
        method.addContentLine(")")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void addRequestBody(Method.Builder method, RestMethod restMethod, Map<TypeName, String> componentNames) {
        Optional<Annotation> requestBodyMetadata = requestBodyAnnotation(restMethod);
        Optional<RestMethodParameter> entityParameter = entityParameter(restMethod);
        List<RestMethodParameter> formParameters = formParameters(restMethod);
        if (!formParameters.isEmpty()) {
            if (entityParameter.isPresent()) {
                throw new CodegenException("@Http.Entity and @Http.FormParam cannot be combined on declarative"
                                                   + " OpenAPI method " + restMethodDescription(restMethod));
            }
            formRequestBodies.addRequestBody(method,
                                             restMethodDescription(restMethod),
                                             requestBodyMetadata.orElse(null),
                                             restMethod.consumes(),
                                             formParameters,
                                             componentNames);
            return;
        }
        if (entityParameter.isEmpty()) {
            if (requestBodyMetadata.isPresent()) {
                throw new CodegenException("@OpenApi.RequestBody on " + restMethodDescription(restMethod)
                                                   + " requires an @Http.Entity parameter or @Http.FormParam"
                                                   + " parameters");
            }
            return;
        }

        entityParameter.ifPresent(parameter -> {
            Annotation requestBody = requestBodyMetadata.orElse(null);
            TypeName entityType = schemas.schemaType(parameter.typeName());
            List<Annotation> contentAnnotations = requestBody == null
                    ? List.of()
                    : requestBody.annotationValues("content").orElseGet(List::of);
            validator.validateContentMediaTypes("@OpenApi.RequestBody on " + restMethodDescription(restMethod),
                                                contentAnnotations,
                                                restMethod.consumes());
            method.addContentLine(".requestBody(requestBody -> requestBody")
                    .increaseContentPadding()
                    .increaseContentPadding();
            if (requestBody != null) {
                requestBody.stringValue()
                        .filter(not(String::isBlank))
                        .ifPresent(description -> method.addContent(".description(")
                                .addContent(expressions.stringExpression(description))
                                .addContentLine(")"));
            }
            Optional<Boolean> requiredOverride = requestBody == null
                    ? Optional.empty()
                    : required(requestBody);
            boolean required = requiredOverride.orElse(!parameter.typeName().isOptional());
            if (required || requiredOverride.isPresent()) {
                method.addContent(".required(")
                        .addContent(Boolean.toString(required))
                        .addContentLine(")");
            }
            if (contentAnnotations.isEmpty()) {
                for (String mediaType : mediaTypes(restMethod.consumes())) {
                    method.addContent(".content(")
                            .addContent(expressions.validatedStringExpression(mediaType))
                            .addContent(", ")
                            .addContent(schemas.mediaTypeConsumer(schemas.schemaExpression(entityType, componentNames)))
                            .addContentLine(")");
                }
            } else {
                for (Annotation content : contentAnnotations) {
                    addContent(method, restMethod.consumes(), content, entityType, true, componentNames);
                }
            }
            method.addContentLine(")")
                    .decreaseContentPadding()
                    .decreaseContentPadding();
        });
    }

    private void addResponses(Method.Builder method, RestMethod restMethod, Map<TypeName, String> componentNames) {
        List<Annotation> explicitResponses = repeatableAnnotations(restMethod.annotations(),
                                                                    OPENAPI_RESPONSES_ANNOTATION,
                                                                    OPENAPI_RESPONSE_ANNOTATION);
        if (explicitResponses.isEmpty()) {
            addInferredResponses(method, restMethod, componentNames);
            return;
        }
        validator.validateResponses(restMethodDescription(restMethod), explicitResponses);
        for (Annotation response : explicitResponses) {
            addResponse(method, restMethod, response, componentNames);
        }
        if (restMethod.returnType().isOptional() && !hasExplicitResponse(explicitResponses, 404)) {
            addNotFoundResponse(method);
        }
    }

    private void addResponse(Method.Builder method,
                             RestMethod restMethod,
                             Annotation response,
                             Map<TypeName, String> componentNames) {
        int status = response.intValue("status")
                .orElseThrow(() -> new CodegenException("@OpenApi.Response status is required"));
        TypeName responseType = schemas.responseType(restMethod.returnType());
        boolean hasEntity = schemas.hasResponseEntity(restMethod.returnType());
        List<Annotation> contentAnnotations = response.annotationValues("content").orElseGet(List::of);
        validator.validateContentMediaTypes("@OpenApi.Response on " + restMethodDescription(restMethod)
                                                    + " for status " + status,
                                            contentAnnotations,
                                            restMethod.produces());
        method.addContent(".response(")
                .addContent(expressions.stringLiteral(String.valueOf(status)))
                .addContent(", ")
                .addContent("response -> response.description(")
                .addContent(expressions.stringExpression(response.stringValue("description").orElse(statusDescription(status))))
                .addContentLine(")")
                .increaseContentPadding()
                .increaseContentPadding();
        response.stringValue("summary")
                .filter(not(String::isBlank))
                .ifPresent(summary -> method.addContent(".summary(")
                        .addContent(expressions.stringExpression(summary))
                        .addContentLine(")"));
        addResponseHeaders(method, restMethod, response, componentNames);

        for (Annotation content : contentAnnotations) {
            addContent(method, restMethod.produces(), content, responseType, hasEntity, componentNames);
        }

        method.addContentLine(")")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void addInferredResponses(Method.Builder method, RestMethod restMethod, Map<TypeName, String> componentNames) {
        TypeName returnType = restMethod.returnType();
        int status = restMethod.status()
                .map(it -> it.code())
                .orElseGet(() -> returnType.boxed().equals(TypeNames.BOXED_VOID) ? 204 : 200);
        TypeName responseType = schemas.responseType(returnType);
        boolean hasEntity = schemas.hasResponseEntity(returnType);
        method.addContent(".response(")
                .addContent(expressions.stringLiteral(String.valueOf(status)))
                .addContent(", ")
                .addContent("response -> response.description(")
                .addContent(expressions.stringLiteral(restMethod.status()
                                                  .flatMap(it -> it.reason())
                                                  .orElse(statusDescription(status))))
                .addContentLine(")")
                .increaseContentPadding()
                .increaseContentPadding();
        addInferredResponseHeaders(method, restMethod);
        if (hasEntity) {
            for (String mediaType : mediaTypes(restMethod.produces())) {
                method.addContent(".content(")
                        .addContent(expressions.validatedStringExpression(mediaType))
                        .addContent(", ")
                        .addContent(schemas.mediaTypeConsumer(schemas.schemaExpression(responseType, componentNames)))
                        .addContentLine(")");
            }
        }
        method.addContentLine(")")
                .decreaseContentPadding()
                .decreaseContentPadding();

        if (returnType.isOptional()) {
            addNotFoundResponse(method);
        }
    }

    private boolean hasExplicitResponse(List<Annotation> explicitResponses, int status) {
        for (Annotation response : explicitResponses) {
            if (response.intValue("status").orElse(-1) == status) {
                return true;
            }
        }
        return false;
    }

    private void addNotFoundResponse(Method.Builder method) {
        method.addContentLine(".response(\"404\", response -> response.description(\"Not Found\"))");
    }

    private void addResponseHeaders(Method.Builder method,
                                    RestMethod restMethod,
                                    Annotation response,
                                    Map<TypeName, String> componentNames) {
        List<Annotation> explicitHeaders = response.annotationValues("headers").orElseGet(List::of);
        validator.validateResponseHeaders(restMethodDescription(restMethod),
                                          explicitHeaders,
                                          inferredResponseHeaderNames(restMethod));
        addInferredResponseHeaders(method, restMethod);
        explicitHeaders.forEach(header -> addResponseHeader(method, restMethod, header, componentNames));
    }

    private List<String> inferredResponseHeaderNames(RestMethod restMethod) {
        List<String> names = new ArrayList<>();
        restMethod.headers()
                .stream()
                .map(HeaderValue::name)
                .filter(header -> !isContentTypeHeader(header))
                .forEach(names::add);
        restMethod.computedHeaders()
                .stream()
                .map(ComputedHeader::headerName)
                .filter(header -> !isContentTypeHeader(header))
                .forEach(names::add);
        return names;
    }

    private void addInferredResponseHeaders(Method.Builder method, RestMethod restMethod) {
        restMethod.headers()
                .stream()
                .filter(header -> !isContentTypeHeader(header.name()))
                .forEach(header -> addResponseHeader(method, header));
        restMethod.computedHeaders()
                .stream()
                .filter(header -> !isContentTypeHeader(header.headerName()))
                .forEach(header -> addResponseHeader(method, header));
    }

    private void addResponseHeader(Method.Builder method, HeaderValue header) {
        method.addContent(".header(")
                .addContent(expressions.stringLiteral(header.name()))
                .addContent(", header -> header.required(true).schema(")
                .addContent(schemas.stringSchemaWithDefaultExpression(header.value()))
                .addContentLine("))");
    }

    private void addResponseHeader(Method.Builder method, ComputedHeader header) {
        method.addContent(".header(")
                .addContent(expressions.stringLiteral(header.headerName()))
                .addContent(", header -> header.schema(")
                .addContent(schemas.schemaExpression(TypeNames.STRING))
                .addContentLine("))");
    }

    private void addResponseHeader(Method.Builder method,
                                   RestMethod restMethod,
                                   Annotation header,
                                   Map<TypeName, String> componentNames) {
        String name = header.stringValue("name")
                .filter(not(String::isBlank))
                .orElseThrow(() -> new CodegenException("@OpenApi.Header name is required"));
        List<Annotation> contentAnnotations = header.annotationValues("content").orElseGet(List::of);
        TypeName schemaType = header.typeValue("schema")
                .filter(Predicate.not(VOID::equals))
                .orElse(TypeNames.STRING);

        method.addContent(".header(")
                .addContent(expressions.validatedStringExpression(name))
                .addContentLine(", header -> header")
                .increaseContentPadding()
                .increaseContentPadding();
        header.stringValue()
                .filter(not(String::isBlank))
                .ifPresent(description -> method.addContent(".description(")
                        .addContent(expressions.stringExpression(description))
                        .addContentLine(")"));
        required(header)
                .ifPresent(required -> method.addContent(".required(")
                        .addContent(Boolean.toString(required))
                        .addContentLine(")"));
        header.booleanValue("deprecated")
                .filter(Boolean::booleanValue)
                .ifPresent(deprecated -> method.addContentLine(".deprecated(true)"));
        if (contentAnnotations.isEmpty()) {
            method.addContent(".schema(")
                    .addContent(schemas.schemaExpression(schemaType, componentNames))
                    .addContentLine(")");
        } else {
            for (Annotation content : contentAnnotations) {
                addContent(method, List.of(DEFAULT_MEDIA_TYPE), content, schemaType, true, componentNames);
            }
        }
        method.addContentLine(")")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void addContent(Method.Builder method,
                            List<String> inferredMediaTypes,
                            Annotation content,
                            TypeName inferredSchemaType,
                            boolean hasInferredSchema,
                            Map<TypeName, String> componentNames) {
        List<String> mediaTypes = validator.contentMediaTypes(content, inferredMediaTypes);
        for (String mediaType : mediaTypes) {
            method.addContent(".content(")
                    .addContent(expressions.validatedStringExpression(mediaType))
                    .addContent(", ")
                    .addContent(schemas.mediaTypeConsumer(content, inferredSchemaType, hasInferredSchema, componentNames))
                    .addContentLine(")");
        }
    }

    private List<OpenApiSchemaBinding> schemaBindings(ServerEndpoint endpoint) {
        Set<TypeName> schemaTypes = new LinkedHashSet<>();
        for (RestMethod restMethod : endpoint.methods()) {
            if (hasAnnotation(restMethod.annotations(), OPENAPI_HIDDEN_ANNOTATION)) {
                continue;
            }
            collectOperationSchemaComponents(schemaTypes, restMethod);
        }

        List<OpenApiSchemaBinding> result = new ArrayList<>();
        Set<String> usedSchemaNames = new HashSet<>();
        Set<String> usedFieldNames = new HashSet<>();
        for (TypeName schemaType : schemaTypes) {
            String schemaName = schemas.uniqueSchemaName(schemas.schemaName(schemaType), usedSchemaNames);
            result.add(new OpenApiSchemaBinding(schemaType,
                                         schemaName,
                                         schemas.uniqueFieldName(schemas.schemaFieldName(schemaName), usedFieldNames)));
        }
        return result;
    }

    private void collectOperationSchemaComponents(Set<TypeName> schemaTypes, RestMethod restMethod) {
        List<Annotation> methodParameters = methodParameterAnnotations(restMethod);
        pathParameters(restMethod)
                .forEach(parameter -> collectParameterSchemaComponents(schemaTypes,
                                                                       parameter,
                                                                       "path",
                                                                       methodParameters));
        queryParameters(restMethod)
                .forEach(parameter -> collectParameterSchemaComponents(schemaTypes,
                                                                       parameter,
                                                                       "query",
                                                                       methodParameters));
        headerParameters(restMethod).stream()
                .filter(parameter -> !isSpecialHeader(parameterName(parameter, "header")))
                .forEach(parameter -> collectParameterSchemaComponents(schemaTypes,
                                                                       parameter,
                                                                       "header",
                                                                       methodParameters));
        cookieParameters(restMethod)
                .forEach(parameter -> collectParameterSchemaComponents(schemaTypes,
                                                                       parameter,
                                                                       "cookie",
                                                                       methodParameters));
        formParameters(restMethod)
                .forEach(parameter -> schemas.collectSchemaComponent(schemaTypes, parameter.typeName()));
        entityParameter(restMethod).ifPresent(parameter -> {
            TypeName inferredSchemaType = schemas.schemaType(parameter.typeName());
            List<Annotation> contentAnnotations = requestBodyAnnotation(restMethod)
                    .flatMap(requestBody -> requestBody.annotationValues("content"))
                    .orElseGet(List::of);
            if (contentAnnotations.isEmpty()) {
                schemas.collectSchemaComponent(schemaTypes, inferredSchemaType);
            } else {
                contentAnnotations.forEach(content -> collectContentSchemaComponent(schemaTypes,
                                                                                   content,
                                                                                   inferredSchemaType,
                                                                                   true));
            }
        });

        TypeName responseType = schemas.responseType(restMethod.returnType());
        boolean hasResponseEntity = schemas.hasResponseEntity(restMethod.returnType());
        List<Annotation> explicitResponses = repeatableAnnotations(restMethod.annotations(),
                                                                    OPENAPI_RESPONSES_ANNOTATION,
                                                                    OPENAPI_RESPONSE_ANNOTATION);
        if (explicitResponses.isEmpty()) {
            if (hasResponseEntity) {
                schemas.collectSchemaComponent(schemaTypes, responseType);
            }
            return;
        }

        for (Annotation response : explicitResponses) {
            List<Annotation> contentAnnotations = response.annotationValues("content").orElseGet(List::of);
            for (Annotation content : contentAnnotations) {
                collectContentSchemaComponent(schemaTypes, content, responseType, hasResponseEntity);
            }
            response.annotationValues("headers")
                    .orElseGet(List::of)
                    .forEach(header -> collectHeaderSchemaComponent(schemaTypes, header));
        }
    }

    private void collectHeaderSchemaComponent(Set<TypeName> schemaTypes, Annotation header) {
        Optional<TypeName> explicitSchema = header.typeValue("schema")
                .filter(Predicate.not(VOID::equals));
        explicitSchema.ifPresent(schemaType -> schemas.collectSchemaComponent(schemaTypes, schemaType));
        TypeName inferredSchemaType = explicitSchema.orElse(TypeNames.STRING);
        header.annotationValues("content")
                .orElseGet(List::of)
                .forEach(content -> collectContentSchemaComponent(schemaTypes, content, inferredSchemaType, true));
    }

    private void collectParameterSchemaComponents(Set<TypeName> schemaTypes,
                                                  RestMethodParameter parameter,
                                                  String in,
                                                  List<Annotation> methodParameters) {
        List<Annotation> annotations = new ArrayList<>(matchingMethodParameters(methodParameters, parameter, in, false));
        annotations.addAll(repeatableAnnotations(parameter.annotations(),
                                                 OPENAPI_PARAMETERS_ANNOTATION,
                                                 OPENAPI_PARAMETER_ANNOTATION));
        TypeName inferredSchemaType = schemas.schemaType(parameter.typeName());
        List<Annotation> contentAnnotations = annotationValues(annotations, "content");
        if (contentAnnotations.isEmpty()) {
            schemas.collectSchemaComponent(schemaTypes, inferredSchemaType);
        } else {
            contentAnnotations.forEach(content -> collectContentSchemaComponent(schemaTypes,
                                                                               content,
                                                                               inferredSchemaType,
                                                                               true));
        }
    }

    private void collectContentSchemaComponent(Set<TypeName> schemaTypes,
                                               Annotation content,
                                               TypeName inferredSchemaType,
                                               boolean hasInferredSchema) {
        Optional<TypeName> explicitSchema = content.typeValue("schema")
                .filter(Predicate.not(VOID::equals));
        if (explicitSchema.isPresent() || hasInferredSchema) {
            schemas.collectSchemaComponent(schemaTypes, explicitSchema.orElse(inferredSchemaType));
        }
        content.typeValue("itemSchema")
                .filter(Predicate.not(VOID::equals))
                .ifPresent(itemSchema -> schemas.collectSchemaComponent(schemaTypes, itemSchema));
    }

    private List<RestMethodParameter> pathParameters(RestMethod restMethod) {
        return parameters(restMethod.pathParameters(), restMethod, HTTP_PATH_PARAM_ANNOTATION);
    }

    private List<RestMethodParameter> queryParameters(RestMethod restMethod) {
        return parameters(restMethod.queryParameters(), restMethod, HTTP_QUERY_PARAM_ANNOTATION);
    }

    private List<RestMethodParameter> headerParameters(RestMethod restMethod) {
        return parameters(restMethod.headerParameters(), restMethod, HTTP_HEADER_PARAM_ANNOTATION);
    }

    private List<RestMethodParameter> cookieParameters(RestMethod restMethod) {
        return parameters(annotatedParameters(restMethod, HTTP_COOKIE_PARAM_ANNOTATION),
                          restMethod,
                          HTTP_COOKIE_PARAM_ANNOTATION);
    }

    private List<RestMethodParameter> formParameters(RestMethod restMethod) {
        return parameters(annotatedParameters(restMethod, HTTP_FORM_PARAM_ANNOTATION),
                          restMethod,
                          HTTP_FORM_PARAM_ANNOTATION);
    }

    private Optional<RestMethodParameter> entityParameter(RestMethod restMethod) {
        return restMethod.entityParameter()
                .or(() -> requestParamsParameters(restMethod, HTTP_ENTITY_ANNOTATION)
                        .stream()
                        .findFirst());
    }

    private List<RestMethodParameter> annotatedParameters(RestMethod restMethod, TypeName annotation) {
        return restMethod.parameters()
                .stream()
                .filter(parameter -> Annotations.findFirst(annotation, parameter.annotations()).isPresent())
                .toList();
    }

    private List<RestMethodParameter> parameters(List<RestMethodParameter> directParameters,
                                                 RestMethod restMethod,
                                                 TypeName annotation) {
        List<RestMethodParameter> result = new ArrayList<>(directParameters);
        result.addAll(requestParamsParameters(restMethod, annotation));
        return result;
    }

    private List<RestMethodParameter> requestParamsParameters(RestMethod restMethod, TypeName annotation) {
        List<RestMethodParameter> result = new ArrayList<>();
        for (RestMethodParameter parameter : restMethod.parameters()) {
            if (Annotations.findFirst(HTTP_REQUEST_PARAMS_ANNOTATION, parameter.annotations()).isEmpty()) {
                continue;
            }
            TypeInfo requestParamsType = HttpCodegenValidation.requestParamsRecordType(
                    ctx::typeInfo,
                    parameter.typeName(),
                    parameter.parameter().originatingElementValue());
            HttpCodegenValidation.validateRequestParamsBodyComponents(requestParamsType);
            for (TypedElementInfo component : HttpCodegenValidation.requestParamsComponents(requestParamsType)) {
                if (Annotations.findFirst(annotation, component.annotations()).isPresent()) {
                    result.add(componentParameter(restMethod, parameter, component));
                }
            }
        }
        return result;
    }

    private RestMethodParameter componentParameter(RestMethod restMethod,
                                                  RestMethodParameter requestParamsParameter,
                                                  TypedElementInfo component) {
        return RestMethodParameter.builder()
                .annotations(new HashSet<>(component.annotations()))
                .name(component.elementName())
                .typeName(component.typeName())
                .index(requestParamsParameter.index())
                .method(restMethod.method())
                .type(restMethod.type())
                .parameter(component)
                .build();
    }

    private void addSchemaInjection(ClassModel.Builder classModel, List<OpenApiSchemaBinding> schemaBindings) {
        if (schemaBindings.isEmpty()) {
            return;
        }
        for (OpenApiSchemaBinding schemaBinding : schemaBindings) {
            classModel.addField(field -> field
                    .accessModifier(AccessModifier.PRIVATE)
                    .isFinal(true)
                    .type(JSON_SCHEMA_PROVIDER)
                    .name(schemaBinding.fieldName()));
        }

        classModel.addConstructor(ctr -> {
            ctr.accessModifier(AccessModifier.PACKAGE_PRIVATE)
                    .addAnnotation(Annotation.create(SERVICE_ANNOTATION_INJECT));
            for (OpenApiSchemaBinding schemaBinding : schemaBindings) {
                ctr.addParameter(parameter -> parameter
                                .type(JSON_SCHEMA_PROVIDER)
                                .addAnnotation(Annotation.builder()
                                                       .typeName(SERVICE_ANNOTATION_NAMED_BY_TYPE)
                                                       .property("value", schemaBinding.type())
                                                       .build())
                                .name(schemaBinding.fieldName()))
                        .addContent("this.")
                        .addContent(schemaBinding.fieldName())
                        .addContent(" = ")
                        .addContent(schemaBinding.fieldName())
                        .addContentLine(";");
            }
        });
    }

    private String parameterName(RestMethodParameter parameter, String in) {
        TypeName annotationType = switch (in) {
        case "path" -> HTTP_PATH_PARAM_ANNOTATION;
        case "query" -> HTTP_QUERY_PARAM_ANNOTATION;
        case "header" -> HTTP_HEADER_PARAM_ANNOTATION;
        case "cookie" -> HTTP_COOKIE_PARAM_ANNOTATION;
        case "form" -> HTTP_FORM_PARAM_ANNOTATION;
        default -> throw new CodegenException("Unsupported OpenAPI parameter location: " + in);
        };
        return Annotations.findFirst(annotationType, parameter.annotations())
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank))
                .orElse(parameter.name());
    }

    private boolean required(RestMethod restMethod,
                             RestMethodParameter parameter,
                             String in,
                             TypeName type,
                             TypeName schemaType,
                             List<Annotation> annotations) {
        Optional<Boolean> explicit = required(annotations);
        if ("path".equals(in)) {
            if (explicit.filter(Predicate.not(Boolean::booleanValue)).isPresent()) {
                throw new CodegenException("@OpenApi.Parameter on " + restMethodDescription(restMethod)
                                                   + " cannot make a path parameter optional");
            }
            return true;
        }
        boolean required = parameterRequired(type, parameter.annotations());
        if (required && explicit.filter(Predicate.not(Boolean::booleanValue)).isPresent()
                && ("query".equals(in) || "header".equals(in) || "cookie".equals(in))) {
            throw new CodegenException("@OpenApi.Parameter on " + restMethodDescription(restMethod)
                                               + " cannot make a required " + in + " parameter optional");
        }
        return explicit.orElse(required);
    }

    private boolean formParameterRequired(RestMethodParameter parameter) {
        return parameterRequired(parameter.typeName(), parameter.annotations());
    }

    private boolean parameterRequired(TypeName type, Collection<Annotation> annotations) {
        if (type.isOptional()) {
            return false;
        }

        return DefaultsCodegen.findDefault(new HashSet<>(annotations), type).isEmpty();
    }

    private void validateParameterLocation(RestMethod restMethod, String in, Optional<String> location) {
        location.filter(not(in::equals))
                .ifPresent(it -> {
                    throw new CodegenException("@OpenApi.Parameter on " + restMethodDescription(restMethod)
                                                       + " cannot document a " + in + " parameter as " + it);
                });
    }

    private void validateParameterName(RestMethod restMethod,
                                       String in,
                                       String parameterName,
                                       Optional<String> configuredName) {
        configuredName.filter(not(parameterName::equals))
                .ifPresent(it -> {
                    throw new CodegenException("@OpenApi.Parameter on " + restMethodDescription(restMethod)
                                                       + " cannot document a " + in + " parameter named "
                                                       + parameterName + " as " + it);
                });
    }

    private void validateParameterAllowReserved(RestMethod restMethod, String in, boolean allowReserved) {
        if (allowReserved && !"query".equals(in)) {
            throw new CodegenException("@OpenApi.Parameter on " + restMethodDescription(restMethod)
                                               + " cannot use allowReserved=true for a " + in + " parameter");
        }
    }

    private void validateParameterContentSerialization(RestMethod restMethod,
                                                       String in,
                                                       String name,
                                                       boolean hasExplicitContent,
                                                       Optional<String> configuredStyle,
                                                       Optional<Boolean> configuredExplode) {
        if (!hasExplicitContent) {
            return;
        }
        if (configuredStyle.isPresent()) {
            throw new CodegenException("@OpenApi.Parameter on " + restMethodDescription(restMethod)
                                               + " cannot define style when content is defined for " + in
                                               + " parameter " + name);
        }
        if (configuredExplode.isPresent()) {
            throw new CodegenException("@OpenApi.Parameter on " + restMethodDescription(restMethod)
                                               + " cannot define explode when content is defined for " + in
                                               + " parameter " + name);
        }
    }

    private void validateParameterSerialization(RestMethod restMethod,
                                                String in,
                                                TypeName schemaType,
                                                Optional<String> style,
                                                Optional<Boolean> explode) {
        style.ifPresent(it -> validateParameterStyle(restMethod, in, schemaType, it));
        if ("header".equals(in) && explode.filter(Boolean::booleanValue).isPresent()) {
            throw new CodegenException("@OpenApi.Parameter on " + restMethodDescription(restMethod)
                                               + " cannot use explode=true for a header parameter");
        }
        if ("query".equals(in)
                && style.filter(OpenApiSourceGenerator::isDelimitedQueryStyle).isPresent()
                && explode.filter(Boolean::booleanValue).isPresent()) {
            throw new CodegenException("@OpenApi.Parameter on " + restMethodDescription(restMethod)
                                               + " cannot use explode=true with " + style.orElseThrow()
                                               + " style for a query parameter");
        }
    }

    private void validateParameterStyle(RestMethod restMethod, String in, TypeName schemaType, String style) {
        switch (in) {
        case "path" -> {
            if (!"simple".equals(style)) {
                throw unsupportedStyle(restMethod, in, style);
            }
        }
        case "query" -> {
            switch (style) {
            case "pipeDelimited", "spaceDelimited" -> {
                if (!schemaType.isList()) {
                    throw new CodegenException("@OpenApi.Parameter on " + restMethodDescription(restMethod)
                                                       + " cannot use " + style + " style for a scalar query parameter");
                }
            }
            case "deepObject" -> throw new CodegenException("@OpenApi.Parameter on " + restMethodDescription(restMethod)
                                                                    + " cannot use deepObject style because declarative"
                                                                    + " HTTP parameters do not support deep object binding");
            default -> {
                if (!"form".equals(style)) {
                    throw unsupportedStyle(restMethod, in, style);
                }
            }
            }
        }
        case "header" -> {
            if (!"simple".equals(style)) {
                throw unsupportedStyle(restMethod, in, style);
            }
        }
        case "cookie" -> {
            if (!"form".equals(style)) {
                throw unsupportedStyle(restMethod, in, style);
            }
        }
        default -> throw new CodegenException("Unsupported OpenAPI parameter location: " + in);
        }
    }

    private CodegenException unsupportedStyle(RestMethod restMethod, String in, String style) {
        return new CodegenException("@OpenApi.Parameter on " + restMethodDescription(restMethod)
                                            + " cannot use " + style + " style for a " + in + " parameter");
    }

    private static boolean isDelimitedQueryStyle(String style) {
        return "pipeDelimited".equals(style) || "spaceDelimited".equals(style);
    }

    private List<String> mediaTypes(List<String> mediaTypes) {
        return mediaTypes.isEmpty() ? List.of(DEFAULT_MEDIA_TYPE) : mediaTypes;
    }

    private boolean isSpecialHeader(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return "accept".equals(normalized)
                || "content-type".equals(normalized)
                || "authorization".equals(normalized);
    }

    private boolean isContentTypeHeader(String name) {
        return "content-type".equals(name.toLowerCase(Locale.ROOT));
    }

    private Optional<Annotation> operationAnnotation(RestMethod method) {
        return Annotations.findFirst(OPENAPI_OPERATION_ANNOTATION, method.annotations());
    }

    private Optional<Annotation> requestBodyAnnotation(RestMethod method) {
        return Annotations.findFirst(OPENAPI_REQUEST_BODY_ANNOTATION, method.annotations());
    }

    private List<Annotation> methodParameterAnnotations(RestMethod method) {
        return repeatableAnnotations(method.annotations(), OPENAPI_PARAMETERS_ANNOTATION, OPENAPI_PARAMETER_ANNOTATION);
    }

    private List<Annotation> matchingMethodParameters(List<Annotation> methodParameters,
                                                      RestMethodParameter parameter,
                                                      String in) {
        return matchingMethodParameters(methodParameters, parameter, in, true);
    }

    private List<Annotation> matchingMethodParameters(List<Annotation> methodParameters,
                                                      RestMethodParameter parameter,
                                                      String in,
                                                      boolean remove) {
        List<Annotation> result = new ArrayList<>();
        String name = parameterName(parameter, in);
        for (Iterator<Annotation> iterator = methodParameters.iterator(); iterator.hasNext();) {
            Annotation annotation = iterator.next();
            Optional<String> annotationName = validatedStringValue(annotation, "name");
            Optional<String> annotationIn = validatedStringValue(annotation, "in");
            if (annotationName.filter(name::equals).isPresent() && annotationIn.filter(in::equals).isPresent()) {
                result.add(annotation);
                if (remove) {
                    iterator.remove();
                }
            }
        }
        return result;
    }

    private CodegenException unmatchedMethodParameter(RestMethod restMethod, Annotation annotation) {
        Optional<String> name = validatedStringValue(annotation, "name");
        Optional<String> in = validatedStringValue(annotation, "in");
        if (name.isEmpty() || in.isEmpty()) {
            return new CodegenException("Method-level @OpenApi.Parameter on " + restMethodDescription(restMethod)
                                                + " must declare non-blank name and in values");
        }
        return new CodegenException("Method-level @OpenApi.Parameter on " + restMethodDescription(restMethod)
                                            + " does not match a generated parameter: " + in.get() + " " + name.get());
    }

    private String restMethodDescription(RestMethod restMethod) {
        return restMethod.type().typeName().fqName() + "." + restMethod.name();
    }

    private Optional<String> explicitStringValue(List<Annotation> annotations) {
        return explicitStringValue(annotations, "value");
    }

    private Optional<String> explicitStringValue(List<Annotation> annotations, String property) {
        for (int i = annotations.size() - 1; i >= 0; i--) {
            Optional<String> value = "value".equals(property)
                    ? annotations.get(i).stringValue()
                    : annotations.get(i).stringValue(property);
            if (value.filter(not(String::isBlank)).isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private Optional<String> validatedExplicitStringValue(List<Annotation> annotations, String property) {
        for (int i = annotations.size() - 1; i >= 0; i--) {
            Optional<String> value = validatedStringValue(annotations.get(i), property);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private Optional<String> validatedStringValue(Annotation annotation, String property) {
        Optional<String> value = "value".equals(property)
                ? annotation.stringValue()
                : annotation.stringValue(property);
        return value.map(validator::expressionDefaultValue)
                .filter(not(String::isBlank));
    }

    private List<Annotation> annotationValues(List<Annotation> annotations, String property) {
        List<Annotation> result = new ArrayList<>();
        annotations.forEach(annotation -> annotation.annotationValues(property).ifPresent(result::addAll));
        return result;
    }

    private boolean booleanFlag(List<Annotation> annotations, String property) {
        return annotations.stream()
                .flatMap(annotation -> annotation.booleanValue(property).stream())
                .anyMatch(Boolean::booleanValue);
    }

    private Optional<Boolean> required(List<Annotation> annotations) {
        for (int i = annotations.size() - 1; i >= 0; i--) {
            Optional<Boolean> required = required(annotations.get(i));
            if (required.isPresent()) {
                return required;
            }
        }
        return Optional.empty();
    }

    private Optional<Boolean> required(Annotation annotation) {
        return triState(annotation, "required", "Required");
    }

    private Optional<Boolean> explode(List<Annotation> annotations) {
        for (int i = annotations.size() - 1; i >= 0; i--) {
            Optional<Boolean> explode = triState(annotations.get(i), "explode", "Explode");
            if (explode.isPresent()) {
                return explode;
            }
        }
        return Optional.empty();
    }

    private Optional<Boolean> triState(Annotation annotation, String property, String enumName) {
        return annotation.stringValue(property)
                .map(this::enumName)
                .flatMap(value -> switch (value) {
                case "UNSPECIFIED" -> Optional.empty();
                case "TRUE" -> Optional.of(true);
                case "FALSE" -> Optional.of(false);
                default -> throw new CodegenException("@OpenApi." + enumName + " has unsupported value: " + value);
                });
    }

    private Optional<String> style(List<Annotation> annotations) {
        for (int i = annotations.size() - 1; i >= 0; i--) {
            Optional<String> style = annotations.get(i).stringValue("style")
                    .map(this::enumName)
                    .filter(value -> !"UNSPECIFIED".equals(value))
                    .map(this::styleName);
            if (style.isPresent()) {
                return style;
            }
        }
        return Optional.empty();
    }

    private String enumName(String value) {
        int dot = value.lastIndexOf('.');
        return dot == -1 ? value : value.substring(dot + 1);
    }

    private String styleName(String style) {
        return switch (style) {
        case "MATRIX" -> "matrix";
        case "LABEL" -> "label";
        case "FORM" -> "form";
        case "SIMPLE" -> "simple";
        case "SPACE_DELIMITED" -> "spaceDelimited";
        case "PIPE_DELIMITED" -> "pipeDelimited";
        case "DEEP_OBJECT" -> "deepObject";
        default -> throw new CodegenException("@OpenApi.Style has unsupported value: " + style);
        };
    }

    private Optional<String> inferredStyle(TypeName schemaType, String in) {
        if (!schemaType.isList()) {
            return Optional.empty();
        }
        return switch (in) {
        case "query", "cookie" -> Optional.of("form");
        case "header" -> Optional.of("simple");
        default -> Optional.empty();
        };
    }

    private Optional<Boolean> inferredExplode(TypeName schemaType, String in) {
        if (!schemaType.isList()) {
            return Optional.empty();
        }
        return switch (in) {
        case "query", "cookie" -> Optional.of(true);
        case "header" -> Optional.of(false);
        default -> Optional.empty();
        };
    }

    private void addExamples(Method.Builder method, List<Annotation> examples) {
        for (int i = 0; i < examples.size(); i++) {
            Annotation example = examples.get(i);
            method.addContent(".example(")
                    .addContent(expressions.validatedStringExpression(validator.exampleName(example, i)))
                    .addContent(", ")
                    .addContent(exampleExpression(example))
                    .addContentLine(")");
        }
    }

    private String examplesExpression(List<Annotation> examples) {
        StringBuilder result = new StringBuilder();
        addExamples(result, examples);
        return result.toString();
    }

    private void addExamples(StringBuilder builder, List<Annotation> examples) {
        for (int i = 0; i < examples.size(); i++) {
            Annotation example = examples.get(i);
            builder.append(".example(")
                    .append(expressions.validatedStringExpression(validator.exampleName(example, i)))
                    .append(", ")
                    .append(exampleExpression(example))
                    .append(")");
        }
    }

    private String exampleExpression(Annotation example) {
        StringBuilder result = new StringBuilder(OPENAPI_DOCUMENT_EXAMPLE.fqName()).append(".builder()");
        example.stringValue("summary")
                .filter(not(String::isBlank))
                .ifPresent(summary -> result.append(".summary(")
                        .append(expressions.stringExpression(summary))
                        .append(")"));
        example.stringValue("description")
                .filter(not(String::isBlank))
                .ifPresent(description -> result.append(".description(")
                        .append(expressions.stringExpression(description))
                        .append(")"));
        example.stringValue("value")
                .filter(not(String::isBlank))
                .ifPresent(value -> result.append(".value(")
                        .append("exampleValue(")
                        .append(expressions.stringExpression(value))
                        .append("))"));
        example.stringValue("dataValue")
                .filter(not(String::isBlank))
                .ifPresent(value -> result.append(".dataValue(")
                        .append("exampleValue(")
                        .append(expressions.stringExpression(value))
                        .append("))"));
        example.stringValue("serializedValue")
                .filter(not(String::isBlank))
                .ifPresent(value -> result.append(".serializedValue(")
                        .append(expressions.stringExpression(value))
                        .append(")"));
        example.stringValue("externalValue")
                .filter(not(String::isBlank))
                .ifPresent(value -> result.append(".externalValue(")
                        .append(expressions.stringExpression(value))
                        .append(")"));
        return result.append(".build()").toString();
    }

    private static boolean hasAnnotation(Set<Annotation> annotations, TypeName annotationType) {
        return Annotations.findFirst(annotationType, annotations).isPresent();
    }

    private boolean hasEmptySecurityRequirements(Set<Annotation> annotations) {
        return Annotations.findFirst(OPENAPI_SECURITY_REQUIREMENTS_ANNOTATION, annotations)
                .flatMap(Annotation::annotationValues)
                .filter(List::isEmpty)
                .isPresent()
                && Annotations.findFirst(OPENAPI_SECURITY_REQUIREMENT_ANNOTATION, annotations).isEmpty()
                && Annotations.findFirst(OPENAPI_SECURITY_SCHEME_REQUIREMENT_ANNOTATION, annotations).isEmpty();
    }

    private List<OpenApiSecurityRequirement> securityRequirements(String owner, Set<Annotation> annotations) {
        Optional<Annotation> direct = Annotations.findFirst(OPENAPI_SECURITY_SCHEME_REQUIREMENT_ANNOTATION,
                                                            annotations);
        Optional<Annotation> container = Annotations.findFirst(OPENAPI_SECURITY_REQUIREMENTS_ANNOTATION, annotations);
        Optional<Annotation> requirement = Annotations.findFirst(OPENAPI_SECURITY_REQUIREMENT_ANNOTATION, annotations);

        if (direct.isPresent()) {
            if (container.isPresent() || requirement.isPresent()) {
                throw new CodegenException("@OpenApi.SecuritySchemeRequirement on " + owner
                                                   + " cannot be combined with @OpenApi.SecurityRequirement or "
                                                   + "@OpenApi.SecurityRequirements");
            }
            return List.of(new OpenApiSecurityRequirement(List.of(direct.get())));
        }

        List<OpenApiSecurityRequirement> result = new ArrayList<>();
        if (container.isPresent()) {
            container.get()
                    .annotationValues()
                    .orElseGet(List::of)
                    .forEach(it -> result.add(new OpenApiSecurityRequirement(it.annotationValues()
                            .orElseGet(List::of))));
        } else {
            requirement.ifPresent(it -> result.add(new OpenApiSecurityRequirement(it.annotationValues()
                    .orElseGet(List::of))));
        }
        return result;
    }

    // Package-private for direct testing without reflection.
    static List<Annotation> repeatableAnnotations(Set<Annotation> annotations,
                                                  TypeName containerType,
                                                  TypeName annotationType) {
        List<Annotation> result = new ArrayList<>();
        Annotations.findFirst(containerType, annotations)
                .flatMap(Annotation::annotationValues)
                .ifPresent(result::addAll);
        Annotations.findFirst(annotationType, annotations)
                .ifPresent(result::add);
        return result;
    }

    private boolean hasStringValue(Annotation annotation, String property) {
        Optional<String> value = "value".equals(property)
                ? annotation.stringValue()
                : annotation.stringValue(property);
        return value.filter(not(String::isBlank)).isPresent();
    }

    private TypeName generatedType(TypeName sourceType, String suffix) {
        return TypeName.builder()
                .packageName(sourceType.packageName())
                .className(sourceType.classNameWithEnclosingNames().replace('.', '_') + "__" + suffix + "Source")
                .build();
    }

    private String endpointName(TypeName typeName) {
        String className = typeName.className();
        if (className.endsWith("Endpoint") && className.length() > "Endpoint".length()) {
            className = className.substring(0, className.length() - "Endpoint".length());
        }
        if (className.isEmpty()) {
            return className;
        }
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    private String statusDescription(int status) {
        return switch (status) {
        case 200 -> "OK";
        case 201 -> "Created";
        case 202 -> "Accepted";
        case 204 -> "No Content";
        case 400 -> "Bad Request";
        case 401 -> "Unauthorized";
        case 403 -> "Forbidden";
        case 404 -> "Not Found";
        case 409 -> "Conflict";
        case 500 -> "Internal Server Error";
        default -> "HTTP " + status;
        };
    }

}
