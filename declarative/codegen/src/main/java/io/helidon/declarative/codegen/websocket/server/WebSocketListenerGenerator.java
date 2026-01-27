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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.http.webserver.AbstractParametersProvider;
import io.helidon.service.codegen.FieldHandler;
import io.helidon.service.codegen.RegistryRoundContext;

import static io.helidon.common.types.TypeNames.PRIMITIVE_BOOLEAN;
import static io.helidon.common.types.TypeNames.PRIMITIVE_INT;
import static io.helidon.common.types.TypeNames.STRING;
import static io.helidon.declarative.codegen.DeclarativeTypes.BUFFER_DATA;
import static io.helidon.declarative.codegen.DeclarativeTypes.BYTE_BUFFER;
import static io.helidon.declarative.codegen.DeclarativeTypes.COMMON_MAPPERS;
import static io.helidon.declarative.codegen.DeclarativeTypes.PATH_MATCHERS;
import static io.helidon.declarative.codegen.DeclarativeTypes.THROWABLE;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_HEADERS;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_PATH_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_PATH_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_PROLOGUE;
import static io.helidon.declarative.codegen.websocket.WebSocketTypes.ANNOTATION_ON_CLOSE;
import static io.helidon.declarative.codegen.websocket.WebSocketTypes.ANNOTATION_ON_ERROR;
import static io.helidon.declarative.codegen.websocket.WebSocketTypes.ANNOTATION_ON_MESSAGE;
import static io.helidon.declarative.codegen.websocket.WebSocketTypes.ANNOTATION_ON_OPEN;
import static io.helidon.declarative.codegen.websocket.WebSocketTypes.ANNOTATION_ON_UPGRADE;
import static io.helidon.declarative.codegen.websocket.WebSocketTypes.WS_LISTENER_BASE;
import static io.helidon.declarative.codegen.websocket.WebSocketTypes.WS_SESSION;
import static io.helidon.declarative.codegen.websocket.WebSocketTypes.WS_UPGRADE_EXCEPTION;
import static io.helidon.declarative.codegen.websocket.server.WebSocketExtension.GENERATOR;

class WebSocketListenerGenerator extends AbstractParametersProvider {

    static void generate(RegistryRoundContext roundContext,
                         TypeInfo serverEndpoint,
                         TypeName endpointType,
                         TypeName generatedListener) {
        new WebSocketListenerGenerator().process(roundContext,
                                                 serverEndpoint,
                                                 endpointType,
                                                 generatedListener);
    }

    @Override
    protected String providerType() {
        return "Path Param";
    }

