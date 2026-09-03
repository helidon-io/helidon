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
import java.util.Map;
import java.util.Set;

final class OpenApiDocumentWalker {
    private OpenApiDocumentWalker() {
    }

    static void walk(Map<String, Object> document,
                     OpenApiDialect dialect,
                     Visitor visitor) {
        Node root = new Node(Kind.DOCUMENT, document, "", null, null);
        if (!visit(root, visitor)) {
            return;
        }
        Set<String> documentFields = dialect.fields(Kind.DOCUMENT);
        if (documentFields.contains("info") && document.containsKey("info")) {
            walkInfo("info", document.get("info"), root, dialect, visitor);
        }
        if (documentFields.contains("servers")) {
            walkServers("servers", list(document.get("servers")), root, dialect, visitor);
        }
        if (documentFields.contains("paths")) {
            walkPathItems("paths", object(document.get("paths")), true, root, dialect, visitor);
        }
        if (documentFields.contains("webhooks")) {
            walkPathItems("webhooks", object(document.get("webhooks")), false, root, dialect, visitor);
        }
        if (documentFields.contains("tags")) {
            walkTags("tags", list(document.get("tags")), root, dialect, visitor);
        }
        if (documentFields.contains("externalDocs") && document.containsKey("externalDocs")) {
            walkExternalDocs("externalDocs", document.get("externalDocs"), root, dialect, visitor);
        }
        if (documentFields.contains("components") && document.containsKey("components")) {
            walkComponents("components", document.get("components"), root, dialect, visitor);
        }
    }

    private static void walkComponents(String location,
                                       Object value,
                                       Node parent,
                                       OpenApiDialect dialect,
                                       Visitor visitor) {
        Node componentNode = new Node(Kind.COMPONENTS, value, location, "components", parent);
        if (!visit(componentNode, visitor)) {
            return;
        }
        Map<String, Object> components = componentNode.value();
        Set<String> componentFields = dialect.fields(Kind.COMPONENTS);
        if (componentFields.contains("pathItems")) {
            walkPathItems(location + ".pathItems",
                          object(components.get("pathItems")),
                          false,
                          componentNode,
                          dialect,
                          visitor);
        }
        if (componentFields.contains("callbacks")) {
            walkCallbacks(location + ".callbacks",
                          object(components.get("callbacks")),
                          componentNode,
                          dialect,
                          visitor);
        }
        if (componentFields.contains("parameters")) {
            walkParameterMap(location + ".parameters",
                             object(components.get("parameters")),
                             componentNode,
                             dialect,
                             visitor);
        }
        if (componentFields.contains("headers")) {
            walkHeaders(location + ".headers",
                        object(components.get("headers")),
                        componentNode,
                        dialect,
                        visitor);
        }
        if (componentFields.contains("requestBodies")) {
            walkRequestBodies(location + ".requestBodies",
                              object(components.get("requestBodies")),
                              componentNode,
                              dialect,
                              visitor);
        }
        if (componentFields.contains("responses")) {
            walkResponses(location + ".responses",
                          object(components.get("responses")),
                          false,
                          componentNode,
                          dialect,
                          visitor);
        }
        if (componentFields.contains("mediaTypes")) {
            walkMediaTypes(location + ".mediaTypes",
                           object(components.get("mediaTypes")),
                           componentNode,
                           dialect,
                           visitor);
        }
        if (componentFields.contains("examples")) {
            walkExamples(location + ".examples",
                         object(components.get("examples")),
                         componentNode,
                         visitor);
        }
        if (componentFields.contains("links")) {
            walkLinks(location + ".links",
                      object(components.get("links")),
                      componentNode,
                      visitor);
        }
        if (componentFields.contains("securitySchemes")) {
            walkSecuritySchemes(location + ".securitySchemes",
                                object(components.get("securitySchemes")),
                                componentNode,
                                dialect,
                                visitor);
        }
    }

    private static void walkInfo(String location,
                                 Object value,
                                 Node parent,
                                 OpenApiDialect dialect,
                                 Visitor visitor) {
        Node infoNode = new Node(Kind.INFO, value, location, "info", parent);
        if (!visit(infoNode, visitor)) {
            return;
        }
        Map<String, Object> info = infoNode.value();
        if (info.containsKey("contact")) {
            walkSimpleObject(location + ".contact",
                             "contact",
                             info.get("contact"),
                             Kind.CONTACT,
                             infoNode,
                             visitor);
        }
        if (info.containsKey("license")) {
            walkSimpleObject(location + ".license",
                             "license",
                             info.get("license"),
                             Kind.LICENSE,
                             infoNode,
                             visitor);
        }
    }

