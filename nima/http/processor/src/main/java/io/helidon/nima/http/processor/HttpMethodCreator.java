/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.http.processor;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.lang.model.element.ElementKind;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.inject.tools.CustomAnnotationTemplateRequest;
import io.helidon.inject.tools.CustomAnnotationTemplateResponse;
import io.helidon.inject.tools.GenericTemplateCreator;
import io.helidon.inject.tools.GenericTemplateCreatorRequest;
import io.helidon.inject.tools.spi.CustomAnnotationTemplateCreator;

/**
 * Annotation processor that generates a service for each method annotated with an HTTP method annotation.
 * Service provider implementation of a {@link CustomAnnotationTemplateCreator}.
 */
public class HttpMethodCreator extends HttpCreatorBase implements CustomAnnotationTemplateCreator {
    private static final String PATH_ANNOTATION = "io.helidon.common.http.Endpoint.Path";
    private static final TypeName PATH_ANNOTATION_TYPE = TypeName.create(PATH_ANNOTATION);
    private static final String GET_ANNOTATION = "io.helidon.common.http.Endpoint.GET";
    private static final String HTTP_METHOD_ANNOTATION = "io.helidon.common.http.Endpoint.HttpMethod";
    private static final TypeName HTTP_METHOD_ANNOTATION_TYPE = TypeName.create(HTTP_METHOD_ANNOTATION);
    private static final String POST_ANNOTATION = "io.helidon.common.http.Endpoint.POST";
    private static final String PATH_PARAM_ANNOTATION = "io.helidon.common.http.Endpoint.PathParam";
    private static final String HEADER_PARAM_ANNOTATION = "io.helidon.common.http.Endpoint.HeaderParam";
    private static final String QUERY_PARAM_ANNOTATION = "io.helidon.common.http.Endpoint.QueryParam";
    private static final String ENTITY_PARAM_ANNOTATION = "io.helidon.common.http.Endpoint.Entity";
    private static final String QUERY_NO_DEFAULT = "io.helidon.http.Endpoint.QueryParam_NO_DEFAULT_VALUE";

    private static final Set<String> PARAM_ANNOTATIONS = Set.of(
            HEADER_PARAM_ANNOTATION,
            QUERY_PARAM_ANNOTATION,
            PATH_PARAM_ANNOTATION,
            ENTITY_PARAM_ANNOTATION
    );

    /**
     * Default constructor used by the {@link java.util.ServiceLoader}.
     */
    public HttpMethodCreator() {
    }

    @Override
    public Set<String> annoTypes() {
        return Set.of(GET_ANNOTATION,
                      HEADER_PARAM_ANNOTATION,
                      HTTP_METHOD_ANNOTATION,
                      POST_ANNOTATION,
                      PATH_ANNOTATION,
                      QUERY_PARAM_ANNOTATION);
    }

    @Override
    public Optional<CustomAnnotationTemplateResponse> create(CustomAnnotationTemplateRequest request) {
        TypeInfo enclosingType = request.enclosingTypeInfo();

        if (!ElementKind.METHOD.name().equals(request.targetElement().elementTypeKind())) {
            // we are only interested in methods, not in classes
            return Optional.empty();
        }

        String classname = className(request.annoTypeName(),
                                     enclosingType.typeName(),
                                     request.targetElement().elementName());
        TypeName generatedTypeName = TypeName.builder()
                .packageName(enclosingType.typeName().packageName())
                .className(classname)
                .build();

        GenericTemplateCreator genericTemplateCreator = request.genericTemplateCreator();
        GenericTemplateCreatorRequest genericCreatorRequest = GenericTemplateCreatorRequest.builder()
                .customAnnotationTemplateRequest(request)
                .template(Templates.loadTemplate("nima", "http-method.java.hbs"))
                .generatedTypeName(generatedTypeName)
                .overrideProperties(addProperties(request))
                .build();
        return genericTemplateCreator.create(genericCreatorRequest);
    }

