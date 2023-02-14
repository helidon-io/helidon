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
import java.util.Set;

import io.helidon.nima.http.api.GET;
import io.helidon.nima.http.api.HeaderParam;
import io.helidon.nima.http.api.HttpMethod;
import io.helidon.nima.http.api.POST;
import io.helidon.nima.http.api.Path;
import io.helidon.nima.http.api.QueryParam;
import io.helidon.pico.processor.spi.CustomAnnotationTemplateProducer;
import io.helidon.pico.processor.spi.CustomAnnotationTemplateProducerRequest;
import io.helidon.pico.processor.spi.CustomAnnotationTemplateProducerResponse;
import io.helidon.pico.processor.spi.TemplateHelperTools;
import io.helidon.pico.tools.types.AnnotationAndValue;
import io.helidon.pico.tools.types.TypeName;
import io.helidon.pico.tools.types.TypedElementName;

/**
 * Annotation processor that generates a service for each method annotated with an HTTP method annotation.
 * Service provider implementation of a {@link io.helidon.pico.processor.spi.CustomAnnotationTemplateProducer}.
 */
public class HttpMethodProducer implements CustomAnnotationTemplateProducer {
    /**
     * Default constructor used by the {@link java.util.ServiceLoader}.
     */
    public HttpMethodProducer() {
    }

    @Override
    public Set<Class<? extends Annotation>> getAnnoTypes() {
        return Set.of(GET.class, POST.class, HttpMethod.class);
    }

    @Override
    public CustomAnnotationTemplateProducerResponse produce(CustomAnnotationTemplateProducerRequest request,
                                                            TemplateHelperTools tools) {

        String classname = request.getEnclosingClassType().getClassName() + "_"
                + request.getAnnoType().getClassName() + "_"
                + request.getElementName();
        TypeName generatedType = TypeName.create(request.getEnclosingClassType().getPackageName(), classname);
        return tools.produceStandardCodeGenResponse(request,
                                                    generatedType,
                                                    tools.supplyUsingLiteralTemplate(
                                                            loadTemplate("nima", "http-method.java.hbs")),
                                                    it -> addProperties(request, tools, it), null);
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

    private Map<String, Object> addProperties(CustomAnnotationTemplateProducerRequest request,
                                              TemplateHelperTools tools,
                                              Map<String, Object> currentProperties) {
        Map<String, Object> response = new HashMap<>(currentProperties);

        HttpDef http = new HttpDef();
        /*
           Method response
           http.response.type
           http.response.isVoid
         */
        TypeName returnType = request.getReturnType();
        if ("void".equals(returnType.getClassName())) {
            http.response = new HttpResponse();
        } else {
            http.response = new HttpResponse(returnType.getName());
        }

        // http.methodName - name of the method in source code (not HTTP Method)
        http.methodName = request.getElementName();

        // http.params (full string)
        List<HeaderDef> headerList = new LinkedList<>();
        List<TypedElementName> elementArgs = request.getElementArgs();
        LinkedList<String> parameters = new LinkedList<>();
        int headerCount = 1;
        for (TypedElementName elementArg : elementArgs) {
            String type = elementArg.getTypeName().getName();

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
        if (request.getElementName().equals(HttpMethod.class.getSimpleName())) {
            http.method = findAnnotValue(request.getElementAnnotations(), HttpMethod.class.getName(), null);
            if (http.method == null) {
                throw new IllegalStateException("HTTP method producer called without HTTP Method annotation (such as @GET)");
            }
        } else {
            http.method = request.getAnnoType().getClassName();
        }

        // HTTP Path (if defined)
        http.path = findAnnotValue(request.getElementAnnotations(), Path.class.getName(), "");

        response.put("http", http);
        return response;
    }

    private void processParameter(HttpDef httpDef,
                                  LinkedList<String> parameters,
                                  List<HeaderDef> headerList,
                                  String type,
                                  TypedElementName elementArg) {
        // depending on annotations
        List<AnnotationAndValue> annotations = elementArg.getAnnotations();
        if (annotations.size() == 0) {
            throw new IllegalStateException("Parameters must be annotated with one of @Entity, @PathParam, @HeaderParam, "
                                                    + "@QueryParam - parameter "
                                                    + elementArg.getElementName() + " is not annotated at all.");
        }

        AnnotationAndValue httpAnnotation = null;

        for (AnnotationAndValue annotation : annotations) {
            if (annotation.getTypeName().getPackageName().equals(HeaderParam.class.getPackageName())) {
                if (httpAnnotation == null) {
                    httpAnnotation = annotation;
                } else {
                    throw new IllegalStateException("Parameters must be annotated with one of @Entity, @PathParam, @HeaderParam, "
                                                            + "@QueryParam - parameter "
                                                            + elementArg.getElementName() + " has more than one annotation.");
                }
            }
        }

        if (httpAnnotation == null) {
            throw new IllegalStateException("Parameters must be annotated with one of @Entity, @PathParam, @HeaderParam, "
                                                    + "@QueryParam - parameter "
                                                    + elementArg.getElementName() + " has neither of these.");
        }

        // todo now we only support String for query, path and header -> add conversions
        switch (httpAnnotation.getTypeName().getClassName()) {
        case ("PathParam") -> parameters.add("req.path().templateParameters().first(\"" + httpAnnotation.getValue() + "\"),");
        case ("Entity") -> parameters.add("req.content().as(" + type + ".class),");
        case ("HeaderParam") -> {
            String headerName = "HEADER_" + (headerList.size() + 1);
            headerList.add(new HeaderDef(headerName, httpAnnotation.getValue()));
            parameters.add("req.headers().get(" + headerName + ").value(),");
        }
        case ("QueryParam") -> {
            httpDef.hasQueryParams = true;
            String defaultValue = httpAnnotation.getValue("defaultValue");
            if (defaultValue == null || QueryParam.NO_DEFAULT_VALUE.equals(defaultValue)) {
                defaultValue = "null";
            } else {
                defaultValue = "\"" + defaultValue + "\"";
            }
            String queryParam = httpAnnotation.getValue();
            // TODO string is hardcoded, we need to add support for mapping
            parameters.add("query(req, res, \"" + queryParam + "\", " + defaultValue + ", String.class),");
        }
        default -> throw new IllegalStateException("Invalid annotation on HTTP parameter: " + elementArg.getElementName());
        }
    }

    private String findAnnotValue(List<AnnotationAndValue> elementAnnotations, String name, String defaultValue) {
        for (AnnotationAndValue elementAnnotation : elementAnnotations) {
            if (name.equals(elementAnnotation.getTypeName().getName())) {
                return elementAnnotation.getValue();
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
