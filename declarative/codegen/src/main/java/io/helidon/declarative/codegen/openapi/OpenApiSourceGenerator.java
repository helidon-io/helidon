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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.declarative.codegen.DeclarativeTypes;
import io.helidon.declarative.codegen.model.http.RestMethod;
import io.helidon.declarative.codegen.model.http.RestMethodParameter;
import io.helidon.declarative.codegen.model.http.ServerEndpoint;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;

import static io.helidon.declarative.codegen.DeclarativeTypes.SINGLETON_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_HEADER_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_PATH_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_QUERY_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.JSON_OBJECT;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.JSON_SCHEMA_PROVIDER;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.JSON_STRING;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_CONTACT_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_DOCUMENT_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_DOCUMENT_BUILDER;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_DOCUMENT_CONTEXT;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_EXTENSION_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_EXTENSIONS_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_EXTERNAL_DOCS_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_HIDDEN_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_INFO_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_LICENSE_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_OPERATION_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_PARAMETER_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_RESPONSES_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_RESPONSE_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SECURITY_REQUIREMENTS_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SECURITY_REQUIREMENT_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SECURITY_SCHEMES_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SECURITY_SCHEME_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SERVERS_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SERVER_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SOURCE_BASE;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_TAGS_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_TAG_ANNOTATION;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED_BY_TYPE;
import static java.util.function.Predicate.not;

final class OpenApiSourceGenerator {
    private static final TypeName GENERATOR = TypeName.create(OpenApiSourceGenerator.class);
    private static final String DEFAULT_MEDIA_TYPE = "application/json";

    private final RegistryCodegenContext ctx;

    OpenApiSourceGenerator(RegistryCodegenContext ctx) {
        this.ctx = ctx;
    }

    void process(RegistryRoundContext roundContext, List<ServerEndpoint> endpoints) {
        Collection<TypeInfo> documents = roundContext.annotatedTypes(OPENAPI_DOCUMENT_ANNOTATION);
        if (documents.isEmpty()) {
            return;
        }

        for (TypeInfo document : documents) {
            processDocument(roundContext, document);
        }

        for (ServerEndpoint endpoint : endpoints) {
            if (!hasAnnotation(endpoint.annotations(), OPENAPI_HIDDEN_ANNOTATION)) {
                processEndpoint(roundContext, endpoint);
            }
        }
    }

    private void processDocument(RegistryRoundContext roundContext, TypeInfo typeInfo) {
        TypeName generatedType = generatedType(typeInfo.typeName(), "OpenApi");
        ClassModel.Builder classModel = sourceClass(typeInfo.typeName(), generatedType);

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
        TypeName generatedType = generatedType(typeInfo.typeName(), "OpenApi");
        List<SchemaBinding> schemaBindings = schemaBindings(endpoint);
        ClassModel.Builder classModel = sourceClass(typeInfo.typeName(), generatedType);
        addSchemaInjection(classModel, schemaBindings);

        endpoint.listener()
                .ifPresent(listener -> classModel.addMethod(method -> method
                        .accessModifier(AccessModifier.PUBLIC)
                        .addAnnotation(Annotations.OVERRIDE)
                        .returnType(TypeNames.PRIMITIVE_BOOLEAN)
                        .name("supports")
                        .addParameter(context -> context
                                .type(OPENAPI_DOCUMENT_CONTEXT)
                                .name("context"))
                        .addContent("return ")
                        .addContent(stringLiteral(listener))
                        .addContentLine(".equals(context.listener());")));

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
                        .addContent(stringLiteral(self))
                        .addContentLine(");"));
        document.stringValue("jsonSchemaDialect")
                .filter(not(String::isBlank))
                .ifPresent(dialect -> method.addContent("document.jsonSchemaDialect(")
                        .addContent(stringLiteral(dialect))
                        .addContentLine(");"));

        Annotation info = Annotations.findFirst(OPENAPI_INFO_ANNOTATION, annotations)
                .orElseThrow(() -> new CodegenException("@OpenApi.Document on " + typeInfo.typeName().fqName()
                                                                + " requires @OpenApi.Info"));
        addInfo(method, info, annotations);

