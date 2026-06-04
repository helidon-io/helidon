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

package io.helidon.openapi.v30;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.helidon.common.Api;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonValue;
import io.helidon.json.JsonValueType;
import io.helidon.openapi.OpenApiDocument;

/**
 * Internal reader from structured JSON into the version-neutral OpenAPI document model.
 */
@Api.Internal
public final class OpenApiDocumentReader {
    private static final Set<String> FIXED_PATH_OPERATION_FIELDS = Set.of("get",
                                                                          "put",
                                                                          "post",
                                                                          "delete",
                                                                          "options",
                                                                          "head",
                                                                          "patch",
                                                                          "trace",
                                                                          "query");

    private OpenApiDocumentReader() {
    }

    /**
     * Read a document.
     *
     * @param source source JSON object
     * @return document model
     */
    public static OpenApiDocument read(JsonObject source) {
        OpenApiDocument.Builder builder = OpenApiDocument.builder();
        string(source, "openapi", builder::openapi);
        string(source, "$self", builder::self);
        string(source, "jsonSchemaDialect", builder::jsonSchemaDialect);
        object(source, "info", info -> builder.info(info(info)));
        array(source, "servers", servers -> servers.values()
                .forEach(server -> object(server, value -> builder.server(server(value)))));
        object(source, "paths", paths -> paths.keysAsStrings()
                .forEach(path -> object(paths, path, item -> builder.path(path, pathItem(item)))));
        object(source, "webhooks", webhooks -> webhooks.keysAsStrings()
                .forEach(name -> object(webhooks,
                                        name,
                                        item -> builder.webhook(name, pathItem(item)))));
        object(source, "components", components -> builder.components(components(components)));
        array(source, "security", security -> security.values()
                .forEach(requirement -> object(requirement,
                                               value -> builder.securityRequirement(securityRequirement(value)))));
        array(source, "tags", tags -> tags.values()
                .forEach(tag -> object(tag, value -> builder.tag(tag(value)))));
        object(source, "externalDocs", docs -> builder.externalDocs(externalDocs(docs)));
        source.keysAsStrings()
                .stream()
                .filter(name -> name.startsWith("x-"))
                .forEach(name -> value(source, name, value -> builder.extension(name, value)));
        return builder.build();
    }

    private static OpenApiDocument.Info info(JsonObject source) {
        OpenApiDocument.InfoBuilder builder = OpenApiDocument.Info.builder();
        string(source, "title", builder::title);
        string(source, "version", builder::version);
        string(source, "summary", builder::summary);
        string(source, "description", builder::description);
        string(source, "termsOfService", builder::termsOfService);
        object(source, "contact", contact -> builder.contact(contact(contact)));
        object(source, "license", license -> builder.license(license(license)));
        extensions(source, builder::extension);
        return builder.build();
    }

    private static OpenApiDocument.Contact contact(JsonObject source) {
        OpenApiDocument.ContactBuilder builder = OpenApiDocument.Contact.builder();
        string(source, "name", builder::name);
        string(source, "url", builder::url);
        string(source, "email", builder::email);
        return builder.build();
    }

    private static OpenApiDocument.License license(JsonObject source) {
        OpenApiDocument.LicenseBuilder builder = OpenApiDocument.License.builder();
        string(source, "name", builder::name);
        string(source, "identifier", builder::identifier);
        string(source, "url", builder::url);
        return builder.build();
    }

    private static OpenApiDocument.ExternalDocs externalDocs(JsonObject source) {
        OpenApiDocument.ExternalDocsBuilder builder = OpenApiDocument.ExternalDocs.builder();
        string(source, "url", builder::url);
        string(source, "description", builder::description);
        return builder.build();
    }

    private static OpenApiDocument.Server server(JsonObject source) {
        OpenApiDocument.ServerBuilder builder = OpenApiDocument.Server.builder();
        string(source, "url", builder::url);
        string(source, "description", builder::description);
        string(source, "name", builder::name);
        object(source, "variables", variables -> variables.keysAsStrings()
                .forEach(name -> object(variables, name, variable -> builder.variable(name, serverVariable(variable)))));
        return builder.build();
    }

