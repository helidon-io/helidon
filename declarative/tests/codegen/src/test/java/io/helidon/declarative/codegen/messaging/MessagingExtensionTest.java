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
import io.helidon.messaging.EmitterRegistration;
import io.helidon.messaging.FailurePolicy;
import io.helidon.messaging.Message;
import io.helidon.messaging.MessageBatch;
import io.helidon.messaging.ProcessorRegistration;
import io.helidon.service.registry.Service;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

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

        assertThat(MessagingExtension.isConcretePayloadType(listOfInteger), is(true));
        assertThat(MessagingExtension.isConcretePayloadType(TypeNames.WILDCARD), is(false));
        assertThat(MessagingExtension.isConcretePayloadType(typeVariable), is(false));
        assertThat(MessagingExtension.isConcretePayloadType(TypeName.builder(TypeNames.LIST)
                                                                      .addTypeArgument(TypeNames.WILDCARD)
                                                                      .build()),
                   is(false));
        assertThat(MessagingExtension.isConcretePayloadType(TypeName.builder(TypeNames.LIST)
                                                                      .addTypeArgument(typeVariable)
                                                                      .build()),
                   is(false));

        assertThat(MessagingExtension.hasUnresolvedTypeVariable(TypeNames.WILDCARD), is(false));
        assertThat(MessagingExtension.hasUnresolvedTypeVariable(typeVariable), is(true));
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
        assertThat(generatedSource, generatedSource.contains("payloadGenericType()"), is(true));
        assertThat(generatedSource,
                   generatedSource.contains("private static final GenericType<List<Integer>> PAYLOAD_GENERIC_TYPE"),
                   is(true));
        assertThat(generatedSource, generatedSource.contains("new GenericType<List<Integer>>()"), is(true));
        assertThat(generatedSource, generatedSource.contains("return PAYLOAD_GENERIC_TYPE;"), is(true));
        assertThat(generatedSource, generatedSource.contains("envelopeGenericType()"), is(true));
        assertThat(generatedSource,
                   generatedSource.contains("private static final GenericType<KeyedMessage<String, List<Integer>>> "
                                                    + "ENVELOPE_GENERIC_TYPE"),
                   is(true));
        assertThat(generatedSource, generatedSource.contains("new GenericType<KeyedMessage<String, List<Integer>>>()"), is(true));
        assertThat(generatedSource, generatedSource.contains("return ENVELOPE_GENERIC_TYPE;"), is(true));
        assertThat(generatedSource, generatedSource.contains("Class<?> payloadType()"), is(false));
        assertThat(generatedSource, generatedSource.contains("Class<?> envelopeType()"), is(false));
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
        assertThat(generatedSource, generatedSource.contains("new GenericType<Integer>()"), is(true));
        assertThat(generatedSource, generatedSource.contains("return PAYLOAD_GENERIC_TYPE;"), is(true));
        assertThat(generatedSource, generatedSource.contains("return PAYLOAD_GENERIC_TYPE.rawType();"), is(false));
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
        assertThat(generatedSource, generatedSource.contains("consumerInstance.consume((int) typedMessage.entity());"), is(true));

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {
                result.classOutput().toUri().toURL()
        }, getClass().getClassLoader())) {
            Class<?> consumerType = classLoader.loadClass("com.example.PrimitiveOverloadConsumer");
            var constructor = consumerType.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object consumer = constructor.newInstance();
            ConsumerRegistration registration = (ConsumerRegistration) newRegistration(
                    generatedClass(classLoader, result, "PrimitiveOverloadConsumer__MessagingConsumer_"),
                    consumer,
                    passthroughEntryPoints());

            registration.dispatch(MessageBatch.create(Message.create(42)));

            assertThat(invoke(consumer, "invocation"), is("primitive:42"));
        }
    }

    @Test
    void explicitEntityTreatsMessageSubtypeAsPayloadWhileUnannotatedSubtypeRemainsEnvelope() throws Exception {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Message;
                import io.helidon.messaging.MessageHeader;
                import io.helidon.messaging.MessageHeaders;
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
                    public MessageHeaders headers() {
                        return MessageHeaders.create(MessageHeader.create("tenant", "inner"));
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
        assertThat(entitySource, entitySource.contains("new GenericType<MessagePayload>()"), is(true));
        assertThat(entitySource, entitySource.contains("new GenericType<Message<MessagePayload>>()"), is(true));
        assertThat(entitySource, entitySource.contains("var typedMessage = (Message<MessagePayload>) message;"), is(true));
        assertThat(entitySource,
                   entitySource.contains("consumerInstance.consume(typedMessage.entity(), "
                                                + "typedMessage.header(\"tenant\").orElseThrow"),
                   is(true));

        String envelopeSource = generatedSource(result, "EnvelopeConsumer__MessagingConsumer_");
        assertThat(envelopeSource, envelopeSource.contains("new GenericType<String>()"), is(true));
        assertThat(envelopeSource, envelopeSource.contains("new GenericType<MessagePayload>()"), is(true));
        assertThat(envelopeSource, envelopeSource.contains("var typedMessage = (MessagePayload) message;"), is(true));
        assertThat(envelopeSource,
                   envelopeSource.contains("consumerInstance.consume(typedMessage, "
                                                  + "typedMessage.header(\"tenant\").orElseThrow"),
                   is(true));

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
                    entityConsumer,
                    passthroughEntryPoints());

            assertThat(entityRegistration.payloadGenericType().rawType(), sameInstance(payloadType));
            assertThat(entityRegistration.envelopeGenericType().rawType(), sameInstance(Message.class));

            entityRegistration.dispatch(MessageBatch.create(Message.builder(payload)
                                                                      .header("tenant", "outer")
                                                                      .build()));

            assertThat(invoke(entityConsumer, "received"), sameInstance(payload));
            assertThat(invoke(entityConsumer, "tenant"), is("outer"));

            Class<?> envelopeConsumerType = classLoader.loadClass("com.example.EnvelopeConsumer");
            var envelopeConsumerConstructor = envelopeConsumerType.getDeclaredConstructor();
            envelopeConsumerConstructor.setAccessible(true);
            Object envelopeConsumer = envelopeConsumerConstructor.newInstance();
            ConsumerRegistration envelopeRegistration = (ConsumerRegistration) newRegistration(
                    generatedClass(classLoader, result, "EnvelopeConsumer__MessagingConsumer_"),
                    envelopeConsumer,
                    passthroughEntryPoints());

            assertThat(envelopeRegistration.payloadGenericType().rawType(), sameInstance(String.class));
            assertThat(envelopeRegistration.envelopeGenericType().rawType(), sameInstance(payloadType));

            envelopeRegistration.dispatch(MessageBatch.create((Message<?>) payload));

            assertThat(invoke(envelopeConsumer, "received"), sameInstance(payload));
            assertThat(invoke(envelopeConsumer, "tenant"), is("inner"));
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
                import java.util.List;
                import java.util.Optional;

                import io.helidon.messaging.HeaderValue;
                import io.helidon.messaging.Message;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class InterceptedConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(Message<String> message,
                                 @Messaging.HeaderParam("required") String required,
                                 @Messaging.HeaderParam("optional") Optional<String> optional,
                                 @Messaging.HeaderParam("typed-required") HeaderValue typedRequired,
                                 @Messaging.HeaderParam("typed-optional") Optional<HeaderValue> typedOptional,
                                 @Messaging.HeaderParam("all") List<HeaderValue> all) throws IOException {
                    }
                }
                """);

        assertCompilationSucceeded(result);
        String source = generatedSource(result, "InterceptedConsumer__MessagingConsumer_");
        assertThat(source, source.contains("private final InterceptedConsumer consumer;"), is(true));
        assertThat(source, source.contains("Supplier<InterceptedConsumer>"), is(false));
        assertThat(source, source.contains("EntryPoints entryPoints"), is(true));
        assertThat(source, source.contains("entryPoints.handler("), is(true));
        assertThat(source, source.contains("descriptor.qualifiers()"), is(true));
        assertThat(source, source.contains("InterceptedConsumer__ServiceDescriptor.ANNOTATIONS"), is(true));
        assertThat(source, source.contains("InterceptedConsumer__ServiceDescriptor.METHOD_"), is(true));
        assertThat(source, source.contains("this::invoke"), is(true));
        assertThat(source, source.contains("Handler<InterceptedConsumer> handler"), is(true));
        assertThat(source, source.contains("void dispatch(MessageBatch<?> messages)"), is(true));
        assertThat(source, source.contains("dispatchMessage(messages.get(index));"), is(true));
        assertThat(source, source.contains("BatchDeliveryException.sequential("), is(false));
        assertThat(source, source.contains("throw sequentialFailure(messages, index, e);"), is(true));
        assertThat(source, source.contains("private static BatchDeliveryException sequentialFailure("), is(true));
        assertThat(source, source.contains("int failedIndex, Throwable cause"), is(true));
        assertThat(source, source.contains("new ArrayList<BatchItemOutcome>(messages.size())"), is(true));
        assertThat(source, source.contains("if (index < failedIndex)"), is(true));
        assertThat(source, source.contains("BatchItemOutcome.succeeded(index)"), is(true));
        assertThat(source, source.contains("else if (index == failedIndex)"), is(true));
        assertThat(source, source.contains("BatchItemOutcome.indeterminate(index, cause)"), is(true));
        assertThat(source, source.contains("BatchItemOutcome.notAttempted(index)"), is(true));
        assertThat(source, source.contains("return new BatchDeliveryException("), is(true));
        assertThat(source, source.contains("+ failedIndex, cause, messages, outcomes);"), is(true));
        assertThat(source, source.contains("handler.handle(consumer, message)"), is(true));
        assertThat(source, source.contains("invoke(InterceptedConsumer consumerInstance,"), is(true));
        assertThat(source, source.contains("consumerInstance.consume("), is(true));
        assertThat(source, source.contains("consumer.get().consume("), is(false));
        assertThat(source, source.contains("consumer.get()"), is(false));
        assertThat(source, source.contains("typedMessage.header(\"required\").orElseThrow"), is(true));
        assertThat(source, source.contains("typedMessage.header(\"optional\")"), is(true));
        assertThat(source, source.contains("typedMessage.headerValue(\"typed-required\").orElseThrow"), is(true));
        assertThat(source, source.contains("typedMessage.headerValue(\"typed-optional\")"), is(true));
        assertThat(source, source.contains("typedMessage.headers().all(\"all\")"), is(true));
        assertThat(source, source.contains("catch (RuntimeException | Error e)"), is(true));
        assertThat(source, source.contains("catch (Exception e)"), is(false));
        assertThat(source, source.contains("catch (Throwable t)"), is(true));
        assertThat(source, source.contains("throws Exception"), is(false));
    }

    @Test
    void generatedNonSingletonConsumersRetainSupplierLookup() throws IOException {
        TestCompiler.Result result = compile("""
                package com.example;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.PerLookup
                class PerLookupConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(String value) {
                    }
                }

                class ImplicitPerLookupConsumer {
                    @Service.Inject
                    ImplicitPerLookupConsumer() {
                    }

                    @Messaging.ReceiveFrom("audit")
                    void consume(String value) {
                    }
                }

                @Service.Scope
                @Retention(RetentionPolicy.CLASS)
                @Target(ElementType.TYPE)
                @interface CustomScope {
                }

                @CustomScope
                class CustomScopedConsumer {
                    @Messaging.ReceiveFrom("notifications")
                    void consume(String value) {
                    }
                }
                """);

        assertCompilationSucceeded(result);
        assertSupplierConsumer(result, "PerLookupConsumer");
        assertSupplierConsumer(result, "ImplicitPerLookupConsumer");
        assertSupplierConsumer(result, "CustomScopedConsumer");
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
        assertThat(source, source.contains("private static final FailurePolicy DECLARED_FAILURE_POLICY"), is(true));
        assertThat(source,
                   source.contains(".retry(retry -> retry.delay(Duration.parse(\"PT0.25S\")).maxAttempts(3))"),
                   is(true));
        assertThat(source, source.contains(".onExhausted(FailureDisposition.DEAD_LETTER)"), is(true));
        assertThat(source,
                   source.contains(".deadLetter(deadLetter -> deadLetter.channel(\"orders-dlq\"))"),
                   is(true));
        assertThat(source, source.contains("Optional<FailurePolicy> declaredFailurePolicy()"), is(true));
        assertThat(source, source.contains("return Optional.of(DECLARED_FAILURE_POLICY);"), is(true));

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {
                result.classOutput().toUri().toURL()
        }, getClass().getClassLoader())) {
            Class<?> registrationType = generatedClass(classLoader,
                                                       result,
                                                       "FailurePolicyConsumer__MessagingConsumer_");
            ConsumerRegistration registration = (ConsumerRegistration) newRegistration(
                    registrationType,
                    (Object) null,
                    passthroughEntryPoints());
            FailurePolicy policy = registration.declaredFailurePolicy().orElseThrow();
            assertThat(policy.retry().delay(), is(Duration.ofMillis(250)));
            assertThat(policy.retry().maxAttempts(), is(3));
            assertThat(policy.onExhausted().toString(), is("DEAD_LETTER"));
            assertThat(policy.deadLetter().orElseThrow().channel(), is("orders-dlq"));
            assertThat(registration.declaredFailurePolicy().orElseThrow(), sameInstance(policy));
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
        assertThat(annotatedSource, annotatedSource.contains("DECLARED_FAILURE_POLICY"), is(true));
        assertThat(annotatedSource,
                   annotatedSource.contains(".retry(retry -> retry.delay(Duration.parse(\"PT1S\")).maxAttempts(0))"),
                   is(true));
        assertThat(annotatedSource, annotatedSource.contains(".onExhausted(FailureDisposition.FAIL)"), is(true));

        String unannotatedSource = generatedSource(result, "UnannotatedConsumer__MessagingConsumer_");
        assertThat(unannotatedSource, unannotatedSource.contains("DECLARED_FAILURE_POLICY"), is(false));
        assertThat(unannotatedSource, unannotatedSource.contains("declaredFailurePolicy()"), is(false));

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {
                result.classOutput().toUri().toURL()
        }, getClass().getClassLoader())) {
            ConsumerRegistration annotated = (ConsumerRegistration) newRegistration(
                    generatedClass(classLoader, result, "BareFailurePolicyConsumer__MessagingConsumer_"),
                    (Object) null,
                    passthroughEntryPoints());
            FailurePolicy policy = annotated.declaredFailurePolicy().orElseThrow();
            assertThat(policy.retry().delay(), is(Duration.ofSeconds(1)));
            assertThat(policy.retry().maxAttempts(), is(0));
            assertThat(policy.onExhausted().toString(), is("FAIL"));
            assertThat(policy.deadLetter(), is(Optional.empty()));

            ConsumerRegistration unannotated = (ConsumerRegistration) newRegistration(
                    generatedClass(classLoader, result, "UnannotatedConsumer__MessagingConsumer_"),
                    (Object) null,
                    passthroughEntryPoints());
            assertThat(unannotated.declaredFailurePolicy(), is(Optional.empty()));
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
        assertThat(source, source.contains("implements ProcessorRegistration"), is(true));
        assertThat(source, source.contains("String outgoingChannel()"), is(true));
        assertThat(source, source.contains("return \"audit\";"), is(true));
        assertThat(source, source.contains("new GenericType<Integer>()"), is(true));
        assertThat(source, source.contains("new GenericType<Message<Integer>>()"), is(true));
        assertThat(source, source.contains("outgoingPayloadGenericType()"), is(true));
        assertThat(source, source.contains("outgoingEnvelopeGenericType()"), is(true));
        assertThat(source, source.contains("Class<?> payloadType()"), is(false));
        assertThat(source, source.contains("Class<?> envelopeType()"), is(false));
        assertThat(source, source.contains("Class<?> outgoingPayloadType()"), is(false));
        assertThat(source, source.contains("Class<?> outgoingEnvelopeType()"), is(false));
        assertThat(source, source.contains("MessageBatch<?> process(MessageBatch<?> messages)"), is(true));
        assertThat(source, source.contains("processedMessages.add(processMessage(messages.get(index)));"), is(true));
        assertThat(source, source.contains("return messages.derive(processedMessages);"), is(true));
        assertThat(source, source.contains("BatchDeliveryException.attemptedPrefix("), is(false));
        assertThat(source, source.contains("throw attemptedPrefixFailure(messages, index, e);"), is(true));
        assertThat(source, source.contains("private static BatchDeliveryException attemptedPrefixFailure("), is(true));
        assertThat(source, source.contains("int failedIndex, Throwable cause"), is(true));
        assertThat(source, source.contains("new ArrayList<BatchItemOutcome>(messages.size())"), is(true));
        assertThat(source, source.contains("if (index <= failedIndex)"), is(true));
        assertThat(source, source.contains("BatchItemOutcome.indeterminate(index, cause)"), is(true));
        assertThat(source, source.contains("BatchItemOutcome.notAttempted(index)"), is(true));
        assertThat(source, source.contains("return new BatchDeliveryException("), is(true));
        assertThat(source, source.contains("+ failedIndex, cause, messages, outcomes);"), is(true));
        assertThat(source, source.contains("Handler<PayloadProcessor> handler"), is(true));
        assertThat(source, source.contains("handler.handle(consumer, message)"), is(true));
        assertThat(source, source.contains("invoke(PayloadProcessor consumerInstance,"), is(true));
        assertThat(source, source.contains("consumerInstance.process("), is(true));
        assertThat(source, source.contains("consumer.get().process("), is(false));
        assertThat(source, source.contains("consumer.get()"), is(false));
        assertThat(source, source.contains("Objects.requireNonNull("), is(true));
        assertThat(source, source.contains("Message.create(result)"), is(true));
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
        assertThat(source, source.contains("new GenericType<Message<Integer>>()"), is(true));
        assertThat(source, source.contains("return Optional.of(result);"), is(true));
        assertThat(source, source.contains("Message.create(result)"), is(false));
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
        assertThat(source, source.contains("new GenericType<String[][]>()"), is(true));
        assertThat(source, source.contains("new GenericType<ArrayMessage<String>>()"), is(true));

        String consumerSource = generatedSource(result, "ArrayPayloadConsumer__MessagingConsumer_");
        assertThat(consumerSource, consumerSource.contains("new GenericType<String[][]>()"), is(true));
        assertThat(consumerSource, consumerSource.contains("new GenericType<Message<String[][]>>()"), is(true));
        assertThat(consumerSource, consumerSource.contains("return PAYLOAD_GENERIC_TYPE;"), is(true));
        assertThat(consumerSource, consumerSource.contains("return ENVELOPE_GENERIC_TYPE;"), is(true));
        assertThat(consumerSource, consumerSource.contains("return PAYLOAD_GENERIC_TYPE.rawType();"), is(false));
        assertThat(consumerSource, consumerSource.contains("return ENVELOPE_GENERIC_TYPE.rawType();"), is(false));
    }

    @Test
    void preservesArraysOfAsyncTypesAsSynchronousPayloads() throws IOException {
        TestCompiler.Result result = compile("""
                package com.example;

                import java.util.concurrent.CompletableFuture;
                import java.util.concurrent.Flow;
                import java.util.concurrent.Future;
                import java.util.stream.Stream;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class CompletableFutureArrayProcessor {
                    @Messaging.ReceiveFrom("completable-future-in")
                    @Messaging.SendTo("completable-future-out")
                    CompletableFuture<String>[] process(String value) {
                        return null;
                    }
                }

                @Service.Singleton
                class FutureArrayProcessor {
                    @Messaging.ReceiveFrom("future-in")
                    @Messaging.SendTo("future-out")
                    Future<String>[] process(String value) {
                        return null;
                    }
                }

                @Service.Singleton
                class StreamArrayProcessor {
                    @Messaging.ReceiveFrom("stream-in")
                    @Messaging.SendTo("stream-out")
                    Stream<String>[] process(String value) {
                        return null;
                    }
                }

                @Service.Singleton
                class PublisherArrayProcessor {
                    @Messaging.ReceiveFrom("publisher-in")
                    @Messaging.SendTo("publisher-out")
                    Flow.Publisher<String>[] process(String value) {
                        return null;
                    }
                }
                """);

        assertCompilationSucceeded(result);
        assertThat(generatedSource(result, "CompletableFutureArrayProcessor__MessagingConsumer_")
                           .contains("new GenericType<CompletableFuture<String>[]>()"), is(true));
        assertThat(generatedSource(result, "FutureArrayProcessor__MessagingConsumer_")
                           .contains("new GenericType<Future<String>[]>()"), is(true));
        assertThat(generatedSource(result, "StreamArrayProcessor__MessagingConsumer_")
                           .contains("new GenericType<Stream<String>[]>()"), is(true));
        assertThat(generatedSource(result, "PublisherArrayProcessor__MessagingConsumer_")
                           .contains("new GenericType<Flow.Publisher<String>[]>()"), is(true));
    }

    @Test
    void treatsMessageArrayParameterAsPayload() throws IOException {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Message;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                interface CustomMessage<T> extends Message<T> {
                }

                @Service.Singleton
                class MessageArrayConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(Message<String>[] messages) {
                    }
                }

                @Service.Singleton
                class CustomMessageArrayConsumer {
                    @Messaging.ReceiveFrom("custom-orders")
                    void consume(CustomMessage<String>[] messages) {
                    }
                }
                """);

        assertCompilationSucceeded(result);
        String source = generatedSource(result, "MessageArrayConsumer__MessagingConsumer_");
        assertThat(source, source.contains("new GenericType<Message<String>[]>()"), is(true));
        assertThat(source, source.contains("new GenericType<Message<Message<String>[]>>()"), is(true));
        assertThat(source, source.contains("var typedMessage = (Message<Message<String>[]>) message;"), is(true));
        assertThat(source, source.contains("consumerInstance.consume(typedMessage.entity());"), is(true));

        String customSource = generatedSource(result, "CustomMessageArrayConsumer__MessagingConsumer_");
        assertThat(customSource, customSource.contains("new GenericType<CustomMessage<String>[]>()"), is(true));
        assertThat(customSource, customSource.contains("new GenericType<Message<CustomMessage<String>[]>>()"), is(true));
        assertThat(customSource, customSource.contains("consumerInstance.consume(typedMessage.entity());"), is(true));
    }

    @Test
    void treatsArraysOfBatchLikeTypesAsPayloads() throws IOException {
        TestCompiler.Result result = compile("""
                package com.example;

                import java.util.List;

                import io.helidon.messaging.Message;
                import io.helidon.messaging.MessageBatch;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class MessageBatchArrayConsumer {
                    @Messaging.ReceiveFrom("batches")
                    void consume(MessageBatch<String>[] batches) {
                    }
                }

                @Service.Singleton
                class MessageListArrayConsumer {
                    @Messaging.ReceiveFrom("lists")
                    void consume(List<Message<String>>[] messages) {
                    }
                }
                """);

        assertCompilationSucceeded(result);
        String batchSource = generatedSource(result, "MessageBatchArrayConsumer__MessagingConsumer_");
        assertThat(batchSource, batchSource.contains("new GenericType<MessageBatch<String>[]>()"), is(true));
        assertThat(batchSource, batchSource.contains("new GenericType<Message<MessageBatch<String>[]>>()"), is(true));

        String listSource = generatedSource(result, "MessageListArrayConsumer__MessagingConsumer_");
        assertThat(listSource, listSource.contains("new GenericType<List<Message<String>>[]>()"), is(true));
        assertThat(listSource, listSource.contains("new GenericType<Message<List<Message<String>>[]>>()"), is(true));
    }

    @Test
    void normalizesVarargConsumerToArrayPayload() throws IOException {
        TestCompiler.Result result = compile("""
                package com.example;

                import java.util.List;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class VarargConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(String... values) {
                    }
                }

                @Service.Singleton
                class PrimitiveVarargConsumer {
                    @Messaging.ReceiveFrom("numbers")
                    void consume(int... values) {
                    }
                }

                @Service.Singleton
                class ParameterizedVarargConsumer {
                    @Messaging.ReceiveFrom("lists")
                    void consume(List<String>... values) {
                    }
                }
                """);

        assertCompilationSucceeded(result);
        String source = generatedSource(result, "VarargConsumer__MessagingConsumer_");
        assertThat(source, source.contains("new GenericType<String[]>()"), is(true));
        assertThat(source, source.contains("new GenericType<Message<String[]>>()"), is(true));
        assertThat(source, source.contains("var typedMessage = (Message<String[]>) message;"), is(true));
        assertThat(source, source.contains("consumerInstance.consume(typedMessage.entity());"), is(true));

        String primitiveSource = generatedSource(result, "PrimitiveVarargConsumer__MessagingConsumer_");
        assertThat(primitiveSource, primitiveSource.contains("new GenericType<int[]>()"), is(true));
        assertThat(primitiveSource, primitiveSource.contains("new GenericType<Message<int[]>>()"), is(true));
        assertThat(primitiveSource, primitiveSource.contains("consumerInstance.consume(typedMessage.entity());"), is(true));

        String parameterizedSource = generatedSource(result, "ParameterizedVarargConsumer__MessagingConsumer_");
        assertThat(parameterizedSource, parameterizedSource.contains("new GenericType<List<String>[]>()"), is(true));
        assertThat(parameterizedSource, parameterizedSource.contains("new GenericType<Message<List<String>[]>>()"), is(true));
        assertThat(parameterizedSource, parameterizedSource.contains("consumerInstance.consume(typedMessage.entity());"),
                   is(true));
    }

    @Test
    void rejectsUnresolvedGenericVarargPayload() {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class GenericVarargConsumer {
                    @Messaging.ReceiveFrom("orders")
                    <T> void consume(T... values) {
                    }
                }
                """);

        assertDiagnostic(result, "must not use wildcards or unresolved type variables");
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
        assertThat(source, source.contains("new GenericType<String[][]>()"), is(true));
        assertThat(source, source.contains("new GenericType<Message<String[][]>>()"), is(true));
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
        assertThat(source, source.contains("BatchHandler<BatchConsumer> batchHandler"), is(true));
        assertThat(source, source.contains("entryPoints.batchHandler("), is(true));
        assertThat(source, source.contains("this::invokeBatch"), is(true));
        assertThat(source, source.contains("void dispatch(MessageBatch<?> messages)"), is(true));
        assertThat(source, source.contains("dispatchBatch("), is(false));
        assertThat(source, source.contains("batchType()"), is(false));
        assertThat(source, source.contains("batchGenericType()"), is(false));
        assertThat(source, source.contains("boolean batch()"), is(false));
        assertThat(source, source.contains("batchHandler.handle(consumer, messages);"), is(true));
        assertThat(source, source.contains("invokeBatch(BatchConsumer consumerInstance,"), is(true));
        assertThat(source, source.contains("var typedMessages = (MessageBatch<String>) messages;"), is(true));
        assertThat(source, source.contains("consumerInstance.consume(typedMessages);"), is(true));
        assertThat(source, source.contains("consumer.get().consume("), is(false));
        assertThat(source, source.contains("consumer.get()"), is(false));
        assertThat(source, source.contains("catch (Throwable t)"), is(true));
        assertThat(source, source.contains("throws Exception"), is(false));
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
        assertThat(source, source.contains("Emitter<List<String>>, EmitterRegistration"), is(true));
        assertThat(source, source.contains("String channel()"), is(true));
        assertThat(source, source.contains("return \"orders\";"), is(true));
        assertThat(source, source.contains("String producerId()"), is(true));
        assertThat(source, source.contains("com.example.MetadataEmitterProducer#emitter:orders"), is(true));
        assertThat(source, source.contains("new GenericType<List<String>>()"), is(true));
        assertThat(source, source.contains("new GenericType<Message<List<String>>>()"), is(true));
        assertThat(source, source.contains("payloadGenericType()"), is(true));
        assertThat(source, source.contains("envelopeGenericType()"), is(true));
        assertThat(source, source.contains("Class<?> payloadType()"), is(false));
        assertThat(source, source.contains("Class<?> envelopeType()"), is(false));
        assertThat(source, source.contains("void emit(MessageBatch<? extends List<String>> messages)"), is(true));
        assertThat(source, source.contains("void emitBatch("), is(false));
        assertThat(source, source.contains("void emitMessage("), is(false));
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
            ProcessorRegistration processor = (ProcessorRegistration) newRegistration(
                    processorType,
                    (Object) null,
                    passthroughEntryPoints());
            assertCachedTypeMetadata(processor::payloadGenericType);
            assertCachedTypeMetadata(processor::envelopeGenericType);
            assertCachedTypeMetadata(processor::outgoingPayloadGenericType);
            assertCachedTypeMetadata(processor::outgoingEnvelopeGenericType);

            Class<?> emitterType = generatedClass(classLoader,
                                                   result,
                                                   "CachedMetadataService__MessagingEmitter_");
            EmitterRegistration emitter = (EmitterRegistration) newRegistration(
                    emitterType,
                    (Supplier<Object>) () -> null);
            assertCachedTypeMetadata(emitter::payloadGenericType);
            assertCachedTypeMetadata(emitter::envelopeGenericType);
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
                         "must be String, Optional<String>, HeaderValue, Optional<HeaderValue>, or List<HeaderValue>");

        assertDiagnostic(compile("""
                package com.example;

                import java.util.Optional;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class InvalidOptionalHeaderConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(@Messaging.Entity String entity,
                                 @Messaging.HeaderParam("attempt") Optional<Integer> attempt) {
                    }
                }
                """),
                         "must be String, Optional<String>, HeaderValue, Optional<HeaderValue>, or List<HeaderValue>");

        assertDiagnostic(compile("""
                package com.example;

                import java.util.List;

                import io.helidon.messaging.HeaderValue;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class InvalidArrayHeaderConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(@Messaging.Entity String entity,
                                 @Messaging.HeaderParam("attempt") List<HeaderValue>[] attempt) {
                    }
                }
                """),
                         "must be String, Optional<String>, HeaderValue, Optional<HeaderValue>, or List<HeaderValue>");

        assertDiagnostic(compile("""
                package com.example;

                import java.util.List;

                import io.helidon.messaging.HeaderValue;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class InvalidArrayValueHeaderConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(@Messaging.Entity String entity,
                                 @Messaging.HeaderParam("attempt") List<HeaderValue[]> attempt) {
                    }
                }
                """),
                         "must be String, Optional<String>, HeaderValue, Optional<HeaderValue>, or List<HeaderValue>");

        assertDiagnostic(compile("""
                package com.example;

                import java.util.List;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class InvalidListHeaderConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(@Messaging.Entity String entity,
                                 @Messaging.HeaderParam("attempt") List<String> attempt) {
                    }
                }
                """),
                         "must be String, Optional<String>, HeaderValue, Optional<HeaderValue>, or List<HeaderValue>");

        assertDiagnostic(compile("""
                package com.example;

                import java.util.List;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class InvalidRawListHeaderConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(@Messaging.Entity String entity,
                                 @Messaging.HeaderParam("attempt") List attempt) {
                    }
                }
                """),
                         "must be String, Optional<String>, HeaderValue, Optional<HeaderValue>, or List<HeaderValue>");

        assertDiagnostic(compile("""
                package com.example;

                import java.util.Optional;

                import io.helidon.messaging.HeaderValue;
                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class InvalidWildcardHeaderConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(@Messaging.Entity String entity,
                                 @Messaging.HeaderParam("attempt") Optional<? extends HeaderValue> attempt) {
                    }
                }
                """),
                         "must be String, Optional<String>, HeaderValue, Optional<HeaderValue>, or List<HeaderValue>");

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
            assertThat("Expected two distinct generated emitter registrations, found " + registrations,
                       registrations == 2,
                       is(true));
            assertThat("Expected two generated emitter service descriptors, found " + descriptors, descriptors == 2, is(true));
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
        assertThat(consumerSource, consumerSource.contains("channel <code>orders*&#47;x</code>."), is(true));
        assertThat(emitterSource, emitterSource.contains("channel <code>orders*&#47;x</code>."), is(true));
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
        assertThat(consumerSource, consumerSource.contains("channel <code>&#92;u002a&#92;u002f</code>."), is(true));
        assertThat(emitterSource, emitterSource.contains("channel <code>&#92;u002a&#92;u002f</code>."), is(true));
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
        assertThat(Files.readString(consumerSource).contains(escapedChannel), is(true));
        assertThat(Files.readString(emitterSource).contains(escapedChannel), is(true));
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
            assertThat("Expected two distinct generated consumer registrations, found " + registrations,
                       registrations == 2,
                       is(true));
            assertThat("Expected two generated consumer service descriptors, found " + descriptors, descriptors == 2, is(true));
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
        assertThat(source, source.contains("catch (Throwable t)"), is(true));
        assertThat(source, source.contains("Messaging handler com.example.ThrowableConsumer#consume"), is(true));
        assertThat(source, source.contains("failed"), is(true));
        assertThat(source, source.contains("throws Exception"), is(false));
    }

    @Test
    void generatedCheckedInterruptionRestoresInterruptBeforeWrapping() throws IOException {
        TestCompiler.Result result = compile("""
                package com.example;

                import io.helidon.messaging.Messaging;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class InterruptedConsumer {
                    @Messaging.ReceiveFrom("orders")
                    void consume(String value) throws InterruptedException {
                        throw new InterruptedException("interrupted");
                    }
                }
                """);

        assertCompilationSucceeded(result);
        String source = generatedSource(result, "InterruptedConsumer__MessagingConsumer_");
        assertThat(source, source.contains("if (t instanceof InterruptedException)"), is(true));
        assertThat(source, source.contains("Thread.currentThread().interrupt();"), is(true));
        assertThat(source, source.contains("throws Exception"), is(false));
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
            assertThat(diagnostics.toString(), success, is(true));
        }
        return output;
    }

    private void assertRenderedJavadoc(Path output, Path source, String expected) throws IOException {
        String sourceFile = source.getFileName().toString();
        String htmlFile = sourceFile.substring(0, sourceFile.length() - ".java".length()) + ".html";
        String html = Files.readString(output.resolve("com/example").resolve(htmlFile));
        assertThat(html, html.contains(expected), is(true));
    }

    private void assertSingleOccurrence(String source, String expected) {
        int first = source.indexOf(expected);
        assertThat("Generated source does not contain " + expected + ":\n" + source, first >= 0, is(true));
        assertThat("Generated source contains more than one " + expected + ":\n" + source,
                   source.indexOf(expected, first + expected.length()) < 0,
                   is(true));
    }

    private void assertSupplierConsumer(TestCompiler.Result result, String consumerType) throws IOException {
        String source = generatedSource(result, consumerType + "__MessagingConsumer_");
        assertThat(source, source.contains("private final Supplier<" + consumerType + "> consumer;"), is(true));
        assertThat(source, source.contains("Supplier<" + consumerType + "> consumer,"), is(true));
        assertThat(source, source.contains("handler.handle(consumer.get(), message)"), is(true));
        assertSingleOccurrence(source, "consumer.get()");
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
            assertThat("No generated types found for " + generatedTypeMarker, generatedTypes.isEmpty(), is(false));
            for (Path generatedType : generatedTypes) {
                String fileName = generatedType.getFileName().toString();
                String typeName = fileName.substring(0, fileName.length() - ".java".length());
                int typeNameBytes = typeName.getBytes(StandardCharsets.UTF_8).length;
                assertThat("Generated type name cannot be represented by a portable class filename: " + fileName
                                   + " (" + typeNameBytes + " bytes)",
                           typeNameBytes <= 255 - ".class".length(),
                           is(true));
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
            assertThat("Expected " + expectedRegistrations + " generated registrations for " + generatedTypeMarker
                               + ", found " + registrations + ": " + generatedFiles,
                       registrations == expectedRegistrations,
                       is(true));
            assertThat("Expected " + expectedDescriptors + " generated descriptors for " + generatedTypeMarker
                               + ", found " + descriptors + ": " + generatedFiles,
                       descriptors == expectedDescriptors,
                       is(true));
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
        assertThat("Expected one generated registration constructor for " + registrationType.getName(),
                   constructors.length == 1,
                   is(true));
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

    private void assertCachedTypeMetadata(Supplier<?> accessor) {
        Object genericType = accessor.get();
        assertThat(accessor.get(), sameInstance(genericType));
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
        assertThat("Compilation unexpectedly succeeded", result.success(), is(false));
        assertThat("Missing diagnostic '" + expected + "':\n" + diagnostics, diagnostics.contains(expected), is(true));
    }

    private void assertCompilationSucceeded(TestCompiler.Result result) {
        assertThat("Compilation failed:\n" + String.join("\n", result.diagnostics()), result.success(), is(true));
    }
}