        repeatableAnnotations(annotations, OPENAPI_SERVERS_ANNOTATION, OPENAPI_SERVER_ANNOTATION)
                .forEach(server -> writeServer(method, server));
        repeatableAnnotations(annotations, OPENAPI_TAGS_ANNOTATION, OPENAPI_TAG_ANNOTATION)
                .forEach(tag -> writeTag(method, tag));
        Annotations.findFirst(OPENAPI_EXTERNAL_DOCS_ANNOTATION, annotations)
                .ifPresent(externalDocs -> writeExternalDocs(method, externalDocs));
        repeatableAnnotations(annotations, OPENAPI_EXTENSIONS_ANNOTATION, OPENAPI_EXTENSION_ANNOTATION)
                .forEach(extension -> writeExtension(method, extension));
        repeatableAnnotations(annotations, OPENAPI_SECURITY_SCHEMES_ANNOTATION, OPENAPI_SECURITY_SCHEME_ANNOTATION)
                .forEach(scheme -> writeSecurityScheme(method, scheme));
        repeatableAnnotations(annotations,
                              OPENAPI_SECURITY_REQUIREMENTS_ANNOTATION,
                              OPENAPI_SECURITY_REQUIREMENT_ANNOTATION)
                .forEach(requirement -> writeSecurityRequirement(method, requirement));
    }

    private void endpointSourceBody(ServerEndpoint endpoint,
                                    List<SchemaBinding> schemaBindings,
                                    Method.Builder method) {
        String endpointTag = endpointName(endpoint.type().typeName());
        for (SchemaBinding schemaBinding : schemaBindings) {
            addSchemaComponent(method, schemaBinding);
        }
        for (RestMethod restMethod : endpoint.methods()) {
            if (hasAnnotation(restMethod.annotations(), OPENAPI_HIDDEN_ANNOTATION)) {
                continue;
            }
            addOperation(method, endpoint, restMethod, endpointTag);
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
                .addContent(stringLiteral(title))
                .addContent(")")
                .addContentLine()
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(".version(")
                .addContent(stringLiteral(version))
                .addContentLine(")");
        info.stringValue("summary")
                .filter(not(String::isBlank))
                .ifPresent(summary -> method.addContent(".summary(")
                        .addContent(stringLiteral(summary))
                        .addContentLine(")"));
        info.stringValue("description")
                .filter(not(String::isBlank))
                .ifPresent(description -> method.addContent(".description(")
                        .addContent(stringLiteral(description))
                        .addContentLine(")"));
        info.stringValue("termsOfService")
                .filter(not(String::isBlank))
                .ifPresent(terms -> method.addContent(".termsOfService(")
                        .addContent(stringLiteral(terms))
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
                        .addContent(stringLiteral(name))
                        .addContentLine(")"));
        contact.stringValue("url")
                .filter(not(String::isBlank))
                .ifPresent(url -> method.addContent(".url(")
                        .addContent(stringLiteral(url))
                        .addContentLine(")"));
        contact.stringValue("email")
                .filter(not(String::isBlank))
                .ifPresent(email -> method.addContent(".email(")
                        .addContent(stringLiteral(email))
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
                .addContent(stringLiteral(name))
                .addContentLine(")")
                .increaseContentPadding()
                .increaseContentPadding();
        license.stringValue("url")
                .filter(not(String::isBlank))
                .ifPresent(url -> method.addContent(".url(")
                        .addContent(stringLiteral(url))
                        .addContentLine(")"));
        license.stringValue("identifier")
                .filter(not(String::isBlank))
                .ifPresent(identifier -> method.addContent(".identifier(")
                        .addContent(stringLiteral(identifier))
                        .addContentLine(")"));
        method.addContentLine(")")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void writeServer(Method.Builder method, Annotation server) {
        String url = server.stringValue()
                .filter(not(String::isBlank))
                .orElseThrow(() -> new CodegenException("@OpenApi.Server value is required"));
        method.addContent("document.server(server -> server.url(")
                .addContent(stringLiteral(url))
                .addContentLine(")")
                .increaseContentPadding()
                .increaseContentPadding();
        server.stringValue("description")
                .filter(not(String::isBlank))
                .ifPresent(description -> method.addContent(".description(")
                        .addContent(stringLiteral(description))
                        .addContentLine(")"));
        server.stringValue("name")
                .filter(not(String::isBlank))
                .ifPresent(name -> method.addContent(".name(")
                        .addContent(stringLiteral(name))
                        .addContentLine(")"));
        method.addContentLine(");")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void writeTag(Method.Builder method, Annotation tag) {
        String name = tag.stringValue()
                .filter(not(String::isBlank))
                .orElseThrow(() -> new CodegenException("@OpenApi.Tag value is required"));
        method.addContent("document.tag(tag -> tag.name(")
                .addContent(stringLiteral(name))
                .addContentLine(")")
                .increaseContentPadding()
                .increaseContentPadding();
        tag.stringValue("description")
                .filter(not(String::isBlank))
                .ifPresent(description -> method.addContent(".description(")
                        .addContent(stringLiteral(description))
                        .addContentLine(")"));
        tag.stringValue("summary")
                .filter(not(String::isBlank))
                .ifPresent(summary -> method.addContent(".summary(")
                        .addContent(stringLiteral(summary))
                        .addContentLine(")"));
        tag.stringValue("parent")
                .filter(not(String::isBlank))
                .ifPresent(parent -> method.addContent(".parent(")
                        .addContent(stringLiteral(parent))
                        .addContentLine(")"));
        tag.stringValue("kind")
                .filter(not(String::isBlank))
                .ifPresent(kind -> method.addContent(".kind(")
                        .addContent(stringLiteral(kind))
                        .addContentLine(")"));
        method.addContentLine(");")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void writeExternalDocs(Method.Builder method, Annotation externalDocs) {
        String url = externalDocs.stringValue()
                .filter(not(String::isBlank))
                .orElseThrow(() -> new CodegenException("@OpenApi.ExternalDocs value is required"));
        method.addContent("document.externalDocs(externalDocs -> externalDocs.url(")
                .addContent(stringLiteral(url))
                .addContentLine(")")
                .increaseContentPadding()
                .increaseContentPadding();
        externalDocs.stringValue("description")
                .filter(not(String::isBlank))
                .ifPresent(description -> method.addContent(".description(")
                        .addContent(stringLiteral(description))
                        .addContentLine(")"));
        method.addContentLine(");")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void writeExtension(Method.Builder method, Annotation extension) {
        String name = extension.stringValue("name")
                .filter(not(String::isBlank))
                .orElseThrow(() -> new CodegenException("@OpenApi.Extension name is required"));
        if (!name.startsWith("x-")) {
            throw new CodegenException("@OpenApi.Extension name must start with x-: " + name);
        }
        String value = extension.stringValue("value")
                .orElseThrow(() -> new CodegenException("@OpenApi.Extension value is required"));
        method.addContent("document.extension(")
                .addContent(stringLiteral(name))
                .addContent(", ")
                .addContent(JSON_STRING)
                .addContent(".create(")
                .addContent(stringLiteral(value))
                .addContentLine("));");
    }

    private void writeSecurityScheme(Method.Builder method, Annotation scheme) {
        String name = scheme.stringValue("name")
                .filter(not(String::isBlank))
                .orElseThrow(() -> new CodegenException("@OpenApi.SecurityScheme name is required"));
        String type = scheme.stringValue("type")
                .filter(not(String::isBlank))
                .orElseThrow(() -> new CodegenException("@OpenApi.SecurityScheme type is required"));
        method.addContent("document.components(components -> components.securityScheme(")
                .addContent(stringLiteral(name))
                .addContentLine(",")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent("security -> security.type(")
                .addContent(stringLiteral(type))
                .addContentLine(")")
                .increaseContentPadding()
                .increaseContentPadding();
        scheme.stringValue("description")
                .filter(not(String::isBlank))
                .ifPresent(description -> method.addContent(".description(")
                        .addContent(stringLiteral(description))
                        .addContentLine(")"));
        scheme.stringValue("apiKeyName")
                .filter(not(String::isBlank))
                .ifPresent(apiKeyName -> method.addContent(".name(")
                        .addContent(stringLiteral(apiKeyName))
                        .addContentLine(")"));
        scheme.stringValue("scheme")
                .filter(not(String::isBlank))
                .ifPresent(securityScheme -> method.addContent(".scheme(")
                        .addContent(stringLiteral(securityScheme))
                        .addContentLine(")"));
        scheme.stringValue("bearerFormat")
                .filter(not(String::isBlank))
                .ifPresent(format -> method.addContent(".bearerFormat(")
                        .addContent(stringLiteral(format))
                        .addContentLine(")"));
        scheme.stringValue("in")
                .filter(not(String::isBlank))
                .ifPresent(in -> method.addContent(".in(")
                        .addContent(stringLiteral(in))
                        .addContentLine(")"));
        scheme.annotationValue("flows")
                .flatMap(this::oauthFlowsExpression)
                .ifPresent(flows -> method.addContent(".flows(")
                        .addContent(flows)
                        .addContentLine(")"));
        scheme.stringValue("openIdConnectUrl")
                .filter(not(String::isBlank))
                .ifPresent(url -> method.addContent(".openIdConnectUrl(")
                        .addContent(stringLiteral(url))
                        .addContentLine(")"));
        scheme.stringValue("oauth2MetadataUrl")
                .filter(not(String::isBlank))
                .ifPresent(url -> method.addContent(".oauth2MetadataUrl(")
                        .addContent(stringLiteral(url))
                        .addContentLine(")"));
        scheme.booleanValue("deprecated")
                .filter(Boolean::booleanValue)
                .ifPresent(deprecated -> method.addContentLine(".deprecated(true)"));
        method.addContentLine("));")
                .decreaseContentPadding()
                .decreaseContentPadding()
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void writeSecurityRequirement(Method.Builder method, Annotation requirement) {
        List<String> schemes = requirement.stringValues().orElseGet(List::of);
        if (schemes.isEmpty()) {
            method.addContentLine("document.securityRequirement(security -> { });");
            return;
        }
        List<String> scopes = requirement.stringValues("scopes").orElseGet(List::of);
        method.addContent("document.securityRequirement(security -> security")
                .addContentLine()
                .increaseContentPadding()
                .increaseContentPadding();
        schemes.forEach(scheme -> method.addContent(".scheme(")
                .addContent(stringLiteral(scheme))
                .addContent(", ")
                .addContent(stringListExpression(scopes))
                .addContentLine(")"));
        method.addContentLine(");")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private Optional<String> oauthFlowsExpression(Annotation flows) {
        List<JsonObjectEntry> entries = new ArrayList<>();
        addOauthFlow(entries, flows, "implicit");
        addOauthFlow(entries, flows, "password");
        addOauthFlow(entries, flows, "clientCredentials");
        addOauthFlow(entries, flows, "authorizationCode");
        addOauthFlow(entries, flows, "deviceAuthorization");
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(jsonObjectExpression(entries));
    }

    private void addOauthFlow(List<JsonObjectEntry> entries, Annotation flows, String name) {
        flows.annotationValue(name)
                .flatMap(this::oauthFlowExpression)
                .ifPresent(flow -> entries.add(new JsonObjectEntry(name, flow)));
    }

    private Optional<String> oauthFlowExpression(Annotation flow) {
        List<JsonObjectEntry> entries = new ArrayList<>();
        addStringJsonEntry(entries, flow, "authorizationUrl");
        addStringJsonEntry(entries, flow, "deviceAuthorizationUrl");
        addStringJsonEntry(entries, flow, "tokenUrl");
        addStringJsonEntry(entries, flow, "refreshUrl");

        List<Annotation> scopes = flow.annotationValues("scopes").orElseGet(List::of);
        if (entries.isEmpty() && scopes.isEmpty()) {
            return Optional.empty();
        }
        String scopesExpression = scopes.isEmpty() ? JSON_OBJECT.fqName() + ".empty()" : oauthScopesExpression(scopes);
        entries.add(new JsonObjectEntry("scopes", scopesExpression));
        return Optional.of(jsonObjectExpression(entries));
    }

    private void addStringJsonEntry(List<JsonObjectEntry> entries, Annotation annotation, String name) {
        annotation.stringValue(name)
                .filter(not(String::isBlank))
                .ifPresent(value -> entries.add(new JsonObjectEntry(name, stringLiteral(value))));
    }

    private String oauthScopesExpression(List<Annotation> scopes) {
        List<JsonObjectEntry> entries = new ArrayList<>();
        scopes.forEach(scope -> {
            String name = scope.stringValue()
                    .filter(not(String::isBlank))
                    .orElseThrow(() -> new CodegenException("@OpenApi.OAuthScope value is required"));
            String description = scope.stringValue("description")
                    .orElse("");
            entries.add(new JsonObjectEntry(name, stringLiteral(description)));
        });
        return jsonObjectExpression(entries);
    }

    private String jsonObjectExpression(List<JsonObjectEntry> entries) {
        StringBuilder result = new StringBuilder(JSON_OBJECT.fqName()).append(".builder()");
        entries.forEach(entry -> result.append(".set(")
                .append(stringLiteral(entry.name()))
                .append(", ")
                .append(entry.valueExpression())
                .append(")"));
        return result.append(".build()").toString();
    }

    private void addOperation(Method.Builder method,
                              ServerEndpoint endpoint,
                              RestMethod restMethod,
                              String endpointTag) {
        method.addContent("document.path(")
                .addContent(stringLiteral(openApiPath(endpoint, restMethod)))
                .addContentLine(",")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent("path -> path.operation(")
                .addContent(stringLiteral(restMethod.httpMethod().name().toLowerCase(Locale.ROOT)))
                .addContentLine(",")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContentLine("operation -> operation")
                .increaseContentPadding()
                .increaseContentPadding();

        operationAnnotation(restMethod)
                .ifPresentOrElse(operation -> addExplicitOperation(method, operation, restMethod, endpointTag),
                                 () -> addInferredOperation(method, restMethod, endpointTag));
        addParameters(method, restMethod);
        addRequestBody(method, restMethod);
        addResponses(method, restMethod);

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
                        .addContent(stringLiteral(summary))
                        .addContentLine(")"));
        operation.stringValue("description")
                .filter(not(String::isBlank))
                .ifPresent(description -> method.addContent(".description(")
                        .addContent(stringLiteral(description))
                        .addContentLine(")"));
        boolean hasOperationId = operation.stringValue("operationId")
                .filter(not(String::isBlank))
                .map(operationId -> {
                    method.addContent(".operationId(")
                        .addContent(stringLiteral(operationId))
                            .addContentLine(")");
                    return true;
                })
                .orElse(false);
        if (!hasOperationId) {
            method.addContent(".operationId(")
                    .addContent(stringLiteral(operationId(restMethod, endpointTag)))
                    .addContentLine(")");
        }
        List<String> tags = operation.stringValues("tags").orElseGet(List::of);
        if (tags.isEmpty()) {
            method.addContent(".tag(")
                    .addContent(stringLiteral(endpointTag))
                    .addContentLine(")");
        } else {
            tags.forEach(tag -> method.addContent(".tag(")
                    .addContent(stringLiteral(tag))
                    .addContentLine(")"));
        }
        operation.booleanValue("deprecated")
                .filter(Boolean::booleanValue)
                .ifPresent(deprecated -> method.addContentLine(".deprecated(true)"));
    }

    private void addInferredOperation(Method.Builder method, RestMethod restMethod, String endpointTag) {
        method.addContent(".operationId(")
                .addContent(stringLiteral(operationId(restMethod, endpointTag)))
                .addContentLine(")")
                .addContent(".tag(")
                .addContent(stringLiteral(endpointTag))
                .addContentLine(")");
    }

    private String operationId(RestMethod restMethod, String endpointTag) {
        return endpointTag
                + CodegenUtil.capitalize(restMethod.httpMethod().name().toLowerCase(Locale.ROOT))
                + CodegenUtil.capitalize(restMethod.uniqueName());
    }

    private void addParameters(Method.Builder method, RestMethod restMethod) {
        for (RestMethodParameter parameter : restMethod.pathParameters()) {
            addParameter(method, parameter, "path");
        }
        for (RestMethodParameter parameter : restMethod.queryParameters()) {
            addParameter(method, parameter, "query");
        }
        for (RestMethodParameter parameter : restMethod.headerParameters()) {
            String name = parameterName(parameter, "header");
            if (isSpecialHeader(name)) {
                continue;
            }
            addParameter(method, parameter, "header");
        }
    }

    private void addParameter(Method.Builder method, RestMethodParameter parameter, String in) {
        Annotation annotation = Annotations.findFirst(OPENAPI_PARAMETER_ANNOTATION, parameter.annotations())
                .orElse(null);
        TypeName type = parameter.typeName();
        TypeName schemaType = schemaType(type);
        String name = parameterName(parameter, in);

        method.addContent(".parameter(parameter -> parameter.name(")
                .addContent(stringLiteral(name))
                .addContent(")")
                .addContentLine()
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(".in(")
                .addContent(stringLiteral(in))
                .addContentLine(")")
                .addContent(".required(")
                .addContent(Boolean.toString(required(in, type)))
                .addContentLine(")")
                .addContent(".schema(")
                .addContent(schemaExpression(schemaType))
                .addContentLine(")");

        if (schemaType.isList()) {
            if ("query".equals(in)) {
                method.addContentLine(".style(\"form\")")
                        .addContentLine(".explode(true)");
            } else if ("header".equals(in)) {
                method.addContentLine(".style(\"simple\")")
                        .addContentLine(".explode(false)");
            }
        }
        if (annotation != null) {
            annotation.stringValue()
                    .filter(not(String::isBlank))
                    .ifPresent(description -> method.addContent(".description(")
                            .addContent(stringLiteral(description))
                            .addContentLine(")"));
            annotation.stringValue("example")
                    .filter(not(String::isBlank))
                    .ifPresent(example -> method.addContent(".example(")
                            .addContent(JSON_STRING)
                            .addContent(".create(")
                            .addContent(stringLiteral(example))
                            .addContentLine("))"));
            annotation.booleanValue("deprecated")
                    .filter(Boolean::booleanValue)
                    .ifPresent(deprecated -> method.addContentLine(".deprecated(true)"));
        }
        method.addContentLine(")")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void addRequestBody(Method.Builder method, RestMethod restMethod) {
        restMethod.entityParameter().ifPresent(parameter -> {
            TypeName entityType = schemaType(parameter.typeName());
            method.addContentLine(".requestBody(requestBody -> requestBody")
                    .increaseContentPadding()
                    .increaseContentPadding();
            if (!parameter.typeName().isOptional()) {
                method.addContentLine(".required(true)");
            }
            for (String mediaType : mediaTypes(restMethod.consumes())) {
                method.addContent(".content(")
                        .addContent(stringLiteral(mediaType))
                        .addContent(", ")
                        .addContent(mediaTypeConsumer(schemaExpression(entityType)))
                        .addContentLine(")");
            }
            method.addContentLine(")")
                    .decreaseContentPadding()
                    .decreaseContentPadding();
        });
    }

    private void addResponses(Method.Builder method, RestMethod restMethod) {
        List<Annotation> explicitResponses = repeatableAnnotations(restMethod.annotations(),
                                                                    OPENAPI_RESPONSES_ANNOTATION,
                                                                    OPENAPI_RESPONSE_ANNOTATION);
        if (explicitResponses.isEmpty()) {
            addInferredResponses(method, restMethod);
            return;
        }
        for (Annotation response : explicitResponses) {
            addResponse(method, restMethod, response);
        }
    }

    private void addResponse(Method.Builder method, RestMethod restMethod, Annotation response) {
        int status = response.intValue("status")
                .orElseThrow(() -> new CodegenException("@OpenApi.Response status is required"));
        TypeName responseType = responseType(restMethod.returnType());
        boolean hasEntity = hasResponseEntity(restMethod.returnType());
        method.addContent(".response(")
                .addContent(stringLiteral(String.valueOf(status)))
                .addContent(", ")
                .addContent("response -> response.description(")
                .addContent(stringLiteral(response.stringValue("description").orElse(statusDescription(status))))
                .addContentLine(")")
                .increaseContentPadding()
                .increaseContentPadding();
        response.stringValue("summary")
                .filter(not(String::isBlank))
                .ifPresent(summary -> method.addContent(".summary(")
                        .addContent(stringLiteral(summary))
                        .addContentLine(")"));

        List<Annotation> contentAnnotations = response.annotationValues("content").orElseGet(List::of);
        if (contentAnnotations.isEmpty() && hasEntity) {
            for (String mediaType : mediaTypes(restMethod.produces())) {
                method.addContent(".content(")
                        .addContent(stringLiteral(mediaType))
                        .addContent(", ")
                        .addContent(mediaTypeConsumer(schemaExpression(responseType)))
                        .addContentLine(")");
            }
        } else {
            for (Annotation content : contentAnnotations) {
                addContent(method, restMethod.produces(), content, responseType, hasEntity);
            }
        }

        method.addContentLine(")")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void addInferredResponses(Method.Builder method, RestMethod restMethod) {
        TypeName returnType = restMethod.returnType();
        int status = restMethod.status()
                .map(it -> it.code())
                .orElseGet(() -> returnType.boxed().equals(TypeNames.BOXED_VOID) ? 204 : 200);
        TypeName responseType = responseType(returnType);
        boolean hasEntity = hasResponseEntity(returnType);
        method.addContent(".response(")
                .addContent(stringLiteral(String.valueOf(status)))
                .addContent(", ")
                .addContent("response -> response.description(")
                .addContent(stringLiteral(restMethod.status()
                                                  .flatMap(it -> it.reason())
                                                  .orElse(statusDescription(status))))
                .addContentLine(")")
                .increaseContentPadding()
                .increaseContentPadding();
        if (hasEntity) {
            for (String mediaType : mediaTypes(restMethod.produces())) {
                method.addContent(".content(")
                        .addContent(stringLiteral(mediaType))
                        .addContent(", ")
                        .addContent(mediaTypeConsumer(schemaExpression(responseType)))
                        .addContentLine(")");
            }
        }
        method.addContentLine(")")
                .decreaseContentPadding()
                .decreaseContentPadding();

        if (returnType.isOptional()) {
            method.addContentLine(".response(\"404\", response -> response.description(\"Not Found\"))");
        }
    }

    private void addContent(Method.Builder method,
                            List<String> inferredMediaTypes,
                            Annotation content,
                            TypeName inferredSchemaType,
                            boolean hasInferredSchema) {
        Optional<TypeName> explicitSchema = content.typeValue("schema")
                .filter(Predicate.not(TypeName.create(Void.class)::equals));
        TypeName schemaType = explicitSchema.orElse(inferredSchemaType);
        boolean hasSchema = explicitSchema.isPresent() || hasInferredSchema;
        List<String> mediaTypes = content.stringValue()
                .filter(not(String::isBlank))
                .map(List::of)
                .orElseGet(() -> mediaTypes(inferredMediaTypes));
        for (String mediaType : mediaTypes) {
            method.addContent(".content(")
                    .addContent(stringLiteral(mediaType))
                    .addContent(", ")
                    .addContent(mediaTypeConsumer(hasSchema
                                                          ? schemaExpression(schemaType)
                                                          : JSON_OBJECT.fqName() + ".builder().build()"))
                    .addContentLine(")");
        }
    }

    private List<SchemaBinding> schemaBindings(ServerEndpoint endpoint) {
        Set<TypeName> schemaTypes = new LinkedHashSet<>();
        for (RestMethod restMethod : endpoint.methods()) {
            if (hasAnnotation(restMethod.annotations(), OPENAPI_HIDDEN_ANNOTATION)) {
                continue;
            }
            collectOperationSchemaComponents(schemaTypes, restMethod);
        }

        List<SchemaBinding> result = new ArrayList<>();
        Set<String> usedFieldNames = new HashSet<>();
        for (TypeName schemaType : schemaTypes) {
            String schemaName = schemaName(schemaType);
            result.add(new SchemaBinding(schemaType,
                                         schemaName,
                                         uniqueFieldName(schemaFieldName(schemaName), usedFieldNames)));
        }
        return result;
    }

    private void collectOperationSchemaComponents(Set<TypeName> schemaTypes, RestMethod restMethod) {
        restMethod.pathParameters().forEach(parameter -> collectSchemaComponent(schemaTypes, parameter.typeName()));
        restMethod.queryParameters().forEach(parameter -> collectSchemaComponent(schemaTypes, parameter.typeName()));
        restMethod.headerParameters().stream()
                .filter(parameter -> !isSpecialHeader(parameterName(parameter, "header")))
                .forEach(parameter -> collectSchemaComponent(schemaTypes, parameter.typeName()));
        restMethod.entityParameter().ifPresent(parameter -> collectSchemaComponent(schemaTypes, parameter.typeName()));

        TypeName responseType = responseType(restMethod.returnType());
        boolean hasResponseEntity = hasResponseEntity(restMethod.returnType());
        List<Annotation> explicitResponses = repeatableAnnotations(restMethod.annotations(),
                                                                    OPENAPI_RESPONSES_ANNOTATION,
                                                                    OPENAPI_RESPONSE_ANNOTATION);
        if (explicitResponses.isEmpty()) {
            if (hasResponseEntity) {
                collectSchemaComponent(schemaTypes, responseType);
            }
            return;
        }

        for (Annotation response : explicitResponses) {
            List<Annotation> contentAnnotations = response.annotationValues("content").orElseGet(List::of);
            if (contentAnnotations.isEmpty()) {
                if (hasResponseEntity) {
                    collectSchemaComponent(schemaTypes, responseType);
                }
            } else {
                for (Annotation content : contentAnnotations) {
                    collectContentSchemaComponent(schemaTypes, content, responseType, hasResponseEntity);
                }
            }
        }
    }

    private void collectContentSchemaComponent(Set<TypeName> schemaTypes,
                                               Annotation content,
                                               TypeName inferredSchemaType,
                                               boolean hasInferredSchema) {
        Optional<TypeName> explicitSchema = content.typeValue("schema")
                .filter(Predicate.not(TypeName.create(Void.class)::equals));
        if (explicitSchema.isPresent() || hasInferredSchema) {
            collectSchemaComponent(schemaTypes, explicitSchema.orElse(inferredSchemaType));
        }
    }

    private void collectSchemaComponent(Set<TypeName> schemaTypes, TypeName type) {
        TypeName schemaType = schemaType(type);
        if (schemaType.isList()) {
            if (!schemaType.typeArguments().isEmpty()) {
                collectSchemaComponent(schemaTypes, schemaType.typeArguments().getFirst());
            }
            return;
        }
        if (jsonType(schemaType).isPresent()) {
            return;
        }
        schemaTypes.add(schemaType);
    }

    private void addSchemaInjection(ClassModel.Builder classModel, List<SchemaBinding> schemaBindings) {
        if (schemaBindings.isEmpty()) {
            return;
        }
        for (SchemaBinding schemaBinding : schemaBindings) {
            classModel.addField(field -> field
                    .accessModifier(AccessModifier.PRIVATE)
                    .isFinal(true)
                    .type(JSON_SCHEMA_PROVIDER)
                    .name(schemaBinding.fieldName()));
        }

        classModel.addConstructor(ctr -> {
            ctr.accessModifier(AccessModifier.PACKAGE_PRIVATE)
                    .addAnnotation(Annotation.create(SERVICE_ANNOTATION_INJECT));
            for (SchemaBinding schemaBinding : schemaBindings) {
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

    private void addSchemaComponent(Method.Builder method, SchemaBinding schemaBinding) {
        method.addContent("componentSchema(document, ")
                .addContent(schemaBinding.fieldName())
                .addContent(", ")
                .addContent(stringLiteral(schemaBinding.name()))
                .addContentLine(");");
    }

    private String mediaTypeConsumer(String schemaExpression) {
        return "content -> content.schema(" + schemaExpression + ")";
    }

    private String schemaExpression(TypeName type) {
        TypeName schemaType = schemaType(type);
        if (schemaType.isList()) {
            TypeName itemType = schemaType.typeArguments().isEmpty()
                    ? TypeNames.STRING
                    : schemaType.typeArguments().getFirst();
            return "arraySchema(" + schemaExpression(itemType) + ")";
        }
        return jsonType(schemaType)
                .map(it -> "schema(" + stringLiteral(it) + ")")
                .orElseGet(() -> schemaRefExpression(schemaType));
    }

    private String schemaRefExpression(TypeName type) {
        return "schemaRef(" + stringLiteral(schemaName(schemaType(type))) + ")";
    }

    private TypeName schemaType(TypeName type) {
        TypeName unwrapped = type.isOptional() && !type.typeArguments().isEmpty()
                ? type.typeArguments().getFirst()
                : type;
        return unwrapped.boxed();
    }

    private TypeName responseType(TypeName type) {
        return schemaType(type);
    }

    private boolean hasResponseEntity(TypeName type) {
        TypeName boxed = schemaType(type);
        return !boxed.equals(TypeNames.BOXED_VOID);
    }

    private Optional<String> jsonType(TypeName type) {
        TypeName boxed = type.boxed().genericTypeName();
        if (boxed.equals(TypeNames.STRING)) {
            return Optional.of("string");
        }
        if (boxed.equals(TypeNames.BOXED_BOOLEAN)) {
            return Optional.of("boolean");
        }
        if (boxed.equals(TypeNames.BOXED_BYTE)
                || boxed.equals(TypeNames.BOXED_SHORT)
                || boxed.equals(TypeNames.BOXED_INT)
                || boxed.equals(TypeNames.BOXED_LONG)) {
            return Optional.of("integer");
        }
        if (boxed.equals(TypeNames.BOXED_FLOAT)
                || boxed.equals(TypeNames.BOXED_DOUBLE)
                || boxed.equals(TypeName.create("java.math.BigDecimal"))) {
            return Optional.of("number");
        }
        return Optional.empty();
    }

    private String parameterName(RestMethodParameter parameter, String in) {
        TypeName annotationType = switch (in) {
        case "path" -> HTTP_PATH_PARAM_ANNOTATION;
        case "query" -> HTTP_QUERY_PARAM_ANNOTATION;
        case "header" -> HTTP_HEADER_PARAM_ANNOTATION;
        default -> throw new CodegenException("Unsupported OpenAPI parameter location: " + in);
        };
        return Annotations.findFirst(annotationType, parameter.annotations())
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank))
                .orElse(parameter.name());
    }

    private boolean required(String in, TypeName type) {
        if ("path".equals(in)) {
            return true;
        }
        return !type.isOptional();
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

    private Optional<Annotation> operationAnnotation(RestMethod method) {
        return Annotations.findFirst(OPENAPI_OPERATION_ANNOTATION, method.annotations());
    }

    private static boolean hasAnnotation(Set<Annotation> annotations, TypeName annotationType) {
        return Annotations.findFirst(annotationType, annotations).isPresent();
    }

    private List<Annotation> repeatableAnnotations(Set<Annotation> annotations,
                                                   TypeName containerType,
                                                   TypeName annotationType) {
        List<Annotation> result = new ArrayList<>();
        Annotations.findFirst(containerType, annotations)
                .flatMap(Annotation::annotationValues)
                .ifPresent(result::addAll);
        if (result.isEmpty()) {
            Annotations.findFirst(annotationType, annotations)
                    .ifPresent(result::add);
        }
        return result;
    }

    private boolean hasStringValue(Annotation annotation, String property) {
        Optional<String> value = "value".equals(property)
                ? annotation.stringValue()
                : annotation.stringValue(property);
        return value.filter(not(String::isBlank)).isPresent();
    }

    private String stringListExpression(List<String> values) {
        if (values.isEmpty()) {
            return "java.util.List.of()";
        }
        StringBuilder result = new StringBuilder("java.util.List.of(");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(stringLiteral(values.get(i)));
        }
        return result.append(")").toString();
    }

    private String openApiPath(ServerEndpoint endpoint, RestMethod method) {
        String endpointPath = endpoint.path().orElse("");
        String methodPath = method.path().orElse("");
        String joined = joinPath(endpointPath, methodPath);
        return joined.isBlank() ? "/" : joined;
    }

    private String joinPath(String first, String second) {
        if (first.isBlank() || "/".equals(first)) {
            return second.isBlank() ? "/" : ensureLeadingSlash(second);
        }
        if (second.isBlank() || "/".equals(second)) {
            return ensureLeadingSlash(first);
        }
        return ensureLeadingSlash(first).replaceAll("/+$", "") + "/" + second.replaceAll("^/+", "");
    }

    private String ensureLeadingSlash(String path) {
        return path.startsWith("/") ? path : "/" + path;
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

    private String schemaName(TypeName typeName) {
        return typeName.classNameWithEnclosingNames().replace('.', '_');
    }

    private String schemaFieldName(String schemaName) {
        return Character.toLowerCase(schemaName.charAt(0)) + schemaName.substring(1) + "Schema";
    }

    private String uniqueFieldName(String fieldName, Set<String> usedFieldNames) {
        if (usedFieldNames.add(fieldName)) {
            return fieldName;
        }

        int index = 2;
        String candidate = fieldName + index;
        while (!usedFieldNames.add(candidate)) {
            index++;
            candidate = fieldName + index;
        }
        return candidate;
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

    private String stringLiteral(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
            case '\\' -> result.append("\\\\");
            case '"' -> result.append("\\\"");
            case '\n' -> result.append("\\n");
            case '\r' -> result.append("\\r");
            case '\t' -> result.append("\\t");
            default -> result.append(ch);
            }
        }
        return result.append('"').toString();
    }

    private record SchemaBinding(TypeName type, String name, String fieldName) {
    }

    private record JsonObjectEntry(String name, String valueExpression) {
    }
}