    private static OpenApiDocument.ServerVariable serverVariable(JsonObject source) {
        OpenApiDocument.ServerVariableBuilder builder = OpenApiDocument.ServerVariable.builder();
        string(source, "default", builder::value);
        array(source, "enum", values -> builder.allowedValues(stringValues(values)));
        string(source, "description", builder::description);
        return builder.build();
    }

    private static OpenApiDocument.Tag tag(JsonObject source) {
        OpenApiDocument.TagBuilder builder = OpenApiDocument.Tag.builder();
        string(source, "name", builder::name);
        string(source, "summary", builder::summary);
        string(source, "description", builder::description);
        object(source, "externalDocs", docs -> builder.externalDocs(externalDocs(docs)));
        string(source, "parent", builder::parent);
        string(source, "kind", builder::kind);
        return builder.build();
    }

    private static OpenApiDocument.PathItem pathItem(JsonObject source) {
        OpenApiDocument.PathItemBuilder builder = OpenApiDocument.PathItem.builder();
        string(source, "$ref", builder::ref);
        string(source, "summary", builder::summary);
        string(source, "description", builder::description);
        source.keysAsStrings()
                .stream()
                .filter(OpenApiDocumentReader::isFixedPathOperationField)
                .forEach(method -> object(source, method, operation -> builder.operation(method, operation(operation))));
        object(source, "additionalOperations", operations -> operations.keysAsStrings()
                .forEach(method -> object(operations, method,
                                          operation -> builder.additionalOperation(method, operation(operation)))));
        array(source, "servers", servers -> servers.values()
                .forEach(server -> object(server, value -> builder.server(server(value)))));
        array(source, "parameters", parameters -> parameters.values()
                .forEach(parameter -> object(parameter, value -> builder.parameter(parameter(value)))));
        return builder.build();
    }

    private static boolean isFixedPathOperationField(String value) {
        return FIXED_PATH_OPERATION_FIELDS.contains(value);
    }

    private static OpenApiDocument.Operation operation(JsonObject source) {
        OpenApiDocument.OperationBuilder builder = OpenApiDocument.Operation.builder();
        array(source, "tags", tags -> stringValues(tags).forEach(builder::tag));
        string(source, "summary", builder::summary);
        string(source, "description", builder::description);
        object(source, "externalDocs", docs -> builder.externalDocs(externalDocs(docs)));
        string(source, "operationId", builder::operationId);
        array(source, "parameters", parameters -> parameters.values()
                .forEach(parameter -> object(parameter, value -> builder.parameter(parameter(value)))));
        object(source, "requestBody", requestBody -> builder.requestBody(requestBody(requestBody)));
        object(source, "responses", responses -> responses.keysAsStrings()
                .forEach(status -> object(responses, status, response -> builder.response(status, response(response)))));
        object(source, "callbacks", callbacks -> callbacks.keysAsStrings()
                .forEach(name -> object(callbacks, name, callback -> builder.callback(name, pathItem(callback)))));
        bool(source, "deprecated", builder::deprecated);
        array(source, "security", security -> {
            if (security.values().isEmpty()) {
                builder.security(List.of());
                return;
            }
            security.values()
                    .forEach(requirement -> object(requirement,
                                                   value -> builder.securityRequirement(securityRequirement(value))));
        });
        array(source, "servers", servers -> servers.values()
                .forEach(server -> object(server, value -> builder.server(server(value)))));
        extensions(source, builder::extension);
        return builder.build();
    }

