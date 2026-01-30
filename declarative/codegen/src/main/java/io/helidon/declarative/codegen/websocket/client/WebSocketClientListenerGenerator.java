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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import io.helidon.service.codegen.RegistryRoundContext;

import static io.helidon.common.types.TypeNames.PRIMITIVE_BOOLEAN;
import static io.helidon.common.types.TypeNames.PRIMITIVE_INT;
import static io.helidon.common.types.TypeNames.STRING;
import static io.helidon.declarative.codegen.DeclarativeTypes.BUFFER_DATA;
import static io.helidon.declarative.codegen.DeclarativeTypes.BYTE_BUFFER;
import static io.helidon.declarative.codegen.DeclarativeTypes.THROWABLE;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_PATH_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.websocket.WebSocketTypes.ANNOTATION_ON_CLOSE;
import static io.helidon.declarative.codegen.websocket.WebSocketTypes.ANNOTATION_ON_ERROR;
import static io.helidon.declarative.codegen.websocket.WebSocketTypes.ANNOTATION_ON_MESSAGE;
import static io.helidon.declarative.codegen.websocket.WebSocketTypes.ANNOTATION_ON_OPEN;
import static io.helidon.declarative.codegen.websocket.WebSocketTypes.ANNOTATION_ON_UPGRADE;
import static io.helidon.declarative.codegen.websocket.WebSocketTypes.WS_LISTENER_BASE;
import static io.helidon.declarative.codegen.websocket.WebSocketTypes.WS_SESSION;
import static io.helidon.declarative.codegen.websocket.client.WebSocketClientExtension.GENERATOR;

class WebSocketClientListenerGenerator extends AbstractParametersProvider {

    static void generate(RegistryRoundContext roundContext,
                         TypeInfo serverEndpoint,
                         TypeName endpointType,
                         TypeName generatedListener,
                         Map<String, TypeName> pathParams) {
        new WebSocketClientListenerGenerator().process(roundContext,
                                                       serverEndpoint,
                                                       endpointType,
                                                       generatedListener,
                                                       pathParams);
    }

    @Override
    protected String providerType() {
        return "Path Param";
    }

    private void process(RegistryRoundContext roundContext,
                         TypeInfo clientEndpoint,
                         TypeName endpointType,
                         TypeName generatedListener,
                         Map<String, TypeName> pathParams) {
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

        classModel.addField(endpoint -> endpoint
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(endpointType)
                .name("endpoint")
        );

        Constructor.Builder ctr = Constructor.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addParameter(endpoint -> endpoint
                        .type(endpointType)
                        .name("endpoint")
                )
                .addContentLine("this.endpoint = endpoint;");

        for (var pathParam : pathParams.entrySet()) {
            classModel.addField(field -> field
                    .name("user__" + pathParam.getKey())
                    .type(pathParam.getValue())
                    .isFinal(true)
                    .accessModifier(AccessModifier.PRIVATE)
            );
            ctr.addParameter(param -> param
                            .name("user__" + pathParam.getKey())
                            .type(pathParam.getValue())
                    )
                    .addContentLine("this.user__" + pathParam.getKey() + " = user__" + pathParam.getKey() + ";");
        }

        // let's go through all the relevant methods, we just need to process `onHttpUpgrade` last, as there we will
        // read path params

        /*
         @WebSocket.OnError
         */
        List<TypedElementInfo> annotatedMethods = methods(clientEndpoint, ANNOTATION_ON_ERROR);
        checkMaxOne(annotatedMethods, ANNOTATION_ON_ERROR);
        if (!annotatedMethods.isEmpty()) {
            generateOnError(classModel, annotatedMethods.getFirst());
        }

        /*
         @WebSocket.OnClose
         */
        annotatedMethods = methods(clientEndpoint, ANNOTATION_ON_CLOSE);
        checkMaxOne(annotatedMethods, ANNOTATION_ON_CLOSE);
        if (!annotatedMethods.isEmpty()) {
            generateOnClose(classModel, annotatedMethods.getFirst());
        }

        /*
         @WebSocket.OnMessage
         */
        annotatedMethods = methods(clientEndpoint, ANNOTATION_ON_MESSAGE);
        generateOnMessage(classModel, annotatedMethods);

        /*
         @WebSocket.OnOpen
         */
        annotatedMethods = methods(clientEndpoint, ANNOTATION_ON_OPEN);
        checkMaxOne(annotatedMethods, ANNOTATION_ON_OPEN);
        if (!annotatedMethods.isEmpty()) {
            generateOnOpen(classModel, annotatedMethods.getFirst());
        }

        /*
        @WebSocket.OnHttpUpgrade
         */
        annotatedMethods = methods(clientEndpoint, ANNOTATION_ON_UPGRADE);
        if (!annotatedMethods.isEmpty()) {
            throw new CodegenException("Client cannot have methods annotated with " + ANNOTATION_ON_UPGRADE.fqName(),
                                       annotatedMethods.getFirst().originatingElementValue());
        }
        classModel.addConstructor(ctr);
        roundContext.addGeneratedType(generatedListener, classModel, endpointType, clientEndpoint.originatingElementValue());
    }

