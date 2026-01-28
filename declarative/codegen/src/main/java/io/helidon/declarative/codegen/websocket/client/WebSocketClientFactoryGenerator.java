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

package io.helidon.declarative.codegen.websocket.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.AnnotationProperty;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.declarative.codegen.http.HttpTypes;
import io.helidon.service.codegen.RegistryRoundContext;

import static io.helidon.declarative.codegen.DeclarativeTypes.COMMON_MAPPERS;
import static io.helidon.declarative.codegen.DeclarativeTypes.CONFIG;
import static io.helidon.declarative.codegen.DeclarativeTypes.SINGLETON_ANNOTATION;
import static io.helidon.declarative.codegen.websocket.client.WebSocketClientExtension.GENERATOR;
import static io.helidon.declarative.codegen.websocket.client.WebSocketClientTypes.ANNOTATION_ENDPOINT;
import static io.helidon.declarative.codegen.websocket.client.WebSocketClientTypes.CLIENT_ENDPOINT_FACTORY;
import static io.helidon.declarative.codegen.websocket.client.WebSocketClientTypes.WS_CLIENT;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED_BY_TYPE;
import static io.helidon.service.codegen.ServiceCodegenTypes.SET_OF_STRINGS;

class WebSocketClientFactoryGenerator {
    private static final TypeName SUPPLIER_OPTIONAL_CLIENT = TypeName.builder()
            .from(TypeNames.SUPPLIER)
            .addTypeArgument(TypeName.builder()
                                     .from(TypeNames.OPTIONAL)
                                     .addTypeArgument(WS_CLIENT)
                                     .build())
            .build();

    private WebSocketClientFactoryGenerator() {
    }

