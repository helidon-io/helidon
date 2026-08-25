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

package io.helidon.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeclarativeRegistrationTest {
    private static final GenericType<String> STRING_TYPE = new GenericType<>() { };
    private static final GenericType<Integer> INTEGER_TYPE = new GenericType<>() { };
    private static final GenericType<Message<String>> STRING_MESSAGE_TYPE = new GenericType<>() { };
    private static final GenericType<Message<Integer>> INTEGER_MESSAGE_TYPE = new GenericType<>() { };
    private static final GenericType<?> PRIMITIVE_INT_TYPE = GenericType.create(int.class);

    @Test
    void rejectsDuplicateGeneratedHandlerAndProducerIdentities() {
        ConsumerRegistration first = consumer("orders", "service#consume", ignored -> { });
        ConsumerRegistration second = consumer("orders", "service#consume", ignored -> { });

        IllegalArgumentException handlerFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(first, second), Config.empty(), List.of()));
        assertThat(handlerFailure.getMessage(), containsString("Duplicate messaging handler registration service#consume"));

        ConsumerRegistration target = consumer("orders", "target#consume", ignored -> { });
        EmitterRegistration producerOne = emitter("orders", "publisher#orders", STRING_TYPE, STRING_MESSAGE_TYPE);
        EmitterRegistration producerTwo = emitter("orders", "publisher#orders", STRING_TYPE, STRING_MESSAGE_TYPE);
        IllegalArgumentException producerFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(target),
                                          List.of(producerOne, producerTwo),
                                          Config.empty(),
                                          List.of()));
        assertThat(producerFailure.getMessage(),
                   containsString("Duplicate messaging producer registration publisher#orders"));
    }

    @Test
    void rejectsInconsistentManualConsumerAndProcessorTypeMetadata() {
        ConsumerRegistration inconsistentPayload = consumer("orders",
                                                            "manual#payload",
                                                            Integer.class,
                                                            STRING_TYPE,
                                                            Message.class,
                                                            STRING_MESSAGE_TYPE,
                                                            ignored -> { });
        IllegalArgumentException payloadFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(inconsistentPayload), Config.empty(), List.of()));
        assertThat(payloadFailure.getMessage(),
                   is("Messaging handler manual#payload payload raw type java.lang.Integer"
                              + " does not match generic raw type java.lang.String"));

        ConsumerRegistration inconsistentEnvelope = consumer("orders",
                                                             "manual#envelope",
                                                             String.class,
                                                             STRING_TYPE,
                                                             SpecialMessage.class,
                                                             STRING_MESSAGE_TYPE,
                                                             ignored -> { });
        IllegalArgumentException envelopeFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(inconsistentEnvelope), Config.empty(), List.of()));
        assertThat(envelopeFailure.getMessage(),
                   is("Messaging handler manual#envelope envelope raw type " + SpecialMessage.class.getName()
                              + " does not match generic raw type " + Message.class.getName()));

        ProcessorRegistration inconsistentOutgoingPayload = processor("orders",
                                                                       "audit",
                                                                       "manual#outgoing-payload",
                                                                       Integer.class,
                                                                       STRING_TYPE,
                                                                       Message.class,
                                                                       STRING_MESSAGE_TYPE,
                                                                       Function.identity());
        IllegalArgumentException outgoingPayloadFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(inconsistentOutgoingPayload), Config.empty(), List.of()));
        assertThat(outgoingPayloadFailure.getMessage(),
                   is("Messaging processor manual#outgoing-payload outgoing payload raw type java.lang.Integer"
                              + " does not match generic raw type java.lang.String"));

        ProcessorRegistration inconsistentOutgoingEnvelope = processor("orders",
                                                                        "audit",
                                                                        "manual#outgoing-envelope",
                                                                        String.class,
                                                                        STRING_TYPE,
                                                                        SpecialMessage.class,
                                                                        STRING_MESSAGE_TYPE,
                                                                        Function.identity());
        IllegalArgumentException outgoingEnvelopeFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(inconsistentOutgoingEnvelope), Config.empty(), List.of()));
        assertThat(outgoingEnvelopeFailure.getMessage(),
                   is("Messaging processor manual#outgoing-envelope outgoing envelope raw type "
                              + SpecialMessage.class.getName() + " does not match generic raw type "
                              + Message.class.getName()));
    }

    @Test
    void rejectsPayloadTypesThatContradictEnvelopePayloadTypes() {
        ConsumerRegistration inconsistentConsumer = consumer("orders",
                                                             "manual#payload-envelope",
                                                             STRING_TYPE,
                                                             INTEGER_MESSAGE_TYPE,
                                                             ignored -> { });
        IllegalArgumentException consumerFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(inconsistentConsumer), Config.empty(), List.of()));
        assertThat(consumerFailure.getMessage(),
                   is("Messaging handler manual#payload-envelope payload generic type java.lang.String"
                              + " does not match envelope payload type java.lang.Integer declared by "
                              + "io.helidon.messaging.Message<java.lang.Integer>"));

        ProcessorRegistration inconsistentProcessor = processor("orders",
                                                                 "audit",
                                                                 "manual#outgoing-payload-envelope",
                                                                 STRING_TYPE,
                                                                 INTEGER_MESSAGE_TYPE,
                                                                 Function.identity());
        IllegalArgumentException processorFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(inconsistentProcessor), Config.empty(), List.of()));
        assertThat(processorFailure.getMessage(),
                   is("Messaging processor manual#outgoing-payload-envelope outgoing payload generic type "
                              + "java.lang.String does not match envelope payload type java.lang.Integer declared by "
                              + "io.helidon.messaging.Message<java.lang.Integer>"));

        EmitterRegistration inconsistentEmitter = emitter("orders",
                                                          "manual#emitter-payload-envelope",
                                                          STRING_TYPE,
                                                          INTEGER_MESSAGE_TYPE);
        IllegalArgumentException emitterFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(), List.of(inconsistentEmitter), Config.empty(), List.of()));
        assertThat(emitterFailure.getMessage(),
                   is("Messaging emitter manual#emitter-payload-envelope payload generic type java.lang.String"
                              + " does not match envelope payload type java.lang.Integer declared by "
                              + "io.helidon.messaging.Message<java.lang.Integer>"));
    }

    @Test
    void rejectsEnvelopeMetadataThatIsNotAMessageType() {
        ConsumerRegistration inconsistentConsumer = consumer("orders",
                                                             "manual#non-message-envelope",
                                                             String.class,
                                                             STRING_TYPE,
                                                             String.class,
                                                             STRING_TYPE,
                                                             ignored -> { });
        IllegalArgumentException consumerFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(inconsistentConsumer), Config.empty(), List.of()));
        assertThat(consumerFailure.getMessage(),
                   is("Messaging handler manual#non-message-envelope envelope type java.lang.String must implement "
                              + Message.class.getName()));

        ProcessorRegistration inconsistentProcessor = processor("orders",
                                                                 "audit",
                                                                 "manual#non-message-outgoing-envelope",
                                                                 String.class,
                                                                 STRING_TYPE,
                                                                 String.class,
                                                                 STRING_TYPE,
                                                                 Function.identity());
        IllegalArgumentException processorFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(inconsistentProcessor), Config.empty(), List.of()));
        assertThat(processorFailure.getMessage(),
                   is("Messaging processor manual#non-message-outgoing-envelope outgoing envelope type java.lang.String"
                              + " must implement " + Message.class.getName()));

        EmitterRegistration inconsistentEmitter = emitter("orders",
                                                          "manual#non-message-emitter-envelope",
                                                          STRING_TYPE,
                                                          STRING_TYPE);
        IllegalArgumentException emitterFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(), List.of(inconsistentEmitter), Config.empty(), List.of()));
        assertThat(emitterFailure.getMessage(),
                   is("Messaging emitter manual#non-message-emitter-envelope envelope type java.lang.String"
                              + " must implement " + Message.class.getName()));
    }

    @Test
    void rejectsPrimitiveManualMetadataAndAcceptsBoxedPayloadMetadata() {
        ConsumerRegistration primitiveConsumer = consumer("numbers",
                                                          "manual#primitive-payload",
                                                          int.class,
                                                          PRIMITIVE_INT_TYPE,
                                                          Message.class,
                                                          INTEGER_MESSAGE_TYPE,
                                                          ignored -> { });
        IllegalArgumentException primitiveFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(primitiveConsumer), Config.empty(), List.of()));
        assertThat(primitiveFailure.getMessage(),
                   is("Messaging handler manual#primitive-payload payload raw type must not be primitive: int"));

        ConsumerRegistration primitiveGenericConsumer = consumer("numbers",
                                                                 "manual#primitive-generic-payload",
                                                                 Integer.class,
                                                                 PRIMITIVE_INT_TYPE,
                                                                 Message.class,
                                                                 INTEGER_MESSAGE_TYPE,
                                                                 ignored -> { });
        IllegalArgumentException primitiveGenericFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(primitiveGenericConsumer), Config.empty(), List.of()));
        assertThat(primitiveGenericFailure.getMessage(),
                   is("Messaging handler manual#primitive-generic-payload payload generic raw type"
                              + " must not be primitive: int"));

        ProcessorRegistration primitiveProcessor = processor("numbers",
                                                              "audit",
                                                              "manual#primitive-outgoing-payload",
                                                              int.class,
                                                              PRIMITIVE_INT_TYPE,
                                                              Message.class,
                                                              INTEGER_MESSAGE_TYPE,
                                                              Function.identity());
        IllegalArgumentException primitiveProcessorFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(primitiveProcessor), Config.empty(), List.of()));
        assertThat(primitiveProcessorFailure.getMessage(),
                   is("Messaging processor manual#primitive-outgoing-payload outgoing payload raw type"
                              + " must not be primitive: int"));

        EmitterRegistration primitiveEmitter = emitter("numbers",
                                                       "manual#primitive-emitter-payload",
                                                       PRIMITIVE_INT_TYPE,
                                                       INTEGER_MESSAGE_TYPE);
        IllegalArgumentException primitiveEmitterFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(), List.of(primitiveEmitter), Config.empty(), List.of()));
        assertThat(primitiveEmitterFailure.getMessage(),
                   is("Messaging emitter manual#primitive-emitter-payload payload raw type must not be primitive: int"));

        AtomicReference<Message<?>> received = new AtomicReference<>();
        ConsumerRegistration boxedConsumer = consumer("numbers",
                                                      "manual#boxed-payload",
                                                      INTEGER_TYPE,
                                                      INTEGER_MESSAGE_TYPE,
                                                      received::set);
        ChannelRegistry registry = new ChannelRegistry(List.of(boxedConsumer), Config.empty(), List.of());
        try {
            registry.emit("numbers", Message.create(42));
            assertThat(received.get().entity(), is(42));
        } finally {
            registry.close();
        }
    }

    @Test
    void rejectsUnknownAndOutputlessGeneratedTargets() {
        ConsumerRegistration source = consumer("orders", "processor#orders", ignored -> { });
        EmitterRegistration unknown = emitter("missing", "publisher#missing", STRING_TYPE, STRING_MESSAGE_TYPE);
        IllegalArgumentException missingFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(source),
                                          List.of(unknown),
                                          Config.empty(),
                                          List.of()));
        assertThat(missingFailure.getMessage(), containsString("Unknown messaging emitter target channel missing"));

        ProcessorRegistration outputless = processor("orders",
                                                     "audit",
                                                     "processor#outputless",
                                                     STRING_TYPE,
                                                     STRING_MESSAGE_TYPE,
                                                     Function.identity());
        IncomingConnectorProvider incomingProvider = new IncomingConnectorProvider() {
            @Override
            public String connectorType() {
                return "test-in";
            }

            @Override
            public IncomingConnector createIncomingConnector(Config config) {
                throw new AssertionError("Output validation must run before connector creation");
            }
        };
        IllegalArgumentException outputFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(outputless),
                                          yaml("""
                                                  helidon:
                                                    messaging:
                                                      incoming:
                                                        audit:
                                                          connector: test-in
                                                  """),
                                          List.of(incomingProvider)));
        assertThat(outputFailure.getMessage(), containsString("processor target channel audit has no outputs"));
    }

    @Test
    void validatesProducedPayloadAndEnvelopeTypesBeforeStartup() {
        ConsumerRegistration stringTarget = consumer("audit", "audit#consume", ignored -> { });
        EmitterRegistration integerEmitter = emitter("audit",
                                                     "publisher#integer",
                                                     INTEGER_TYPE,
                                                     INTEGER_MESSAGE_TYPE);
        IllegalArgumentException payloadFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(stringTarget),
                                          List.of(integerEmitter),
                                          Config.empty(),
                                          List.of()));
        assertThat(payloadFailure.getMessage(), containsString("conflicting payload types java.lang.String"));
        assertThat(payloadFailure.getMessage(), containsString("java.lang.Integer"));

        EmitterRegistration stringEmitter = emitter("connector-only",
                                                    "publisher#string",
                                                    STRING_TYPE,
                                                    STRING_MESSAGE_TYPE);
        EmitterRegistration conflictingEmitter = emitter("connector-only",
                                                         "publisher#integer",
                                                         INTEGER_TYPE,
                                                         INTEGER_MESSAGE_TYPE);
        IllegalArgumentException connectorOnlyFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(),
                                          List.of(stringEmitter, conflictingEmitter),
                                          Config.empty(),
                                          List.of()));
        assertThat(connectorOnlyFailure.getMessage(), containsString("Channel connector-only has conflicting payload types"));

        ConsumerRegistration specializedTarget = consumer("special",
                                                          "special#consume",
                                                          STRING_TYPE,
                                                          new GenericType<SpecialMessage<String>>() { },
                                                          ignored -> { });
        EmitterRegistration broadEmitter = emitter("special",
                                                   "publisher#broad",
                                                   STRING_TYPE,
                                                   STRING_MESSAGE_TYPE);
        IllegalArgumentException envelopeFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(specializedTarget),
                                          List.of(broadEmitter),
                                          Config.empty(),
                                          List.of()));
        assertThat(envelopeFailure.getMessage(), containsString("produces envelope type"));
        assertThat(envelopeFailure.getMessage(), containsString("cannot accept"));
    }

    @Test
    void acceptsResolvedGenericArrayProcessorEnvelopeAndRejectsRankMismatch() {
        GenericType<String[][]> nestedArrayType = new GenericType<>() { };
        GenericType<Message<String[][]>> nestedArrayMessageType = new GenericType<>() { };
        GenericType<ArrayMessage<String>> processorEnvelopeType = new GenericType<>() { };
        String[][] entity = {{"one"}, {"two"}};
        ArrayMessage<String> processed = new TestArrayMessage<>(entity);
        ProcessorRegistration processor = processor("orders",
                                                     "arrays",
                                                     "processor#arrays",
                                                     nestedArrayType,
                                                     processorEnvelopeType,
                                                     ignored -> processed);
        AtomicReference<Message<?>> received = new AtomicReference<>();
        ConsumerRegistration target = consumer("arrays",
                                               "arrays#consume",
                                               nestedArrayType,
                                               nestedArrayMessageType,
                                               received::set);

        ChannelRegistry registry = new ChannelRegistry(List.of(processor, target), Config.empty(), List.of());
        try {
            registry.emit("orders", Message.create("test"));
            assertThat(received.get(), sameInstance(processed));
            assertThat(received.get().entity(), sameInstance(entity));
        } finally {
            registry.close();
        }

        ProcessorRegistration wrongRank = processor("orders",
                                                    "arrays",
                                                    "processor#wrong-rank",
                                                    nestedArrayType,
                                                    new GenericType<SingleArrayMessage<String>>() { },
                                                    ignored -> processed);
        IllegalArgumentException rankFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(wrongRank, target), Config.empty(), List.of()));
        assertThat(rankFailure.getMessage(), containsString("does not match envelope payload type java.lang.String[]"));
    }

    @Test
    void routesProcessorResultSynchronouslyAndPropagatesDownstreamFailure() {
        AtomicReference<Message<?>> received = new AtomicReference<>();
        ProcessorRegistration processor = processor(
                "orders",
                "audit",
                "processor#orders",
                STRING_TYPE,
                STRING_MESSAGE_TYPE,
                message -> Message.builder(message.entity().toString().toUpperCase())
                        .header("processed", "true")
                        .build());
        ConsumerRegistration target = consumer("audit", "audit#consume", received::set);
        ChannelRegistry registry = new ChannelRegistry(List.of(processor, target), Config.empty(), List.of());
        try {
            registry.emit("orders", Message.create("one"));
            assertThat(received.get().entity(), is("ONE"));
            assertThat(received.get().header("processed").orElseThrow(), is("true"));
        } finally {
            registry.close();
        }

        RuntimeException expected = new RuntimeException("downstream failed");
        ConsumerRegistration failingTarget = consumer("audit", "audit#fail", ignored -> {
            throw expected;
        });
        ChannelRegistry failingRegistry = new ChannelRegistry(List.of(processor, failingTarget),
                                                              Config.empty(),
                                                              List.of());
        try {
            BatchDeliveryException actual = assertThrows(
                    BatchDeliveryException.class,
                    () -> failingRegistry.emit("orders", Message.create("two")));
            assertThat(actual.outcome(0).status(), is(BatchItemStatus.INDETERMINATE));
            assertThat(actual.getCause(), is(instanceOf(BatchDeliveryException.class)));
            assertThat(actual.getCause().getCause(), sameInstance(expected));
        } finally {
            failingRegistry.close();
        }
    }

    @Test
    void rejectsProcessorBatchThatCopiesPublicIdentityWithoutDeliveryLineage() {
        ProcessorRegistration processor = batchProcessor(
                "orders",
                "audit",
                "processor#forged-batch",
                batch -> MessageBatch.<String>builder()
                        .id(batch.id())
                        .add(Message.create("SECOND"))
                        .add(Message.create("FIRST"))
                        .build());
        AtomicBoolean targetInvoked = new AtomicBoolean();
        ConsumerRegistration target = consumer("audit", "audit#consume", ignored -> targetInvoked.set(true));
        ChannelRegistry registry = new ChannelRegistry(List.of(processor, target), Config.empty(), List.of());
        try {
            MessageBatch<String> input = MessageBatch.create(List.of(Message.create("first"),
                                                                     Message.create("second")));

            BatchDeliveryException failure = assertThrows(
                    BatchDeliveryException.class,
                    () -> registry.emitBatch("orders", input));

            assertThat(failure.getCause().getMessage(), containsString("did not preserve batch delivery lineage"));
            assertThat(targetInvoked.get(), is(false));
        } finally {
            registry.close();
        }
    }

    @Test
    void rejectsSynchronousProcessorCyclesDuringGraphPreparation() {
        ProcessorRegistration first = processor("first",
                                                "second",
                                                "processor#first",
                                                STRING_TYPE,
                                                STRING_MESSAGE_TYPE,
                                                Function.identity());
        ProcessorRegistration second = processor("second",
                                                 "first",
                                                 "processor#second",
                                                 STRING_TYPE,
                                                 STRING_MESSAGE_TYPE,
                                                 Function.identity());

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(first, second), Config.empty(), List.of()));
        assertThat(failure.getMessage(), containsString("Cyclic synchronous messaging route"));
        assertThat(failure.getMessage(), containsString("first -> second -> first"));
    }

    private static Config yaml(String yaml) {
        return Config.just(yaml, MediaTypes.APPLICATION_YAML);
    }

    private static ConsumerRegistration consumer(String channel,
                                                 String handlerId,
                                                 Consumer<Message<?>> consumer) {
        return consumer(channel, handlerId, STRING_TYPE, STRING_MESSAGE_TYPE, consumer);
    }

    private static ConsumerRegistration consumer(String channel,
                                                 String handlerId,
                                                 GenericType<?> payloadType,
                                                 GenericType<?> envelopeType,
                                                 Consumer<Message<?>> consumer) {
        return consumer(channel,
                        handlerId,
                        payloadType.rawType(),
                        payloadType,
                        envelopeType.rawType(),
                        envelopeType,
                        consumer);
    }

    private static ConsumerRegistration consumer(String channel,
                                                 String handlerId,
                                                 Class<?> rawPayloadType,
                                                 GenericType<?> payloadType,
                                                 Class<?> rawEnvelopeType,
                                                 GenericType<?> envelopeType,
                                                 Consumer<Message<?>> consumer) {
        return new ConsumerRegistration() {
            @Override
            public String handlerId() {
                return handlerId;
            }

            @Override
            public String channel() {
                return channel;
            }

            @Override
            public Class<?> payloadType() {
                return rawPayloadType;
            }

            @Override
            public GenericType<?> payloadGenericType() {
                return payloadType;
            }

            @Override
            public Class<?> envelopeType() {
                return rawEnvelopeType;
            }

            @Override
            public GenericType<?> envelopeGenericType() {
                return envelopeType;
            }

            @Override
            public void dispatch(MessageBatch<?> batch) {
                for (int i = 0; i < batch.size(); i++) {
                    try {
                        consumer.accept(batch.get(i));
                    } catch (RuntimeException e) {
                        throw BatchDeliveryException.sequential("Test consumer", batch, i, e);
                    }
                }
            }
        };
    }

    private static ProcessorRegistration processor(String incoming,
                                                   String outgoing,
                                                   String handlerId,
                                                   GenericType<?> outgoingPayloadType,
                                                   GenericType<?> outgoingEnvelopeType,
                                                   Function<Message<?>, Message<?>> processor) {
        return processor(incoming,
                         outgoing,
                         handlerId,
                         outgoingPayloadType.rawType(),
                         outgoingPayloadType,
                         outgoingEnvelopeType.rawType(),
                         outgoingEnvelopeType,
                         processor);
    }

    private static ProcessorRegistration processor(String incoming,
                                                   String outgoing,
                                                   String handlerId,
                                                   Class<?> rawOutgoingPayloadType,
                                                   GenericType<?> outgoingPayloadType,
                                                   Class<?> rawOutgoingEnvelopeType,
                                                   GenericType<?> outgoingEnvelopeType,
                                                   Function<Message<?>, Message<?>> processor) {
        return new ProcessorRegistration() {
            @Override
            public String handlerId() {
                return handlerId;
            }

            @Override
            public String channel() {
                return incoming;
            }

            @Override
            public Class<?> payloadType() {
                return String.class;
            }

            @Override
            public GenericType<?> payloadGenericType() {
                return STRING_TYPE;
            }

            @Override
            public GenericType<?> envelopeGenericType() {
                return STRING_MESSAGE_TYPE;
            }

            @Override
            public String outgoingChannel() {
                return outgoing;
            }

            @Override
            public GenericType<?> outgoingPayloadGenericType() {
                return outgoingPayloadType;
            }

            @Override
            public Class<?> outgoingPayloadType() {
                return rawOutgoingPayloadType;
            }

            @Override
            public GenericType<?> outgoingEnvelopeGenericType() {
                return outgoingEnvelopeType;
            }

            @Override
            public Class<?> outgoingEnvelopeType() {
                return rawOutgoingEnvelopeType;
            }

            @Override
            public MessageBatch<?> process(MessageBatch<?> batch) {
                List<Message<?>> results = new ArrayList<>(batch.size());
                for (int i = 0; i < batch.size(); i++) {
                    try {
                        results.add(processor.apply(batch.get(i)));
                    } catch (RuntimeException e) {
                        throw BatchDeliveryException.attemptedPrefix("Test processor", batch, i, e);
                    }
                }
                return batch.derive(results);
            }
        };
    }

    private static ProcessorRegistration batchProcessor(String incoming,
                                                        String outgoing,
                                                        String handlerId,
                                                        Function<MessageBatch<?>, MessageBatch<?>> processor) {
        return new ProcessorRegistration() {
            @Override
            public String handlerId() {
                return handlerId;
            }

            @Override
            public String channel() {
                return incoming;
            }

            @Override
            public Class<?> payloadType() {
                return String.class;
            }

            @Override
            public GenericType<?> payloadGenericType() {
                return STRING_TYPE;
            }

            @Override
            public GenericType<?> envelopeGenericType() {
                return STRING_MESSAGE_TYPE;
            }

            @Override
            public String outgoingChannel() {
                return outgoing;
            }

            @Override
            public GenericType<?> outgoingPayloadGenericType() {
                return STRING_TYPE;
            }

            @Override
            public GenericType<?> outgoingEnvelopeGenericType() {
                return STRING_MESSAGE_TYPE;
            }

            @Override
            public MessageBatch<?> process(MessageBatch<?> batch) {
                return processor.apply(batch);
            }
        };
    }

    private static EmitterRegistration emitter(String channel,
                                               String producerId,
                                               GenericType<?> payloadType,
                                               GenericType<?> envelopeType) {
        return new EmitterRegistration() {
            @Override
            public String channel() {
                return channel;
            }

            @Override
            public String producerId() {
                return producerId;
            }

            @Override
            public GenericType<?> payloadGenericType() {
                return payloadType;
            }

            @Override
            public GenericType<?> envelopeGenericType() {
                return envelopeType;
            }
        };
    }

    private interface SpecialMessage<T> extends Message<T> {
    }

    private interface ArrayMessage<T> extends Message<T[][]> {
    }

    private interface SingleArrayMessage<T> extends Message<T[]> {
    }

    private record TestArrayMessage<T>(T[][] entity) implements ArrayMessage<T> {
        @Override
        public Map<String, String> headers() {
            return Map.of();
        }
    }
}
