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

import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;

/**
 * OpenAPI 3.x version-specific mapper rules.
 */
@Api.Internal
@Prototype.Blueprint
interface OpenApi3xMapperRulesBlueprint {
    /**
     * Target OpenAPI version.
     *
     * @return target OpenAPI version
     */
    String targetVersion();

    /**
     * Whether operations must define responses.
     *
     * @return whether operation responses are required
     */
    @Option.DefaultBoolean(false)
    boolean operationResponsesRequired();

    /**
     * Whether responses must define a non-blank description.
     *
     * @return whether response descriptions are required
     */
    @Option.DefaultBoolean(false)
    boolean responseDescriptionRequired();

    /**
     * Document fields.
     *
     * @return document fields
     */
    @Option.Singular
    Set<String> documentFields();

    /**
     * Info fields.
     *
     * @return info fields
     */
    @Option.Singular
    Set<String> infoFields();

    /**
     * Contact fields.
     *
     * @return contact fields
     */
    @Option.Singular
    Set<String> contactFields();

    /**
     * License fields.
     *
     * @return license fields
     */
    @Option.Singular
    Set<String> licenseFields();

    /**
     * Server fields.
     *
     * @return server fields
     */
    @Option.Singular
    Set<String> serverFields();

    /**
     * Server variable fields.
     *
     * @return server variable fields
     */
    @Option.Singular
    Set<String> serverVariableFields();

    /**
     * Tag fields.
     *
     * @return tag fields
     */
    @Option.Singular
    Set<String> tagFields();

    /**
     * Path item fields.
     *
     * @return path item fields
     */
    @Option.Singular
    Set<String> pathItemFields();

    /**
     * Fixed path operation fields.
     *
     * @return fixed path operation fields
     */
    @Option.Singular
    Set<String> fixedPathOperationFields();

    /**
     * Operation fields.
     *
     * @return operation fields
     */
    @Option.Singular
    Set<String> operationFields();

    /**
     * Parameter fields.
     *
     * @return parameter fields
     */
    @Option.Singular
    Set<String> parameterFields();

    /**
     * Parameter locations.
     *
     * @return parameter locations
     */
    @Option.Singular
    Set<String> parameterLocations();

    /**
     * Header fields.
     *
     * @return header fields
     */
    @Option.Singular
    Set<String> headerFields();

    /**
     * Request body fields.
     *
     * @return request body fields
     */
    @Option.Singular
    Set<String> requestBodyFields();

    /**
     * Response fields.
     *
     * @return response fields
     */
    @Option.Singular
    Set<String> responseFields();

    /**
     * Media type fields.
     *
     * @return media type fields
     */
    @Option.Singular
    Set<String> mediaTypeFields();

    /**
     * Encoding fields.
     *
     * @return encoding fields
     */
    @Option.Singular
    Set<String> encodingFields();

    /**
     * Components fields.
     *
     * @return components fields
     */
    @Option.Singular
    Set<String> componentsFields();

    /**
     * Security scheme fields.
     *
     * @return security scheme fields
     */
    @Option.Singular
    Set<String> securitySchemeFields();

    /**
     * Security scheme types.
     *
     * @return security scheme types
     */
    @Option.Singular
    Set<String> securitySchemeTypes();

    /**
     * OAuth flows fields.
     *
     * @return OAuth flows fields
     */
    @Option.Singular
    Set<String> oauthFlowsFields();

    /**
     * OAuth flow fields.
     *
     * @return OAuth flow fields
     */
    @Option.Singular
    Set<String> oauthFlowFields();

    /**
     * Link fields.
     *
     * @return link fields
     */
    @Option.Singular
    Set<String> linkFields();

    /**
     * Example fields.
     *
     * @return example fields
     */
    @Option.Singular
    Set<String> exampleFields();

    /**
     * External docs fields.
     *
     * @return external docs fields
     */
    @Option.Singular
    Set<String> externalDocsFields();
}
