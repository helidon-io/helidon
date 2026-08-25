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

package io.helidon.declarative.codegen.messaging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.DeclarativeTypes;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceCodegenTypes;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

class MessagingExtension implements RegistryCodegenExtension {
    // Service codegen appends "__ServiceDescriptor" and javac may add nested-class suffixes.
    private static final int MAX_GENERATED_CLASS_NAME_UTF8_BYTES = 200;
    private static final TypeName GENERATOR = TypeName.create(MessagingExtension.class);

    private final RegistryCodegenContext ctx;
    private final Map<String, String> consumerTypeIdentities = new HashMap<>();

    MessagingExtension(RegistryCodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RegistryRoundContext roundContext) {
        Collection<TypedElementInfo> elements = roundContext.annotatedElements(MessagingTypes.RECEIVE_FROM);
        validateSendToAnnotations(roundContext);
        validateOnFailureAnnotations(roundContext);
        Map<ServiceChannel, TypedElementInfo> handlers = new LinkedHashMap<>();
        for (TypedElementInfo element : elements) {
            validateConsumerMethod(element);
            TypeName serviceType = enclosingType(element);
            TypeInfo typeInfo = roundContext.typeInfo(serviceType)
                    .orElseThrow(() -> new CodegenException("Could not obtain messaging consumer type " + serviceType,
                                                            element.originatingElementValue()));
            checkTypeIsService(roundContext, typeInfo);
            validateConcreteOwner(typeInfo, element);
            validateAccessibleType(roundContext,
                                   typeInfo.typeName(),
                                   typeInfo.typeName().packageName(),
                                   element.originatingElementValue(),
                                   "Messaging handler service type");
            validateDirectDeclaration(typeInfo, element);
            String channel = annotationName(element,
                                            MessagingTypes.RECEIVE_FROM,
                                            "@Messaging.ReceiveFrom channel");
            ServiceChannel serviceChannel = new ServiceChannel(serviceType, channel);
            TypedElementInfo previous = handlers.putIfAbsent(serviceChannel, element);
            if (previous != null) {
                throw new CodegenException("Service " + serviceType
                                                   + " declares multiple @Messaging.ReceiveFrom handlers for channel "
                                                   + channel,
                                           element.originatingElementValue());
            }
            ConsumerMethod consumerMethod = consumerMethod(roundContext, element);
            validateConsumerTypes(roundContext, typeInfo, element, consumerMethod);
            generateConsumerRegistration(roundContext,
                                         typeInfo,
                                         element,
                                         channel,
                                         consumerMethod,
                                         declaredFailurePolicy(element, channel));
        }
    }

