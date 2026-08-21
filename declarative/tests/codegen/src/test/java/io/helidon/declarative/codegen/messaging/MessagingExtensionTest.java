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

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import javax.tools.DocumentationTool;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Generated;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.messaging.ConsumerRegistration;
import io.helidon.messaging.Message;
import io.helidon.messaging.MessageBatch;
import io.helidon.service.registry.Service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagingExtensionTest {
    private static final List<Class<?>> COMPILER_CLASSPATH = List.of(
            Generated.class,
            Service.class,
            TypeName.class,
            MessagingExtensionProvider.class
    );

    @Test
    void identifiesConcretePayloadAndEnvelopeTypes() {
        TypeName listOfInteger = TypeName.builder(TypeNames.LIST)
                .addTypeArgument(TypeNames.BOXED_INT)
                .build();
        TypeName typeVariable = TypeName.createFromGenericDeclaration("T");

        assertTrue(MessagingExtension.isConcretePayloadType(listOfInteger));
        assertFalse(MessagingExtension.isConcretePayloadType(TypeNames.WILDCARD));
        assertFalse(MessagingExtension.isConcretePayloadType(typeVariable));
        assertFalse(MessagingExtension.isConcretePayloadType(TypeName.builder(TypeNames.LIST)
                                                                      .addTypeArgument(TypeNames.WILDCARD)
                                                                      .build()));
        assertFalse(MessagingExtension.isConcretePayloadType(TypeName.builder(TypeNames.LIST)
                                                                      .addTypeArgument(typeVariable)
                                                                      .build()));

        assertFalse(MessagingExtension.hasUnresolvedTypeVariable(TypeNames.WILDCARD));
        assertTrue(MessagingExtension.hasUnresolvedTypeVariable(typeVariable));
    }

    @Test
    void rejectsDifferentParameterizedUsagesOfSameEnvelope() {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Message;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                interface KeyedMessage<K, V> extends Message<V> {
                }

                @Service.Singleton
                class ConflictingEnvelopeConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(KeyedMessage<String, Integer> first,
                                 KeyedMessage<Long, Integer> second) {
                    }
                }
                """);

        assertDiagnostic(result, "exactly one primary message view; found 2");
    }

    @Test
    void rejectsTypeVariableInNonPayloadEnvelopeArgument() {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Message;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                interface KeyedMessage<K, V> extends Message<V> {
                }

                @Service.Singleton
                class GenericEnvelopeConsumer {
                    @Messaging.ReceiveFrom("orders")
                    <K> void consume(KeyedMessage<K, Integer> message) {
                    }
                }
                """);

        assertDiagnostic(result, "must not use wildcards or unresolved type variables");
    }

    @Test
    void rejectsWildcardInNonPayloadEnvelopeArgument() {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Message;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                interface KeyedMessage<K, V> extends Message<V> {
                }

                @Service.Singleton
                class WildcardEnvelopeConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(KeyedMessage<?, Integer> message) {
                    }
                }
                """);

        assertDiagnostic(result, "must not use wildcards or unresolved type variables");
    }

    @Test
    void rejectsWildcardBoundContainingTypeVariable() {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Message;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                interface KeyedMessage<K, V> extends Message<V> {
                }

                @Service.Singleton
                class GenericWildcardEnvelopeConsumer {
                    @Messaging.ReceiveFrom("orders")
                    <K> void consume(KeyedMessage<? extends K, Integer> message) {
                    }
                }
                """);

        assertDiagnostic(result, "must not use wildcards or unresolved type variables");
    }

    @Test
    void generatedRegistrationRetainsCompleteGenericTypes() throws IOException {
        TestCompiler.Result result = compile("""
                package com.example;

                import java.util.List;

                import io.helidon.messaging.Message;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                interface KeyedMessage<K, V> extends Message<V> {
                }

                @Service.Singleton
                class GenericMetadataConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(KeyedMessage<String, List<Integer>> message) {
                    }
                }
                """);

        assertCompilationSucceeded(result);
        String generatedSource = generatedSource(result, "GenericMetadataConsumer__MessagingConsumer_");
        assertTrue(generatedSource.contains("payloadGenericType()"), generatedSource);
        assertTrue(generatedSource.contains("private static final GenericType<List<Integer>> PAYLOAD_GENERIC_TYPE"),
                   generatedSource);
        assertTrue(generatedSource.contains("new GenericType<List<Integer>>()"), generatedSource);
        assertTrue(generatedSource.contains("return PAYLOAD_GENERIC_TYPE;"), generatedSource);
        assertTrue(generatedSource.contains("envelopeGenericType()"), generatedSource);
        assertTrue(generatedSource.contains("private static final GenericType<KeyedMessage<String, List<Integer>>> "
                                                    + "ENVELOPE_GENERIC_TYPE"),
                   generatedSource);
        assertTrue(generatedSource.contains("new GenericType<KeyedMessage<String, List<Integer>>>()"),
                   generatedSource);
        assertTrue(generatedSource.contains("return ENVELOPE_GENERIC_TYPE;"), generatedSource);
    }

    @Test
    void generatedRegistrationBoxesPrimitivePayloadMetadata() throws IOException {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class PrimitiveConsumer {
                    @Messaging.ReceiveFrom("numbers")
                    void consume(int value) {
                    }
                }
                """);

        assertCompilationSucceeded(result);
        String generatedSource = generatedSource(result, "PrimitiveConsumer__MessagingConsumer_");
        assertTrue(generatedSource.contains("return PAYLOAD_GENERIC_TYPE.rawType();"), generatedSource);
        assertTrue(generatedSource.contains("new GenericType<Integer>()"), generatedSource);
    }

    @Test
    void generatedPrimitiveHandlerDispatchTargetsAnnotatedOverload() throws Exception {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class PrimitiveOverloadConsumer {
                    private String invocation;

                    @Messaging.ReceiveFrom("numbers")
                    void consume(int value) {
                        invocation = "primitive:" + value;
                    }

                    void consume(Integer value) {
                        invocation = "boxed:" + value;
                    }

                    public String invocation() {
                        return invocation;
                    }
                }
                """);

        assertCompilationSucceeded(result);
        String generatedSource = generatedSource(result, "PrimitiveOverloadConsumer__MessagingConsumer_");
        assertTrue(generatedSource.contains("consumerInstance.consume((int) typedMessage.entity());"), generatedSource);

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {
                result.classOutput().toUri().toURL()
        }, getClass().getClassLoader())) {
            Class<?> consumerType = classLoader.loadClass("com.example.PrimitiveOverloadConsumer");
            var constructor = consumerType.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object consumer = constructor.newInstance();
            ConsumerRegistration registration = (ConsumerRegistration) newRegistration(
                    generatedClass(classLoader, result, "PrimitiveOverloadConsumer__MessagingConsumer_"),
                    (Supplier<Object>) () -> consumer,
                    passthroughEntryPoints());

            registration.dispatch(MessageBatch.create(Message.create(42)));

            assertEquals("primitive:42", invoke(consumer, "invocation"));
        }
    }

    @Test
    void explicitEntityTreatsMessageSubtypeAsPayloadWhileUnannotatedSubtypeRemainsEnvelope() throws Exception {
        TestCompiler.Result result = compile("""
                package com.example;

                import java.util.Map;

                import io.helidon.messaging.Message;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                final class MessagePayload implements Message<String> {
                    private final String value;

                    MessagePayload(String value) {
                        this.value = value;
                    }

                    @Override
                    public String entity() {
                        return value;
                    }

                    @Override
                    public Map<String, String> headers() {
                        return Map.of("tenant", "inner");
                    }
                }

                @Service.Singleton
                class EntityPayloadConsumer {
                    private MessagePayload received;
                    private String tenant;

                    @Messaging.ReceiveFrom("entity-payload")
                    void consume(@Messaging.Entity MessagePayload payload,
                                 @Messaging.HeaderParam("tenant") String tenant) {
                        this.received = payload;
                        this.tenant = tenant;
                    }

                    public MessagePayload received() {
                        return received;
                    }

                    public String tenant() {
                        return tenant;
                    }
                }

                @Service.Singleton
                class EnvelopeConsumer {
                    private MessagePayload received;
                    private String tenant;

                    @Messaging.ReceiveFrom("envelope")
                    void consume(MessagePayload message,
                                 @Messaging.HeaderParam("tenant") String tenant) {
                        this.received = message;
                        this.tenant = tenant;
                    }

                    public MessagePayload received() {
                        return received;
                    }

                    public String tenant() {
                        return tenant;
                    }
                }
                """);

        assertCompilationSucceeded(result);
        String entitySource = generatedSource(result, "EntityPayloadConsumer__MessagingConsumer_");
        assertTrue(entitySource.contains("new GenericType<MessagePayload>()"), entitySource);
        assertTrue(entitySource.contains("new GenericType<Message<MessagePayload>>()"), entitySource);
        assertTrue(entitySource.contains("var typedMessage = (Message<MessagePayload>) message;"), entitySource);
        assertTrue(entitySource.contains("consumerInstance.consume(typedMessage.entity(), "
                                                + "typedMessage.header(\"tenant\").orElseThrow"),
                   entitySource);

        String envelopeSource = generatedSource(result, "EnvelopeConsumer__MessagingConsumer_");
        assertTrue(envelopeSource.contains("new GenericType<String>()"), envelopeSource);
        assertTrue(envelopeSource.contains("new GenericType<MessagePayload>()"), envelopeSource);
        assertTrue(envelopeSource.contains("var typedMessage = (MessagePayload) message;"), envelopeSource);
        assertTrue(envelopeSource.contains("consumerInstance.consume(typedMessage, "
                                                  + "typedMessage.header(\"tenant\").orElseThrow"),
                   envelopeSource);

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {
                result.classOutput().toUri().toURL()
        }, getClass().getClassLoader())) {
            Class<?> payloadType = classLoader.loadClass("com.example.MessagePayload");
            var payloadConstructor = payloadType.getDeclaredConstructor(String.class);
            payloadConstructor.setAccessible(true);
            Object payload = payloadConstructor.newInstance("payload");

            Class<?> entityConsumerType = classLoader.loadClass("com.example.EntityPayloadConsumer");
            var entityConsumerConstructor = entityConsumerType.getDeclaredConstructor();
            entityConsumerConstructor.setAccessible(true);
            Object entityConsumer = entityConsumerConstructor.newInstance();
            ConsumerRegistration entityRegistration = (ConsumerRegistration) newRegistration(
                    generatedClass(classLoader, result, "EntityPayloadConsumer__MessagingConsumer_"),
                    (Supplier<Object>) () -> entityConsumer,
                    passthroughEntryPoints());

            assertSame(payloadType, entityRegistration.payloadType());
            assertSame(Message.class, entityRegistration.envelopeType());

            entityRegistration.dispatch(MessageBatch.create(Message.builder(payload)
                                                                      .header("tenant", "outer")
                                                                      .build()));

            assertSame(payload, invoke(entityConsumer, "received"));
            assertEquals("outer", invoke(entityConsumer, "tenant"));

            Class<?> envelopeConsumerType = classLoader.loadClass("com.example.EnvelopeConsumer");
            var envelopeConsumerConstructor = envelopeConsumerType.getDeclaredConstructor();
            envelopeConsumerConstructor.setAccessible(true);
            Object envelopeConsumer = envelopeConsumerConstructor.newInstance();
            ConsumerRegistration envelopeRegistration = (ConsumerRegistration) newRegistration(
                    generatedClass(classLoader, result, "EnvelopeConsumer__MessagingConsumer_"),
                    (Supplier<Object>) () -> envelopeConsumer,
                    passthroughEntryPoints());

            assertSame(String.class, envelopeRegistration.payloadType());
            assertSame(payloadType, envelopeRegistration.envelopeType());

            envelopeRegistration.dispatch(MessageBatch.create((Message<?>) payload));

            assertSame(payload, invoke(envelopeConsumer, "received"));
            assertEquals("inner", invoke(envelopeConsumer, "tenant"));
        }
    }

    @Test
    void rejectsConflictingEmitterPayloadsForSameServiceAndChannel() {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Emitter;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class ConflictingEmitterProducer {
                    @Service.Inject
                    @Service.Named("orders")
                    Emitter<String> first;

                    @Service.Inject
                    @Service.Named("orders")
                    Emitter<Integer> second;
                }
                """);

        assertDiagnostic(result, "Conflicting messaging emitter payload types for channel orders");
    }

    @Test
    void rejectsWildcardEmitterPayload() {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Emitter;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class WildcardEmitterProducer {
                    @Service.Inject
                    @Service.Named("orders")
                    Emitter<?> emitter;
                }
                """);

        assertDiagnostic(result, "Messaging emitter payload type must be concrete");
    }

    @Test
    void rejectsNestedWildcardEmitterPayload() {
        TestCompiler.Result result = compile("""
                package com.example;

                import java.util.List;

                import io.helidon.messaging.Emitter;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class NestedWildcardEmitterProducer {
                    @Service.Inject
                    @Service.Named("orders")
                    Emitter<List<?>> emitter;
                }
                """);

        assertDiagnostic(result, "Messaging emitter payload type must be concrete");
    }

    @Test
    void rejectsTypeVariableEmitterPayload() {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Emitter;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class GenericEmitterProducer<T> {
                    @Service.Inject
                    @Service.Named("orders")
                    Emitter<T> emitter;
                }
                """);

        assertDiagnostic(result, "Messaging emitter payload type must be concrete");
    }

    @Test
    void generatedConsumerUsesEntryPointAndMethodMetadata() throws IOException {
        TestCompiler.Result result = compile("""
                package com.example;

                import java.io.IOException;
                import java.util.Optional;

                import io.helidon.messaging.Message;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class InterceptedConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(Message<String> message,
                                 @Messaging.HeaderParam("required") String required,
                                 @Messaging.HeaderParam("optional") Optional<String> optional) throws IOException {
                    }
                }
                """);

        assertCompilationSucceeded(result);
        String source = generatedSource(result, "InterceptedConsumer__MessagingConsumer_");
        assertTrue(source.contains("Supplier<InterceptedConsumer> consumer"), source);
        assertTrue(source.contains("EntryPoints entryPoints"), source);
        assertTrue(source.contains("entryPoints.handler("), source);
        assertTrue(source.contains("descriptor.qualifiers()"), source);
        assertTrue(source.contains("InterceptedConsumer__ServiceDescriptor.ANNOTATIONS"), source);
        assertTrue(source.contains("InterceptedConsumer__ServiceDescriptor.METHOD_"), source);
        assertTrue(source.contains("this::invoke"), source);
        assertTrue(source.contains("Handler<InterceptedConsumer> handler"), source);
        assertTrue(source.contains("void dispatch(MessageBatch<?> messages)"), source);
        assertTrue(source.contains("dispatchMessage(messages.get(index));"), source);
        assertTrue(source.contains("BatchDeliveryException.sequential("), source);
        assertTrue(source.contains("handler.handle(consumer.get(), message)"), source);
        assertTrue(source.contains("invoke(InterceptedConsumer consumerInstance,"), source);
        assertTrue(source.contains("consumerInstance.consume("), source);
        assertFalse(source.contains("consumer.get().consume("), source);
        assertSingleOccurrence(source, "consumer.get()");
        assertTrue(source.contains("typedMessage.header(\"required\").orElseThrow"), source);
        assertTrue(source.contains("typedMessage.header(\"optional\")"), source);
        assertTrue(source.contains("catch (RuntimeException | Error e)"), source);
        assertTrue(source.contains("catch (Exception e)"), source);
    }

    @Test
    void generatedConsumerPublishesDeclaredFailurePolicy() throws Exception {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.FailureDisposition;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class FailurePolicyConsumer {
                    @Messaging.ReceiveFrom("orders")
                    @Messaging.OnFailure(
                            retryDelay = "PT0.25S",
                            maxAttempts = 3,
                            onExhausted = FailureDisposition.DEAD_LETTER,
                            deadLetterChannel = "orders-dlq")
                    void consume(String value) {
                    }
                }
                """);

        assertCompilationSucceeded(result);
        String source = generatedSource(result, "FailurePolicyConsumer__MessagingConsumer_");
        assertTrue(source.contains("private static final FailurePolicy DECLARED_FAILURE_POLICY"), source);
        assertTrue(source.contains(".retryDelay(Duration.parse(\"PT0.25S\"))"), source);
        assertTrue(source.contains(".maxAttempts(3)"), source);
        assertTrue(source.contains(".onExhausted(FailureDisposition.DEAD_LETTER)"), source);
        assertTrue(source.contains(".deadLetterChannel(\"orders-dlq\")"), source);
        assertTrue(source.contains("Optional<FailurePolicy> declaredFailurePolicy()"), source);
        assertTrue(source.contains("return Optional.of(DECLARED_FAILURE_POLICY);"), source);

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {
                result.classOutput().toUri().toURL()
        }, getClass().getClassLoader())) {
            Class<?> registrationType = generatedClass(classLoader,
                                                       result,
                                                       "FailurePolicyConsumer__MessagingConsumer_");
            Object registration = newRegistration(
                    registrationType,
                    (Supplier<Object>) () -> null,
                    passthroughEntryPoints());
            Optional<?> declared = (Optional<?>) invoke(registration, "declaredFailurePolicy");
            Object policy = declared.orElseThrow();
            assertEquals(Duration.ofMillis(250), invoke(policy, "retryDelay"));
            assertEquals(3, invoke(policy, "maxAttempts"));
            assertEquals("DEAD_LETTER", invoke(policy, "onExhausted").toString());
            assertEquals(Optional.of("orders-dlq"), invoke(policy, "deadLetterChannel"));
            assertSame(policy,
                       ((Optional<?>) invoke(registration, "declaredFailurePolicy")).orElseThrow());
        }
    }

    @Test
    void bareOnFailureIsPresentWhileUnannotatedRegistrationUsesEmptyDefault() throws Exception {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class BareFailurePolicyConsumer {
                    @Messaging.ReceiveFrom("orders")
                    @Messaging.OnFailure
                    void consume(String value) {
                    }
                }

                @Service.Singleton
                class UnannotatedConsumer {
                    @Messaging.ReceiveFrom("audit")
                    void consume(String value) {
                    }
                }
                """);

        assertCompilationSucceeded(result);
        String annotatedSource = generatedSource(result, "BareFailurePolicyConsumer__MessagingConsumer_");
        assertTrue(annotatedSource.contains("DECLARED_FAILURE_POLICY"), annotatedSource);
        assertTrue(annotatedSource.contains(".retryDelay(Duration.parse(\"PT1S\"))"), annotatedSource);
        assertTrue(annotatedSource.contains(".maxAttempts(0)"), annotatedSource);
        assertTrue(annotatedSource.contains(".onExhausted(FailureDisposition.FAIL)"), annotatedSource);

        String unannotatedSource = generatedSource(result, "UnannotatedConsumer__MessagingConsumer_");
        assertFalse(unannotatedSource.contains("DECLARED_FAILURE_POLICY"), unannotatedSource);
        assertFalse(unannotatedSource.contains("declaredFailurePolicy()"), unannotatedSource);

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {
                result.classOutput().toUri().toURL()
        }, getClass().getClassLoader())) {
            Object annotated = newRegistration(
                    generatedClass(classLoader, result, "BareFailurePolicyConsumer__MessagingConsumer_"),
                    (Supplier<Object>) () -> null,
                    passthroughEntryPoints());
            Optional<?> declared = (Optional<?>) invoke(annotated, "declaredFailurePolicy");
            Object policy = declared.orElseThrow();
            assertEquals(Duration.ofSeconds(1), invoke(policy, "retryDelay"));
            assertEquals(0, invoke(policy, "maxAttempts"));
            assertEquals("FAIL", invoke(policy, "onExhausted").toString());
            assertEquals(Optional.empty(), invoke(policy, "deadLetterChannel"));

            Object unannotated = newRegistration(
                    generatedClass(classLoader, result, "UnannotatedConsumer__MessagingConsumer_"),
                    (Supplier<Object>) () -> null,
                    passthroughEntryPoints());
            assertEquals(Optional.empty(), invoke(unannotated, "declaredFailurePolicy"));
        }
    }

    @Test
    void rejectsOnFailureWithoutReceiveFrom() {
        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class OrphanFailurePolicy {
                    @Messaging.OnFailure(maxAttempts = 1)
                    void consume(String value) {
                    }
                }
                """),
                         "@Messaging.OnFailure is only allowed on @Messaging.ReceiveFrom methods");
    }

    @Test
    void rejectsInvalidOnFailureMembers() {
        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class InvalidRetryDelay {
                    @Messaging.ReceiveFrom("orders")
                    @Messaging.OnFailure(retryDelay = "tomorrow")
                    void consume(String value) {
                    }
                }
                """),
                         "retryDelay must be a valid java.time.Duration");

        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class ZeroRetryDelay {
                    @Messaging.ReceiveFrom("orders")
                    @Messaging.OnFailure(retryDelay = "PT0S")
                    void consume(String value) {
                    }
                }
                """),
                         "retryDelay must be greater than zero");

        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class NegativeAttempts {
                    @Messaging.ReceiveFrom("orders")
                    @Messaging.OnFailure(maxAttempts = -1)
                    void consume(String value) {
                    }
                }
                """),
                         "maxAttempts must be zero or greater");

        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.FailureDisposition;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class UnboundedDrop {
                    @Messaging.ReceiveFrom("orders")
                    @Messaging.OnFailure(onExhausted = FailureDisposition.DROP)
                    void consume(String value) {
                    }
                }
                """),
                         "maxAttempts must be greater than zero for DROP");

        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.FailureDisposition;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class MissingDeadLetterChannel {
                    @Messaging.ReceiveFrom("orders")
                    @Messaging.OnFailure(maxAttempts = 1,
                                         onExhausted = FailureDisposition.DEAD_LETTER)
                    void consume(String value) {
                    }
                }
                """),
                         "deadLetterChannel must be configured for DEAD_LETTER");

        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class DeadLetterChannelWithoutDisposition {
                    @Messaging.ReceiveFrom("orders")
                    @Messaging.OnFailure(deadLetterChannel = "orders-dlq")
                    void consume(String value) {
                    }
                }
                """),
                         "deadLetterChannel is only valid for DEAD_LETTER");

        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.FailureDisposition;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class SelfDeadLetterChannel {
                    @Messaging.ReceiveFrom("orders")
                    @Messaging.OnFailure(maxAttempts = 1,
                                         onExhausted = FailureDisposition.DEAD_LETTER,
                                         deadLetterChannel = "orders")
                    void consume(String value) {
                    }
                }
                """),
                         "deadLetterChannel must differ from the @Messaging.ReceiveFrom channel");
    }

    @Test
    void generatesPayloadReturningProcessor() throws IOException {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class PayloadProcessor {
                    @Messaging.ReceiveFrom("orders")
                    @Messaging.SendTo("audit")
                    Integer process(String value) {
                        return value.length();
                    }
                }
                """);

        assertCompilationSucceeded(result);
        String source = generatedSource(result, "PayloadProcessor__MessagingConsumer_");
        assertTrue(source.contains("implements ProcessorRegistration"), source);
        assertTrue(source.contains("String outgoingChannel()"), source);
        assertTrue(source.contains("return \"audit\";"), source);
        assertTrue(source.contains("new GenericType<Integer>()"), source);
        assertTrue(source.contains("new GenericType<Message<Integer>>()"), source);
        assertTrue(source.contains("MessageBatch<?> process(MessageBatch<?> messages)"), source);
        assertTrue(source.contains("processedMessages.add(processMessage(messages.get(index)));"), source);
        assertTrue(source.contains("return messages.derive(processedMessages);"), source);
        assertTrue(source.contains("BatchDeliveryException.attemptedPrefix("), source);
        assertTrue(source.contains("Handler<PayloadProcessor> handler"), source);
        assertTrue(source.contains("handler.handle(consumer.get(), message)"), source);
        assertTrue(source.contains("invoke(PayloadProcessor consumerInstance,"), source);
        assertTrue(source.contains("consumerInstance.process("), source);
        assertFalse(source.contains("consumer.get().process("), source);
        assertSingleOccurrence(source, "consumer.get()");
        assertTrue(source.contains("Objects.requireNonNull("), source);
        assertTrue(source.contains("Message.create(result)"), source);
    }

    @Test
    void generatesEnvelopeReturningProcessor() throws IOException {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Message;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class EnvelopeProcessor {
                    @Messaging.ReceiveFrom("orders")
                    @Messaging.SendTo("audit")
                    Message<Integer> process(Message<String> message) {
                        return Message.create(message.entity().length());
                    }
                }
                """);

        assertCompilationSucceeded(result);
        String source = generatedSource(result, "EnvelopeProcessor__MessagingConsumer_");
        assertTrue(source.contains("new GenericType<Message<Integer>>()"), source);
        assertTrue(source.contains("return Optional.of(result);"), source);
        assertFalse(source.contains("Message.create(result)"), source);
    }

    @Test
    void preservesGenericArrayPayloadForEnvelopeReturningProcessor() throws IOException {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Message;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                interface ArrayMessage<T> extends Message<T[][]> {
                }

                @Service.Singleton
                class ArrayEnvelopeProcessor {
                    @Messaging.ReceiveFrom("orders")
                    @Messaging.SendTo("audit")
                    ArrayMessage<String> process(String value) {
                        return null;
                    }
                }

                @Service.Singleton
                class ArrayPayloadConsumer {
                    @Messaging.ReceiveFrom("audit")
                    void consume(String[][] value) {
                    }
                }
                """);

        assertCompilationSucceeded(result);
        String source = generatedSource(result, "ArrayEnvelopeProcessor__MessagingConsumer_");
        assertTrue(source.contains("new GenericType<String[][]>()"), source);
        assertTrue(source.contains("new GenericType<ArrayMessage<String>>()"), source);

        String consumerSource = generatedSource(result, "ArrayPayloadConsumer__MessagingConsumer_");
        assertTrue(consumerSource.contains("return PAYLOAD_GENERIC_TYPE.rawType();"), consumerSource);
        assertTrue(consumerSource.contains("new GenericType<String[][]>()"), consumerSource);
        assertTrue(consumerSource.contains("return ENVELOPE_GENERIC_TYPE.rawType();"), consumerSource);
        assertTrue(consumerSource.contains("new GenericType<Message<String[][]>>()"), consumerSource);
    }

    @Test
    void generatesArrayEmitterMetadata() throws IOException {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Emitter;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class ArrayEmitterProducer {
                    @Service.Inject
                    @Service.Named("arrays")
                    Emitter<String[][]> emitter;
                }
                """);

        assertCompilationSucceeded(result);
        String source = generatedSource(result, "ArrayEmitterProducer__MessagingEmitter_");
        assertTrue(source.contains("new GenericType<String[][]>()"), source);
        assertTrue(source.contains("new GenericType<Message<String[][]>>()"), source);
    }

    @Test
    void generatesSingleBatchHandlerInvocation() throws IOException {
        TestCompiler.Result result = compile("""
                package com.example;

                import java.io.IOException;

                import io.helidon.messaging.MessageBatch;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class BatchConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(MessageBatch<String> messages) throws IOException {
                    }
                }
                """);

        assertCompilationSucceeded(result);
        String source = generatedSource(result, "BatchConsumer__MessagingConsumer_");
        assertTrue(source.contains("BatchHandler<BatchConsumer> batchHandler"), source);
        assertTrue(source.contains("entryPoints.batchHandler("), source);
        assertTrue(source.contains("this::invokeBatch"), source);
        assertTrue(source.contains("void dispatch(MessageBatch<?> messages)"), source);
        assertFalse(source.contains("dispatchBatch("), source);
        assertFalse(source.contains("batchType()"), source);
        assertFalse(source.contains("batchGenericType()"), source);
        assertFalse(source.contains("boolean batch()"), source);
        assertTrue(source.contains("batchHandler.handle(consumer.get(), messages);"), source);
        assertTrue(source.contains("invokeBatch(BatchConsumer consumerInstance,"), source);
        assertTrue(source.contains("var typedMessages = (MessageBatch<String>) messages;"), source);
        assertTrue(source.contains("consumerInstance.consume(typedMessages);"), source);
        assertFalse(source.contains("consumer.get().consume("), source);
        assertSingleOccurrence(source, "consumer.get()");
    }

    @Test
    void rejectsSendToOnReceiveFromBatchHandler() {
        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.MessageBatch;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class BatchProcessor {
                    @Messaging.ReceiveFrom("orders")
                    @Messaging.SendTo("audit")
                    void consume(MessageBatch<String> messages) {
                    }
                }
                """),
                         "Batch @Messaging.ReceiveFrom methods cannot declare @Messaging.SendTo");
    }

    @Test
    void messageBatchCannotBeSubtyped() {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.MessageBatch;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                class CustomBatch<T> extends MessageBatch<T> {
                }

                @Service.Singleton
                class BatchConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(CustomBatch<Integer> messages) {
                    }
                }
                """);

        assertDiagnostic(result, "cannot inherit from final");
    }

    @Test
    void rejectsLegacyListOfMessagesBatchHandler() {
        TestCompiler.Result result = compile("""
                package com.example;

                import java.util.List;

                import io.helidon.messaging.Message;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class LegacyBatchConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(List<Message<String>> messages) {
                    }
                }
                """);

        assertDiagnostic(result, "List<Message<T>> batch consumers are not supported; use MessageBatch<T>");
    }

    @Test
    void generatedEmitterPublishesTopologyMetadata() throws IOException {
        TestCompiler.Result result = compile("""
                package com.example;

                import java.util.List;

                import io.helidon.messaging.Emitter;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class MetadataEmitterProducer {
                    @Service.Inject
                    @Service.Named("orders")
                    Emitter<List<String>> emitter;
                }
                """);

        assertCompilationSucceeded(result);
        String source = generatedSource(result, "MetadataEmitterProducer__MessagingEmitter_");
        assertTrue(source.contains("Emitter<List<String>>, EmitterRegistration"), source);
        assertTrue(source.contains("String channel()"), source);
        assertTrue(source.contains("return \"orders\";"), source);
        assertTrue(source.contains("String producerId()"), source);
        assertTrue(source.contains("com.example.MetadataEmitterProducer#emitter:orders"), source);
        assertTrue(source.contains("new GenericType<List<String>>()"), source);
        assertTrue(source.contains("new GenericType<Message<List<String>>>()"), source);
        assertTrue(source.contains("void emitBatch(MessageBatch<? extends List<String>> messages)"), source);
        assertFalse(source.contains("void emitMessage("), source);
    }

    @Test
    void generatedRegistrationsReuseCachedTypeMetadata() throws Exception {
        TestCompiler.Result result = compile("""
                package com.example;

                import java.util.List;

                import io.helidon.messaging.Emitter;
                import io.helidon.messaging.Message;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class CachedMetadataService {
                    @Service.Inject
                    @Service.Named("audit")
                    Emitter<List<String>> emitter;

                    @Messaging.ReceiveFrom("orders")
                    @Messaging.SendTo("audit")
                    Message<List<String>> process(Message<List<String>> message) {
                        return message;
                    }
                }
                """);

        assertCompilationSucceeded(result);
        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {
                result.classOutput().toUri().toURL()
        }, getClass().getClassLoader())) {
            Class<?> processorType = generatedClass(classLoader,
                                                     result,
                                                     "CachedMetadataService__MessagingConsumer_");
            Object processor = newRegistration(
                    processorType,
                    (Supplier<Object>) () -> null,
                    passthroughEntryPoints());
            assertCachedTypeMetadata(processor, "payloadGenericType", "payloadType");
            assertCachedTypeMetadata(processor, "envelopeGenericType", "envelopeType");
            assertCachedTypeMetadata(processor, "outgoingPayloadGenericType", "outgoingPayloadType");
            assertCachedTypeMetadata(processor, "outgoingEnvelopeGenericType", "outgoingEnvelopeType");

            Class<?> emitterType = generatedClass(classLoader,
                                                   result,
                                                   "CachedMetadataService__MessagingEmitter_");
            Object emitter = newRegistration(
                    emitterType,
                    (Supplier<Object>) () -> null);
            assertCachedTypeMetadata(emitter, "payloadGenericType", "payloadType");
            assertCachedTypeMetadata(emitter, "envelopeGenericType", "envelopeType");
        }
    }

    @Test
    void rejectsNonServiceAndInvalidHandlerModifiers() {
        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.Messaging;

                @Deprecated
                class NotAService {
                    @Messaging.ReceiveFrom("orders")
                    void consume(String value) {
                    }
                }
                """),
                         "must be a Service Registry service");

        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class InvalidHandler {
                    @Messaging.ReceiveFrom("orders")
                    private void consume(String value) {
                    }
                }
                """),
                         "only allowed on non-private methods");

        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class StaticHandler {
                    @Messaging.ReceiveFrom("orders")
                    static void consume(String value) {
                    }
                }
                """),
                         "only allowed on instance methods");
    }

    @Test
    void rejectsInvalidChannelNamesAndDuplicateRoutes() {
        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class BlankChannel {
                    @Messaging.ReceiveFrom(" ")
                    void consume(String value) {
                    }
                }
                """),
                         "@Messaging.ReceiveFrom channel must not be blank");

        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class BlankSendToChannel {
                    @Messaging.ReceiveFrom("orders")
                    @Messaging.SendTo(" ")
                    String process(String value) {
                        return value;
                    }
                }
                """),
                         "@Messaging.SendTo channel must not be blank");

        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class PaddedSendToChannel {
                    @Messaging.ReceiveFrom("orders")
                    @Messaging.SendTo(" audit")
                    String process(String value) {
                        return value;
                    }
                }
                """),
                         "@Messaging.SendTo channel must not have leading or trailing whitespace");

        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class ControlCharacterSendToChannel {
                    @Messaging.ReceiveFrom("orders")
                    @Messaging.SendTo("audit\\nchannel")
                    String process(String value) {
                        return value;
                    }
                }
                """),
                         "@Messaging.SendTo channel must not contain control characters");

        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class DuplicateRoute {
                    @Messaging.ReceiveFrom("orders")
                    void first(String value) {
                    }

                    @Messaging.ReceiveFrom("orders")
                    void second(Integer value) {
                    }
                }
                """),
                         "declares multiple @Messaging.ReceiveFrom handlers for channel orders");
    }

    @Test
    void rejectsAmbiguousPrimaryViewsAndInvalidHeaders() {
        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.Message;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class AmbiguousConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(@Messaging.Entity String entity, Message<String> message) {
                    }
                }
                """),
                         "exactly one primary message view; found 2");

        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class InvalidHeaderConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(@Messaging.Entity String entity,
                                 @Messaging.HeaderParam("attempt") Integer attempt) {
                    }
                }
                """),
                         "must be String or Optional<String>");

        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class DuplicateHeaderConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(@Messaging.Entity String entity,
                                 @Messaging.HeaderParam("id") String first,
                                 @Messaging.HeaderParam("id") String second) {
                    }
                }
                """),
                         "Duplicate @Messaging.HeaderParam name id");
    }

    @Test
    void rejectsInvalidTerminalAndProcessorReturns() {
        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class ReturningTerminal {
                    @Messaging.ReceiveFrom("orders")
                    String consume(String value) {
                        return value;
                    }
                }
                """),
                         "Terminal @Messaging.ReceiveFrom methods must return void");

        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class VoidProcessor {
                    @Messaging.ReceiveFrom("orders")
                    @Messaging.SendTo("audit")
                    void consume(String value) {
                    }
                }
                """),
                         "processors must return a payload or Message<T>");

        assertDiagnostic(compile("""
                package com.example;

                import java.util.concurrent.CompletableFuture;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class AsyncProcessor {
                    @Messaging.ReceiveFrom("orders")
                    @Messaging.SendTo("audit")
                    CompletableFuture<String> consume(String value) {
                        return CompletableFuture.completedFuture(value);
                    }
                }
                """),
                         "Asynchronous or publisher @Messaging.ReceiveFrom return types are not supported");

        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class OrphanSendTo {
                    @Messaging.SendTo("audit")
                    String consume(String value) {
                        return value;
                    }
                }
                """),
                         "@Messaging.SendTo is only allowed on @Messaging.ReceiveFrom methods");
    }

    @Test
    void rejectsRawGenericHandlerAndEmitterTypes() {
        assertDiagnostic(compile("""
                package com.example;

                import java.util.List;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class RawPayloadConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(List value) {
                    }
                }
                """),
                         "must not use a raw generic type");

        assertDiagnostic(compile("""
                package com.example;

                import java.util.List;

                import io.helidon.messaging.Emitter;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class RawEmitterProducer {
                    @Service.Inject
                    @Service.Named("orders")
                    Emitter<List> emitter;
                }
                """),
                         "Messaging emitter payload type must be concrete");
    }

    @Test
    void rejectsConflictingEmitterPayloadsAcrossServices() {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Emitter;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class StringProducer {
                    @Service.Inject
                    @Service.Named("orders")
                    Emitter<String> emitter;
                }

                @Service.Singleton
                class IntegerProducer {
                    @Service.Inject
                    @Service.Named("orders")
                    Emitter<Integer> emitter;
                }
                """);

        assertDiagnostic(result, "Conflicting messaging emitter payload types for channel orders");
    }

    @Test
    void rejectsReservedEmitterWildcardChannel() {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Emitter;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class WildcardChannelProducer {
                    @Service.Inject
                    @Service.Named("*")
                    Emitter<String> emitter;
                }
                """);

        assertDiagnostic(result, "must not use the reserved Service.Named wildcard *");
    }

    @Test
    void rejectsAdditionalEmitterQualifier() {
        TestCompiler.Result result = compile("""
                package com.example;

                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                import io.helidon.messaging.Emitter;
                import io.helidon.service.registry.Service;

                @Service.Qualifier
                @Retention(RetentionPolicy.RUNTIME)
                @interface Blue {
                }

                @Service.Singleton
                class QualifiedEmitterProducer {
                    @Service.Inject
                    @Service.Named("orders")
                    @Blue
                    Emitter<String> emitter;
                }
                """);

        assertDiagnostic(result, "Messaging emitters support only a single @Service.Named qualifier");
    }

    @Test
    void generatesDistinctEmittersForChannelsWithCollidingNamesAndHashes() throws IOException {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Emitter;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class CollidingChannelProducer {
                    @Service.Inject
                    @Service.Named("!!{")
                    Emitter<String> first;

                    @Service.Inject
                    @Service.Named("!#=")
                    Emitter<String> second;
                }
                """);

        assertCompilationSucceeded(result);
        try (var generatedSources = Files.walk(result.sourceOutput())) {
            List<String> generatedFiles = generatedSources
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("CollidingChannelProducer__MessagingEmitter_"))
                    .filter(name -> name.endsWith(".java"))
                    .toList();
            long registrations = generatedFiles.stream()
                    .filter(name -> !name.contains("__ServiceDescriptor"))
                    .count();
            long descriptors = generatedFiles.stream()
                    .filter(name -> name.contains("__ServiceDescriptor"))
                    .count();
            assertTrue(registrations == 2,
                       "Expected two distinct generated emitter registrations, found " + registrations);
            assertTrue(descriptors == 2,
                       "Expected two generated emitter service descriptors, found " + descriptors);
        }
    }

    @Test
    void boundsGeneratedEmitterNameForLongChannel() {
        String channel = "a".repeat(300);
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Emitter;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class LongChannelProducer {
                    @Service.Inject
                    @Service.Named("%s")
                    Emitter<String> emitter;
                }
                """.formatted(channel));

        assertCompilationSucceeded(result);
    }

    @Test
    void escapesChannelTextInGeneratedConsumerAndEmitterJavadocs() throws IOException {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Emitter;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class JavadocChannelService {
                    @Service.Inject
                    @Service.Named("orders*/x")
                    Emitter<String> emitter;

                    @Messaging.ReceiveFrom("orders*/x")
                    void consume(String value) {
                    }
                }
                """);

        assertCompilationSucceeded(result);
        String consumerSource = generatedSource(result, "JavadocChannelService__MessagingConsumer_");
        String emitterSource = generatedSource(result, "JavadocChannelService__MessagingEmitter_");
        assertTrue(consumerSource.contains("channel <code>orders*&#47;x</code>."), consumerSource);
        assertTrue(emitterSource.contains("channel <code>orders*&#47;x</code>."), emitterSource);
    }

    @Test
    void escapesUnicodeEscapeSpellingInGeneratedConsumerAndEmitterJavadocs() throws IOException {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Emitter;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class UnicodeJavadocChannelService {
                    @Service.Inject
                    @Service.Named("\\\\u002a\\\\u002f")
                    Emitter<String> emitter;

                    @Messaging.ReceiveFrom("\\\\u002a\\\\u002f")
                    void consume(String value) {
                    }
                }
                """);

        assertCompilationSucceeded(result);
        String consumerSource = generatedSource(result, "UnicodeJavadocChannelService__MessagingConsumer_");
        String emitterSource = generatedSource(result, "UnicodeJavadocChannelService__MessagingEmitter_");
        assertTrue(consumerSource.contains("channel <code>&#92;u002a&#92;u002f</code>."), consumerSource);
        assertTrue(emitterSource.contains("channel <code>&#92;u002a&#92;u002f</code>."), emitterSource);
    }

    @Test
    void escapesInlineTagsInGeneratedConsumerAndEmitterJavadocs() throws IOException {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Emitter;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class InlineTagJavadocChannelService {
                    @Service.Inject
                    @Service.Named("x}{@value&<>")
                    Emitter<String> emitter;

                    @Messaging.ReceiveFrom("x}{@value&<>")
                    void consume(String value) {
                    }
                }
                """);

        assertCompilationSucceeded(result);
        Path consumerSource = generatedSourcePath(result, "InlineTagJavadocChannelService__MessagingConsumer_");
        Path emitterSource = generatedSourcePath(result, "InlineTagJavadocChannelService__MessagingEmitter_");
        String escapedChannel = "channel <code>x&#125;&#123;@value&amp;&lt;&gt;</code>.";
        assertTrue(Files.readString(consumerSource).contains(escapedChannel));
        assertTrue(Files.readString(emitterSource).contains(escapedChannel));
        Path javadocs = generateJavadocs(result, consumerSource, emitterSource);
        assertRenderedJavadoc(javadocs, consumerSource, "Messaging consumer registration for " + escapedChannel);
        assertRenderedJavadoc(javadocs, emitterSource, "Messaging emitter service for " + escapedChannel);
    }

    @Test
    void boundsGeneratedConsumerNameForLongDeclaringType() throws IOException {
        String typeName = "C".repeat(176);
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class %s {
                    @Messaging.ReceiveFrom("orders")
                    void consume(String value) {
                    }
                }
                """.formatted(typeName));

        assertCompilationSucceeded(result);
        assertGeneratedTypeNamesBounded(result, "__MessagingConsumer_");
    }

    @Test
    void boundsGeneratedEmitterNameForLongDeclaringType() throws IOException {
        String typeName = "E".repeat(176);
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Emitter;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class %s {
                    @Service.Inject
                    @Service.Named("orders")
                    Emitter<String> emitter;
                }
                """.formatted(typeName));

        assertCompilationSucceeded(result);
        assertGeneratedTypeNamesBounded(result, "__MessagingEmitter_");
    }

    @Test
    void distinguishesLongDeclaringTypesWithSharedGeneratedPrefix() throws IOException {
        String sharedPrefix = "LongServiceOwner" + "X".repeat(150);
        String firstType = sharedPrefix + "First";
        String secondType = sharedPrefix + "Second";
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Emitter;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class %s {
                    @Service.Inject
                    @Service.Named("audit")
                    Emitter<String> emitter;

                    @Messaging.ReceiveFrom("orders")
                    void consume(String value) {
                    }
                }

                @Service.Singleton
                class %s {
                    @Service.Inject
                    @Service.Named("audit")
                    Emitter<String> emitter;

                    @Messaging.ReceiveFrom("orders")
                    void consume(String value) {
                    }
                }
                """.formatted(firstType, secondType));

        assertCompilationSucceeded(result);
        assertGeneratedTypeCount(result, "__MessagingConsumer_", 2, 2);
        assertGeneratedTypeCount(result, "__MessagingEmitter_", 2, 2);
        assertGeneratedTypeNamesBounded(result, "__MessagingConsumer_");
        assertGeneratedTypeNamesBounded(result, "__MessagingEmitter_");
    }

    @Test
    void generatesDistinctConsumersForCollidingMethodDeclarations() throws IOException {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                class Aa {
                }

                class BB {
                }

                @Service.Singleton
                class CollidingConsumer {
                    @Messaging.ReceiveFrom("first")
                    void consume(Aa value) {
                    }

                    @Messaging.ReceiveFrom("second")
                    void consume(BB value) {
                    }
                }
                """);

        assertCompilationSucceeded(result);
        try (var generatedSources = Files.walk(result.sourceOutput())) {
            List<String> generatedFiles = generatedSources
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("CollidingConsumer__MessagingConsumer_"))
                    .filter(name -> name.endsWith(".java"))
                    .toList();
            long registrations = generatedFiles.stream()
                    .filter(name -> !name.contains("__ServiceDescriptor"))
                    .count();
            long descriptors = generatedFiles.stream()
                    .filter(name -> name.contains("__ServiceDescriptor"))
                    .count();
            assertTrue(registrations == 2,
                       "Expected two distinct generated consumer registrations, found " + registrations);
            assertTrue(descriptors == 2,
                       "Expected two generated consumer service descriptors, found " + descriptors);
        }
    }

    @Test
    void rejectsTypesInaccessibleToGeneratedRegistrations() {
        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class PrivatePayloadConsumer {
                    private static class Payload {
                    }

                    @Messaging.ReceiveFrom("orders")
                    void consume(Payload payload) {
                    }
                }
                """),
                         "not accessible from generated messaging code");

        assertDiagnostic(compile("""
                package com.example;

                import io.helidon.messaging.Emitter;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class PrivateEmitterProducer {
                    private static class Payload {
                    }

                    @Service.Inject
                    @Service.Named("orders")
                    Emitter<Payload> emitter;
                }
                """),
                         "not accessible from generated messaging code");
    }

    @Test
    void supportsHandlerDeclaringThrowableOutsideException() throws IOException {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class ThrowableConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(String value) throws Throwable {
                    }
                }
                """);

        assertCompilationSucceeded(result);
        String source = generatedSource(result, "ThrowableConsumer__MessagingConsumer_");
        assertTrue(source.contains("catch (Throwable t)"), source);
        assertTrue(source.contains("threw a checked Throwable outside Exception"), source);
    }

    private TestCompiler.Result compile(String source) {
        return TestCompiler.builder()
                .currentRelease()
                .addClasspath(COMPILER_CLASSPATH)
                .addClasspath(loadClass("io.helidon.builder.api.Prototype"))
                .addClasspath(loadClass("io.helidon.config.ConfigBuilderSupport"))
                .addClasspath(loadClass("io.helidon.messaging.Messaging"))
                .update(this::addProcessor)
                .printDiagnostics(false)
                .addSource("Consumer.java", source)
                .build()
                .compile();
    }

    private void addProcessor(TestCompiler.Builder builder) {
        try {
            Class<?> processorType = Class.forName("javax.annotation.processing.Processor");
            Object processor = Class.forName("io.helidon.codegen.apt.AptProcessor")
                    .getConstructor()
                    .newInstance();
            builder.getClass()
                    .getMethod("addProcessor", processorType)
                    .invoke(builder, processor);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not configure the test annotation processor", e);
        }
    }

    private String generatedSource(TestCompiler.Result result, String filePrefix) throws IOException {
        return Files.readString(generatedSourcePath(result, filePrefix));
    }

    private Path generateJavadocs(TestCompiler.Result result, Path... sources) throws IOException {
        DocumentationTool javadoc = ToolProvider.getSystemDocumentationTool();
        Path output = Files.createDirectories(result.classOutput().resolve("javadoc"));
        StringWriter diagnostics = new StringWriter();
        try (StandardJavaFileManager fileManager = javadoc.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            String classpath = result.classOutput() + File.pathSeparator + System.getProperty("java.class.path");
            Boolean success = javadoc.getTask(diagnostics,
                                               fileManager,
                                               null,
                                               null,
                                               List.of("-private",
                                                       "-quiet",
                                                       "-Xdoclint:syntax",
                                                       "-classpath",
                                                       classpath,
                                                       "-d",
                                                       output.toString()),
                                               fileManager.getJavaFileObjects(sources))
                    .call();
            assertTrue(success, diagnostics.toString());
        }
        return output;
    }

    private void assertRenderedJavadoc(Path output, Path source, String expected) throws IOException {
        String sourceFile = source.getFileName().toString();
        String htmlFile = sourceFile.substring(0, sourceFile.length() - ".java".length()) + ".html";
        String html = Files.readString(output.resolve("com/example").resolve(htmlFile));
        assertTrue(html.contains(expected), html);
    }

    private void assertSingleOccurrence(String source, String expected) {
        int first = source.indexOf(expected);
        assertTrue(first >= 0, "Generated source does not contain " + expected + ":\n" + source);
        assertTrue(source.indexOf(expected, first + expected.length()) < 0,
                   "Generated source contains more than one " + expected + ":\n" + source);
    }

    private Path generatedSourcePath(TestCompiler.Result result, String filePrefix) throws IOException {
        try (var generatedSources = Files.walk(result.sourceOutput())) {
            return generatedSources
                    .filter(path -> path.getFileName().toString().startsWith(filePrefix))
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().contains("__ServiceDescriptor"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Generated source not found for " + filePrefix));
        }
    }

    private void assertGeneratedTypeNamesBounded(TestCompiler.Result result,
                                                 String generatedTypeMarker) throws IOException {
        try (var generatedSources = Files.walk(result.sourceOutput())) {
            List<Path> generatedTypes = generatedSources
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().contains(generatedTypeMarker))
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .toList();
            assertFalse(generatedTypes.isEmpty(), "No generated types found for " + generatedTypeMarker);
            for (Path generatedType : generatedTypes) {
                String fileName = generatedType.getFileName().toString();
                String typeName = fileName.substring(0, fileName.length() - ".java".length());
                int typeNameBytes = typeName.getBytes(StandardCharsets.UTF_8).length;
                assertTrue(typeNameBytes <= 255 - ".class".length(),
                           "Generated type name cannot be represented by a portable class filename: " + fileName
                                   + " (" + typeNameBytes + " bytes)");
            }
        }
    }

    private void assertGeneratedTypeCount(TestCompiler.Result result,
                                          String generatedTypeMarker,
                                          int expectedRegistrations,
                                          int expectedDescriptors) throws IOException {
        try (var generatedSources = Files.walk(result.sourceOutput())) {
            List<String> generatedFiles = generatedSources
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.contains(generatedTypeMarker))
                    .filter(name -> name.endsWith(".java"))
                    .toList();
            long registrations = generatedFiles.stream()
                    .filter(name -> !name.contains("__ServiceDescriptor"))
                    .count();
            long descriptors = generatedFiles.stream()
                    .filter(name -> name.contains("__ServiceDescriptor"))
                    .count();
            assertTrue(registrations == expectedRegistrations,
                       "Expected " + expectedRegistrations + " generated registrations for " + generatedTypeMarker
                               + ", found " + registrations + ": " + generatedFiles);
            assertTrue(descriptors == expectedDescriptors,
                       "Expected " + expectedDescriptors + " generated descriptors for " + generatedTypeMarker
                               + ", found " + descriptors + ": " + generatedFiles);
        }
    }

    private Class<?> generatedClass(URLClassLoader classLoader,
                                    TestCompiler.Result result,
                                    String filePrefix) throws IOException, ClassNotFoundException {
        Path relativePath = result.sourceOutput().relativize(generatedSourcePath(result, filePrefix));
        String sourceName = relativePath.toString();
        String className = sourceName.substring(0, sourceName.length() - ".java".length())
                .replace(File.separatorChar, '.');
        return classLoader.loadClass(className);
    }

    private Object newRegistration(Class<?> registrationType,
                                   Object... arguments) throws ReflectiveOperationException {
        var constructors = registrationType.getDeclaredConstructors();
        assertTrue(constructors.length == 1,
                   "Expected one generated registration constructor for " + registrationType.getName());
        var constructor = constructors[0];
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

    private Object passthroughEntryPoints() {
        Class<?> entryPointsType = loadClass("io.helidon.messaging.MessagingEntryPoint$EntryPoints");
        return Proxy.newProxyInstance(
                entryPointsType.getClassLoader(),
                new Class<?>[] {entryPointsType},
                (proxy, method, arguments) -> arguments[arguments.length - 1]);
    }

    private void assertCachedTypeMetadata(Object registration,
                                          String genericMethod,
                                          String rawMethod) throws ReflectiveOperationException {
        Object genericType = invoke(registration, genericMethod);
        assertSame(genericType, invoke(registration, genericMethod));
        assertSame(invoke(genericType, "rawType"), invoke(registration, rawMethod));
    }

    private Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        var method = target.getClass().getMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Missing test compiler classpath type " + className, e);
        }
    }

    private void assertDiagnostic(TestCompiler.Result result, String expected) {
        String diagnostics = String.join("\n", result.diagnostics());
        assertFalse(result.success(), "Compilation unexpectedly succeeded");
        assertTrue(diagnostics.contains(expected), "Missing diagnostic '" + expected + "':\n" + diagnostics);
    }

    private void assertCompilationSucceeded(TestCompiler.Result result) {
        assertTrue(result.success(), "Compilation failed:\n" + String.join("\n", result.diagnostics()));
    }
}
