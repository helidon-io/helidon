/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.lang.model.element.ElementKind;

import io.helidon.builder.processor.spi.TypeInfo;
import io.helidon.builder.types.AnnotationAndValue;
import io.helidon.builder.types.DefaultTypeName;
import io.helidon.builder.types.TypeName;
import io.helidon.builder.types.TypedElementName;
import io.helidon.common.http.GET;
import io.helidon.common.http.HeaderParam;
import io.helidon.common.http.HttpMethod;
import io.helidon.common.http.POST;
import io.helidon.common.http.Path;
import io.helidon.common.http.QueryParam;
import io.helidon.pico.tools.CustomAnnotationTemplateCreator;
import io.helidon.pico.tools.CustomAnnotationTemplateRequest;
import io.helidon.pico.tools.CustomAnnotationTemplateResponse;
import io.helidon.pico.tools.TemplateHelperTools;

/**
 * Annotation processor that generates a service for each method annotated with an HTTP method annotation.
 * Service provider implementation of a {@link io.helidon.pico.tools.CustomAnnotationTemplateCreator}.
 */
public class HttpMethodProducer implements CustomAnnotationTemplateCreator {
    /**
     * Default constructor used by the {@link java.util.ServiceLoader}.
     */
    public HttpMethodProducer() {
    }

    @Override
    public Set<Class<? extends Annotation>> annoTypes() {
        return Set.of(GET.class, POST.class, HttpMethod.class);
    }

    @Override
    public Optional<CustomAnnotationTemplateResponse> create(CustomAnnotationTemplateRequest request) {
        TypeInfo enclosingType = request.enclosingTypeInfo();

        if (!ElementKind.METHOD.name().equals(enclosingType.typeKind())) {
            // we are only interested in methods, not in classes
            return Optional.empty();
        }

        String classname = enclosingType.typeName().className() + "_"
                + request.annoTypeName().className() + "_"
                + request.targetElement().elementName();
        TypeName generatedType = DefaultTypeName.create(enclosingType.typeName().packageName(), classname);

        TemplateHelperTools tools = request.templateHelperTools();
        return tools.produceStandardCodeGenResponse(request,
                                                    generatedType,
                                                    () -> Templates.loadTemplate("nima", "http-method.java.hbs"),
                                                    it -> addProperties(request, it));
    }

    String loadTemplate(String templateProfile, String name) {
        String path = "templates/pico/" + templateProfile + "/" + name;
        try {
            InputStream in = getClass().getClassLoader().getResourceAsStream(path);
            if (in == null) {
                return null;
            }
            try (in) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> addProperties(CustomAnnotationTemplateRequest request,
                                              Map<String, Object> currentProperties) {
        TypedElementName targetElement = request.targetElement();
        Map<String, Object> response = new HashMap<>(currentProperties);

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
        List<TypedElementName> elementArgs = request.targetElementArgs();
        LinkedList<String> parameters = new LinkedList<>();
        int headerCount = 1;
        for (TypedElementName elementArg : elementArgs) {
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
        if (request.annoTypeName().className().equals(HttpMethod.class)) {
            http.method = findAnnotValue(targetElement.annotations(), HttpMethod.class.getName(), null);
            if (http.method == null) {
                throw new IllegalStateException("HTTP method producer called without HTTP Method annotation (such as @GET)");
            }
        } else {
            http.method = request.annoTypeName().className();
        }

        // HTTP Path (if defined)
        http.path = findAnnotValue(targetElement.annotations(), Path.class.getName(), "");

        response.put("http", http);
        return response;
    }

    private void processParameter(HttpDef httpDef,
                                  LinkedList<String> parameters,
                                  List<HeaderDef> headerList,
                                  String type,
                                  TypedElementName elementArg) {
        // depending on annotations
        List<AnnotationAndValue> annotations = elementArg.annotations();
        if (annotations.size() == 0) {
            throw new IllegalStateException("Parameters must be annotated with one of @Entity, @PathParam, @HeaderParam, "
                                                    + "@QueryParam - parameter "
                                                    + elementArg.elementName() + " is not annotated at all.");
        }

        AnnotationAndValue httpAnnotation = null;

        for (AnnotationAndValue annotation : annotations) {
            if (annotation.typeName().packageName().equals(HeaderParam.class.getPackageName())) {
                if (httpAnnotation == null) {
                    httpAnnotation = annotation;
                } else {
                    throw new IllegalStateException("Parameters must be annotated with one of @Entity, @PathParam, @HeaderParam, "
                                                            + "@QueryParam - parameter "
                                                            + elementArg.elementName() + " has more than one annotation.");
                }
            }
        }

        if (httpAnnotation == null) {
            throw new IllegalStateException("Parameters must be annotated with one of @Entity, @PathParam, @HeaderParam, "
                                                    + "@QueryParam - parameter "
                                                    + elementArg.elementName() + " has neither of these.");
        }

        // todo now we only support String for query, path and header -> add conversions
        switch (httpAnnotation.typeName().className()) {
        case ("PathParam") -> parameters.add("req.path().templateParameters().first(\"" + httpAnnotation.value().orElseThrow()
                                                     + "\"),");
        case ("Entity") -> parameters.add("req.content().as(" + type + ".class),");
        case ("HeaderParam") -> {
            String headerName = "HEADER_" + (headerList.size() + 1);
            headerList.add(new HeaderDef(headerName, httpAnnotation.value().orElseThrow()));
            parameters.add("req.headers().get(" + headerName + ").value(),");
        }
        case ("QueryParam") -> {
            httpDef.hasQueryParams = true;
            String defaultValue = httpAnnotation.value("defaultValue").orElse(null);
            if (defaultValue == null || QueryParam.NO_DEFAULT_VALUE.equals(defaultValue)) {
                defaultValue = "null";
            } else {
                defaultValue = "\"" + defaultValue + "\"";
            }
            String queryParam = httpAnnotation.value().get();
            // TODO string is hardcoded, we need to add support for mapping
            parameters.add("query(req, res, \"" + queryParam + "\", " + defaultValue + ", String.class),");
        }
        default -> throw new IllegalStateException("Invalid annotation on HTTP parameter: " + elementArg.elementName());
        }
    }

    private String findAnnotValue(List<AnnotationAndValue> elementAnnotations, String name, String defaultValue) {
        for (AnnotationAndValue elementAnnotation : elementAnnotations) {
            if (name.equals(elementAnnotation.typeName().name())) {
                return elementAnnotation.value().orElseThrow();
            }
        }
        return defaultValue;
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
