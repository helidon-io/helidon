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

import java.util.List;
import java.util.Locale;
import java.util.Map;

final class OpenApiMediaTypeValidator {
    private final OpenApiDialect dialect;
    private final OpenApiReferenceResolver resolver;

    private OpenApiMediaTypeValidator(Map<String, Object> document, OpenApiDialect dialect) {
        this.dialect = dialect;
        this.resolver = OpenApiReferenceResolver.create(document);
    }

    static void validate(Map<String, Object> document, OpenApiDialect dialect) {
        OpenApiMediaTypeValidator validator = new OpenApiMediaTypeValidator(document, dialect);
        OpenApiDocumentWalker.walk(document, dialect, validator::validateNode);
    }

    private boolean validateNode(OpenApiDocumentWalker.Node node) {
        if (node.kind() == OpenApiDocumentWalker.Kind.ENCODING) {
            return validateEncoding(node.location(), node.value());
        }
        if (node.kind() == OpenApiDocumentWalker.Kind.MEDIA_TYPE) {
            OpenApiReferenceResolver.Resolution resolution = resolver.resolveComponent(node.value(), "mediaTypes");
            Map<String, Object> mediaType = resolution.status() == OpenApiReferenceResolver.Status.RESOLVED
                    ? resolution.value()
                    : Map.of();
            return validateMediaType(node.location(), node.name(), mediaType);
        }
        return true;
    }

    private boolean validateMediaType(String location,
                                      String mediaType,
                                      Map<String, Object> mediaTypeObject) {
        if (mediaTypeObject.containsKey("$ref")) {
            return false;
        }
        validateEncodingFields(location, "media type", mediaTypeObject);
        boolean hasPrefixEncoding = mediaTypeObject.containsKey("prefixEncoding");
        boolean hasItemEncoding = mediaTypeObject.containsKey("itemEncoding");
        boolean hasPositionalEncoding = hasPrefixEncoding || hasItemEncoding;
        if (hasPositionalEncoding) {
            if (mediaType != null && !isMultipart(mediaType)) {
                return false;
            }
            if (!isSchema(mediaTypeObject.get("itemSchema"))
                    && !isArraySchema(mediaTypeObject.get("schema"))) {
                throw new IllegalStateException("OpenAPI " + dialect.version() + " media type at " + location
                                                        + " requires itemSchema or an array schema for positional encoding");
            }
        }
        return true;
    }

    private boolean validateEncoding(String location, Map<String, Object> encodingObject) {
        if (encodingObject.containsKey("$ref")) {
            return false;
        }
        validateEncodingFields(location, "encoding", encodingObject);
        return true;
    }

    private void validateEncodingFields(String location, String objectType, Map<String, Object> object) {
        boolean hasEncoding = object.containsKey("encoding");
        boolean hasPrefixEncoding = object.containsKey("prefixEncoding");
        boolean hasItemEncoding = object.containsKey("itemEncoding");
        boolean hasPositionalEncoding = hasPrefixEncoding || hasItemEncoding;
        if (hasEncoding && hasPositionalEncoding) {
            throw new IllegalStateException("OpenAPI " + dialect.version() + " " + objectType + " at " + location
                                                    + " cannot combine encoding with prefixEncoding or itemEncoding");
        }
    }

    private boolean isArraySchema(Object value) {
        Map<String, Object> schema = object(value);
        if (schema.isEmpty()) {
            return false;
        }
        Object type = schema.get("type");
        if (type instanceof String string) {
            return "array".equals(string);
        }
        if (type instanceof List<?> list) {
            return list.contains("array");
        }
        if (!(schema.get("$ref") instanceof String)) {
            return true;
        }
        OpenApiReferenceResolver.Resolution resolution = resolver.resolveComponent(schema, "schemas");
        return resolution.status() != OpenApiReferenceResolver.Status.RESOLVED
                || isArraySchema(resolution.value());
    }

    private static boolean isMultipart(String mediaType) {
        int parameterStart = mediaType.indexOf(';');
        String type = parameterStart < 0 ? mediaType : mediaType.substring(0, parameterStart);
        return type.trim().toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    private static boolean isSchema(Object value) {
        return value instanceof Map<?, ?> || value instanceof Boolean;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }
}