    private static void walkServers(String location,
                                    Iterable<?> servers,
                                    Node parent,
                                    OpenApiDialect dialect,
                                    Visitor visitor) {
        int index = 0;
        for (Object value : servers) {
            walkServer(location + "[" + index + "]", String.valueOf(index), value, parent, visitor);
            index++;
        }
    }

    private static void walkServer(String location,
                                   String name,
                                   Object value,
                                   Node parent,
                                   Visitor visitor) {
        Node serverNode = new Node(Kind.SERVER, value, location, name, parent);
        if (!visit(serverNode, visitor)) {
            return;
        }
        object(serverNode.value().get("variables")).forEach((variableName, variable) -> walkSimpleObject(
                serverNode.location() + ".variables." + variableName,
                variableName,
                variable,
                Kind.SERVER_VARIABLE,
                serverNode,
                visitor));
    }

    private static void walkTags(String location,
                                 Iterable<?> tags,
                                 Node parent,
                                 OpenApiDialect dialect,
                                 Visitor visitor) {
        int index = 0;
        for (Object value : tags) {
            Node tagNode = new Node(Kind.TAG,
                                    value,
                                    location + "[" + index + "]",
                                    String.valueOf(index),
                                    parent);
            if (visit(tagNode, visitor) && tagNode.value().containsKey("externalDocs")) {
                walkExternalDocs(tagNode.location() + ".externalDocs",
                                 tagNode.value().get("externalDocs"),
                                 tagNode,
                                 dialect,
                                 visitor);
            }
            index++;
        }
    }

    private static void walkExternalDocs(String location,
                                         Object externalDocs,
                                         Node parent,
                                         OpenApiDialect dialect,
                                         Visitor visitor) {
        walkSimpleObject(location,
                         "externalDocs",
                         externalDocs,
                         Kind.EXTERNAL_DOCS,
                         parent,
                         visitor);
    }

    private static void walkSecuritySchemes(String location,
                                            Map<String, Object> securitySchemes,
                                            Node parent,
                                            OpenApiDialect dialect,
                                            Visitor visitor) {
        securitySchemes.forEach((name, value) -> walkSecurityScheme(location + "." + name,
                                                                    name,
                                                                    value,
                                                                    parent,
                                                                    dialect,
                                                                    visitor));
    }

    private static void walkSecurityScheme(String location,
                                           String name,
                                           Object value,
                                           Node parent,
                                           OpenApiDialect dialect,
                                           Visitor visitor) {
        Node securitySchemeNode = new Node(Kind.SECURITY_SCHEME, value, location, name, parent);
        if (!visit(securitySchemeNode, visitor)) {
            return;
        }
        Map<String, Object> securityScheme = securitySchemeNode.value();
        if (securityScheme.containsKey("$ref") || !securityScheme.containsKey("flows")) {
            return;
        }
        Node flowsNode = new Node(Kind.OAUTH_FLOWS,
                                  securityScheme.get("flows"),
                                  location + ".flows",
                                  "flows",
                                  securitySchemeNode);
        if (!visit(flowsNode, visitor)) {
            return;
        }
        Map<String, Object> flows = flowsNode.value();
        for (String flowName : dialect.oauthFlowFields()) {
            if (!flows.containsKey(flowName)) {
                continue;
            }
            walkSimpleObject(flowsNode.location() + "." + flowName,
                             flowName,
                             flows.get(flowName),
                             Kind.OAUTH_FLOW,
                             flowsNode,
                             visitor);
        }
    }

    private static void walkSimpleObject(String location,
                                         String name,
                                         Object value,
                                         Kind kind,
                                         Node parent,
                                         Visitor visitor) {
        visitor.visit(new Node(kind, value, location, name, parent));
    }

    private static void walkExamples(String location,
                                     Map<String, Object> examples,
                                     Node parent,
                                     Visitor visitor) {
        examples.forEach((name, value) -> visitor.visit(
                new Node(Kind.EXAMPLE, value, location + "." + name, name, parent)));
    }

    private static void walkLinks(String location,
                                  Map<String, Object> links,
                                  Node parent,
                                  Visitor visitor) {
        links.forEach((name, value) -> {
            Node linkNode = new Node(Kind.LINK, value, location + "." + name, name, parent);
            if (!visit(linkNode, visitor) || linkNode.value().containsKey("$ref")) {
                return;
            }
            if (linkNode.value().containsKey("server")) {
                walkServer(linkNode.location() + ".server",
                           "server",
                           linkNode.value().get("server"),
                           linkNode,
                           visitor);
            }
        });
    }