    private static OpenApiDocument.Parameter parameter(JsonObject source) {
        if (string(source, "$ref").isPresent()) {
            OpenApiDocument.ParameterBuilder builder = OpenApiDocument.Parameter.builder();
            reference(source, builder::ref, builder::summary, builder::description);
            return builder.build();
        }
        OpenApiDocument.ParameterBuilder builder = OpenApiDocument.Parameter.builder();
        string(source, "name", builder::name);
        string(source, "in", builder::in);
        string(source, "description", builder::description);
        bool(source, "required", builder::required);
        bool(source, "deprecated", builder::deprecated);
        bool(source, "allowEmptyValue", builder::allowEmptyValue);
        string(source, "style", builder::style);
        bool(source, "explode", builder::explode);
        bool(source, "allowReserved", builder::allowReserved);
        value(source, "schema", builder::schema);
        value(source, "example", builder::example);
        object(source, "examples", examples -> examples.keysAsStrings()
                .forEach(name -> object(examples, name, example -> builder.example(name, example(example)))));
        object(source, "content", content -> content.keysAsStrings()
                .forEach(mediaType -> object(content, mediaType,
                                             value -> builder.content(mediaType, mediaType(value)))));
        return builder.build();
    }

    private static OpenApiDocument.Header header(JsonObject source) {
        if (string(source, "$ref").isPresent()) {
            OpenApiDocument.HeaderBuilder builder = OpenApiDocument.Header.builder();
            reference(source, builder::ref, builder::summary, builder::description);
            return builder.build();
        }
        OpenApiDocument.HeaderBuilder builder = OpenApiDocument.Header.builder();
        string(source, "description", builder::description);
        bool(source, "required", builder::required);
        bool(source, "deprecated", builder::deprecated);
        bool(source, "allowEmptyValue", builder::allowEmptyValue);
        string(source, "style", builder::style);
        bool(source, "explode", builder::explode);
        bool(source, "allowReserved", builder::allowReserved);
        value(source, "schema", builder::schema);
        value(source, "example", builder::example);
        object(source, "examples", examples -> examples.keysAsStrings()
                .forEach(name -> object(examples, name, example -> builder.example(name, example(example)))));
        object(source, "content", content -> content.keysAsStrings()
                .forEach(mediaType -> object(content, mediaType,
                                             value -> builder.content(mediaType, mediaType(value)))));
        return builder.build();
    }

    private static OpenApiDocument.RequestBody requestBody(JsonObject source) {
        if (string(source, "$ref").isPresent()) {
            OpenApiDocument.RequestBodyBuilder builder = OpenApiDocument.RequestBody.builder();
            reference(source, builder::ref, builder::summary, builder::description);
            return builder.build();
        }
        OpenApiDocument.RequestBodyBuilder builder = OpenApiDocument.RequestBody.builder();
        string(source, "description", builder::description);
        object(source, "content", content -> content.keysAsStrings()
                .forEach(mediaType -> object(content, mediaType,
                                             value -> builder.content(mediaType, mediaType(value)))));
        bool(source, "required", builder::required);
        return builder.build();
    }

    private static OpenApiDocument.Response response(JsonObject source) {
        if (string(source, "$ref").isPresent()) {
            OpenApiDocument.ResponseBuilder builder = OpenApiDocument.Response.builder();
            reference(source, builder::ref, builder::summary, builder::description);
            return builder.build();
        }
        OpenApiDocument.ResponseBuilder builder = OpenApiDocument.Response.builder();
        string(source, "description", builder::description);
        string(source, "summary", builder::summary);
        object(source, "headers", headers -> headers.keysAsStrings()
                .forEach(name -> object(headers, name, header -> builder.header(name, header(header)))));
        object(source, "content", content -> content.keysAsStrings()
                .forEach(mediaType -> object(content, mediaType,
                                             value -> builder.content(mediaType, mediaType(value)))));
        object(source, "links", links -> links.keysAsStrings()
                .forEach(name -> object(links, name, link -> builder.link(name, link(link)))));
        return builder.build();
    }

    private static OpenApiDocument.MediaTypeObject mediaType(JsonObject source) {
        if (string(source, "$ref").isPresent()) {
            OpenApiDocument.MediaTypeObjectBuilder builder = OpenApiDocument.MediaTypeObject.builder();
            reference(source, builder::ref, builder::summary, builder::description);
            return builder.build();
        }
        OpenApiDocument.MediaTypeObjectBuilder builder = OpenApiDocument.MediaTypeObject.builder();
        value(source, "schema", builder::schema);
        value(source, "itemSchema", builder::itemSchema);
        value(source, "example", builder::example);
        object(source, "examples", examples -> examples.keysAsStrings()
                .forEach(name -> object(examples, name, example -> builder.example(name, example(example)))));
        object(source, "encoding", encodings -> encodings.keysAsStrings()
                .forEach(name -> object(encodings, name, encoding -> builder.encoding(name, encoding(encoding)))));
        array(source, "prefixEncoding", builder::prefixEncoding);
        object(source, "itemEncoding", itemEncoding -> builder.itemEncoding(encoding(itemEncoding)));
        return builder.build();
    }

