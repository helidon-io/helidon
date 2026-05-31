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

package io.helidon.openapi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.common.Api;

/**
 * OpenAPI document annotations.
 */
@Api.Preview
@Api.Since("27.0.0")
public final class OpenApi {
    private OpenApi() {
    }

    /**
     * Requiredness override.
     */
    public enum Required {
        /**
         * Infer requiredness from the HTTP binding and Java type.
         */
        UNSPECIFIED,

        /**
         * Document the item as required.
         */
        TRUE,

        /**
         * Document the item as optional.
         */
        FALSE
    }

    /**
     * Parameter explode override.
     */
    public enum Explode {
        /**
         * Infer explode from the HTTP binding and parameter type.
         */
        UNSPECIFIED,

        /**
         * Document the parameter with {@code explode=true}.
         */
        TRUE,

        /**
         * Document the parameter with {@code explode=false}.
         */
        FALSE
    }

    /**
     * Parameter serialization style.
     */
    public enum Style {
        /**
         * Infer style from the parameter location and type.
         */
        UNSPECIFIED,

        /**
         * Matrix style parameters.
         */
        MATRIX,

        /**
         * Label style parameters.
         */
        LABEL,

        /**
         * Form style parameters.
         */
        FORM,

        /**
         * Simple style parameters.
         */
        SIMPLE,

        /**
         * Space-delimited array parameters.
         */
        SPACE_DELIMITED,

        /**
         * Pipe-delimited array parameters.
         */
        PIPE_DELIMITED,

        /**
         * Deep object style parameters.
         */
        DEEP_OBJECT
    }

    /**
     * Marker for a type that contributes document-level OpenAPI metadata.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Document {
        /**
         * Document identity URI.
         *
         * @return document identity URI
         */
        String self() default "";