    private static void walkPathItems(String location,
                                      Map<String, Object> pathItems,
                                      boolean skipExtensions,
                                      Node parent,
                                      OpenApiDialect dialect,
                                      Visitor visitor) {
        pathItems.forEach((name, value) -> {
            if (!skipExtensions || !name.startsWith("x-")) {
                walkPathItem(location + "." + name, name, value, parent, dialect, visitor);
            }
        });
    }

    private static void walkPathItem(String location,
                                     String name,
                                     Object value,
                                     Node parent,
                                     OpenApiDialect dialect,
                                     Visitor visitor) {
        Node pathItemNode = new Node(Kind.PATH_ITEM, value, location, name, parent);
        if (!visit(pathItemNode, visitor)) {
            return;
        }
        Map<String, Object> pathItem = pathItemNode.value();
        walkParameters(location + ".parameters",
                       list(pathItem.get("parameters")),
                       pathItemNode,
                       dialect,
                       visitor);
        walkServers(location + ".servers",
                    list(pathItem.get("servers")),
                    pathItemNode,
                    dialect,
                    visitor);
        for (String method : dialect.fixedPathOperationFields()) {
            if (pathItem.containsKey(method)) {
                walkOperation(location + "." + method,
                              method,
                              pathItem.get(method),
                              pathItemNode,
                              dialect,
                              visitor);
            }
        }
        if (dialect.fields(Kind.PATH_ITEM).contains("additionalOperations")) {
            object(pathItem.get("additionalOperations")).forEach((method, operation) -> walkOperation(
                    location + ".additionalOperations." + method,
                    method,
                    operation,
                    pathItemNode,
                    dialect,
                    visitor));
        }
    }

    private static void walkOperation(String location,
                                      String name,
                                      Object value,
                                      Node parent,
                                      OpenApiDialect dialect,
                                      Visitor visitor) {
        Node operationNode = new Node(Kind.OPERATION, value, location, name, parent);
        if (!visit(operationNode, visitor)) {
            return;
        }
        Map<String, Object> operation = operationNode.value();
        walkParameters(location + ".parameters",
                       list(operation.get("parameters")),
                       operationNode,
                       dialect,
                       visitor);
        walkServers(location + ".servers",
                    list(operation.get("servers")),
                    operationNode,
                    dialect,
                    visitor);
        if (operation.containsKey("requestBody")) {
            walkRequestBody(location + ".requestBody",
                            "requestBody",
                            operation.get("requestBody"),
                            operationNode,
                            dialect,
                            visitor);
        }
        walkResponses(location + ".responses",
                      object(operation.get("responses")),
                      true,
                      operationNode,
                      dialect,
                      visitor);
        if (operation.containsKey("externalDocs")) {
            walkExternalDocs(location + ".externalDocs",
                             operation.get("externalDocs"),
                             operationNode,
                             dialect,
                             visitor);
        }
        walkCallbacks(location + ".callbacks",
                      object(operation.get("callbacks")),
                      operationNode,
                      dialect,
                      visitor);
    }

    private static void walkCallbacks(String location,
                                      Map<String, Object> callbacks,
                                      Node parent,
                                      OpenApiDialect dialect,
                                      Visitor visitor) {
        callbacks.forEach((name, value) -> {
            Node callbackNode = new Node(Kind.CALLBACK, value, location + "." + name, name, parent);
            if (!visit(callbackNode, visitor)) {
                return;
            }
            Map<String, Object> callback = callbackNode.value();
            if (callback.containsKey("$ref")) {
                return;
            }
            callback.forEach((expression, pathItem) -> {
                if (!expression.startsWith("x-")) {
                    walkPathItem(callbackNode.location() + "." + expression,
                                 expression,
                                 pathItem,
                                 callbackNode,
                                 dialect,
                                 visitor);
                }
            });
        });
    }

    private static void walkParameters(String location,
                                       Iterable<?> parameters,
                                       Node parent,
                                       OpenApiDialect dialect,
                                       Visitor visitor) {
        int index = 0;
        for (Object value : parameters) {
            walkParameter(location + "[" + index + "]",
                          String.valueOf(index),
                          value,
                          parent,
                          dialect,
                          visitor);
            index++;
        }
    }

    private static void walkParameterMap(String location,
                                         Map<String, Object> parameters,
                                         Node parent,
                                         OpenApiDialect dialect,
                                         Visitor visitor) {
        parameters.forEach((name, value) -> walkParameter(location + "." + name,
                                                          name,
                                                          value,
                                                          parent,
                                                          dialect,
                                                          visitor));
    }

