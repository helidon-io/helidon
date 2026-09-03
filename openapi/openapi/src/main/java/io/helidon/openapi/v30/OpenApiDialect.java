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

import java.util.Objects;
import java.util.Set;

final class OpenApiDialect {
    private final OpenApi3xMapperRules rules;

    private OpenApiDialect(OpenApi3xMapperRules rules) {
        this.rules = Objects.requireNonNull(rules);
    }

    static OpenApiDialect create(OpenApi3xMapperRules rules) {
        return new OpenApiDialect(rules);
    }

    String version() {
        return rules.targetVersion();
    }

    Set<String> fixedPathOperationFields() {
        return rules.fixedPathOperationFields();
    }

    Set<String> fields(OpenApiDocumentWalker.Kind kind) {
        return switch (kind) {
        case DOCUMENT -> rules.documentFields();
        case INFO -> rules.infoFields();
        case CONTACT -> rules.contactFields();
        case LICENSE -> rules.licenseFields();
        case EXTERNAL_DOCS -> rules.externalDocsFields();
        case SERVER -> rules.serverFields();
        case SERVER_VARIABLE -> rules.serverVariableFields();
        case TAG -> rules.tagFields();
        case PATH_ITEM -> rules.pathItemFields();
        case OPERATION -> rules.operationFields();
        case PARAMETER -> rules.parameterFields();
        case HEADER -> rules.headerFields();
        case REQUEST_BODY -> rules.requestBodyFields();
        case RESPONSE -> rules.responseFields();
        case MEDIA_TYPE -> rules.mediaTypeFields();
        case ENCODING -> rules.encodingFields();
        case COMPONENTS -> rules.componentsFields();
        case SECURITY_SCHEME -> rules.securitySchemeFields();
        case OAUTH_FLOWS -> rules.oauthFlowsFields();
        case OAUTH_FLOW -> rules.oauthFlowFields();
        case EXAMPLE -> rules.exampleFields();
        case LINK -> rules.linkFields();
        default -> Set.of();
        };
    }

    Set<String> oauthFlowFields() {
        return rules.oauthFlowsFields();
    }

    Set<String> parameterLocations() {
        return rules.parameterLocations();
    }

    Set<String> securitySchemeTypes() {
        return rules.securitySchemeTypes();
    }

    boolean operationResponsesRequired() {
        return rules.operationResponsesRequired();
    }

    boolean responseDescriptionRequired() {
        return rules.responseDescriptionRequired();
    }

    boolean supportsQueryStringParameters() {
        return rules.parameterLocations().contains("querystring");
    }

    boolean supportsBooleanSchemas() {
        return !version().startsWith("3.0");
    }

    boolean additionalItemsHasSchemaValue() {
        return version().startsWith("3.0");
    }

    boolean schemaReferenceSiblingsIgnored() {
        return version().startsWith("3.0");
    }
}
