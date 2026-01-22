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

package io.helidon.declarative.codegen.websocket.server;

import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.codegen.RegistryRoundContext;

import static io.helidon.declarative.codegen.DeclarativeTypes.COMMON_MAPPERS;
import static io.helidon.declarative.codegen.DeclarativeTypes.SINGLETON_ANNOTATION;
import static io.helidon.declarative.codegen.websocket.server.WebSocketExtension.GENERATOR;
import static io.helidon.declarative.codegen.websocket.server.WebSocketServerTypes.ANNOTATION_LISTENER;
import static io.helidon.declarative.codegen.websocket.server.WebSocketServerTypes.WS_ROUTE;
import static io.helidon.declarative.codegen.websocket.server.WebSocketServerTypes.WS_ROUTE_REGISTRATION;

class WebSocketRegistrationGenerator {
    private WebSocketRegistrationGenerator() {
    }

    static void generate(RegistryRoundContext roundContext,
                         TypeInfo serverEndpoint,
                         TypeName endpointType,
                         TypeName generatedRegistration,
                         TypeName generatedListener) {

        ClassModel.Builder classModel = ClassModel.builder()
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 endpointType,
                                                 generatedRegistration))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               endpointType,
                                                               generatedRegistration,
                                                               "1",
                                                               ""))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .type(generatedRegistration)
                .addAnnotation(SINGLETON_ANNOTATION)
                .addInterface(WS_ROUTE_REGISTRATION);

        classModel.addField(route -> route
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(WS_ROUTE)
                .name("route")
        );

        classModel.addField(mappers -> mappers
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(COMMON_MAPPERS)
                .name("mappers"));

        classModel.addConstructor(ctr -> ctr
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addParameter(COMMON_MAPPERS, "mappers")
                .addParameter(TypeName.builder(TypeNames.SUPPLIER)
                                      .addTypeArgument(endpointType)
                                      .build(), "endpoint")
                .addContentLine("this.mappers = mappers;")
                .addContent("this.route = ")
                .addContent(WS_ROUTE)
                .addContent(".create(")
                .addContent(generatedListener)
                .addContent(".PATH, () -> new ")
                .addContent(generatedListener)
                .addContentLine("(mappers, endpoint.get()));")
        );

        /*
        route and toString are always present
         */
        classModel.addMethod(route -> route
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(WS_ROUTE)
                .name("route")
                .addContentLine("return route;")
        );
        classModel.addMethod(toString -> toString
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(TypeNames.STRING)
                .name("toString")
                .addContent("return ")
                .addContentLiteral("WebSocket route registration for " + endpointType.className() + "(")
                .addContent(" + ")
                .addContent(generatedListener)
                .addContent(".PATH + ")
                .addContentLiteral(")")
                .addContentLine(";")
        );

        /*
        Socket methods only if they return something else than the default impl
         */
        String listener = serverEndpoint.findAnnotation(ANNOTATION_LISTENER)
                .flatMap(Annotation::stringValue)
                .orElse("@default");
        if (!"@default".equals(listener)) {

            classModel.addMethod(socket -> socket
                    .addAnnotation(Annotations.OVERRIDE)
                    .accessModifier(AccessModifier.PUBLIC)
                    .returnType(TypeNames.STRING)
                    .name("socket")
                    .addContent("return ")
                    .addContentLiteral(listener)
                    .addContentLine(";")
            );
            classModel.addMethod(socketRequired -> socketRequired
                    .addAnnotation(Annotations.OVERRIDE)
                    .accessModifier(AccessModifier.PUBLIC)
                    .returnType(TypeNames.PRIMITIVE_BOOLEAN)
                    .name("socketRequired")
                    .addContent("return true;") // we do not have an annotation option to change this now
            );
        }

        roundContext.addGeneratedType(generatedRegistration, classModel, endpointType, serverEndpoint.originatingElementValue());
    }
}