    private static void walkParameter(String location,
                                      String name,
                                      Object value,
                                      Node parent,
                                      OpenApiDialect dialect,
                                      Visitor visitor) {
        Node parameterNode = new Node(Kind.PARAMETER, value, location, name, parent);
        if (!visit(parameterNode, visitor)) {
            return;
        }
        Map<String, Object> parameter = parameterNode.value();
        if (!parameter.containsKey("$ref")) {
            walkContent(location + ".content",
                        object(parameter.get("content")),
                        parameterNode,
                        dialect,
                        visitor);
            walkExamples(location + ".examples",
                         object(parameter.get("examples")),
                         parameterNode,
                         visitor);
        }
    }

    private static void walkHeaders(String location,
                                    Map<String, Object> headers,
                                    Node parent,
                                    OpenApiDialect dialect,
                                    Visitor visitor) {
        headers.forEach((name, value) -> walkHeader(location + "." + name,
                                                    name,
                                                    value,
                                                    parent,
                                                    dialect,
                                                    visitor));
    }

    private static void walkHeader(String location,
                                   String name,
                                   Object value,
                                   Node parent,
                                   OpenApiDialect dialect,
                                   Visitor visitor) {
        Node headerNode = new Node(Kind.HEADER, value, location, name, parent);
        if (!visit(headerNode, visitor)) {
            return;
        }
        Map<String, Object> header = headerNode.value();
        if (!header.containsKey("$ref")) {
            walkContent(location + ".content",
                        object(header.get("content")),
                        headerNode,
                        dialect,
                        visitor);
            walkExamples(location + ".examples",
                         object(header.get("examples")),
                         headerNode,
                         visitor);
        }
    }

    private static void walkRequestBodies(String location,
                                          Map<String, Object> requestBodies,
                                          Node parent,
                                          OpenApiDialect dialect,
                                          Visitor visitor) {
        requestBodies.forEach((name, value) -> walkRequestBody(location + "." + name,
                                                               name,
                                                               value,
                                                               parent,
                                                               dialect,
                                                               visitor));
    }

    private static void walkRequestBody(String location,
                                        String name,
                                        Object value,
                                        Node parent,
                                        OpenApiDialect dialect,
                                        Visitor visitor) {
        Node requestBodyNode = new Node(Kind.REQUEST_BODY, value, location, name, parent);
        if (!visit(requestBodyNode, visitor)) {
            return;
        }
        Map<String, Object> requestBody = requestBodyNode.value();
        if (!requestBody.containsKey("$ref")) {
            walkContent(location + ".content",
                        object(requestBody.get("content")),
                        requestBodyNode,
                        dialect,
                        visitor);
        }
    }

    private static void walkResponses(String location,
                                      Map<String, Object> responses,
                                      boolean skipExtensions,
                                      Node parent,
                                      OpenApiDialect dialect,
                                      Visitor visitor) {
        responses.forEach((name, value) -> {
            if (!skipExtensions || !name.startsWith("x-")) {
                walkResponse(location + "." + name,
                             name,
                             value,
                             parent,
                             dialect,
                             visitor);
            }
        });
    }

    private static void walkResponse(String location,
                                     String name,
                                     Object value,
                                     Node parent,
                                     OpenApiDialect dialect,
                                     Visitor visitor) {
        Node responseNode = new Node(Kind.RESPONSE, value, location, name, parent);
        if (!visit(responseNode, visitor)) {
            return;
        }
        Map<String, Object> response = responseNode.value();
        if (response.containsKey("$ref")) {
            return;
        }
        walkContent(location + ".content",
                    object(response.get("content")),
                    responseNode,
                    dialect,
                    visitor);
        walkHeaders(location + ".headers",
                    object(response.get("headers")),
                    responseNode,
                    dialect,
                    visitor);
        walkLinks(location + ".links",
                  object(response.get("links")),
                  responseNode,
                  visitor);
    }

    private static void walkContent(String location,
                                    Map<String, Object> content,
                                    Node parent,
                                    OpenApiDialect dialect,
                                    Visitor visitor) {
        content.forEach((name, value) -> walkMediaType(location + "." + name,
                                                       name,
                                                       value,
                                                       parent,
                                                       dialect,
                                                       visitor));
    }

    private static void walkMediaTypes(String location,
                                       Map<String, Object> mediaTypes,
                                       Node parent,
                                       OpenApiDialect dialect,
                                       Visitor visitor) {
        mediaTypes.forEach((name, value) -> walkMediaType(location + "." + name,
                                                         null,
                                                         value,
                                                         parent,
                                                         dialect,
                                                         visitor));
    }

