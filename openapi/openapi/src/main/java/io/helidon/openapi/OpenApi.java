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
     * <p>
     * Declarative OpenAPI generation supports {@link #SIMPLE} style for path and header parameters, and {@link #FORM},
     * {@link #SPACE_DELIMITED}, or {@link #PIPE_DELIMITED} style for query parameters. Delimited query styles require
     * list-valued parameters and cannot use {@link Explode#TRUE}. Header parameters cannot use {@link Explode#TRUE}.
     * {@link #DEEP_OBJECT}, {@link #MATRIX}, and {@link #LABEL} are not supported by declarative HTTP parameter binding.
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
     * Marker for a type that contributes document-level OpenAPI metadata and enables generated OpenAPI data for
     * declarative endpoints.
     * <p>
     * A type annotated with {@code @OpenApi.Document} must also be annotated with {@link Info @OpenApi.Info}.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Document {
        /**
         * Document identity URI.
         * <p>
         * Rendered only for OpenAPI 3.2 output.
         *
         * @return document identity URI
         */
        String self() default "";

        /**
         * JSON Schema dialect URI.
         * <p>
         * Rendered only for OpenAPI 3.1 and later output.
         *
         * @return JSON Schema dialect URI
         */
        String jsonSchemaDialect() default "";
    }

    /**
     * OpenAPI Info Object metadata.
     * <p>
     * Use only on {@link Document @OpenApi.Document} metadata types.
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
         * <p>
         * Rendered only for OpenAPI 3.1 and later output.
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
     * <p>
     * Use only on {@link Document @OpenApi.Document} metadata types.
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
     * <p>
     * Use only on {@link Document @OpenApi.Document} metadata types.
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
         * <p>
         * For OpenAPI 3.1 and later output, this value is ignored when {@link #identifier()} is set.
         *
         * @return URL
         */
        String url() default "";

        /**
         * SPDX license identifier.
         * <p>
         * Rendered only for OpenAPI 3.1 and later output. When set together with {@link #url()}, this value takes
         * precedence for those versions; the URL remains available for OpenAPI 3.0 output.
         *
         * @return license identifier
         */
        String identifier() default "";
    }

    /**
     * OpenAPI Server Object metadata.
     * <p>
     * Type-level usage applies only to {@link Document @OpenApi.Document} metadata types. Method-level usage applies to
     * generated operations.
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
         * <p>
         * Rendered only for OpenAPI 3.2 output.
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
     * <p>
     * Use only on {@link Document @OpenApi.Document} metadata types to declare top-level tags. Use
     * {@link Operation#tags()} to tag generated operations.
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
         * <p>
         * Rendered only for OpenAPI 3.2 output.
         *
         * @return summary
         */
        String summary() default "";

        /**
         * Parent tag name.
         * <p>
         * Rendered only for OpenAPI 3.2 output.
         *
         * @return parent tag name
         */
        String parent() default "";

        /**
         * Tag kind.
         * <p>
         * Rendered only for OpenAPI 3.2 output.
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
     * <p>
     * Type-level usage applies only to {@link Document @OpenApi.Document} metadata types. Method-level usage applies to
     * generated operations.
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
         * Complete OpenAPI path template for this operation. This is useful when the Helidon route template cannot be
         * represented directly as an OpenAPI path. The value is not relative to the declarative HTTP path annotation.
         * <p>
         * Path overrides must start with {@code /} and must declare the same path parameters as the generated route.
         * Use simple OpenAPI path parameters such as {@code {id}}. Regex constraints, optional path segments, wildcards,
         * escaped path characters, greedy parameters, and path parameter names containing path or template metacharacters
         * are not supported.
         *
         * @return OpenAPI path template
         */
        String path() default "";

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
     * <p>
     * On a method parameter, this annotation decorates the generated parameter from the declarative HTTP binding; it
     * cannot change the bound parameter {@link #name()} or {@link #in()} location. On a method, this annotation must
     * declare non-blank {@link #name()} and {@link #in()} values which match an existing generated path, query, or header
     * parameter.
     * <p>
     * Path parameters are always required and cannot be made optional. Query and header parameters which are required by
     * the Java signature or HTTP binding cannot be made optional. {@link #allowReserved()} can be used only for query
     * parameters. If {@link #content()} is configured, {@link #style()} and {@link #explode()} must not be configured.
     * <p>
     * Generated OpenAPI omits declarative header parameters named {@code Accept}, {@code Content-Type}, or
     * {@code Authorization}. Use media type metadata, request body metadata, or security metadata to describe those
     * concerns.
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
         * <p>
         * Method-target usage requires a non-blank value matching a generated parameter. Parameter-target usage cannot
         * override the generated parameter name.
         *
         * @return name
         */
        String name() default "";

        /**
         * Parameter location. Defaults to the HTTP binding location on parameter-target usage.
         * <p>
         * Method-target usage requires a non-blank value matching a generated parameter location. Supported generated
         * locations are {@code path}, {@code query}, and {@code header}. Parameter-target usage cannot override the
         * generated parameter location.
         *
         * @return location
         */
        String in() default "";

        /**
         * Requiredness override.
         * <p>
         * Path parameters are always required. Required query and header parameters cannot be made optional.
         *
         * @return requiredness
         */
        Required required() default Required.UNSPECIFIED;

        /**
         * Parameter example.
         * <p>
         * Mutually exclusive with {@link #examples()}. Can be used with generated schema parameters or explicit
         * {@link #content()}.
         *
         * @return example
         */
        String example() default "";

        /**
         * Parameter examples.
         * <p>
         * Mutually exclusive with {@link #example()}. Can be used with generated schema parameters or explicit
         * {@link #content()}.
         *
         * @return examples
         */
        Example[] examples() default {};

        /**
         * Parameter content entries.
         * <p>
         * At most one entry is supported. When configured, generated schema, {@link #style()}, and {@link #explode()}
         * are omitted. Parameter {@link #example()} or {@link #examples()} can still be configured.
         *
         * @return content
         */
        Content[] content() default {};

        /**
         * Parameter style.
         * <p>
         * Must remain unspecified when {@link #content()} is configured. See {@link Style} for supported styles by
         * parameter location.
         *
         * @return style
         */
        Style style() default Style.UNSPECIFIED;

        /**
         * Parameter explode override.
         * <p>
         * Must remain unspecified when {@link #content()} is configured. Header parameters cannot use
         * {@link Explode#TRUE}. Query parameters cannot use {@link Explode#TRUE} with {@link Style#SPACE_DELIMITED} or
         * {@link Style#PIPE_DELIMITED}.
         *
         * @return explode
         */
        Explode explode() default Explode.UNSPECIFIED;

        /**
         * Whether reserved characters are allowed unencoded.
         * <p>
         * Supported only for query parameters.
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
     * <p>
     * Use only on a method with an effective declarative HTTP request body input. The input can be a direct
     * {@link io.helidon.http.Http.Entity @Http.Entity} parameter, an
     * {@link io.helidon.http.Http.RequestParams @Http.RequestParams} record with an {@code @Http.Entity} component, or
     * one or more {@link io.helidon.http.Http.FormParam @Http.FormParam} parameters or request-param record components.
     * <p>
     * For entity inputs, content is inferred from the entity type and the method's consumed media types unless
     * {@link #content()} overrides it. For form inputs, generated content is always
     * {@code application/x-www-form-urlencoded}; the schema is inferred from the form field names and types and cannot be
     * overridden with {@link Content#schema()}.
     * <p>
     * Requiredness is inferred from the effective input: {@code Optional} entity parameters, entity components, and form
     * fields are optional, defaulted form fields are optional, and other entity or form inputs are required. Configure
     * {@link #required()} to override the request body required flag.
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
     * <p>
     * When a method declares one or more explicit responses, generated OpenAPI response content is taken only from
     * {@link #content()}; return-type response content is not inferred for those explicit responses.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    @Repeatable(Responses.class)
    @Documented
    public @interface Response {
        /**
         * HTTP status code in the range {@code 100..599}. A method can declare at most one response for each status.
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
         * <p>
         * Rendered only for OpenAPI 3.2 output.
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

        /**
         * Response headers.
         *
         * @return headers
         */
        Header[] headers() default {};
    }

    /**
     * OpenAPI Header Object metadata.
     */
    @Target({})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Header {
        /**
         * Header name.
         * <p>
         * Response header names cannot be {@code Content-Type} and cannot repeat case-insensitively within one response.
         * Use {@link Content} to describe response media types.
         *
         * @return header name
         */
        String name();

        /**
         * Header description.
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
         * Whether the header is deprecated.
         *
         * @return deprecated flag
         */
        boolean deprecated() default false;

        /**
         * Schema class. Defaults to {@link String}.
         *
         * @return schema class
         */
        Class<?> schema() default Void.class;

        /**
         * Header content entries.
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
         * <p>
         * Rendered only for OpenAPI 3.2 output.
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
     * <p>
     * {@link #value()} is mutually exclusive with {@link #dataValue()}, {@link #serializedValue()}, and
     * {@link #externalValue()}. {@link #serializedValue()} and {@link #externalValue()} are mutually exclusive.
     * {@link #dataValue()} can be used by itself, or with either {@link #serializedValue()} or
     * {@link #externalValue()} for OpenAPI 3.2 output.
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
         * <p>
         * Declarative OpenAPI generation parses valid JSON as a structured OpenAPI value and emits non-JSON text as a
         * string.
         * <p>
         * Mutually exclusive with {@link #dataValue()}, {@link #serializedValue()}, and {@link #externalValue()}.
         *
         * @return value
         */
        String value() default "";

        /**
         * OpenAPI 3.2 data value.
         * <p>
         * Declarative OpenAPI generation parses valid JSON as a structured OpenAPI value and emits non-JSON text as a
         * string.
         * <p>
         * Rendered only for OpenAPI 3.2 output.
         * <p>
         * Can be used with either {@link #serializedValue()} or {@link #externalValue()}. Mutually exclusive with
         * {@link #value()}.
         *
         * @return data value
         */
        String dataValue() default "";

        /**
         * OpenAPI 3.2 serialized value.
         * <p>
         * Declarative OpenAPI generation emits this value as a string and does not parse it as JSON.
         * <p>
         * Rendered only for OpenAPI 3.2 output.
         * <p>
         * Can be used with {@link #dataValue()}. Mutually exclusive with {@link #value()} and
         * {@link #externalValue()}.
         *
         * @return serialized value
         */
        String serializedValue() default "";

        /**
         * External example value URI.
         * <p>
         * Can be used with {@link #dataValue()}. Mutually exclusive with {@link #value()} and
         * {@link #serializedValue()}.
         *
         * @return external value URI
         */
        String externalValue() default "";
    }

    /**
     * OpenAPI Specification Extension metadata.
     * <p>
     * Type-level usage applies only to {@link Document @OpenApi.Document} metadata types. Method-level usage applies to
     * generated operations.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @Repeatable(Extensions.class)
    @Documented
    public @interface Extension {
        /**
         * Extension name. Must start with {@code x-}.
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
     * <p>
     * Supported {@link #type()} values are {@code apiKey}, {@code http}, {@code mutualTLS}, {@code oauth2}, and
     * {@code openIdConnect}. The {@code apiKey} type requires {@link #apiKeyName()} and {@link #in()} with
     * {@code query}, {@code header}, or {@code cookie}. The {@code http} type requires {@link #scheme()}. The
     * {@code mutualTLS} type has no additional required fields. The {@code oauth2} type requires {@link #flows()} with
     * at least one configured flow.
     * The {@code openIdConnect} type requires {@link #openIdConnectUrl()}.
     * <p>
     * Declarative OpenAPI generation rejects fields that do not apply to the selected {@link #type()}.
     * Prefer the type-specific annotations such as {@link ApiKeySecurityScheme}, {@link HttpSecurityScheme},
     * {@link MutualTlsSecurityScheme}, {@link OAuth2SecurityScheme}, and {@link OidcSecurityScheme} when they match
     * the security scheme you need.
     * <p>
     * Use only on {@link Document @OpenApi.Document} metadata types.
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
         * Scheme type. Supported values are {@code apiKey}, {@code http}, {@code mutualTLS}, {@code oauth2}, and
         * {@code openIdConnect}.
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
         * API key parameter name. Valid only when {@link #type()} is {@code apiKey}, where it is required.
         *
         * @return API key parameter name
         */
        String apiKeyName() default "";

        /**
         * HTTP authorization scheme. Valid only when {@link #type()} is {@code http}, where it is required.
         *
         * @return scheme
         */
        String scheme() default "";

        /**
         * Bearer format. Valid only when {@link #type()} is {@code http} and {@link #scheme()} is {@code bearer}.
         *
         * @return bearer format
         */
        String bearerFormat() default "";

        /**
         * API key location. Valid only when {@link #type()} is {@code apiKey}, where it is required; supported values
         * are {@code query}, {@code header}, and {@code cookie}.
         *
         * @return location
         */
        String in() default "";

        /**
         * OAuth flows. Valid only when {@link #type()} is {@code oauth2}, where at least one flow must be configured.
         *
         * @return OAuth flows
         */
        OAuthFlows flows() default @OAuthFlows;

        /**
         * OpenID Connect discovery URL. Valid only when {@link #type()} is {@code openIdConnect}, where it is
         * required.
         *
         * @return OpenID Connect discovery URL
         */
        String openIdConnectUrl() default "";

        /**
         * OpenAPI 3.2 OAuth 2 metadata URL. Valid only when {@link #type()} is {@code oauth2}.
         * <p>
         * Rendered only for OpenAPI 3.2 output.
         *
         * @return OAuth 2 metadata URL
         */
        String oauth2MetadataUrl() default "";

        /**
         * Whether the security scheme is deprecated.
         * <p>
         * Rendered only for OpenAPI 3.2 output.
         *
         * @return deprecated flag
         */
        boolean deprecated() default false;
    }

    /**
     * OpenAPI API Key Security Scheme Object metadata.
     * <p>
     * Use only on {@link Document @OpenApi.Document} metadata types.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Repeatable(ApiKeySecuritySchemes.class)
    @Documented
    public @interface ApiKeySecurityScheme {
        /**
         * Component name.
         *
         * @return name
         */
        String name();

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
        String apiKeyName();

        /**
         * API key location; supported values are {@code query}, {@code header}, and {@code cookie}.
         *
         * @return location
         */
        String in();

        /**
         * Whether the security scheme is deprecated.
         * <p>
         * Rendered only for OpenAPI 3.2 output.
         *
         * @return deprecated flag
         */
        boolean deprecated() default false;
    }

    /**
     * OpenAPI HTTP Security Scheme Object metadata.
     * <p>
     * Use only on {@link Document @OpenApi.Document} metadata types.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Repeatable(HttpSecuritySchemes.class)
    @Documented
    public @interface HttpSecurityScheme {
        /**
         * Component name.
         *
         * @return name
         */
        String name();

        /**
         * Security scheme description.
         *
         * @return description
         */
        String description() default "";

        /**
         * HTTP authorization scheme.
         *
         * @return scheme
         */
        String scheme();

        /**
         * Bearer format. Valid only when {@link #scheme()} is {@code bearer}.
         *
         * @return bearer format
         */
        String bearerFormat() default "";

        /**
         * Whether the security scheme is deprecated.
         * <p>
         * Rendered only for OpenAPI 3.2 output.
         *
         * @return deprecated flag
         */
        boolean deprecated() default false;
    }

    /**
     * OpenAPI Mutual TLS Security Scheme Object metadata.
     * <p>
     * Use only on {@link Document @OpenApi.Document} metadata types.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Repeatable(MutualTlsSecuritySchemes.class)
    @Documented
    public @interface MutualTlsSecurityScheme {
        /**
         * Component name.
         *
         * @return name
         */
        String name();

        /**
         * Security scheme description.
         *
         * @return description
         */
        String description() default "";

        /**
         * Whether the security scheme is deprecated.
         * <p>
         * Rendered only for OpenAPI 3.2 output.
         *
         * @return deprecated flag
         */
        boolean deprecated() default false;
    }

    /**
     * OpenAPI OAuth 2 Security Scheme Object metadata.
     * <p>
     * Use only on {@link Document @OpenApi.Document} metadata types.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Repeatable(OAuth2SecuritySchemes.class)
    @Documented
    public @interface OAuth2SecurityScheme {
        /**
         * Component name.
         *
         * @return name
         */
        String name();

        /**
         * Security scheme description.
         *
         * @return description
         */
        String description() default "";

        /**
         * OAuth flows. At least one flow must be configured.
         *
         * @return OAuth flows
         */
        OAuthFlows flows();

        /**
         * OpenAPI 3.2 OAuth 2 metadata URL.
         * <p>
         * Rendered only for OpenAPI 3.2 output.
         *
         * @return OAuth 2 metadata URL
         */
        String oauth2MetadataUrl() default "";

        /**
         * Whether the security scheme is deprecated.
         * <p>
         * Rendered only for OpenAPI 3.2 output.
         *
         * @return deprecated flag
         */
        boolean deprecated() default false;
    }

    /**
     * OpenAPI OIDC Security Scheme Object metadata.
     * <p>
     * Use only on {@link Document @OpenApi.Document} metadata types.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Repeatable(OidcSecuritySchemes.class)
    @Documented
    public @interface OidcSecurityScheme {
        /**
         * Component name.
         *
         * @return name
         */
        String name();

        /**
         * Security scheme description.
         *
         * @return description
         */
        String description() default "";

        /**
         * OpenID Connect discovery URL.
         *
         * @return OpenID Connect discovery URL
         */
        String openIdConnectUrl();

        /**
         * Whether the security scheme is deprecated.
         * <p>
         * Rendered only for OpenAPI 3.2 output.
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
         * <p>
         * Rendered only for OpenAPI 3.2 output.
         *
         * @return device authorization flow
         */
        OAuthFlow deviceAuthorization() default @OAuthFlow;
    }

    /**
     * OpenAPI OAuth Flow Object metadata.
     * <p>
     * The {@code implicit} flow requires {@link #authorizationUrl()}. The {@code password} and
     * {@code clientCredentials} flows require {@link #tokenUrl()}. The {@code authorizationCode} flow requires
     * {@link #authorizationUrl()} and {@link #tokenUrl()}. The {@code deviceAuthorization} flow requires
     * {@link #deviceAuthorizationUrl()} and {@link #tokenUrl()}.
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
         * <p>
         * Rendered only for OpenAPI 3.2 output.
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
     * Container for repeated {@link ApiKeySecurityScheme}.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface ApiKeySecuritySchemes {
        /**
         * Security schemes.
         *
         * @return security schemes
         */
        ApiKeySecurityScheme[] value();
    }

    /**
     * Container for repeated {@link HttpSecurityScheme}.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface HttpSecuritySchemes {
        /**
         * Security schemes.
         *
         * @return security schemes
         */
        HttpSecurityScheme[] value();
    }

    /**
     * Container for repeated {@link MutualTlsSecurityScheme}.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface MutualTlsSecuritySchemes {
        /**
         * Security schemes.
         *
         * @return security schemes
         */
        MutualTlsSecurityScheme[] value();
    }

    /**
     * Container for repeated {@link OAuth2SecurityScheme}.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface OAuth2SecuritySchemes {
        /**
         * Security schemes.
         *
         * @return security schemes
         */
        OAuth2SecurityScheme[] value();
    }

    /**
     * Container for repeated {@link OidcSecurityScheme}.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface OidcSecuritySchemes {
        /**
         * Security schemes.
         *
         * @return security schemes
         */
        OidcSecurityScheme[] value();
    }

    /**
     * OpenAPI Security Requirement Object scheme entry metadata.
     * <p>
     * Use directly on {@link Document @OpenApi.Document} metadata types, endpoint types, or methods to declare a single
     * security requirement object with one scheme. Use inside {@link SecurityRequirement @OpenApi.SecurityRequirement}
     * to declare multiple schemes required together by one OpenAPI security requirement object.
     * <p>
     * On {@link Document @OpenApi.Document} metadata types, direct type-level usage emits a top-level document security
     * requirement. On endpoint types, direct type-level usage applies to generated operations for the endpoint.
     * Method-level usage replaces inherited endpoint requirements for that operation. Direct usage cannot be combined
     * with {@link SecurityRequirement @OpenApi.SecurityRequirement} or
     * {@link SecurityRequirements @OpenApi.SecurityRequirements} on the same type or method.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface SecuritySchemeRequirement {
        /**
         * Required scheme name.
         *
         * @return scheme name
         */
        String value();

        /**
         * OAuth/OpenID Connect scopes for this scheme.
         *
         * @return scopes
         */
        String[] scopes() default {};
    }

    /**
     * OpenAPI Security Requirement Object metadata.
     * <p>
     * On {@link Document @OpenApi.Document} metadata types, type-level requirements emit top-level document security
     * requirements. On endpoint types, type-level requirements apply to generated operations for the endpoint.
     * Method-level requirements replace inherited endpoint requirements for that operation. Each annotation emits one
     * OpenAPI security requirement object. Multiple
     * {@link SecuritySchemeRequirement @OpenApi.SecuritySchemeRequirement} entries inside one annotation require all
     * listed schemes together. Repeated {@code @OpenApi.SecurityRequirement} annotations declare alternative
     * requirement objects.
     * <p>
     * An empty individual requirement emits an empty OpenAPI security requirement object. To clear inherited endpoint
     * security for an operation, use an empty {@link SecurityRequirements} container instead.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @Repeatable(SecurityRequirements.class)
    @Documented
    public @interface SecurityRequirement {
        /**
         * Required schemes. All schemes are required together.
         * <p>
         * Using an empty value emits an empty OpenAPI security requirement object. Use an empty
         * {@link SecurityRequirements} container to declare an operation with no security requirements.
         *
         * @return scheme requirements
         */
        SecuritySchemeRequirement[] value();
    }

    /**
     * Container for repeated {@link SecurityRequirement}.
     * <p>
     * An empty container on a method clears inherited endpoint security and emits {@code security: []} for the
     * generated operation. An empty container on an endpoint clears endpoint-level security for operations which do not
     * declare method-level security requirements.
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