    private static OpenApiDocument.Encoding encoding(JsonObject source) {
        OpenApiDocument.EncodingBuilder builder = OpenApiDocument.Encoding.builder();
        string(source, "contentType", builder::contentType);
        object(source, "headers", headers -> headers.keysAsStrings()
                .forEach(name -> object(headers, name, header -> builder.header(name, header(header)))));
        object(source, "encoding", encodings -> encodings.keysAsStrings()
                .forEach(name -> object(encodings, name, encoding -> builder.encoding(name, encoding(encoding)))));
        array(source, "prefixEncoding", builder::prefixEncoding);
        object(source, "itemEncoding", itemEncoding -> builder.itemEncoding(encoding(itemEncoding)));
        string(source, "style", builder::style);
        bool(source, "explode", builder::explode);
        bool(source, "allowReserved", builder::allowReserved);
        return builder.build();
    }

    private static OpenApiDocument.Example example(JsonObject source) {
        if (string(source, "$ref").isPresent()) {
            OpenApiDocument.ExampleBuilder builder = OpenApiDocument.Example.builder();
            reference(source, builder::ref, builder::summary, builder::description);
            return builder.build();
        }
        OpenApiDocument.ExampleBuilder builder = OpenApiDocument.Example.builder();
        string(source, "summary", builder::summary);
        string(source, "description", builder::description);
        value(source, "value", builder::value);
        value(source, "dataValue", builder::dataValue);
        string(source, "serializedValue", builder::serializedValue);
        string(source, "externalValue", builder::externalValue);
        return builder.build();
    }

    private static OpenApiDocument.Link link(JsonObject source) {
        if (string(source, "$ref").isPresent()) {
            OpenApiDocument.LinkBuilder builder = OpenApiDocument.Link.builder();
            reference(source, builder::ref, builder::summary, builder::description);
            return builder.build();
        }
        OpenApiDocument.LinkBuilder builder = OpenApiDocument.Link.builder();
        string(source, "operationRef", builder::operationRef);
        string(source, "operationId", builder::operationId);
        object(source, "parameters", builder::parameters);
        value(source, "requestBody", builder::requestBody);
        string(source, "description", builder::description);
        object(source, "server", server -> builder.server(server(server)));
        return builder.build();
    }

    private static OpenApiDocument.Components components(JsonObject source) {
        OpenApiDocument.ComponentsBuilder builder = OpenApiDocument.Components.builder();
        object(source, "schemas", values -> values.keysAsStrings()
                .forEach(name -> value(values, name, schema -> builder.schema(name, schema))));
        object(source, "responses", values -> values.keysAsStrings()
                .forEach(name -> object(values, name, response -> builder.response(name, response(response)))));
        object(source, "parameters", values -> values.keysAsStrings()
                .forEach(name -> object(values, name, parameter -> builder.parameter(name, parameter(parameter)))));
        object(source, "examples", values -> values.keysAsStrings()
                .forEach(name -> object(values, name, example -> builder.example(name, example(example)))));
        object(source, "requestBodies", values -> values.keysAsStrings()
                .forEach(name -> object(values, name, requestBody -> builder.requestBody(name, requestBody(requestBody)))));
        object(source, "headers", values -> values.keysAsStrings()
                .forEach(name -> object(values, name, header -> builder.header(name, header(header)))));
        object(source, "securitySchemes", values -> values.keysAsStrings()
                .forEach(name -> object(values, name, scheme -> builder.securityScheme(name, securityScheme(scheme)))));
        object(source, "links", values -> values.keysAsStrings()
                .forEach(name -> object(values, name, link -> builder.link(name, link(link)))));
        object(source, "callbacks", values -> values.keysAsStrings()
                .forEach(name -> object(values, name, callback -> builder.callback(name, pathItem(callback)))));
        object(source, "pathItems", values -> values.keysAsStrings()
                .forEach(name -> object(values, name, pathItem -> builder.pathItem(name, pathItem(pathItem)))));
        object(source, "mediaTypes", values -> values.keysAsStrings()
                .forEach(name -> object(values, name, mediaType -> builder.mediaType(name, mediaType(mediaType)))));
        return builder.build();
    }