    private static void walkMediaType(String location,
                                      String name,
                                      Object value,
                                      Node parent,
                                      OpenApiDialect dialect,
                                      Visitor visitor) {
        Node mediaTypeNode = new Node(Kind.MEDIA_TYPE, value, location, name, parent);
        if (!visit(mediaTypeNode, visitor)) {
            return;
        }
        Map<String, Object> mediaType = mediaTypeNode.value();
        if (mediaType.containsKey("$ref")) {
            return;
        }
        walkExamples(location + ".examples",
                     object(mediaType.get("examples")),
                     mediaTypeNode,
                     visitor);
        walkEncodingMap(location + ".encoding",
                        object(mediaType.get("encoding")),
                        mediaTypeNode,
                        dialect,
                        visitor);
        if (dialect.fields(Kind.MEDIA_TYPE).contains("prefixEncoding")) {
            walkEncodings(location + ".prefixEncoding",
                          list(mediaType.get("prefixEncoding")),
                          mediaTypeNode,
                          dialect,
                          visitor);
        }
        if (dialect.fields(Kind.MEDIA_TYPE).contains("itemEncoding") && mediaType.containsKey("itemEncoding")) {
            walkEncoding(location + ".itemEncoding",
                         "itemEncoding",
                         mediaType.get("itemEncoding"),
                         mediaTypeNode,
                         dialect,
                         visitor);
        }
    }

    private static void walkEncodingMap(String location,
                                        Map<String, Object> encodings,
                                        Node parent,
                                        OpenApiDialect dialect,
                                        Visitor visitor) {
        encodings.forEach((name, value) -> walkEncoding(location + "." + name,
                                                        name,
                                                        value,
                                                        parent,
                                                        dialect,
                                                        visitor));
    }

    private static void walkEncodings(String location,
                                      Iterable<?> encodings,
                                      Node parent,
                                      OpenApiDialect dialect,
                                      Visitor visitor) {
        int index = 0;
        for (Object value : encodings) {
            walkEncoding(location + "[" + index + "]",
                         String.valueOf(index),
                         value,
                         parent,
                         dialect,
                         visitor);
            index++;
        }
    }

    private static void walkEncoding(String location,
                                     String name,
                                     Object value,
                                     Node parent,
                                     OpenApiDialect dialect,
                                     Visitor visitor) {
        Node encodingNode = new Node(Kind.ENCODING, value, location, name, parent);
        if (!visit(encodingNode, visitor)) {
            return;
        }
        Map<String, Object> encoding = encodingNode.value();
        if (encoding.isEmpty()) {
            return;
        }
        walkHeaders(location + ".headers",
                    object(encoding.get("headers")),
                    encodingNode,
                    dialect,
                    visitor);
        if (dialect.fields(Kind.ENCODING).contains("encoding")) {
            walkEncodingMap(location + ".encoding",
                            object(encoding.get("encoding")),
                            encodingNode,
                            dialect,
                            visitor);
        }
        if (dialect.fields(Kind.ENCODING).contains("prefixEncoding")) {
            walkEncodings(location + ".prefixEncoding",
                          list(encoding.get("prefixEncoding")),
                          encodingNode,
                          dialect,
                          visitor);
        }
        if (dialect.fields(Kind.ENCODING).contains("itemEncoding") && encoding.containsKey("itemEncoding")) {
            walkEncoding(location + ".itemEncoding",
                         "itemEncoding",
                         encoding.get("itemEncoding"),
                         encodingNode,
                         dialect,
                         visitor);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> result ? result : List.of();
    }

    private static boolean visit(Node node, Visitor visitor) {
        return visitor.visit(node) && node.hasObjectValue();
    }

    enum Kind {
        DOCUMENT,
        INFO,
        CONTACT,
        LICENSE,
        EXTERNAL_DOCS,
        SERVER,
        SERVER_VARIABLE,
        TAG,
        COMPONENTS,
        PATH_ITEM,
        OPERATION,
        CALLBACK,
        PARAMETER,
        HEADER,
        REQUEST_BODY,
        RESPONSE,
        MEDIA_TYPE,
        ENCODING,
        SECURITY_SCHEME,
        OAUTH_FLOWS,
        OAUTH_FLOW,
        EXAMPLE,
        LINK
    }

    record Node(Kind kind, Object rawValue, String location, String name, Node parent) {
        boolean hasObjectValue() {
            return rawValue instanceof Map<?, ?>;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> value() {
            return hasObjectValue() ? (Map<String, Object>) rawValue : Map.of();
        }
    }

    interface Visitor {
        boolean visit(Node node);
    }
}