    static void generate(RegistryRoundContext roundContext,
                         TypeInfo serverEndpoint,
                         TypeName endpointType,
                         TypeName generatedFactory,
                         TypeName generatedListener,
                         Map<String, TypeName> pathParams) {

        ClassModel.Builder classModel = ClassModel.builder()
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 endpointType,
                                                 generatedFactory))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               endpointType,
                                                               generatedFactory,
                                                               "1",
                                                               ""))
                .accessModifier(serverEndpoint.accessModifier())
                .description("Factory to connect web socket endpoint {@link " + endpointType.fqName() + "} to a server.")
                .type(generatedFactory)
                .addAnnotation(SINGLETON_ANNOTATION)
                .superType(CLIENT_ENDPOINT_FACTORY)
                .addAnnotation(Annotation.builder()
                                       .typeName(SERVICE_ANNOTATION_NAMED_BY_TYPE)
                                       .putProperty("value", AnnotationProperty.create(endpointType))
                                       .build());

        classModel.addField(pathParamsField -> pathParamsField
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .isFinal(true)
                .type(SET_OF_STRINGS)
                .name("PATH_PARAMS")
                .addContent(Set.class)
                .addContent(".of(")
                .addContent(pathParams.keySet()
                                    .stream()
                                    .map(it -> "\"" + it + "\"")
                                    .collect(Collectors.joining(", ")))
                .addContent(")")
        );

        var endpointAnnotation = serverEndpoint.annotation(ANNOTATION_ENDPOINT);
        String endpointString = endpointAnnotation.stringValue().orElse("");
        String pathString = serverEndpoint.findAnnotation(HttpTypes.HTTP_PATH_ANNOTATION)
                .flatMap(Annotation::stringValue)
                .orElse("/");

        classModel.addField(mappers -> mappers
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(COMMON_MAPPERS)
                .name("mappers"));

        classModel.addField(wsClient -> wsClient
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(WS_CLIENT)
                .name("client")
        );

        TypeName endpointSupplier = TypeName.builder(TypeNames.SUPPLIER)
                .addTypeArgument(endpointType)
                .build();

        classModel.addField(ep -> ep
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(endpointSupplier)
                .name("endpointSupplier")
        );

        classModel.addConstructor(ctr -> ctr
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addParameter(COMMON_MAPPERS, "mappers")
                .addParameter(CONFIG, "config")
                .addParameter(TypeName.builder(TypeNames.SUPPLIER)
                                      .addTypeArgument(endpointType)
                                      .build(), "endpointSupplier")
                .addContent("super(config,")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContentLiteral(endpointString)
                .addContentLine(",")
                .addContentLiteral(pathString)
                .addContentLine(",")
                .addContentLine("PATH_PARAMS);")
                .decreaseContentPadding()
                .decreaseContentPadding()
                .addContentLine()
                .addContentLine("this.mappers = mappers;")
                .addContentLine("this.endpointSupplier = endpointSupplier;")
                .addContentLine()
                .update(it -> handleWsClient(serverEndpoint, it))
        );

        // WsClient client
        classModel.addMethod(clientMethod -> clientMethod
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(WS_CLIENT)
                .name("client")
                .addContentLine("return client;")
        );

        // generated public methods with path parameters
        classModel.addMethod(connectMethod -> connectMethod
                .name("connect")
                .description("Connect to the configured endpoint"
                                     + (pathParams.isEmpty() ? "." : " with path parameters."))
                .accessModifier(AccessModifier.PUBLIC)
                .update(it -> {
                    // add path param parameters
                    pathParams.forEach((name, type) -> {
                        it.addParameter(param -> param
                                .type(type)
                                .name(name)
                                .description(name + " path parameter"));
                    });
                })
                .addContent("connect(this.client, ")
                .addContent(pathParams.keySet()
                                    .stream()
                                    .collect(Collectors.joining(", ")))
                .addContentLine(");")
        );

        connectWithClientMethod(generatedListener, classModel, pathParams);

        // and the doConnect method that

        classModel.addMethod(toString -> toString
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(TypeNames.STRING)
                .name("toString")
                .addContent("return ")
                .addContentLiteral("WebSocket client factory for " + endpointType.className())
                .addContentLine(";")
        );
        doConnectMethod(classModel, pathParams);

        roundContext.addGeneratedType(generatedFactory, classModel, endpointType, serverEndpoint.originatingElementValue());
    }

    private static void doConnectMethod(ClassModel.Builder classModel,
                                        Map<String, TypeName> pathParams) {
        var doConnect = Method.builder()
                .name("doConnect")
                .accessModifier(AccessModifier.PROTECTED)
                .addAnnotation(Annotations.OVERRIDE)
                .addParameter(WS_CLIENT, "client")
                .addParameter(TypeName.builder(TypeNames.MAP)
                                      .addTypeArgument(TypeNames.STRING)
                                      .addTypeArgument(TypeNames.STRING)
                                      .build(), "pathParameters");

        // parameters
        boolean prefixParams = pathParams.containsKey("client");

        for (var pathParam : pathParams.entrySet()) {
            doConnect.addContent("var ")
                    .addContent(prefixParams ? "user_" : "")
                    .addContent(pathParam.getKey())
                    .addContent(" = ");

            if (pathParam.getValue().equals(TypeNames.STRING)) {
                doConnect.addContent("pathParameters.get(")
                        .addContentLiteral(pathParam.getKey())
                        .addContentLine(");");
            } else {
                doConnect.addContent("mappers.map(pathParameters.get(")
                        .addContentLiteral(pathParam.getKey())
                        .addContent("), ")
                        .addContent(String.class)
                        .addContent(".class, ")
                        .addContent(pathParam.getValue())
                        .addContent(".class, ")
                        .addContentLiteral("websocket")
                        .addContentLine(");");
            }
        }
        doConnect.addContent("connect(client, ")
                .addContent(pathParams.keySet()
                                    .stream()
                                    .map(name -> prefixParams ? "user_" + name : name)
                                    .collect(Collectors.joining(", ")))
                .addContentLine(");");
        classModel.addMethod(doConnect);

    }

    private static void connectWithClientMethod(TypeName generatedListener,
                                                ClassModel.Builder classModel,
                                                Map<String, TypeName> pathParams) {
        var connectWithClient = Method.builder()
                .name("connect")
                .description("Connect using the provided client"
                                     + (pathParams.isEmpty() ? "." : " with path parameters."))
                .accessModifier(AccessModifier.PUBLIC)
                .addParameter(WS_CLIENT, "client");

        // parameters
        boolean prefixParams = pathParams.containsKey("client");

        // add path param parameters
        pathParams.forEach((name, type) -> {
            connectWithClient.addParameter(param -> param
                    .type(type)
                    .name(prefixParams ? "user_" + name : name)
                    .description(name + " path parameter"));
        });

        if (pathParams.isEmpty()) {
            connectWithClient.addContent("doConnect(client, pathParams, () -> new ")
                    .addContent(generatedListener)
                    .addContentLine("(endpointSupplier.get()));");
        } else {
            // now for each parameter that is not a string, convert to String
            connectWithClient.addContent(Map.class)
                    .addContent("<String, String> pathParams = new ")
                    .addContent(HashMap.class)
                    .addContentLine("<>();");

            for (var pathParam : pathParams.entrySet()) {
                String paramName = (prefixParams ? "user_" : "") + pathParam.getKey();

                connectWithClient.addContent("pathParams.put(")
                        .addContentLiteral(pathParam.getKey())
                        .addContent(", ");

                if (pathParam.getValue().equals(TypeNames.STRING)) {
                    connectWithClient.addContent(paramName);
                } else {
                    connectWithClient.addContent("mappers.map(")
                            .addContent(paramName)
                            .addContent(", ")
                            .addContent(pathParam.getValue())
                            .addContent(".class")
                            .addContent(", ")
                            .addContent(String.class)
                            .addContent(".class, \"websocket\")");
                }
                connectWithClient.addContentLine(");");
            }

            connectWithClient.addContentLine("doConnect(client,")
                    .increaseContentPadding()
                    .increaseContentPadding()
                    .addContentLine("pathParams,")
                    .addContent("() -> new ")
                    .addContent(generatedListener)
                    .addContent("(endpointSupplier.get(), ")
                    .addContent(pathParams.keySet()
                                        .stream()
                                        .map(name -> prefixParams ? "user_" + name : name)
                                        .collect(Collectors.joining(", ")))
                    .addContentLine("));")
                    .decreaseContentPadding()
                    .decreaseContentPadding();
        }

        classModel.addMethod(connectWithClient);
    }

    private static void handleWsClient(TypeInfo serverEndpoint, Constructor.Builder constructor) {
        constructor.addContent(WS_CLIENT)
                .addContentLine(" tmpClient = null;");

        var endpointAnnotation = serverEndpoint.annotation(ANNOTATION_ENDPOINT);
        // first from configuration
        String configKey = endpointAnnotation.stringValue("configKey").orElse("");
        if (!configKey.isEmpty()) {
            constructor.addContent("var clientConfig = config.get(")
                    .addContentLiteral(configKey)
                    .addContentLine(");")
                    .addContentLine("if (clientConfig.exists()) {")
                    .addContent("tmpClient = ")
                    .addContent(WS_CLIENT)
                    .addContentLine(".create(clientConfig);")
                    .addContentLine("}");
        }
        // then a named client
        String namedClientName = endpointAnnotation.stringValue("clientName").orElse("");
        if (!namedClientName.isEmpty()) {
            constructor.addParameter(param -> param
                    .type(SUPPLIER_OPTIONAL_CLIENT)
                    .name("namedClientSupplier")
                    .addAnnotation(Annotation.builder()
                                           .typeName(SERVICE_ANNOTATION_NAMED)
                                           .putProperty("value", AnnotationProperty.create(namedClientName))
                                           .build())
            );
            constructor.addContentLine("if (tmpClient == null) {")
                    .addContentLine("tmpClient = namedClientSupplier.get().orElse(null);")
                    .addContentLine("}");
        }
        // then the default service registry client
        constructor.addParameter(SUPPLIER_OPTIONAL_CLIENT, "clientSupplier")
                .addContentLine("if (tmpClient == null) {")
                .addContentLine("tmpClient = clientSupplier.get().orElse(null);")
                .addContentLine("}");
        // fallback to new client
        constructor.addContentLine("if (tmpClient == null) {")
                .addContent("tmpClient = ")
                .addContent(WS_CLIENT)
                .addContentLine(".create();")
                .addContentLine("}");
        // assign to field
        constructor.addContentLine("this.client = tmpClient;");

    }
}