    private void generateOnMessage(ClassModel.Builder classModel,
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
            generateBinaryOnMessage(classModel, binaryMethods.getFirst());
        }
        if (!textMethods.isEmpty()) {
            generateTextOnMessage(classModel, textMethods.getFirst());
        }
    }

    private void generateTextOnMessage(ClassModel.Builder classModel,
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
            case STRING -> textWithLast(onMessage,
                                        method.handlingMethod(),
                                        it -> it.addContent("text"));
            case READER -> textWithLast(onMessage,
                                        method.handlingMethod(),
                                        it -> it.addContent("new ")
                                                .addContent(StringReader.class)
                                                .addContent("(text)"));
            case PRIMITIVE_TYPE -> textWithLast(onMessage,
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
            case STRING -> textWithoutLast(onMessage,
                                           method.handlingMethod(),
                                           "textString",
                                           cb -> cb.addContent("it"));
            case READER -> textWithoutLast(onMessage,
                                           method.handlingMethod(),
                                           "textReader",
                                           cb -> cb.addContent("it"));
            case PRIMITIVE_TYPE -> textWithoutLast(onMessage,
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

    private void textWithoutLast(Method.Builder onMessage,
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
                onMessage.addContent(pathParamField(argument.annotation(HTTP_PATH_PARAM_ANNOTATION)));
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

    private String pathParamField(Annotation pathParamAnnotation) {
        return "user__" + pathParamAnnotation
                .stringValue()
                // the option is mandatory
                .orElseThrow();
    }

    private void binaryWithLast(Method.Builder onMessage,
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
                onMessage.addContent(pathParamField(argument.annotation(HTTP_PATH_PARAM_ANNOTATION)));
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

    private void binaryWithoutLast(Method.Builder onMessage,
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
                onMessage.addContent(pathParamField(argument.annotation(HTTP_PATH_PARAM_ANNOTATION)));
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
    private void textWithLast(Method.Builder onMessage,
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
                onMessage.addContent(pathParamField(argument.annotation(HTTP_PATH_PARAM_ANNOTATION)));
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
            case BUFFER_DATA -> binaryWithLast(onMessage,
                                               method.handlingMethod(),
                                               it -> {
                                               },
                                               it -> it.addContent("buffer"));
            case BYTE_BUFFER -> binaryWithLast(onMessage,
                                               method.handlingMethod(),
                                               it -> it.addContent("var bb = ")
                                                       .addContent(BYTE_BUFFER)
                                                       .addContentLine(".allocate(buffer.available());")
                                                       .addContentLine("buffer.writeTo(bb, buffer.available());")
                                                       .addContentLine("bb.flip();"),
                                               it -> it.addContent("bb"));

            case BYTE_ARRAY -> binaryWithLast(onMessage,
                                              method.handlingMethod(),
                                              it -> it.addContentLine("var bytes = new byte[buffer.available()];")
                                                      .addContentLine("buffer.read(bytes);"),
                                              it -> it.addContent("bytes"));
            case INPUT_STREAM -> binaryWithLast(onMessage,
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
            case BUFFER_DATA -> binaryWithoutLast(onMessage,
                                                  method.handlingMethod(),
                                                  "binaryBufferData");
            case BYTE_BUFFER -> binaryWithoutLast(onMessage,
                                                  method.handlingMethod(),
                                                  "binaryByteBuffer");
            case BYTE_ARRAY -> binaryWithoutLast(onMessage,
                                                 method.handlingMethod(),
                                                 "binaryByteArray");
            case INPUT_STREAM -> binaryWithoutLast(onMessage,
                                                   method.handlingMethod(),
                                                   "binaryInputStream");
            default -> throw new CodegenException("Unknown WebSocket method kind: " + method.kind(),
                                                  method.handlingMethod().originatingElementValue());
            }
        }

        classModel.addMethod(onMessage);
    }

    private void generateOnOpen(ClassModel.Builder classModel,
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
                onOpen.addContent(pathParamField(argument.annotation(HTTP_PATH_PARAM_ANNOTATION)));
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

    private void generateOnClose(ClassModel.Builder classModel,
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
                onClose.addContent(pathParamField(argument.annotation(HTTP_PATH_PARAM_ANNOTATION)));
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
                onError.addContent(pathParamField(argument.annotation(HTTP_PATH_PARAM_ANNOTATION)));
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
}