    private static OpenApiDocument.SecurityScheme securityScheme(JsonObject source) {
        if (string(source, "$ref").isPresent()) {
            OpenApiDocument.SecuritySchemeBuilder builder = OpenApiDocument.SecurityScheme.builder();
            reference(source, builder::ref, builder::summary, builder::description);
            return builder.build();
        }
        OpenApiDocument.SecuritySchemeBuilder builder = OpenApiDocument.SecurityScheme.builder();
        string(source, "type", builder::type);
        string(source, "description", builder::description);
        string(source, "name", builder::name);
        string(source, "in", builder::in);
        string(source, "scheme", builder::scheme);
        string(source, "bearerFormat", builder::bearerFormat);
        object(source, "flows", builder::flows);
        string(source, "openIdConnectUrl", builder::openIdConnectUrl);
        string(source, "oauth2MetadataUrl", builder::oauth2MetadataUrl);
        bool(source, "deprecated", builder::deprecated);
        return builder.build();
    }

    private static OpenApiDocument.SecurityRequirement securityRequirement(JsonObject source) {
        OpenApiDocument.SecurityRequirementBuilder builder = OpenApiDocument.SecurityRequirement.builder();
        source.keysAsStrings()
                .forEach(name -> array(source, name, scopes -> builder.scheme(name, stringValues(scopes))));
        return builder.build();
    }

    private static void reference(JsonObject source,
                                  Consumer<String> ref,
                                  Consumer<String> summary,
                                  Consumer<String> description) {
        string(source, "$ref", ref);
        string(source, "summary", summary);
        string(source, "description", description);
    }

    private static void extensions(JsonObject source, BiConsumer<String, JsonValue> consumer) {
        source.keysAsStrings()
                .stream()
                .filter(name -> name.startsWith("x-"))
                .forEach(name -> value(source, name, value -> consumer.accept(name, value)));
    }

    private static List<String> stringValues(JsonArray array) {
        List<String> result = new ArrayList<>();
        array.values().forEach(value -> {
            if (value.type() == JsonValueType.STRING) {
                result.add(value.asString().value());
            }
        });
        return result;
    }

    private static Optional<String> string(JsonObject source, String name) {
        return source.value(name)
                .filter(value -> value.type() == JsonValueType.STRING)
                .map(value -> value.asString().value());
    }

    private static void string(JsonObject source, String name, Consumer<String> consumer) {
        string(source, name).ifPresent(consumer);
    }

    private static void bool(JsonObject source, String name, Consumer<Boolean> consumer) {
        source.value(name)
                .filter(value -> value.type() == JsonValueType.BOOLEAN)
                .map(value -> value.asBoolean().value())
                .ifPresent(consumer);
    }

    private static void object(JsonObject source, String name, Consumer<JsonObject> consumer) {
        source.value(name).ifPresent(value -> object(value, consumer));
    }

    private static void object(JsonValue value, Consumer<JsonObject> consumer) {
        if (value.type() == JsonValueType.OBJECT) {
            consumer.accept(value.asObject());
        }
    }

    private static void array(JsonObject source, String name, Consumer<JsonArray> consumer) {
        source.value(name)
                .filter(value -> value.type() == JsonValueType.ARRAY)
                .map(JsonValue::asArray)
                .ifPresent(consumer);
    }

    private static void value(JsonObject source, String name, Consumer<JsonValue> consumer) {
        source.value(name).ifPresent(consumer);
    }
}