    private Map<String, Object> addProperties(CustomAnnotationTemplateRequest request) {
        TypedElementInfo targetElement = request.targetElement();
        Map<String, Object> response = new HashMap<>();

        HttpDef http = new HttpDef();
        /*
           Method response
           http.response.type
           http.response.isVoid
         */
        TypeName returnType = targetElement.typeName();
        if ("void".equals(returnType.className())) {
            http.response = new HttpResponse();
        } else {
            http.response = new HttpResponse(returnType.name());
        }

        // http.methodName - name of the method in source code (not HTTP Method)
        http.methodName = targetElement.elementName();

        // http.params (full string)
        List<HeaderDef> headerList = new LinkedList<>();
        List<TypedElementInfo> elementArgs = request.targetElementArgs();
        LinkedList<String> parameters = new LinkedList<>();
        int headerCount = 1;
        for (TypedElementInfo elementArg : elementArgs) {
            String type = elementArg.typeName().name();

            switch (type) {
            case "io.helidon.nima.webserver.http.ServerRequest" -> parameters.add("req,");
            case "io.helidon.nima.webserver.http.ServerResponse" -> parameters.add("res,");
            default -> processParameter(http, parameters, headerList, type, elementArg);
            }
        }

        if (!parameters.isEmpty()) {
            String last = parameters.removeLast();
            last = last.substring(0, last.length() - 1);
            parameters.addLast(last);
        }
        http.params = parameters;

        /*
            Headers
            http.headers, field, name
         */
        http.headers = headerList;

        /*
            HTTP Method
            http.method
         */
        if (request.annoTypeName().equals(HTTP_METHOD_ANNOTATION_TYPE)) {
            http.method = targetElement.findAnnotation(HTTP_METHOD_ANNOTATION_TYPE)
                    .flatMap(Annotation::value)
                    .orElse(null);
            if (http.method == null) {
                throw new IllegalStateException("HTTP method producer called without HTTP Method annotation (such as @GET)");
            }
        } else {
            http.method = request.annoTypeName().className();
        }

        // HTTP Path (if defined)
        http.path = targetElement.findAnnotation(PATH_ANNOTATION_TYPE)
                .flatMap(Annotation::value)
                .orElse("");

        response.put("http", http);
        return response;
    }

    private void processParameter(HttpDef httpDef,
                                  List<String> parameters,
                                  List<HeaderDef> headerList,
                                  String type,
                                  TypedElementInfo elementArg) {
        // depending on annotations
        List<Annotation> annotations = elementArg.annotations();
        if (annotations.size() == 0) {
            throw new IllegalStateException("Parameters must be annotated with one of @Entity, @PathParam, @HeaderParam, "
                                                    + "@QueryParam - parameter "
                                                    + elementArg.elementName() + " is not annotated at all.");
        }

        Annotation httpAnnotation = null;

        for (Annotation annotation : annotations) {
            if (PARAM_ANNOTATIONS.contains(annotation.typeName().resolvedName())) {
                if (httpAnnotation == null) {
                    httpAnnotation = annotation;
                } else {
                    throw new IllegalStateException("Parameters must be annotated with one of " + PARAM_ANNOTATIONS
                                                            + ", - parameter "
                                                            + elementArg.elementName() + " has more than one annotation.");
                }
            }
        }

        if (httpAnnotation == null) {
            throw new IllegalStateException("Parameters must be annotated with one of " + PARAM_ANNOTATIONS
                                                    + ", - parameter "
                                                    + elementArg.elementName() + " has neither of these.");
        }

        // todo now we only support String for query, path and header -> add conversions
        switch (httpAnnotation.typeName().resolvedName()) {
        case PATH_PARAM_ANNOTATION -> parameters.add("req.path().pathParameters().value(\"" + httpAnnotation.value().orElseThrow()
                                                             + "\"),");
        case (ENTITY_PARAM_ANNOTATION) -> parameters.add("req.content().as(" + type + ".class),");
        case (HEADER_PARAM_ANNOTATION) -> {
            String headerName = "HEADER_" + (headerList.size() + 1);
            headerList.add(new HeaderDef(headerName, httpAnnotation.value().orElseThrow()));
            parameters.add("req.headers().get(" + headerName + ").value(),");
        }
        case (QUERY_PARAM_ANNOTATION) -> {
            httpDef.hasQueryParams = true;
            String defaultValue = httpAnnotation.getValue("defaultValue").orElse(null);
            if (defaultValue == null || QUERY_NO_DEFAULT.equals(defaultValue)) {
                defaultValue = "null";
            } else {
                defaultValue = "\"" + defaultValue + "\"";
            }
            String queryParam = httpAnnotation.value().get();
            // TODO string is hardcoded, we need to add support for mapping
            parameters.add("query(req, res, \"" + queryParam + "\", " + defaultValue + ", String.class),");
        }
        default -> throw new IllegalStateException("Invalid annotation \"" + httpAnnotation.typeName().resolvedName()
                                                           + "\" on HTTP parameter: " + elementArg.elementName());
        }
    }