        /**
         * JSON Schema dialect URI.
         *
         * @return JSON Schema dialect URI
         */
        String jsonSchemaDialect() default "";
    }

    /**
     * OpenAPI Info Object metadata.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Info {
        /**
         * API title.
         *
         * @return title
         */
        String title();

        /**
         * API version.
         *
         * @return version
         */
        String version();

        /**
         * API description.
         *
         * @return description
         */
        String description() default "";

        /**
         * API summary.
         *
         * @return summary
         */
        String summary() default "";

        /**
         * API terms of service URL.
         *
         * @return terms of service URL
         */
        String termsOfService() default "";
    }

    /**
     * OpenAPI Contact Object metadata.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Contact {
        /**
         * Contact name.
         *
         * @return name
         */
        String value() default "";

        /**
         * Contact URL.
         *
         * @return URL
         */
        String url() default "";

        /**
         * Contact email.
         *
         * @return email
         */
        String email() default "";
    }

    /**
     * OpenAPI License Object metadata.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface License {
        /**
         * License name.
         *
         * @return name
         */
        String value();

        /**
         * License URL.
         *
         * @return URL
         */
        String url() default "";

        /**
         * SPDX license identifier.
         *
         * @return license identifier
         */
        String identifier() default "";
    }

    /**
     * OpenAPI Server Object metadata.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @Repeatable(Servers.class)
    @Documented
    public @interface Server {
        /**
         * Server URL.
         *
         * @return URL
         */
        String value();

        /**
         * Server description.
         *
         * @return description
         */
        String description() default "";

        /**
         * Server name.
         *
         * @return name
         */
        String name() default "";
    }

    /**
     * Container for repeated {@link Server}.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Servers {
        /**
         * Servers.
         *
         * @return servers
         */
        Server[] value();
    }

    /**
     * OpenAPI Tag Object metadata.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Repeatable(Tags.class)
    @Documented
    public @interface Tag {
        /**
         * Tag name.
         *
         * @return name
         */
        String value();

        /**
         * Tag description.
         *
         * @return description
         */
        String description() default "";

        /**
         * Tag summary.
         *
         * @return summary
         */
        String summary() default "";

        /**
         * Parent tag name.
         *
         * @return parent tag name
         */
        String parent() default "";

        /**
         * Tag kind.
         *
         * @return tag kind
         */
        String kind() default "";
    }

    /**
     * Container for repeated {@link Tag}.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Tags {
        /**
         * Tags.
         *
         * @return tags
         */
        Tag[] value();
    }

    /**
     * OpenAPI External Documentation Object metadata.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface ExternalDocs {
        /**
         * Documentation URL.
         *
         * @return URL
         */
        String value();

        /**
         * Documentation description.
         *
         * @return description
         */
        String description() default "";
    }

    /**
     * OpenAPI Operation Object metadata.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Operation {
        /**
         * Short operation summary.
         *
         * @return summary
         */
        String value() default "";

        /**
         * Operation id.
         *
         * @return operation id
         */
        String operationId() default "";

        /**
         * Operation description.
         *
         * @return description
         */
        String description() default "";

        /**
         * Operation tags.
         *
         * @return tag names
         */
        String[] tags() default {};

        /**
         * Whether the operation is deprecated.
         *
         * @return deprecated flag
         */
        boolean deprecated() default false;
    }

    /**
     * Excludes an endpoint type or operation method from generated OpenAPI output.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Hidden {
    }

    /**
     * OpenAPI Parameter Object metadata.
     */
    @Target({ElementType.METHOD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.CLASS)
    @Repeatable(Parameters.class)
    @Documented
    public @interface Parameter {
        /**
         * Parameter description.
         *
         * @return description
         */
        String value() default "";

        /**
         * Parameter name. Defaults to the HTTP binding name on parameter-target usage.
         *
         * @return name
         */
        String name() default "";

        /**
         * Parameter location. Defaults to the HTTP binding location on parameter-target usage.
         *
         * @return location
         */
        String in() default "";

        /**
         * Requiredness override.
         *
         * @return requiredness
         */
        Required required() default Required.UNSPECIFIED;

        /**
         * Parameter example.
         *
         * @return example
         */
        String example() default "";

        /**
         * Parameter examples.
         *
         * @return examples
         */
        Example[] examples() default {};

        /**
         * Parameter content entries.
         *
         * @return content
         */
        Content[] content() default {};

        /**
         * Parameter style.
         *
         * @return style
         */
        Style style() default Style.UNSPECIFIED;

        /**
         * Parameter explode override.
         *
         * @return explode
         */
        Explode explode() default Explode.UNSPECIFIED;

        /**
         * Whether reserved characters are allowed unencoded.
         *
         * @return allow reserved flag
         */
        boolean allowReserved() default false;

        /**
         * Whether the parameter is deprecated.
         *
         * @return deprecated flag
         */
        boolean deprecated() default false;
    }

    /**
     * Container for repeated {@link Parameter}.
     */
    @Target({ElementType.METHOD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Parameters {
        /**
         * Parameters.
         *
         * @return parameters
         */
        Parameter[] value();
    }

    /**
     * OpenAPI Request Body Object metadata.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface RequestBody {
        /**
         * Request body description.
         *
         * @return description
         */
        String value() default "";

        /**
         * Requiredness override.
         *
         * @return requiredness
         */
        Required required() default Required.UNSPECIFIED;

        /**
         * Request body content entries.
         *
         * @return content
         */
        Content[] content() default {};
    }

    /**
     * OpenAPI Response Object metadata.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    @Repeatable(Responses.class)
    @Documented
    public @interface Response {
        /**
         * HTTP status code.
         *
         * @return status code
         */
        int status();

        /**
         * Response description.
         *
         * @return description
         */
        String description();

        /**
         * Response summary.
         *
         * @return summary
         */
        String summary() default "";

        /**
         * Response content entries.
         *
         * @return content
         */
        Content[] content() default {};
    }

    /**
     * Container for repeated {@link Response}.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Responses {
        /**
         * Responses.
         *
         * @return responses
         */
        Response[] value();
    }

    /**
     * OpenAPI Media Type Object metadata.
     */
    @Target({})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Content {
        /**
         * Media type.
         *
         * @return media type
         */
        String value() default "";

        /**
         * Schema class. Defaults to the effective request or response entity type.
         *
         * @return schema class
         */
        Class<?> schema() default Void.class;

        /**
         * Item schema class for OpenAPI 3.2 sequential media types.
         *
         * @return item schema class
         */
        Class<?> itemSchema() default Void.class;

        /**
         * Examples.
         *
         * @return examples
         */
        Example[] examples() default {};
    }

    /**
     * OpenAPI Example Object metadata.
     */
    @Target({})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Example {
        /**
         * Example name.
         *
         * @return name
         */
        String name() default "";

        /**
         * Example summary.
         *
         * @return summary
         */
        String summary() default "";

        /**
         * Example description.
         *
         * @return description
         */
        String description() default "";

        /**
         * Example value.
         *
         * @return value
         */
        String value() default "";

        /**
         * OpenAPI 3.2 data value.
         *
         * @return data value
         */
        String dataValue() default "";

        /**
         * OpenAPI 3.2 serialized value.
         *
         * @return serialized value
         */
        String serializedValue() default "";

        /**
         * External example value URI.
         *
         * @return external value URI
         */
        String externalValue() default "";
    }

    /**
     * OpenAPI Specification Extension metadata.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @Repeatable(Extensions.class)
    @Documented
    public @interface Extension {
        /**
         * Extension name.
         *
         * @return name
         */
        String name();

        /**
         * Extension value.
         *
         * @return value
         */
        String value();
    }

    /**
     * Container for repeated {@link Extension}.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Extensions {
        /**
         * Extensions.
         *
         * @return extensions
         */
        Extension[] value();
    }

    /**
     * OpenAPI Security Scheme Object metadata.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Repeatable(SecuritySchemes.class)
    @Documented
    public @interface SecurityScheme {
        /**
         * Component name.
         *
         * @return name
         */
        String name();

        /**
         * Scheme type.
         *
         * @return type
         */
        String type();

        /**
         * Security scheme description.
         *
         * @return description
         */
        String description() default "";

        /**
         * API key parameter name.
         *
         * @return API key parameter name
         */
        String apiKeyName() default "";

        /**
         * HTTP authorization scheme.
         *
         * @return scheme
         */
        String scheme() default "";

        /**
         * Bearer format.
         *
         * @return bearer format
         */
        String bearerFormat() default "";

        /**
         * API key location.
         *
         * @return location
         */
        String in() default "";

        /**
         * OAuth flows.
         *
         * @return OAuth flows
         */
        OAuthFlows flows() default @OAuthFlows;

        /**
         * OpenID Connect discovery URL.
         *
         * @return OpenID Connect discovery URL
         */
        String openIdConnectUrl() default "";

        /**
         * OpenAPI 3.2 OAuth 2 metadata URL.
         *
         * @return OAuth 2 metadata URL
         */
        String oauth2MetadataUrl() default "";

        /**
         * Whether the security scheme is deprecated.
         *
         * @return deprecated flag
         */
        boolean deprecated() default false;
    }

    /**
     * OpenAPI OAuth Flows Object metadata.
     */
    @Target({})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface OAuthFlows {
        /**
         * OAuth implicit flow.
         *
         * @return implicit flow
         */
        OAuthFlow implicit() default @OAuthFlow;

        /**
         * OAuth resource owner password flow.
         *
         * @return password flow
         */
        OAuthFlow password() default @OAuthFlow;

        /**
         * OAuth client credentials flow.
         *
         * @return client credentials flow
         */
        OAuthFlow clientCredentials() default @OAuthFlow;

        /**
         * OAuth authorization code flow.
         *
         * @return authorization code flow
         */
        OAuthFlow authorizationCode() default @OAuthFlow;

        /**
         * OpenAPI 3.2 OAuth device authorization flow.
         *
         * @return device authorization flow
         */
        OAuthFlow deviceAuthorization() default @OAuthFlow;
    }

    /**
     * OpenAPI OAuth Flow Object metadata.
     */
    @Target({})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface OAuthFlow {
        /**
         * Authorization URL.
         *
         * @return authorization URL
         */
        String authorizationUrl() default "";

        /**
         * OpenAPI 3.2 device authorization URL.
         *
         * @return device authorization URL
         */
        String deviceAuthorizationUrl() default "";

        /**
         * Token URL.
         *
         * @return token URL
         */
        String tokenUrl() default "";

        /**
         * Refresh URL.
         *
         * @return refresh URL
         */
        String refreshUrl() default "";

        /**
         * OAuth scopes.
         *
         * @return scopes
         */
        OAuthScope[] scopes() default {};
    }

    /**
     * OpenAPI OAuth scope metadata.
     */
    @Target({})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface OAuthScope {
        /**
         * Scope name.
         *
         * @return scope name
         */
        String value();

        /**
         * Scope description.
         *
         * @return scope description
         */
        String description() default "";
    }

    /**
     * Container for repeated {@link SecurityScheme}.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface SecuritySchemes {
        /**
         * Security schemes.
         *
         * @return security schemes
         */
        SecurityScheme[] value();
    }

    /**
     * OpenAPI Security Requirement Object metadata.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @Repeatable(SecurityRequirements.class)
    @Documented
    public @interface SecurityRequirement {
        /**
         * Required scheme names.
         *
         * @return scheme names
         */
        String[] value() default {};

        /**
         * OAuth/OpenID Connect scopes for all named schemes.
         *
         * @return scopes
         */
        String[] scopes() default {};
    }

    /**
     * Container for repeated {@link SecurityRequirement}.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface SecurityRequirements {
        /**
         * Security requirements.
         *
         * @return security requirements
         */
        SecurityRequirement[] value();
    }
}