    private void process(RegistryRoundContext roundContext,
                         TypeInfo serverEndpoint,
                         TypeName endpointType,
                         TypeName generatedListener) {
        ClassModel.Builder classModel = ClassModel.builder()
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 endpointType,
                                                 generatedListener))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               endpointType,
                                                               generatedListener,
                                                               "1",
                                                               ""))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .type(generatedListener)
                .superType(WS_LISTENER_BASE);

        String path = serverEndpoint.findAnnotation(HTTP_PATH_ANNOTATION)
                .flatMap(Annotation::stringValue)
                .orElse("/");

        classModel.addField(pathField -> pathField
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .isStatic(true)
                .isFinal(true)
                .type(TypeNames.STRING)
                .name("PATH")
                .addContentLiteral(path)
        );

        classModel.addField(endpoint -> endpoint
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(endpointType)
                .name("endpoint")
        );

        classModel.addField(mappers -> mappers
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(COMMON_MAPPERS)
                .name("mappers"));

        Constructor.Builder ctr = Constructor.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addParameter(COMMON_MAPPERS, "mappers")
                .addParameter(endpoint -> endpoint
                        .type(endpointType)
                        .name("endpoint")
                )
                .addContentLine("this.mappers = mappers;")
                .addContentLine("this.endpoint = endpoint;");

        FieldHandler fieldHandler = FieldHandler.create(classModel, ctr);

        Map<String, AtomicInteger> pathParamFieldCounters = new HashMap<>();
        Map<PathParamKey, String> pathParamFields = new HashMap<>();

        // let's go through all the relevant methods, we just need to process `onHttpUpgrade` last, as there we will
        // read path params

        /*
         @WebSocket.OnError
         */
        List<TypedElementInfo> annotatedMethods = methods(serverEndpoint, ANNOTATION_ON_ERROR);
        checkMaxOne(annotatedMethods, ANNOTATION_ON_ERROR);
        if (!annotatedMethods.isEmpty()) {
            generateOnError(classModel, pathParamFieldCounters, pathParamFields, annotatedMethods.getFirst());
        }

        /*
         @WebSocket.OnClose
         */
        annotatedMethods = methods(serverEndpoint, ANNOTATION_ON_CLOSE);
        checkMaxOne(annotatedMethods, ANNOTATION_ON_CLOSE);
        if (!annotatedMethods.isEmpty()) {
            generateOnClose(classModel, pathParamFieldCounters, pathParamFields, annotatedMethods.getFirst());
        }

        /*
         @WebSocket.OnMessage
         */
        annotatedMethods = methods(serverEndpoint, ANNOTATION_ON_MESSAGE);
        generateOnMessage(classModel, fieldHandler, pathParamFieldCounters, pathParamFields, annotatedMethods);

        /*
         @WebSocket.OnOpen
         */
        annotatedMethods = methods(serverEndpoint, ANNOTATION_ON_OPEN);
        checkMaxOne(annotatedMethods, ANNOTATION_ON_OPEN);
        if (!annotatedMethods.isEmpty()) {
            generateOnOpen(classModel, pathParamFieldCounters, pathParamFields, annotatedMethods.getFirst());
        }

        /*
        @WebSocket.OnHttpUpgrade
         */
        annotatedMethods = methods(serverEndpoint, ANNOTATION_ON_UPGRADE);
        checkMaxOne(annotatedMethods, ANNOTATION_ON_UPGRADE);
        generateOnHttpUpgrade(classModel, pathParamFieldCounters, pathParamFields, annotatedMethods);

        classModel.addConstructor(ctr);
        roundContext.addGeneratedType(generatedListener, classModel, endpointType, serverEndpoint.originatingElementValue());
    }

    private void generateOnMessage(ClassModel.Builder classModel,
                                   FieldHandler fieldHandler,
                                   Map<String, AtomicInteger> pathParamFieldCounters,
                                   Map<PathParamKey, String> pathParamFields,
                                   List<TypedElementInfo> annotatedMethods) {
        /*
         * We can have 0 - 1 text message methods and 0 - 1 binary message methods
         * Javadoc of `OnMessage`:
         * <ul>
         *     <li>{@link io.helidon.common.buffers.BufferData}</li>
         *     <li>{@link java.nio.ByteBuffer}</li>
         *     <li>{@code byte[]}</li>
         *     <li>{@link java.io.InputStream} - this method will be invoked on a separate virtual thread</li>
         * </ul>
         *
         * Text messages can have the following parameter type (MUST have one of these):
         * <ul>
         *     <li>{@link java.lang.String}</li>
         *     <li>{@link java.io.Reader} - this method will be invoked on a separate virtual thread</li>
         *     <li>non-boolean primitive type</li>
         * </ul>
         */
        List<BinaryMethod> binaryMethods = new ArrayList<>();
        List<TextMethod> textMethods = new ArrayList<>();

        for (TypedElementInfo annotatedMethod : annotatedMethods) {
            for (TypedElementInfo param : annotatedMethod.parameterArguments()) {
                if (param.hasAnnotation(HTTP_PATH_PARAM_ANNOTATION)) {
                    continue;
                }
                TypeName typeName = param.typeName();
                if (typeName.equals(WS_SESSION)) {
                    continue;
                }
                if (typeName.equals(TypeNames.STRING)) {
                    textMethods.add(new TextMethod(TextKind.STRING, typeName, annotatedMethod));
                    break;
                }
                if (typeName.equals(TypeName.create(Reader.class))) {
                    textMethods.add(new TextMethod(TextKind.READER, typeName, annotatedMethod));
                    break;
                }
                if (typeName.equals(BUFFER_DATA)) {
                    binaryMethods.add(new BinaryMethod(BinaryKind.BUFFER_DATA, typeName, annotatedMethod));
                    break;
                }
                if (typeName.equals(TypeName.create(ByteBuffer.class))) {
                    binaryMethods.add(new BinaryMethod(BinaryKind.BYTE_BUFFER, typeName, annotatedMethod));
                    break;
                }
                if (typeName.equals(TypeName.create(InputStream.class))) {
                    binaryMethods.add(new BinaryMethod(BinaryKind.INPUT_STREAM, typeName, annotatedMethod));
                    break;
                }
                if (typeName.equals(TypeName.create(byte[].class))) {
                    binaryMethods.add(new BinaryMethod(BinaryKind.BYTE_ARRAY, typeName, annotatedMethod));
                    break;
                }
                if (typeName.primitive()) {
                    textMethods.add(new TextMethod(TextKind.PRIMITIVE_TYPE, typeName, annotatedMethod));
                    break;
                }
                throw new CodegenException("Invalid method signature. Method annotated with "
                                                   + ANNOTATION_ON_MESSAGE.fqName()
                                                   + " does not have an expected binary or text parameter.",
                                           annotatedMethod.originatingElementValue());
            }
        }

        checkMaxOneBinary(binaryMethods);
        checkMaxOneText(textMethods);

        if (!binaryMethods.isEmpty()) {
            generateBinaryOnMessage(classModel, pathParamFieldCounters, pathParamFields, binaryMethods.getFirst());
        }
        if (!textMethods.isEmpty()) {
            generateTextOnMessage(classModel, pathParamFieldCounters, pathParamFields, textMethods.getFirst());
        }
    }

    private void generateTextOnMessage(ClassModel.Builder classModel,
                                       Map<String, AtomicInteger> pathParamFieldCounters,
                                       Map<PathParamKey, String> pathParamFields,
                                       TextMethod method) {
        // parameter by type: `String`, or `Reader`, or a primitive type (never boolean)
        // boolean parameter - whether this is the last message
        // `WsSession` - session parameter
        // parameter annotated with `@Http.PathParam`

        Method.Builder onMessage = Method.builder()
                .name("onMessage")
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .addParameter(WS_SESSION, "session")
                .addParameter(STRING, "text")
                .addParameter(TypeNames.PRIMITIVE_BOOLEAN, "last");

        if (hasLast(method.handlingMethod())) {
            switch (method.kind()) {
            case STRING -> textWithLast(pathParamFieldCounters,
                                        pathParamFields,
                                        onMessage,
                                        method.handlingMethod(),
                                        it -> it.addContent("text"));
            case READER -> textWithLast(pathParamFieldCounters,
                                        pathParamFields,
                                        onMessage,
                                        method.handlingMethod(),
                                        it -> it.addContent("new ")
                                                .addContent(StringReader.class)
                                                .addContent("(text)"));
            case PRIMITIVE_TYPE -> textWithLast(pathParamFieldCounters,
                                                pathParamFields,
                                                onMessage,
                                                method.handlingMethod(),
                                                it -> it.addContent("mappers.map(text, ")
                                                        .addContent(String.class)
                                                        .addContent(".class, ")
                                                        .addContent(method.paramType())
                                                        .addContent(".class, \"websocket\")"));

            default -> throw new CodegenException("Unknown WebSocket method kind: " + method.kind(),
                                                  method.handlingMethod().originatingElementValue());
            }
        } else {

            switch (method.kind()) {
            case STRING -> textWithoutLast(pathParamFieldCounters,
                                           pathParamFields,
                                           onMessage,
                                           method.handlingMethod(),
                                           "textString",
                                           cb -> cb.addContent("it"));
            case READER -> textWithoutLast(pathParamFieldCounters,
                                           pathParamFields,
                                           onMessage,
                                           method.handlingMethod(),
                                           "textReader",
                                           cb -> cb.addContent("it"));
            case PRIMITIVE_TYPE -> textWithoutLast(pathParamFieldCounters,
                                                   pathParamFields,
                                                   onMessage,
                                                   method.handlingMethod(),
                                                   "textString",
                                                   it -> it.addContent("mappers.map(it, ")
                                                           .addContent(String.class)
                                                           .addContent(".class, ")
                                                           .addContent(method.paramType())
                                                           .addContent(".class, \"websocket\")"));

            default -> throw new CodegenException("Unknown WebSocket method kind: " + method.kind(),
                                                  method.handlingMethod().originatingElementValue());
            }
        }

        classModel.addMethod(onMessage);
    }

    private void textWithoutLast(Map<String, AtomicInteger> pathParamFieldCounters,
                                 Map<PathParamKey, String> pathParamFields,
                                 Method.Builder onMessage,
                                 TypedElementInfo handlingMethod,
                                 String invokeMethodName,
                                 Consumer<ContentBuilder<?>> itContentHandler) {
        onMessage.addContent(invokeMethodName)
                .addContent("(session, text, last, it -> endpoint.")
                .addContent(handlingMethod.elementName())
                .addContent("(");

        boolean first = true;

        for (TypedElementInfo argument : handlingMethod.parameterArguments()) {
            if (first) {
                first = false;
            } else {
                onMessage.addContent(", ");
            }

            var type = argument.typeName();
            if (argument.hasAnnotation(HTTP_PATH_PARAM_ANNOTATION)) {
                // handle path param
                onMessage.addContent(pathParamField(pathParamFieldCounters,
                                                    pathParamFields,
                                                    type,
                                                    argument.annotation(HTTP_PATH_PARAM_ANNOTATION)));
            } else {
                if (type.equals(WS_SESSION)) {
                    onMessage.addContent("session");
                } else {
                    itContentHandler.accept(onMessage);
                }
            }
        }
        onMessage.addContentLine("));");
    }

    private void binaryWithLast(Map<String, AtomicInteger> pathParamFieldCounters,
                                Map<PathParamKey, String> pathParamFields,
                                Method.Builder onMessage,
                                TypedElementInfo handlingMethod,
                                Consumer<ContentBuilder<?>> methodBodyConsumer,
                                Consumer<ContentBuilder<?>> parameterConsumer) {

        methodBodyConsumer.accept(onMessage);
        onMessage.addContent("endpoint.")
                .addContent(handlingMethod.elementName())
                .addContent("(");

        boolean first = true;

        for (TypedElementInfo argument : handlingMethod.parameterArguments()) {
            if (first) {
                first = false;
            } else {
                onMessage.addContent(", ");
            }

            var type = argument.typeName();
            if (argument.hasAnnotation(HTTP_PATH_PARAM_ANNOTATION)) {
                // handle path param
                onMessage.addContent(pathParamField(pathParamFieldCounters,
                                                    pathParamFields,
                                                    type,
                                                    argument.annotation(HTTP_PATH_PARAM_ANNOTATION)));
            } else {
                if (type.equals(WS_SESSION)) {
                    onMessage.addContent("session");
                } else if (type.equals(PRIMITIVE_BOOLEAN)) {
                    onMessage.addContent("last");
                } else {
                    parameterConsumer.accept(onMessage);
                }
            }
        }
        onMessage.addContentLine(");");
    }

    private void binaryWithoutLast(Map<String, AtomicInteger> pathParamFieldCounters,
                                   Map<PathParamKey, String> pathParamFields,
                                   Method.Builder onMessage,
                                   TypedElementInfo handlingMethod,
                                   String invokeMethodName) {

        onMessage.addContent(invokeMethodName)
                .addContent("(session, buffer, last, it -> endpoint.")
                .addContent(handlingMethod.elementName())
                .addContent("(");

        boolean first = true;

        for (TypedElementInfo argument : handlingMethod.parameterArguments()) {
            if (first) {
                first = false;
            } else {
                onMessage.addContent(", ");
            }

            var type = argument.typeName();
            if (argument.hasAnnotation(HTTP_PATH_PARAM_ANNOTATION)) {
                // handle path param
                onMessage.addContent(pathParamField(pathParamFieldCounters,
                                                    pathParamFields,
                                                    type,
                                                    argument.annotation(HTTP_PATH_PARAM_ANNOTATION)));
            } else {
                if (type.equals(WS_SESSION)) {
                    onMessage.addContent("session");
                } else {
                    onMessage.addContent("it");
                }
            }
        }
        onMessage.addContentLine("));");
    }

    /*
    consumer - i.e. new StringReader(text)
     */
    private void textWithLast(Map<String, AtomicInteger> pathParamFieldCounters,
                              Map<PathParamKey, String> pathParamFields,
                              Method.Builder onMessage,
                              TypedElementInfo handlingMethod,
                              Consumer<ContentBuilder<?>> consumer) {
        onMessage.addContent("endpoint.")
                .addContent(handlingMethod.elementName())
                .addContent("(");

        boolean first = true;

        for (TypedElementInfo argument : handlingMethod.parameterArguments()) {
            if (first) {
                first = false;
            } else {
                onMessage.addContent(", ");
            }

            var type = argument.typeName();
            if (argument.hasAnnotation(HTTP_PATH_PARAM_ANNOTATION)) {
                // handle path param
                onMessage.addContent(pathParamField(pathParamFieldCounters,
                                                    pathParamFields,
                                                    type,
                                                    argument.annotation(HTTP_PATH_PARAM_ANNOTATION)));
            } else {
                if (type.equals(WS_SESSION)) {
                    onMessage.addContent("session");
                } else if (type.equals(PRIMITIVE_BOOLEAN)) {
                    onMessage.addContent("last");
                } else {
                    consumer.accept(onMessage);
                }
            }
        }
        onMessage.addContentLine(");");
    }

    private boolean hasLast(TypedElementInfo typedElementInfo) {
        for (TypedElementInfo argument : typedElementInfo.parameterArguments()) {
            if (argument.hasAnnotation(HTTP_PATH_PARAM_ANNOTATION)) {
                continue;
            }
            if (argument.typeName().equals(PRIMITIVE_BOOLEAN)) {
                return true;
            }
        }
        return false;
    }

    private void generateBinaryOnMessage(ClassModel.Builder classModel,
                                         Map<String, AtomicInteger> pathParamFieldCounters,
                                         Map<PathParamKey, String> pathParamFields,
                                         BinaryMethod method) {
        // parameter by type: `String`, or `Reader`, or a primitive type (never boolean)
        // boolean parameter - whether this is the last message
        // `WsSession` - session parameter
        // parameter annotated with `@Http.PathParam`

        Method.Builder onMessage = Method.builder()
                .name("onMessage")
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .addParameter(WS_SESSION, "session")
                .addParameter(BUFFER_DATA, "buffer")
                .addParameter(TypeNames.PRIMITIVE_BOOLEAN, "last");

        if (hasLast(method.handlingMethod())) {
            switch (method.kind()) {
            case BUFFER_DATA -> binaryWithLast(pathParamFieldCounters,
                                               pathParamFields,
                                               onMessage,
                                               method.handlingMethod(),
                                               it -> {
                                               },
                                               it -> it.addContent("buffer"));
            case BYTE_BUFFER -> binaryWithLast(pathParamFieldCounters,
                                               pathParamFields,
                                               onMessage,
                                               method.handlingMethod(),
                                               it -> it.addContent("var bb = ")
                                                       .addContent(BYTE_BUFFER)
                                                       .addContentLine(".allocate(buffer.available());")
                                                       .addContentLine("buffer.writeTo(bb, buffer.available());")
                                                       .addContentLine("bb.flip();"),
                                               it -> it.addContent("bb"));

            case BYTE_ARRAY -> binaryWithLast(pathParamFieldCounters,
                                              pathParamFields,
                                              onMessage,
                                              method.handlingMethod(),
                                              it -> it.addContentLine("var bytes = new byte[buffer.available()];")
                                                      .addContentLine("buffer.read(bytes);"),
                                              it -> it.addContent("bytes"));
            case INPUT_STREAM -> binaryWithLast(pathParamFieldCounters,
                                                pathParamFields,
                                                onMessage,
                                                method.handlingMethod(),
                                                it -> it.addContentLine("var bytes = new byte[buffer.available()];")
                                                        .addContentLine("buffer.read(bytes);")
                                                        .addContent("var inputStream = new ")
                                                        .addContent(ByteArrayInputStream.class)
                                                        .addContent("(bytes);"),
                                                it -> it.addContent("inputStream"));
            default -> throw new CodegenException("Unknown WebSocket method kind: " + method.kind(),
                                                  method.handlingMethod().originatingElementValue());
            }
        } else {

            switch (method.kind()) {
            case BUFFER_DATA -> binaryWithoutLast(pathParamFieldCounters,
                                                  pathParamFields,
                                                  onMessage,
                                                  method.handlingMethod(),
                                                  "binaryBufferData");
            case BYTE_BUFFER -> binaryWithoutLast(pathParamFieldCounters,
                                                  pathParamFields,
                                                  onMessage,
                                                  method.handlingMethod(),
                                                  "binaryByteBuffer");
            case BYTE_ARRAY -> binaryWithoutLast(pathParamFieldCounters,
                                                 pathParamFields,
                                                 onMessage,
                                                 method.handlingMethod(),
                                                 "binaryByteArray");
            case INPUT_STREAM -> binaryWithoutLast(pathParamFieldCounters,
                                                   pathParamFields,
                                                   onMessage,
                                                   method.handlingMethod(),
                                                   "binaryInputStream");
            default -> throw new CodegenException("Unknown WebSocket method kind: " + method.kind(),
                                                  method.handlingMethod().originatingElementValue());
            }
        }

        classModel.addMethod(onMessage);
    }

    private void generateOnOpen(ClassModel.Builder classModel,
                                Map<String, AtomicInteger> pathParamFieldCounters,
                                Map<PathParamKey, String> pathParamFields,
                                TypedElementInfo handlingMethod) {
        // we support one parameter by type: `WsSession`
        // in addition we support parameters annotated with `@Http.PathParam`

        Method.Builder onOpen = Method.builder()
                .name("onOpen")
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .addParameter(WS_SESSION, "session");

        onOpen.addContent("endpoint.")
                .addContent(handlingMethod.elementName())
                .addContent("(");

        boolean first = true;

        for (TypedElementInfo argument : handlingMethod.parameterArguments()) {
            if (first) {
                first = false;
            } else {
                onOpen.addContent(", ");
            }

            var type = argument.typeName();
            if (argument.hasAnnotation(HTTP_PATH_PARAM_ANNOTATION)) {
                // handle path param
                pathParamField(pathParamFieldCounters,
                               pathParamFields,
                               type,
                               argument.annotation(HTTP_PATH_PARAM_ANNOTATION));
                // handle path param
                onOpen.addContent(pathParamField(pathParamFieldCounters,
                                                 pathParamFields,
                                                 type,
                                                 argument.annotation(HTTP_PATH_PARAM_ANNOTATION)));
            } else {
                if (type.equals(WS_SESSION)) {
                    onOpen.addContent("session");
                } else {
                    throw new CodegenException("Unsupported parameter type for onClose method: " + type.fqName(),
                                               handlingMethod.originatingElementValue());
                }
            }
        }
        onOpen.addContentLine(");");

        classModel.addMethod(onOpen);

    }

    private void generateOnHttpUpgrade(ClassModel.Builder classModel,
                                       Map<String, AtomicInteger> pathParamFieldCounters,
                                       Map<PathParamKey, String> pathParamFields,
                                       List<TypedElementInfo> annotatedMethods) {
        /*
          we support:
          HttpPrologue
          Headers
          parameters annotated with `@Http.PathParam`
         */

        Method.Builder onUpgrade = Method.builder()
                .name("onHttpUpgrade")
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .addParameter(HTTP_PROLOGUE, "prologue")
                .addParameter(HTTP_HEADERS, "headers")
                .addThrows(it -> it.type(WS_UPGRADE_EXCEPTION))
                .returnType(TypeName.builder(TypeNames.OPTIONAL)
                                    .addTypeArgument(HTTP_HEADERS)
                                    .build());

        // first pass - collect all path params
        if (!annotatedMethods.isEmpty()) {
            TypedElementInfo handlingMethod = annotatedMethods.getFirst();
            for (TypedElementInfo argument : handlingMethod.parameterArguments()) {
                var type = argument.typeName();
                if (argument.hasAnnotation(HTTP_PATH_PARAM_ANNOTATION)) {
                    // handle path param
                    pathParamField(pathParamFieldCounters,
                                   pathParamFields,
                                   type,
                                   argument.annotation(HTTP_PATH_PARAM_ANNOTATION));
                }
            }
        }

        if (annotatedMethods.isEmpty() && pathParamFields.isEmpty()) {
            // no need to create this method at all - we do not call a user method, nor do we need to read path parameters
            return;
        }

        // now we have all path params collected, let's generate their fields and the method content
        if (!pathParamFields.isEmpty()) {
            onUpgrade.addContent("var matched = ")
                    .addContent(PATH_MATCHERS)
                    .addContentLine(".create(PATH).match(prologue.uriPath());")
                    .addContentLine("if (!matched.accepted()) {")
                    .addContent("throw new ")
                    .addContent(WS_UPGRADE_EXCEPTION)
                    .addContent("(")
                    .addContentLiteral("Wrong matched path")
                    .addContentLine(");")
                    .addContentLine("}")
                    .addContentLine()
                    .addContentLine("var params = matched.path().pathParameters();")
                    .addContentLine();

            pathParamFields.forEach((key, field) -> {
                classModel.addField(pathParamField -> pathParamField
                        .accessModifier(AccessModifier.PRIVATE)
                        .isVolatile(true)
                        .type(key.type())
                        .name(field)
                );
                onUpgrade.addContent(field)
                        .addContent(" = params");
                codegenFromParameters(onUpgrade, key.type(), key.name(), key.type().isOptional());
            });
        }
        if (annotatedMethods.isEmpty()) {
            onUpgrade.addContent("return ")
                    .addContent(TypeNames.OPTIONAL)
                    .addContentLine(".empty();");
        } else {
            TypedElementInfo handlingMethod = annotatedMethods.getFirst();

            boolean isVoid = handlingMethod.typeName().equals(TypeNames.PRIMITIVE_VOID);
            if (!isVoid) {
                onUpgrade.addContent("var response = ");
            }

            onUpgrade.addContent("endpoint.")
                    .addContent(handlingMethod.elementName())
                    .addContent("(");

            boolean first = true;

            for (TypedElementInfo argument : handlingMethod.parameterArguments()) {
                if (first) {
                    first = false;
                } else {
                    onUpgrade.addContent(", ");
                }

                var type = argument.typeName();
                if (argument.hasAnnotation(HTTP_PATH_PARAM_ANNOTATION)) {
                    // handle path param
                    onUpgrade.addContent(pathParamField(pathParamFieldCounters,
                                                        pathParamFields,
                                                        type,
                                                        argument.annotation(HTTP_PATH_PARAM_ANNOTATION)));
                } else {
                    if (type.equals(HTTP_PROLOGUE)) {
                        onUpgrade.addContent("prologue");
                    } else if (type.equals(HTTP_HEADERS)) {
                        onUpgrade.addContent("headers");
                    } else {
                        throw new CodegenException("Unsupported parameter type for onHttpUpgrade method: " + type.fqName(),
                                                   handlingMethod.originatingElementValue());
                    }
                }
            }
            onUpgrade.addContentLine(");");

            if (isVoid) {
                onUpgrade.addContent("return ")
                        .addContent(TypeNames.OPTIONAL)
                        .addContentLine(".empty();");
            } else {
                if (handlingMethod.typeName().isOptional()) {
                    onUpgrade.addContentLine("return response;");
                } else {
                    onUpgrade.addContent("return ")
                            .addContent(TypeNames.OPTIONAL)
                            .addContentLine(".ofNullable(response);");
                }
            }
        }
        classModel.addMethod(onUpgrade);
    }

    private void generateOnClose(ClassModel.Builder classModel,
                                 Map<String, AtomicInteger> pathParamFieldCounters,
                                 Map<PathParamKey, String> pathParamFields,
                                 TypedElementInfo handlingMethod) {
        // we support three parameter by type: `WsSession`, `int` (status code), `String` (reason)
        // in addition we support parameters annotated with `@Http.PathParam`

        Method.Builder onClose = Method.builder()
                .name("onClose")
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .addParameter(WS_SESSION, "session")
                .addParameter(PRIMITIVE_INT, "status")
                .addParameter(STRING, "reason");

        onClose.addContent("endpoint.")
                .addContent(handlingMethod.elementName())
                .addContent("(");

        boolean first = true;

        for (TypedElementInfo argument : handlingMethod.parameterArguments()) {
            if (first) {
                first = false;
            } else {
                onClose.addContent(", ");
            }

            var type = argument.typeName();
            if (argument.hasAnnotation(HTTP_PATH_PARAM_ANNOTATION)) {
                // handle path param
                onClose.addContent(pathParamField(pathParamFieldCounters,
                                                  pathParamFields,
                                                  type,
                                                  argument.annotation(HTTP_PATH_PARAM_ANNOTATION)));
            } else {
                if (type.equals(WS_SESSION)) {
                    onClose.addContent("session");
                } else if (type.equals(PRIMITIVE_INT)) {
                    onClose.addContent("status");
                } else if (type.equals(STRING)) {
                    onClose.addContent("reason");
                } else {
                    throw new CodegenException("Unsupported parameter type for onClose method: " + type.fqName(),
                                               handlingMethod.originatingElementValue());
                }
            }
        }
        onClose.addContentLine(");");

        classModel.addMethod(onClose);
    }

    private void generateOnError(ClassModel.Builder classModel,
                                 Map<String, AtomicInteger> pathParamFieldCounters,
                                 Map<PathParamKey, String> pathParamFields,
                                 TypedElementInfo handlingMethod) {
        // we support two parameters by type: `WsSession` and `Throwable`
        // in addition we support parameters annotated with `@Http.PathParam`

        Method.Builder onError = Method.builder()
                .name("onError")
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .addParameter(WS_SESSION, "session")
                .addParameter(THROWABLE, "t");

        onError.addContent("endpoint.")
                .addContent(handlingMethod.elementName())
                .addContent("(");
        boolean first = true;

        for (TypedElementInfo argument : handlingMethod.parameterArguments()) {
            if (first) {
                first = false;
            } else {
                onError.addContent(", ");
            }

            var type = argument.typeName();
            if (argument.hasAnnotation(HTTP_PATH_PARAM_ANNOTATION)) {
                // handle path param
                onError.addContent(pathParamField(pathParamFieldCounters,
                                                  pathParamFields,
                                                  type,
                                                  argument.annotation(HTTP_PATH_PARAM_ANNOTATION)));
            } else {
                if (type.equals(WS_SESSION)) {
                    onError.addContent("session");
                } else if (type.equals(THROWABLE)) {
                    onError.addContent("t");
                } else {
                    throw new CodegenException("Unsupported parameter type for onError method: " + type.fqName(),
                                               handlingMethod.originatingElementValue());
                }
            }
        }
        onError.addContentLine(");");

        classModel.addMethod(onError);
    }

    private String pathParamField(Map<String, AtomicInteger> pathParamFieldCounters,
                                  Map<PathParamKey, String> pathParamFields,
                                  TypeName type,
                                  Annotation annotation) {
        String pathParamName = annotation.stringValue()
                .get();
        PathParamKey key = new PathParamKey(pathParamName, type);
        if (pathParamFields.containsKey(key)) {
            return pathParamFields.get(key);
        }
        String fieldName;
        if (pathParamFieldCounters.containsKey(pathParamName)) {
            fieldName = pathParamName + "_" + pathParamFieldCounters.get(pathParamName).getAndIncrement();
        } else {
            pathParamFieldCounters.put(pathParamName, new AtomicInteger());
            fieldName = pathParamName;
        }
        pathParamFields.put(key, fieldName);
        return fieldName;
    }

    private List<TypedElementInfo> methods(TypeInfo serverEndpoint, TypeName annotationType) {
        var result = serverEndpoint.elementInfo()
                .stream()
                .filter(ElementInfoPredicates.hasAnnotation(annotationType))
                .collect(Collectors.toUnmodifiableList());

        for (TypedElementInfo element : result) {
            checkNotPrivate(annotationType, element);
            checkNotStatic(annotationType, element);
            checkNotAbstract(annotationType, element);
        }

        return result;
    }

    private void checkNotAbstract(TypeName annotationType, TypedElementInfo it) {
        if (it.elementModifiers().contains(Modifier.ABSTRACT)) {
            throw new CodegenException("Methods annotated with " + annotationType.fqName() + " must be at least package private",
                                       it.originatingElementValue());
        }
    }

    private void checkNotStatic(TypeName annotationType, TypedElementInfo it) {
        if (it.elementModifiers().contains(Modifier.STATIC)) {
            throw new CodegenException("Methods annotated with " + annotationType.fqName() + " must not be static",
                                       it.originatingElementValue());
        }
    }

    private void checkNotPrivate(TypeName annotationType, TypedElementInfo it) {
        if (it.accessModifier() == AccessModifier.PRIVATE) {
            throw new CodegenException("Methods annotated with " + annotationType.fqName() + " must be at least package private",
                                       it.originatingElementValue());
        }
    }

    private void checkMaxOne(List<TypedElementInfo> annotatedMethods, TypeName annotationType) {
        if (annotatedMethods.size() > 1) {
            throw new CodegenException("There can be maximally one method annotated with " + annotationType.fqName(),
                                       annotatedMethods.getFirst().originatingElementValue());
        }
    }

    private void checkMaxOneBinary(List<BinaryMethod> annotatedMethods) {
        if (annotatedMethods.size() > 1) {
            throw new CodegenException("There can be maximally one method annotated with " + ANNOTATION_ON_MESSAGE
                                               + " handling binary messages",
                                       annotatedMethods.getFirst().handlingMethod().originatingElementValue());
        }
    }

    private void checkMaxOneText(List<TextMethod> annotatedMethods) {
        if (annotatedMethods.size() > 1) {
            throw new CodegenException("There can be maximally one method annotated with " + ANNOTATION_ON_MESSAGE
                                               + " handling text messages",
                                       annotatedMethods.getFirst().handlingMethod().originatingElementValue());
        }
    }

    private enum BinaryKind {
        BUFFER_DATA,
        BYTE_BUFFER,
        BYTE_ARRAY,
        INPUT_STREAM
    }

    private enum TextKind {
        STRING,
        READER,
        PRIMITIVE_TYPE
    }

    private record BinaryMethod(BinaryKind kind, TypeName paramType, TypedElementInfo handlingMethod) {
    }

    private record TextMethod(TextKind kind, TypeName paramType, TypedElementInfo handlingMethod) {
    }

    private record PathParamKey(String name, TypeName type) {
    }
}