    /**
     * Needed for template processing.
     * Do not use.
     */
    @Deprecated(since = "1.0.0")
    public static class HttpDef {
        private List<HeaderDef> headers;
        private LinkedList<String> params;
        private HttpResponse response;
        private String methodName;
        private String method;
        private String path;
        private boolean hasQueryParams;

        /**
         * Needed for template processing.
         * Do not use.
         * @return do not use
         */
        @Deprecated(since = "1.0.0")
        public List<HeaderDef> getHeaders() {
            return headers;
        }

        /**
         * Needed for template processing.
         * Do not use.
         * @return do not use
         */
        @Deprecated(since = "1.0.0")
        public LinkedList<String> getParams() {
            return params;
        }

        /**
         * Needed for template processing.
         * Do not use.
         * @return do not use
         */
        @Deprecated(since = "1.0.0")
        public HttpResponse getResponse() {
            return response;
        }

        /**
         * Needed for template processing.
         * Do not use.
         * @return do not use
         */
        @Deprecated(since = "1.0.0")
        public String getMethodName() {
            return methodName;
        }

        /**
         * Needed for template processing.
         * Do not use.
         * @return do not use
         */
        @Deprecated(since = "1.0.0")
        public String getMethod() {
            return method;
        }

        /**
         * Needed for template processing.
         * Do not use.
         * @return do not use
         */
        @Deprecated(since = "1.0.0")
        public String getPath() {
            return path;
        }

        /**
         * Needed for template processing.
         * Do not use.
         * @return do not use
         */
        @Deprecated(since = "1.0.0")
        public boolean isHasQueryParams() {
            return hasQueryParams;
        }
    }

    /**
     * Needed for template processing.
     * Do not use.
     */
    @Deprecated(since = "1.0.0")
    public static class HttpResponse {
        private final String type;
        private final boolean isVoid;

        HttpResponse() {
            this.type = null;
            this.isVoid = true;
        }

        HttpResponse(String type) {
            this.type = type;
            this.isVoid = false;
        }

        /**
         * Needed for template processing.
         * Do not use.
         * @return do not use
         */
        @Deprecated(since = "1.0.0")
        public String getType() {
            return type;
        }

        /**
         * Needed for template processing.
         * Do not use.
         * @return do not use
         */
        @Deprecated(since = "1.0.0")
        public boolean isVoid() {
            return isVoid;
        }
    }

    /**
     * Needed for template processing.
     * Do not use.
     */
    @Deprecated(since = "1.0.0")
    public static class HeaderDef {
        private String field;
        private String name;

        HeaderDef(String field, String name) {
            this.field = field;
            this.name = name;
        }

        /**
         * Needed for template processing.
         * Do not use.
         * @return do not use
         */
        @Deprecated(since = "1.0.0")
        public String getField() {
            return field;
        }

        /**
         * Needed for template processing.
         * Do not use.
         * @return do not use
         */
        @Deprecated(since = "1.0.0")
        public String getName() {
            return name;
        }
    }
}