    private void generateConsumerRegistration(RegistryRoundContext roundContext,
                                              TypeInfo typeInfo,
                                              TypedElementInfo element,
                                              String channel,
                                              ConsumerMethod consumerMethod,
                                              Optional<FailurePolicyMetadata> failurePolicy) {
        TypeName payloadType = consumerMethod.payloadType();
        TypeName payloadMetadataType = payloadType.boxed();
        TypeName generatedType = TypeName.builder()
                .packageName(typeInfo.typeName().packageName())
                .className(consumerClassName(typeInfo, element))
                .build();
        boolean singleton = typeInfo.hasAnnotation(ServiceCodegenTypes.SERVICE_ANNOTATION_SINGLETON);
        TypeName consumerInjectionType = singleton ? typeInfo.typeName() : supplierType(typeInfo.typeName());

        ClassModel.Builder classModel = ClassModel.builder()
                .copyright(CodegenUtil.copyright(GENERATOR, typeInfo.typeName(), generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR, typeInfo.typeName(), generatedType, "1", ""))
                .addAnnotation(DeclarativeTypes.SUPPRESS_API)
                .type(generatedType)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .description("Messaging consumer registration for channel <code>" + escapeJavadoc(channel) + "</code>.")
                .addInterface(consumerMethod.processor()
                                      ? MessagingTypes.PROCESSOR_REGISTRATION
                                      : MessagingTypes.CONSUMER_REGISTRATION)
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_SINGLETON));

        classModel.addField(consumer -> consumer
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(consumerInjectionType)
                .name("consumer"));

        classModel.addField(handler -> handler
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(TypeName.builder(consumerMethod.explicitBatchHandler()
                                               ? MessagingTypes.MESSAGING_ENTRY_POINT_BATCH_HANDLER
                                               : MessagingTypes.MESSAGING_ENTRY_POINT_HANDLER)
                              .addTypeArgument(typeInfo.typeName())
                              .build())
                .name(consumerMethod.explicitBatchHandler() ? "batchHandler" : "handler"));

        classModel.addConstructor(ctr -> ctr
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addParameter(consumer -> consumer
                        .type(consumerInjectionType)
                        .name("consumer"))
                .addParameter(entryPoints -> entryPoints
                        .type(MessagingTypes.MESSAGING_ENTRY_POINTS)
                        .name("entryPoints"))
                .addContentLine("this.consumer = consumer;")
                .addContent("var descriptor = ")
                .addContent(ctx.descriptorType(typeInfo.typeName()))
                .addContentLine(".INSTANCE;")
                .addContent("this.")
                .addContent(consumerMethod.explicitBatchHandler() ? "batchHandler" : "handler")
                .addContent(" = entryPoints.")
                .addContent(consumerMethod.explicitBatchHandler() ? "batchHandler" : "handler")
                .addContentLine("(")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContentLine("descriptor,")
                .addContentLine("descriptor.qualifiers(),")
                .addContent(ctx.descriptorType(typeInfo.typeName()))
                .addContentLine(".ANNOTATIONS,")
                .addContent(ctx.descriptorType(typeInfo.typeName()))
                .addContent(".METHOD_")
                .addContent(CodegenUtil.toConstantName(ctx.uniqueName(typeInfo, element)))
                .addContentLine(",")
                .addContent("this::")
                .addContent(consumerMethod.explicitBatchHandler() ? "invokeBatch" : "invoke")
                .addContentLine(");")
                .decreaseContentPadding()
                .decreaseContentPadding());

        addLiteralMethod(classModel, "handlerId", handlerId(typeInfo, element));

        addLiteralMethod(classModel, "channel", channel);

        failurePolicy.ifPresent(policy -> addFailurePolicyMetadata(classModel, policy));

        addGenericTypeField(classModel, "PAYLOAD_GENERIC_TYPE", payloadMetadataType);
        addGenericTypeField(classModel, "ENVELOPE_GENERIC_TYPE", consumerMethod.envelopeType());

        classModel.addMethod(method -> method
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(TypeNames.CLASS_WILDCARD)
                .name("payloadType")
                .addContentLine("return PAYLOAD_GENERIC_TYPE.rawType();"));

        addGenericTypeMethod(classModel, "payloadGenericType", "PAYLOAD_GENERIC_TYPE");

        classModel.addMethod(method -> method
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(TypeNames.CLASS_WILDCARD)
                .name("envelopeType")
                .addContentLine("return ENVELOPE_GENERIC_TYPE.rawType();"));

        addGenericTypeMethod(classModel, "envelopeGenericType", "ENVELOPE_GENERIC_TYPE");

        if (consumerMethod.processor()) {
            addProcessorMetadata(classModel, consumerMethod);
            addProcessMethod(classModel, typeInfo, element, singleton);
        } else {
            addDispatchMethod(classModel, typeInfo, element, consumerMethod.explicitBatchHandler(), singleton);
        }
        addInvocationMethod(roundContext, classModel, typeInfo, element, consumerMethod);

        roundContext.addGeneratedType(generatedType,
                                      classModel,
                                      typeInfo.typeName(),
                                      element.originatingElementValue());
    }

    private void addLiteralMethod(ClassModel.Builder classModel, String name, String value) {
        classModel.addMethod(method -> method
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(TypeNames.STRING)
                .name(name)
                .addContent("return ")
                .addContentLiteral(value)
                .addContentLine(";"));
    }

    private void addFailurePolicyMetadata(ClassModel.Builder classModel, FailurePolicyMetadata policy) {
        classModel.addField(field -> {
            field.accessModifier(AccessModifier.PRIVATE)
                    .isStatic(true)
                    .isFinal(true)
                    .type(MessagingTypes.FAILURE_POLICY)
                    .name("DECLARED_FAILURE_POLICY")
                    .addContent(MessagingTypes.FAILURE_POLICY)
                    .addContentLine(".builder()")
                    .increaseContentPadding()
                    .addContent(".retryDelay(")
                    .addContent(Duration.class)
                    .addContent(".parse(")
                    .addContentLiteral(policy.retryDelay())
                    .addContentLine("))")
                    .addContent(".maxAttempts(")
                    .addContent(String.valueOf(policy.maxAttempts()))
                    .addContentLine(")")
                    .addContent(".onExhausted(")
                    .addContent(MessagingTypes.FAILURE_DISPOSITION)
                    .addContent(".")
                    .addContent(policy.onExhausted())
                    .addContentLine(")");
            policy.deadLetterChannel().ifPresent(channel -> field
                    .addContent(".deadLetterChannel(")
                    .addContentLiteral(channel)
                    .addContentLine(")"));
            field.addContent(".build()")
                    .decreaseContentPadding();
        });

        classModel.addMethod(method -> method
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(TypeName.builder(MessagingTypes.OPTIONAL)
                                    .addTypeArgument(MessagingTypes.FAILURE_POLICY)
                                    .build())
                .name("declaredFailurePolicy")
                .addContent("return ")
                .addContent(MessagingTypes.OPTIONAL)
                .addContentLine(".of(DECLARED_FAILURE_POLICY);"));
    }

    private void addDispatchMethod(ClassModel.Builder classModel,
                                   TypeInfo typeInfo,
                                   TypedElementInfo element,
                                   boolean explicitBatchHandler,
                                   boolean singleton) {
        if (explicitBatchHandler) {
            classModel.addMethod(dispatch -> dispatch
                    .addAnnotation(Annotations.OVERRIDE)
                    .accessModifier(AccessModifier.PUBLIC)
                    .returnType(TypeName.create(void.class))
                    .name("dispatch")
                    .addParameter(messages -> messages
                            .type(messageBatchWildcardType())
                            .name("messages"))
                    .update(method -> addBatchHandlerInvocation(method, typeInfo, element, singleton)));
            return;
        }

        classModel.addMethod(dispatch -> dispatch
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(TypeName.create(void.class))
                .name("dispatch")
                .addParameter(messages -> messages
                        .type(messageBatchWildcardType())
                        .name("messages"))
                .addContentLine("for (int index = 0; index < messages.size(); index++) {")
                .increaseContentPadding()
                .addContentLine("try {")
                .increaseContentPadding()
                .addContentLine("dispatchMessage(messages.get(index));")
                .decreaseContentPadding()
                .addContentLine("} catch (RuntimeException e) {")
                .increaseContentPadding()
                .addContent("throw ")
                .addContent(MessagingTypes.BATCH_DELIVERY_EXCEPTION)
                .addContent(".sequential(")
                .addContentLiteral("Messaging consumer " + handlerId(typeInfo, element))
                .addContentLine(", messages, index, e);")
                .decreaseContentPadding()
                .addContentLine("}")
                .decreaseContentPadding()
                .addContentLine("}"));

        classModel.addMethod(dispatch -> dispatch
                .accessModifier(AccessModifier.PRIVATE)
                .returnType(TypeName.create(void.class))
                .name("dispatchMessage")
                .addParameter(message -> message
                        .type(messageWildcardType())
                        .name("message"))
                .update(method -> addHandlerInvocation(method, typeInfo, element, singleton)));
    }

    private void addProcessMethod(ClassModel.Builder classModel,
                                  TypeInfo typeInfo,
                                  TypedElementInfo element,
                                  boolean singleton) {
        classModel.addMethod(process -> process
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(messageBatchWildcardType())
                .name("process")
                .addParameter(messages -> messages
                        .type(messageBatchWildcardType())
                        .name("messages"))
                .addContent("var processedMessages = new ")
                .addContent(MessagingTypes.ARRAY_LIST)
                .addContent("<")
                .addContent(messageWildcardType())
                .addContentLine(">(messages.size());")
                .addContentLine("for (int index = 0; index < messages.size(); index++) {")
                .increaseContentPadding()
                .addContentLine("try {")
                .increaseContentPadding()
                .addContentLine("processedMessages.add(processMessage(messages.get(index)));")
                .decreaseContentPadding()
                .addContentLine("} catch (RuntimeException e) {")
                .increaseContentPadding()
                .addContent("throw ")
                .addContent(MessagingTypes.BATCH_DELIVERY_EXCEPTION)
                .addContent(".attemptedPrefix(")
                .addContentLiteral("Messaging processor " + handlerId(typeInfo, element))
                .addContentLine(", messages, index, e);")
                .decreaseContentPadding()
                .addContentLine("}")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("return messages.derive(processedMessages);"));

        classModel.addMethod(process -> process
                .accessModifier(AccessModifier.PRIVATE)
                .returnType(messageWildcardType())
                .name("processMessage")
                .addParameter(message -> message
                        .type(messageWildcardType())
                        .name("message"))
                .update(method -> addHandlerInvocation(method, typeInfo, element, singleton)));
    }

    private void addHandlerInvocation(Method.Builder method,
                                      TypeInfo typeInfo,
                                      TypedElementInfo element,
                                      boolean singleton) {
        String description = handlerId(typeInfo, element);
        method.addContentLine("try {")
                .increaseContentPadding();
        if (element.hasAnnotation(MessagingTypes.SEND_TO)) {
            method.addContent("return handler.handle(")
                    .addContent(singleton ? "consumer" : "consumer.get()")
                    .addContentLine(", message)")
                    .increaseContentPadding()
                    .addContent(".orElseThrow(() -> new ")
                    .addContent(MessagingTypes.MESSAGING_EXCEPTION)
                    .addContent("(")
                    .addContentLiteral("Messaging processor " + description + " did not return a message")
                    .addContentLine("));")
                    .decreaseContentPadding();
        } else {
            method.addContent("handler.handle(")
                    .addContent(singleton ? "consumer" : "consumer.get()")
                    .addContentLine(", message);");
        }
        method.decreaseContentPadding()
                .addContentLine("} catch (RuntimeException | Error e) {")
                .increaseContentPadding()
                .addContentLine("throw e;")
                .decreaseContentPadding()
                .addContentLine("} catch (Exception e) {")
                .increaseContentPadding()
                .addContent("throw new ")
                .addContent(MessagingTypes.MESSAGING_EXCEPTION)
                .addContent("(")
                .addContentLiteral("Messaging handler " + description + " failed")
                .addContentLine(", e);")
                .decreaseContentPadding()
                .addContentLine("}");
    }

    private void addBatchHandlerInvocation(Method.Builder method,
                                           TypeInfo typeInfo,
                                           TypedElementInfo element,
                                           boolean singleton) {
        String description = handlerId(typeInfo, element);
        method.addContentLine("try {")
                .increaseContentPadding()
                .addContent("batchHandler.handle(")
                .addContent(singleton ? "consumer" : "consumer.get()")
                .addContentLine(", messages);")
                .decreaseContentPadding()
                .addContentLine("} catch (RuntimeException | Error e) {")
                .increaseContentPadding()
                .addContentLine("throw e;")
                .decreaseContentPadding()
                .addContentLine("} catch (Exception e) {")
                .increaseContentPadding()
                .addContent("throw new ")
                .addContent(MessagingTypes.MESSAGING_EXCEPTION)
                .addContent("(")
                .addContentLiteral("Messaging batch handler " + description + " failed")
                .addContentLine(", e);")
                .decreaseContentPadding()
                .addContentLine("}");
    }

    private void addProcessorMetadata(ClassModel.Builder classModel, ConsumerMethod consumerMethod) {
        addLiteralMethod(classModel, "outgoingChannel", consumerMethod.outgoingChannel());
        addGenericTypeField(classModel,
                            "OUTGOING_PAYLOAD_GENERIC_TYPE",
                            consumerMethod.outgoingPayloadType().boxed());
        addGenericTypeMethod(classModel,
                             "outgoingPayloadGenericType",
                             "OUTGOING_PAYLOAD_GENERIC_TYPE");
        addGenericTypeField(classModel,
                            "OUTGOING_ENVELOPE_GENERIC_TYPE",
                            consumerMethod.outgoingEnvelopeType());
        addGenericTypeMethod(classModel,
                             "outgoingEnvelopeGenericType",
                             "OUTGOING_ENVELOPE_GENERIC_TYPE");
    }

    private void addGenericTypeField(ClassModel.Builder classModel, String fieldName, TypeName type) {
        classModel.addField(field -> field
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .isFinal(true)
                .type(genericType(type))
                .name(fieldName)
                .addContent("new ")
                .addContent(genericType(type))
                .addContent("() { }"));
    }

    private void addGenericTypeMethod(ClassModel.Builder classModel, String methodName, String fieldName) {
        classModel.addMethod(method -> method
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(genericTypeWildcard())
                .name(methodName)
                .addContent("return ")
                .addContent(fieldName)
                .addContentLine(";"));
    }

    private void addInvocationMethod(RegistryRoundContext roundContext,
                                     ClassModel.Builder classModel,
                                     TypeInfo typeInfo,
                                     TypedElementInfo element,
                                     ConsumerMethod consumerMethod) {
        if (consumerMethod.explicitBatchHandler()) {
            classModel.addMethod(invoke -> invoke
                    .addAnnotation(Annotation.create(SuppressWarnings.class, "unchecked"))
                    .accessModifier(AccessModifier.PRIVATE)
                    .returnType(TypeNames.PRIMITIVE_VOID)
                    .name("invokeBatch")
                    .addThrows(thrown -> thrown.type(Exception.class))
                    .addParameter(consumer -> consumer
                            .type(typeInfo.typeName())
                            .name("consumerInstance"))
                    .addParameter(messages -> messages
                            .type(messageBatchWildcardType())
                            .name("messages"))
                    .addContent("var typedMessages = (")
                    .addContent(messageBatchType(consumerMethod.payloadType()))
                    .addContentLine(") messages;")
                    .addContentLine("try {")
                    .increaseContentPadding()
                    .addContent("consumerInstance.")
                    .addContent(element.elementName())
                    .addContentLine("(typedMessages);")
                    .update(method -> addInvocationThrowableBoundary(method, handlerId(typeInfo, element))));
            return;
        }

        Method.Builder invoke = Method.builder()
                .accessModifier(AccessModifier.PRIVATE)
                .returnType(optionalMessageWildcardType())
                .name("invoke")
                .addThrows(thrown -> thrown.type(Exception.class))
                .addParameter(consumer -> consumer
                        .type(typeInfo.typeName())
                        .name("consumerInstance"))
                .addParameter(message -> message
                        .type(messageWildcardType())
                        .name("message"));

        invoke.addAnnotation(Annotation.create(SuppressWarnings.class, "unchecked"))
                .addContent("var typedMessage = (")
                .addContent(consumerMethod.envelopeType())
                .addContentLine(") message;")
                .addContentLine("try {")
                .increaseContentPadding();

        if (consumerMethod.processor()) {
            invoke.addContent("var result = ")
                    .addContent(MessagingTypes.OBJECTS)
                    .addContentLine(".requireNonNull(")
                    .increaseContentPadding()
                    .increaseContentPadding();
        }
        invoke.addContent("consumerInstance.")
                .addContent(element.elementName())
                .addContent("(");

        for (int i = 0; i < element.parameterArguments().size(); i++) {
            if (i > 0) {
                invoke.addContent(", ");
            }
            addParameterDispatch(roundContext,
                                 invoke,
                                 element.parameterArguments().get(i),
                                 element.parameterArguments().size() == 1,
                                 handlerId(typeInfo, element));
        }

        if (consumerMethod.processor()) {
            invoke.addContentLine("),")
                    .addContentLiteral("Messaging processor " + handlerId(typeInfo, element) + " returned null")
                    .addContentLine(");")
                    .decreaseContentPadding()
                    .decreaseContentPadding()
                    .addContent("return ")
                    .addContent(MessagingTypes.OPTIONAL)
                    .addContent(".of(");
            if (consumerMethod.returnsEnvelope()) {
                invoke.addContent("result");
            } else {
                invoke.addContent(MessagingTypes.MESSAGE)
                        .addContent(".create(result)");
            }
            invoke.addContentLine(");");
        } else {
            invoke.addContentLine(");")
                    .addContent("return ")
                    .addContent(MessagingTypes.OPTIONAL)
                    .addContentLine(".empty();");
        }
        addInvocationThrowableBoundary(invoke, handlerId(typeInfo, element));
        classModel.addMethod(invoke);
    }

    private void addInvocationThrowableBoundary(Method.Builder method, String handlerId) {
        method.decreaseContentPadding()
                .addContentLine("} catch (Exception e) {")
                .increaseContentPadding()
                .addContentLine("throw e;")
                .decreaseContentPadding()
                .addContentLine("} catch (Error e) {")
                .increaseContentPadding()
                .addContentLine("throw e;")
                .decreaseContentPadding()
                .addContentLine("} catch (Throwable t) {")
                .increaseContentPadding()
                .addContent("throw new ")
                .addContent(MessagingTypes.MESSAGING_EXCEPTION)
                .addContent("(")
                .addContentLiteral("Messaging handler " + handlerId + " threw a checked Throwable outside Exception")
                .addContentLine(", t);")
                .decreaseContentPadding()
                .addContentLine("}");
    }

    private void addParameterDispatch(RegistryRoundContext roundContext,
                                      Method.Builder dispatch,
                                      TypedElementInfo argument,
                                      boolean singleParameter,
                                      String handlerId) {
        if (argument.hasAnnotation(MessagingTypes.HEADER_PARAM)) {
            String headerName = annotationName(argument,
                                               MessagingTypes.HEADER_PARAM,
                                               "@Messaging.HeaderParam name");
            dispatch.addContent("typedMessage.header(")
                    .addContentLiteral(headerName);
            if (argument.typeName().equals(optionalStringType())) {
                dispatch.addContent(")");
            } else {
                dispatch.addContent(").orElseThrow(() -> new ")
                        .addContent(MessagingTypes.MESSAGING_EXCEPTION)
                        .addContent("(")
                        .addContentLiteral("Missing required messaging header " + headerName
                                                   + " for handler " + handlerId)
                        .addContent("))");
            }
            return;
        }

        MessageType messageType = argument.hasAnnotation(MessagingTypes.ENTITY)
                ? null
                : messageType(roundContext, argument.typeName(), argument.originatingElementValue());
        if (argument.hasAnnotation(MessagingTypes.ENTITY)
                || (singleParameter && messageType == null)) {
            if (argument.typeName().primitive() && !argument.typeName().array()) {
                dispatch.addContent("(")
                        .addContent(argument.typeName())
                        .addContent(") ");
            }
            dispatch.addContent("typedMessage.entity()");
            return;
        }

        if (messageType != null) {
            dispatch.addContent("typedMessage");
            return;
        }

        throw new CodegenException("Unsupported @Messaging.ReceiveFrom parameter " + argument.toDeclaration()
                                           + ". Use @Messaging.Entity, @Messaging.HeaderParam, or Message<T>.",
                                   argument.originatingElementValue());
    }

    private ConsumerMethod consumerMethod(RegistryRoundContext roundContext, TypedElementInfo element) {
        if (element.parameterArguments().isEmpty()) {
            throw new CodegenException("@Messaging.ReceiveFrom methods must declare exactly one primary message view",
                                       element.originatingElementValue());
        }

        if (element.parameterArguments().size() == 1) {
            TypedElementInfo argument = element.parameterArguments().getFirst();
            rejectLegacyListBatch(roundContext, argument);
            BatchType batchType = batchType(roundContext, argument);
            if (batchType != null) {
                if (argument.hasAnnotation(MessagingTypes.HEADER_PARAM)
                        || argument.hasAnnotation(MessagingTypes.ENTITY)) {
                    throw new CodegenException("MessageBatch<T> consumer parameters cannot use messaging annotations",
                                               argument.originatingElementValue());
                }
                if (element.hasAnnotation(MessagingTypes.SEND_TO)) {
                    throw new CodegenException("Batch @Messaging.ReceiveFrom methods cannot declare @Messaging.SendTo",
                                               element.originatingElementValue());
                }
                validateTerminalReturn(element);
                return ConsumerMethod.terminalBatch(batchType.payloadType(),
                                                    messageType(batchType.payloadType()));
            }
        } else {
            for (TypedElementInfo argument : element.parameterArguments()) {
                rejectLegacyListBatch(roundContext, argument);
                if (batchType(roundContext, argument) != null) {
                    throw new CodegenException("MessageBatch<T> consumers must declare exactly one parameter",
                                               argument.originatingElementValue());
                }
            }
        }

        List<MessageType> primaryViews = new java.util.ArrayList<>();
        Set<String> headerNames = new LinkedHashSet<>();
        for (TypedElementInfo argument : element.parameterArguments()) {
            boolean entity = argument.hasAnnotation(MessagingTypes.ENTITY);
            boolean header = argument.hasAnnotation(MessagingTypes.HEADER_PARAM);
            if (entity && header) {
                throw new CodegenException("A messaging parameter cannot be both @Messaging.Entity and "
                                                   + "@Messaging.HeaderParam",
                                           argument.originatingElementValue());
            }
            if (header) {
                validateHeaderParameter(argument, headerNames);
                continue;
            }

            MessageType declaredMessage = entity
                    ? null
                    : messageType(roundContext,
                                  argument.typeName(),
                                  argument.originatingElementValue());
            if (entity) {
                TypeName payloadType = concreteType(roundContext,
                                                    argument.typeName().boxed(),
                                                    argument.originatingElementValue(),
                                                    "Messaging payload parameter");
                primaryViews.add(new MessageType(messageType(payloadType), payloadType));
            } else if (declaredMessage != null) {
                primaryViews.add(declaredMessage);
            } else if (element.parameterArguments().size() == 1) {
                TypeName payloadType = concreteType(roundContext,
                                                    argument.typeName().boxed(),
                                                    argument.originatingElementValue(),
                                                    "Messaging payload parameter");
                primaryViews.add(new MessageType(messageType(payloadType), payloadType));
            } else {
                throw new CodegenException("Unsupported unannotated @Messaging.ReceiveFrom parameter "
                                                   + argument.toDeclaration()
                                                   + ". Multi-parameter handlers require @Messaging.Entity, "
                                                   + "@Messaging.HeaderParam, or Message<T>.",
                                           argument.originatingElementValue());
            }
        }

        if (primaryViews.size() != 1) {
            throw new CodegenException("@Messaging.ReceiveFrom methods must declare exactly one primary message view; found "
                                               + primaryViews.size(),
                                       element.originatingElementValue());
        }

        MessageType primary = primaryViews.getFirst();
        if (!element.hasAnnotation(MessagingTypes.SEND_TO)) {
            validateTerminalReturn(element);
            return ConsumerMethod.terminal(primary.payloadType(), primary.envelopeType());
        }

        String outgoingChannel = annotationName(element,
                                                MessagingTypes.SEND_TO,
                                                "@Messaging.SendTo channel");
        if (element.typeName().equals(TypeNames.PRIMITIVE_VOID)) {
            throw new CodegenException("@Messaging.SendTo processors must return a payload or Message<T>",
                                       element.originatingElementValue());
        }
        rejectAsyncReturn(roundContext, element);
        MessageType returnedMessage = messageType(roundContext,
                                                  element.typeName(),
                                                  element.originatingElementValue());
        if (returnedMessage != null) {
            return ConsumerMethod.processor(primary.payloadType(),
                                            primary.envelopeType(),
                                            outgoingChannel,
                                            returnedMessage.payloadType(),
                                            returnedMessage.envelopeType(),
                                            true);
        }
        TypeName outgoingPayload = concreteType(roundContext,
                                                element.typeName().boxed(),
                                                element.originatingElementValue(),
                                                "Messaging processor return type");
        return ConsumerMethod.processor(primary.payloadType(),
                                        primary.envelopeType(),
                                        outgoingChannel,
                                        outgoingPayload,
                                        messageType(outgoingPayload),
                                        false);
    }

    private void validateTerminalReturn(TypedElementInfo element) {
        if (!element.typeName().equals(TypeNames.PRIMITIVE_VOID)) {
            throw new CodegenException("Terminal @Messaging.ReceiveFrom methods must return void; add "
                                               + "@Messaging.SendTo for a processor return value",
                                       element.originatingElementValue());
        }
    }

    private void rejectAsyncReturn(RegistryRoundContext roundContext, TypedElementInfo element) {
        if (isAsyncReturnType(roundContext, element.typeName(), new HashSet<>())) {
            throw new CodegenException("Asynchronous or publisher @Messaging.ReceiveFrom return types are not supported: "
                                               + element.typeName(),
                                       element.originatingElementValue());
        }
    }

    private boolean isAsyncReturnType(RegistryRoundContext roundContext,
                                      TypeName typeName,
                                      Set<String> visited) {
        TypeName rawType = typeName.genericTypeName();
        if (!visited.add(rawType.fqName())) {
            return false;
        }
        if (MessagingTypes.ASYNC_RETURN_TYPES.contains(rawType.fqName())) {
            return true;
        }
        TypeInfo typeInfo = roundContext.typeInfo(rawType).orElse(null);
        if (typeInfo == null) {
            return false;
        }
        if (typeInfo.interfaceTypeInfo()
                .stream()
                .anyMatch(it -> isAsyncReturnType(roundContext, it.typeName(), visited))) {
            return true;
        }
        return typeInfo.superTypeInfo()
                .map(it -> isAsyncReturnType(roundContext, it.typeName(), visited))
                .orElse(false);
    }

    private void validateHeaderParameter(TypedElementInfo argument, Set<String> headerNames) {
        String headerName = annotationName(argument,
                                           MessagingTypes.HEADER_PARAM,
                                           "@Messaging.HeaderParam name");
        if (!headerNames.add(headerName)) {
            throw new CodegenException("Duplicate @Messaging.HeaderParam name " + headerName,
                                       argument.originatingElementValue());
        }
        if (!argument.typeName().equals(TypeNames.STRING)
                && !argument.typeName().equals(optionalStringType())) {
            throw new CodegenException("@Messaging.HeaderParam parameters must be String or Optional<String>; "
                                               + "automatic header conversion is not supported",
                                       argument.originatingElementValue());
        }
    }

    private void rejectLegacyListBatch(RegistryRoundContext roundContext, TypedElementInfo argument) {
        TypeName typeName = argument.typeName();
        if (!typeName.genericTypeName().equals(MessagingTypes.LIST)) {
            return;
        }
        if (typeName.typeArguments().isEmpty()) {
            return;
        }
        MessageType messageType = messageType(roundContext,
                                              typeName.typeArguments().getFirst(),
                                              argument.originatingElementValue());
        if (messageType != null) {
            throw new CodegenException("List<Message<T>> batch consumers are not supported; use MessageBatch<T>",
                                       argument.originatingElementValue());
        }
    }

    private BatchType batchType(RegistryRoundContext roundContext, TypedElementInfo argument) {
        TypeName envelopeType = argument.typeName();
        if (!envelopeType.genericTypeName().equals(MessagingTypes.MESSAGE_BATCH)) {
            return null;
        }
        if (envelopeType.typeArguments().isEmpty()) {
            throw new CodegenException("MessageBatch consumer parameters must declare a payload type",
                                       argument.originatingElementValue());
        }
        TypeName payloadType = envelopeType.typeArguments().getFirst();
        if (!isConcretePayloadType(payloadType)) {
            throw new CodegenException("MessageBatch consumer parameter " + envelopeType
                                               + " resolves to non-concrete payload type " + payloadType,
                                       argument.originatingElementValue());
        }
        return new BatchType(concreteType(roundContext,
                                          payloadType,
                                          argument.originatingElementValue(),
                                          "MessageBatch payload type"));
    }

    private MessageType messageType(RegistryRoundContext roundContext, TypeName envelopeType, Object origin) {
        TypeInfo typeInfo = roundContext.typeInfo(envelopeType.genericTypeName()).orElse(null);
        if (typeInfo == null) {
            return null;
        }

        TypeName resolvedMessageType = resolveSuperType(typeInfo,
                                                        envelopeType,
                                                        MessagingTypes.MESSAGE,
                                                        new HashSet<>());
        if (resolvedMessageType == null) {
            return null;
        }
        if (resolvedMessageType.typeArguments().isEmpty()) {
            throw new CodegenException("Message consumer parameters must declare a payload type", origin);
        }

        TypeName payloadType = resolvedMessageType.typeArguments().getFirst();
        if (!isConcretePayloadType(payloadType)) {
            throw new CodegenException("Message consumer parameter " + envelopeType
                                               + " resolves to non-concrete payload type " + payloadType,
                                       origin);
        }
        if (!isConcretePayloadType(envelopeType)) {
            throw new CodegenException("Message envelope type must not use wildcards or unresolved type variables: "
                                               + envelopeType,
                                       origin);
        }
        concreteType(roundContext, envelopeType, origin, "Message envelope type");
        return new MessageType(envelopeType,
                               concreteType(roundContext, payloadType, origin, "Message payload type"));
    }

    private TypeName resolveSuperType(TypeInfo typeInfo,
                                      TypeName typeUsage,
                                      TypeName targetType,
                                      Set<String> visited) {
        String visitKey = typeInfo.rawType().fqName() + "|" + typeUsage.resolvedName();
        if (!visited.add(visitKey)) {
            return null;
        }
        if (typeInfo.rawType().equals(targetType)) {
            return typeUsage;
        }

        Map<String, TypeName> bindings = typeBindings(typeInfo.declaredType(), typeUsage);
        for (TypeInfo interfaceInfo : typeInfo.interfaceTypeInfo()) {
            TypeName interfaceUsage = resolveType(interfaceInfo.typeName(), bindings);
            TypeName resolved = resolveSuperType(interfaceInfo, interfaceUsage, targetType, visited);
            if (resolved != null) {
                return resolved;
            }
        }
        if (typeInfo.superTypeInfo().isPresent()) {
            TypeInfo superTypeInfo = typeInfo.superTypeInfo().get();
            TypeName superTypeUsage = resolveType(superTypeInfo.typeName(), bindings);
            return resolveSuperType(superTypeInfo, superTypeUsage, targetType, visited);
        }
        return null;
    }

    private Map<String, TypeName> typeBindings(TypeName declaredType, TypeName typeUsage) {
        Map<String, TypeName> bindings = new HashMap<>();
        int typeArgumentCount = Math.min(declaredType.typeArguments().size(), typeUsage.typeArguments().size());
        for (int i = 0; i < typeArgumentCount; i++) {
            TypeName declaredArgument = declaredType.typeArguments().get(i);
            if (declaredArgument.generic() && !declaredArgument.wildcard()) {
                bindings.put(declaredArgument.className(), typeUsage.typeArguments().get(i));
            }
        }
        typeArgumentCount = Math.min(declaredType.typeParameters().size(), typeUsage.typeArguments().size());
        for (int i = 0; i < typeArgumentCount; i++) {
            bindings.putIfAbsent(declaredType.typeParameters().get(i), typeUsage.typeArguments().get(i));
        }
        return bindings;
    }

    private TypeName resolveType(TypeName typeName, Map<String, TypeName> bindings) {
        if (typeName.array()) {
            TypeName componentType = typeName.componentType().orElseThrow();
            TypeName resolvedComponentType = resolveType(componentType, bindings);
            if (resolvedComponentType.equals(componentType)) {
                return typeName;
            }
            return TypeName.builder(resolvedComponentType)
                    .componentType(resolvedComponentType)
                    .array(true)
                    .vararg(typeName.vararg())
                    .build();
        }
        if (typeName.generic() && !typeName.wildcard()) {
            TypeName resolved = bindings.get(typeName.className());
            if (resolved != null) {
                return resolved;
            }
        }
        if (typeName.typeArguments().isEmpty()) {
            return typeName;
        }

        var resolvedArguments = typeName.typeArguments()
                .stream()
                .map(it -> resolveType(it, bindings))
                .toList();
        if (resolvedArguments.equals(typeName.typeArguments())) {
            return typeName;
        }
        return TypeName.builder(typeName)
                .typeArguments(resolvedArguments)
                .build();
    }

    private TypeName concreteType(RegistryRoundContext roundContext,
                                  TypeName typeName,
                                  Object origin,
                                  String description) {
        if (!isConcretePayloadType(typeName)) {
            throw new CodegenException(description + " must not use wildcards or unresolved type variables: " + typeName,
                                       origin);
        }
        if (hasRawGenericUsage(roundContext, typeName, new HashSet<>())) {
            throw new CodegenException(description + " must not use a raw generic type: " + typeName, origin);
        }
        return typeName;
    }

    static void validateAccessibleType(RegistryRoundContext roundContext,
                                       TypeName typeName,
                                       String generatedPackage,
                                       Object origin,
                                       String description) {
        validateAccessibleType(roundContext,
                               typeName,
                               generatedPackage,
                               origin,
                               description,
                               new HashSet<>());
    }

    private static void validateAccessibleType(RegistryRoundContext roundContext,
                                               TypeName typeName,
                                               String generatedPackage,
                                               Object origin,
                                               String description,
                                               Set<String> visited) {
        if (!visited.add(typeName.resolvedName())) {
            return;
        }
        typeName.componentType()
                .ifPresent(component -> validateAccessibleType(roundContext,
                                                               component,
                                                               generatedPackage,
                                                               origin,
                                                               description,
                                                               visited));

        TypeName rawType = typeName.genericTypeName();
        validateEnclosingTypeAccess(roundContext, rawType, generatedPackage, origin, description);
        typeName.typeArguments()
                .forEach(argument -> validateAccessibleType(roundContext,
                                                            argument,
                                                            generatedPackage,
                                                            origin,
                                                            description,
                                                            visited));
        typeName.lowerBounds()
                .forEach(bound -> validateAccessibleType(roundContext,
                                                         bound,
                                                         generatedPackage,
                                                         origin,
                                                         description,
                                                         visited));
        typeName.upperBounds()
                .forEach(bound -> validateAccessibleType(roundContext,
                                                         bound,
                                                         generatedPackage,
                                                         origin,
                                                         description,
                                                         visited));
    }

    private static void validateEnclosingTypeAccess(RegistryRoundContext roundContext,
                                                    TypeName typeName,
                                                    String generatedPackage,
                                                    Object origin,
                                                    String description) {
        List<String> enclosingNames = typeName.enclosingNames();
        for (int i = 0; i < enclosingNames.size(); i++) {
            TypeName enclosingType = TypeName.builder()
                    .packageName(typeName.packageName())
                    .enclosingNames(enclosingNames.subList(0, i))
                    .className(enclosingNames.get(i))
                    .build();
            validateTypeAccess(roundContext, enclosingType, generatedPackage, origin, description);
        }
        validateTypeAccess(roundContext, typeName, generatedPackage, origin, description);
    }

    private static void validateTypeAccess(RegistryRoundContext roundContext,
                                           TypeName typeName,
                                           String generatedPackage,
                                           Object origin,
                                           String description) {
        TypeInfo typeInfo = roundContext.typeInfo(typeName).orElse(null);
        if (typeInfo == null) {
            return;
        }
        AccessModifier access = typeInfo.accessModifier();
        if (access != AccessModifier.PUBLIC
                && (access == AccessModifier.PRIVATE || !typeName.packageName().equals(generatedPackage))) {
            throw new CodegenException(description + " references type " + typeName.fqName()
                                               + " that is not accessible from generated messaging code in package "
                                               + generatedPackage,
                                       origin);
        }
    }

    private void validateConsumerTypes(RegistryRoundContext roundContext,
                                       TypeInfo typeInfo,
                                       TypedElementInfo element,
                                       ConsumerMethod consumerMethod) {
        String generatedPackage = typeInfo.typeName().packageName();
        validateAccessibleType(roundContext,
                               consumerMethod.payloadType(),
                               generatedPackage,
                               element.originatingElementValue(),
                               "Messaging handler payload type");
        validateAccessibleType(roundContext,
                               consumerMethod.envelopeType(),
                               generatedPackage,
                               element.originatingElementValue(),
                               "Messaging handler envelope type");
        if (consumerMethod.processor()) {
            validateAccessibleType(roundContext,
                                   consumerMethod.outgoingPayloadType(),
                                   generatedPackage,
                                   element.originatingElementValue(),
                                   "Messaging processor return payload type");
            validateAccessibleType(roundContext,
                                   consumerMethod.outgoingEnvelopeType(),
                                   generatedPackage,
                                   element.originatingElementValue(),
                                   "Messaging processor return envelope type");
        }
    }

    private boolean hasRawGenericUsage(RegistryRoundContext roundContext,
                                       TypeName typeName,
                                       Set<String> visited) {
        String visitKey = typeName.resolvedName();
        if (!visited.add(visitKey)) {
            return false;
        }
        if (typeName.typeArguments().isEmpty()) {
            TypeInfo typeInfo = roundContext.typeInfo(typeName.genericTypeName()).orElse(null);
            if (typeInfo != null
                    && (!typeInfo.declaredType().typeArguments().isEmpty()
                    || !typeInfo.declaredType().typeParameters().isEmpty())) {
                return true;
            }
        }
        return typeName.typeArguments().stream().anyMatch(it -> hasRawGenericUsage(roundContext, it, visited))
                || typeName.lowerBounds().stream().anyMatch(it -> hasRawGenericUsage(roundContext, it, visited))
                || typeName.upperBounds().stream().anyMatch(it -> hasRawGenericUsage(roundContext, it, visited));
    }

    static boolean isConcretePayloadType(TypeName typeName) {
        if (typeName.wildcard()
                || isUnresolvedTypeVariable(typeName)) {
            return false;
        }
        return typeName.typeArguments().stream().allMatch(MessagingExtension::isConcretePayloadType)
                && typeName.lowerBounds().stream().allMatch(MessagingExtension::isConcretePayloadType)
                && typeName.upperBounds().stream().allMatch(MessagingExtension::isConcretePayloadType);
    }

    static String escapeJavadoc(String value) {
        return value.replace("&", "&amp;")
                .replace("\\", "&#92;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("{", "&#123;")
                .replace("}", "&#125;")
                .replace("*/", "*&#47;");
    }

    static boolean hasUnresolvedTypeVariable(TypeName typeName) {
        return isUnresolvedTypeVariable(typeName)
                || typeName.typeArguments().stream().anyMatch(MessagingExtension::hasUnresolvedTypeVariable)
                || typeName.lowerBounds().stream().anyMatch(MessagingExtension::hasUnresolvedTypeVariable)
                || typeName.upperBounds().stream().anyMatch(MessagingExtension::hasUnresolvedTypeVariable);
    }

    private static boolean isUnresolvedTypeVariable(TypeName typeName) {
        return !typeName.wildcard()
                && typeName.generic()
                && typeName.typeArguments().isEmpty()
                && typeName.packageName().isEmpty();
    }

    private TypeName messageWildcardType() {
        return TypeName.builder()
                .from(MessagingTypes.MESSAGE)
                .addTypeArgument(TypeNames.WILDCARD)
                .build();
    }

    private TypeName optionalMessageWildcardType() {
        return TypeName.builder(MessagingTypes.OPTIONAL)
                .addTypeArgument(messageWildcardType())
                .build();
    }

    private TypeName optionalStringType() {
        return TypeName.builder(MessagingTypes.OPTIONAL)
                .addTypeArgument(TypeNames.STRING)
                .build();
    }

    private TypeName supplierType(TypeName suppliedType) {
        return TypeName.builder(TypeNames.SUPPLIER)
                .addTypeArgument(suppliedType)
                .build();
    }

    private TypeName genericType(TypeName typeArgument) {
        return TypeName.builder()
                .from(MessagingTypes.GENERIC_TYPE)
                .addTypeArgument(typeArgument)
                .build();
    }

    private TypeName genericTypeWildcard() {
        return genericType(TypeNames.WILDCARD);
    }

    private TypeName messageType(TypeName payloadType) {
        return TypeName.builder()
                .from(MessagingTypes.MESSAGE)
                .addTypeArgument(payloadType)
                .build();
    }

    private TypeName messageBatchWildcardType() {
        return TypeName.builder(MessagingTypes.MESSAGE_BATCH)
                .addTypeArgument(TypeNames.WILDCARD)
                .build();
    }

    private TypeName messageBatchType(TypeName payloadType) {
        return TypeName.builder(MessagingTypes.MESSAGE_BATCH)
                .addTypeArgument(payloadType)
                .build();
    }

    private void validateConsumerMethod(TypedElementInfo element) {
        if (element.kind() != ElementKind.METHOD) {
            throw new CodegenException("@Messaging.ReceiveFrom is only allowed on methods",
                                       element.originatingElementValue());
        }
        if (element.accessModifier() == AccessModifier.PRIVATE) {
            throw new CodegenException("@Messaging.ReceiveFrom is only allowed on non-private methods",
                                       element.originatingElementValue());
        }
        if (element.elementModifiers().contains(Modifier.STATIC)) {
            throw new CodegenException("@Messaging.ReceiveFrom is only allowed on instance methods",
                                       element.originatingElementValue());
        }
        if (element.elementModifiers().contains(Modifier.ABSTRACT)) {
            throw new CodegenException("@Messaging.ReceiveFrom is not allowed on abstract methods",
                                       element.originatingElementValue());
        }
        if (element.elementModifiers().contains(Modifier.NATIVE)) {
            throw new CodegenException("@Messaging.ReceiveFrom is not allowed on native methods",
                                       element.originatingElementValue());
        }
    }

    private void checkTypeIsService(RegistryRoundContext roundContext, TypeInfo typeInfo) {
        if (roundContext.generatedType(ctx.descriptorType(typeInfo.typeName())).isEmpty()) {
            throw new CodegenException("@Messaging.ReceiveFrom declaring type must be a Service Registry service",
                                       typeInfo.originatingElementValue());
        }
    }

    private void validateConcreteOwner(TypeInfo typeInfo, TypedElementInfo element) {
        if ((typeInfo.kind() != ElementKind.CLASS && typeInfo.kind() != ElementKind.RECORD)
                || typeInfo.elementModifiers().contains(Modifier.ABSTRACT)) {
            throw new CodegenException("@Messaging.ReceiveFrom declaring type must be a concrete class or record",
                                       element.originatingElementValue());
        }
    }

    private void validateDirectDeclaration(TypeInfo typeInfo, TypedElementInfo element) {
        boolean declared = typeInfo.elementInfo()
                .stream()
                .anyMatch(candidate -> candidate.kind() == ElementKind.METHOD
                        && candidate.signature().equals(element.signature()));
        if (!declared) {
            throw new CodegenException("Inherited @Messaging.ReceiveFrom methods are not registered; override and annotate "
                                               + "the method directly on " + typeInfo.typeName(),
                                       element.originatingElementValue());
        }
    }

    private void validateSendToAnnotations(RegistryRoundContext roundContext) {
        for (TypedElementInfo element : roundContext.annotatedElements(MessagingTypes.SEND_TO)) {
            if (element.kind() != ElementKind.METHOD || !element.hasAnnotation(MessagingTypes.RECEIVE_FROM)) {
                throw new CodegenException("@Messaging.SendTo is only allowed on @Messaging.ReceiveFrom methods",
                                           element.originatingElementValue());
            }
        }
    }

    private void validateOnFailureAnnotations(RegistryRoundContext roundContext) {
        for (TypedElementInfo element : roundContext.annotatedElements(MessagingTypes.ON_FAILURE)) {
            if (element.kind() != ElementKind.METHOD || !element.hasAnnotation(MessagingTypes.RECEIVE_FROM)) {
                throw new CodegenException("@Messaging.OnFailure is only allowed on @Messaging.ReceiveFrom methods",
                                           element.originatingElementValue());
            }
        }
    }

    private Optional<FailurePolicyMetadata> declaredFailurePolicy(TypedElementInfo element, String sourceChannel) {
        if (!element.hasAnnotation(MessagingTypes.ON_FAILURE)) {
            return Optional.empty();
        }

        Annotation annotation = element.annotation(MessagingTypes.ON_FAILURE);
        String retryDelay = annotation.stringValue("retryDelay").orElse("PT1S");
        Duration parsedRetryDelay;
        try {
            parsedRetryDelay = Duration.parse(retryDelay);
        } catch (DateTimeParseException e) {
            throw new CodegenException("@Messaging.OnFailure retryDelay must be a valid java.time.Duration: "
                                               + retryDelay,
                                       e,
                                       element.originatingElementValue());
        }
        if (parsedRetryDelay.isZero() || parsedRetryDelay.isNegative()) {
            throw new CodegenException("@Messaging.OnFailure retryDelay must be greater than zero",
                                       element.originatingElementValue());
        }

        int maxAttempts = annotation.intValue("maxAttempts").orElse(0);
        if (maxAttempts < 0) {
            throw new CodegenException("@Messaging.OnFailure maxAttempts must be zero or greater",
                                       element.originatingElementValue());
        }

        String onExhausted = annotation.stringValue("onExhausted").orElse("FAIL");
        String declaredDeadLetterChannel = annotation.stringValue("deadLetterChannel").orElse("");
        Optional<String> deadLetterChannel = Optional.empty();
        switch (onExhausted) {
        case "FAIL" -> {
            if (!declaredDeadLetterChannel.isEmpty()) {
                throw new CodegenException("@Messaging.OnFailure deadLetterChannel is only valid for DEAD_LETTER",
                                           element.originatingElementValue());
            }
        }
        case "DROP" -> {
            if (maxAttempts == 0) {
                throw new CodegenException("@Messaging.OnFailure maxAttempts must be greater than zero for DROP",
                                           element.originatingElementValue());
            }
            if (!declaredDeadLetterChannel.isEmpty()) {
                throw new CodegenException("@Messaging.OnFailure deadLetterChannel is only valid for DEAD_LETTER",
                                           element.originatingElementValue());
            }
        }
        case "DEAD_LETTER" -> {
            if (maxAttempts == 0) {
                throw new CodegenException("@Messaging.OnFailure maxAttempts must be greater than zero for DEAD_LETTER",
                                           element.originatingElementValue());
            }
            if (declaredDeadLetterChannel.isEmpty()) {
                throw new CodegenException("@Messaging.OnFailure deadLetterChannel must be configured for DEAD_LETTER",
                                           element.originatingElementValue());
            }
            String channel = validateName(element,
                                          declaredDeadLetterChannel,
                                          "@Messaging.OnFailure deadLetterChannel");
            if (sourceChannel.equals(channel)) {
                throw new CodegenException("@Messaging.OnFailure deadLetterChannel must differ from the "
                                                   + "@Messaging.ReceiveFrom channel",
                                           element.originatingElementValue());
            }
            deadLetterChannel = Optional.of(channel);
        }
        default -> throw new CodegenException("Unsupported @Messaging.OnFailure onExhausted value " + onExhausted,
                                              element.originatingElementValue());
        }

        return Optional.of(new FailurePolicyMetadata(retryDelay,
                                                     maxAttempts,
                                                     onExhausted,
                                                     deadLetterChannel));
    }

    private String annotationName(TypedElementInfo element, TypeName annotationType, String description) {
        String value = element.annotation(annotationType)
                .stringValue()
                .orElseThrow(() -> new CodegenException(description + " is required",
                                                        element.originatingElementValue()));
        return validateName(element, value, description);
    }

    private String validateName(TypedElementInfo element, String value, String description) {
        if (value.isBlank()) {
            throw new CodegenException(description + " must not be blank", element.originatingElementValue());
        }
        if (!value.equals(value.strip())) {
            throw new CodegenException(description + " must not have leading or trailing whitespace",
                                       element.originatingElementValue());
        }
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new CodegenException(description + " must not contain control characters",
                                       element.originatingElementValue());
        }
        return value;
    }

    private TypeName enclosingType(TypedElementInfo element) {
        return element.enclosingType()
                .orElseThrow(() -> new CodegenException("Element " + element + " does not have an enclosing type",
                                                        element.originatingElementValue()));
    }

    private String consumerClassName(TypeInfo typeInfo, TypedElementInfo element) {
        String identity = handlerId(typeInfo, element);
        String candidate = generatedClassName(typeInfo.typeName(), "__MessagingConsumer_", identity);
        String generatedTypeName = typeInfo.typeName().packageName() + "." + candidate;
        String existing = consumerTypeIdentities.putIfAbsent(generatedTypeName, identity);
        if (existing != null && !existing.equals(identity)) {
            throw new CodegenException("Generated messaging consumer name collision between "
                                               + existing + " and " + identity,
                                       element.originatingElementValue());
        }
        return candidate;
    }

    static String generatedEmitterClassName(TypeName serviceType, String producerId) {
        return generatedClassName(serviceType, "__MessagingEmitter_", producerId);
    }

    private static String generatedClassName(TypeName serviceType, String marker, String identity) {
        String ownerName = serviceType.classNameWithEnclosingNames().replace('.', '_');
        String suffix = marker + stableIdentifier(identity);
        int ownerPrefixBytes = MAX_GENERATED_CLASS_NAME_UTF8_BYTES
                - suffix.getBytes(StandardCharsets.UTF_8).length;
        return utf8Prefix(ownerName, ownerPrefixBytes) + suffix;
    }

    private static String utf8Prefix(String value, int maxBytes) {
        int byteCount = 0;
        int end = 0;
        while (end < value.length()) {
            int codePoint = value.codePointAt(end);
            int codePointBytes = utf8Length(codePoint);
            if (byteCount + codePointBytes > maxBytes) {
                break;
            }
            byteCount += codePointBytes;
            end += Character.charCount(codePoint);
        }
        return value.substring(0, end);
    }

    private static int utf8Length(int codePoint) {
        if (codePoint <= 0x7F) {
            return 1;
        }
        if (codePoint <= 0x7FF) {
            return 2;
        }
        if (codePoint <= 0xFFFF) {
            return 3;
        }
        return 4;
    }

    private static String stableIdentifier(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String handlerId(TypeInfo typeInfo, TypedElementInfo element) {
        return typeInfo.typeName().fqName() + "#" + element.signature().text();
    }

    private record MessageType(TypeName envelopeType, TypeName payloadType) {
    }

    private record BatchType(TypeName payloadType) {
    }

    private record FailurePolicyMetadata(String retryDelay,
                                         int maxAttempts,
                                         String onExhausted,
                                         Optional<String> deadLetterChannel) {
    }

    private record ConsumerMethod(TypeName payloadType,
                                  TypeName envelopeType,
                                  boolean explicitBatchHandler,
                                  String outgoingChannel,
                                  TypeName outgoingPayloadType,
                                  TypeName outgoingEnvelopeType,
                                  boolean returnsEnvelope) {
        private static ConsumerMethod terminal(TypeName payloadType, TypeName envelopeType) {
            return new ConsumerMethod(payloadType, envelopeType, false, null, null, null, false);
        }

        private static ConsumerMethod terminalBatch(TypeName payloadType,
                                                    TypeName envelopeType) {
            return new ConsumerMethod(payloadType,
                                      envelopeType,
                                      true,
                                      null,
                                      null,
                                      null,
                                      false);
        }

        private static ConsumerMethod processor(TypeName payloadType,
                                                TypeName envelopeType,
                                                String outgoingChannel,
                                                TypeName outgoingPayloadType,
                                                TypeName outgoingEnvelopeType,
                                                boolean returnsEnvelope) {
            return new ConsumerMethod(payloadType,
                                      envelopeType,
                                      false,
                                      outgoingChannel,
                                      outgoingPayloadType,
                                      outgoingEnvelopeType,
                                      returnsEnvelope);
        }

        private boolean processor() {
            return outgoingChannel != null;
        }
    }

    private record ServiceChannel(TypeName serviceType, String channel) {
    }
}
