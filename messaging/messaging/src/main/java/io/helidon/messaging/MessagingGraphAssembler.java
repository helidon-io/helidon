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

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import io.helidon.common.GenericType;
import io.helidon.messaging.spi.IncomingChannel;
import io.helidon.messaging.spi.MessagingChannelConfig;
import io.helidon.messaging.spi.MessagingConnector;
import io.helidon.messaging.spi.MessagingIncomingConfig;
import io.helidon.messaging.spi.MessagingOutgoingConfig;
import io.helidon.messaging.spi.OutgoingChannel;

/**
 * Shared graph assembly from programmatic and generated registrations.
 */
final class MessagingGraphAssembler {
    private static final Class<?> STANDARD_RETRY_TYPE = FailurePolicy.create().retry().getClass();

    private final Map<String, MessagingChannel<?>> channels = new LinkedHashMap<>();
    private final MessagingConfig config;
    private final DefaultMessagingGraph graph;

    MessagingGraphAssembler(MessagingConfig config) {
        this.config = Objects.requireNonNull(config);
        this.graph = new DefaultMessagingGraph(new DeliveryEngine(config));
        List<ConsumerRegistration> consumers = config.consumerRegistrations();
        List<EmitterRegistration> emitters = config.emitterRegistrations();
        try {
            ownProgrammaticResources();
            validateSourceChannels();
            validateRegistrationIdentities(consumers, emitters);
            validateRegistrationTypeMetadata(consumers, emitters);
            Map<String, PayloadContribution> payloadContributions =
                    new LinkedHashMap<>(validateChannelPayloadContributions(consumers, emitters));
            for (MessagingChannel<?> handle : config.channelHandles()) {
                addPayloadContribution(payloadContributions,
                                       handle.name(),
                                       handle.payloadType(),
                                       "programmatic channel");
            }
            Map<String, List<ConsumerRegistration>> grouped = new LinkedHashMap<>();
            for (ConsumerRegistration registration : consumers) {
                grouped.computeIfAbsent(registration.channel(), _ -> new ArrayList<>()).add(registration);
            }
            grouped.forEach((channel, registrations) -> channels.put(channel, createChannel(channel, registrations)));
            new TreeSet<>(config.outgoing().keySet()).forEach(channel -> ensureChannel(channel, payloadContributions));
            new TreeSet<>(config.incoming().keySet()).forEach(channel -> ensureChannel(channel, payloadContributions));
            for (MessagingChannel<?> handle : config.channelHandles()) {
                channels.computeIfAbsent(handle.name(),
                                         _ -> createConfiguredChannel(handle.name(), handle.payloadType()));
                addEmitter(handle);
            }
            Map<String, MessagingConnector> connectors = connectors(config.connector());
            List<OutgoingBinding> outgoingBindings = prepareOutgoingBindings(config.outgoing(), connectors);
            List<IncomingDescriptor> incomingDescriptors = prepareIncomingDescriptors(config.incoming(),
                                                                                     connectors,
                                                                                     grouped);
            Set<String> outputChannels = new LinkedHashSet<>(grouped.keySet());
            outgoingBindings.stream().map(OutgoingBinding::channel).forEach(outputChannels::add);
            for (MessagingChannel<?> handle : config.channelHandles()) {
                if (!outputChannels.contains(handle.name())) {
                    throw new IllegalArgumentException("Messaging channel " + handle.name()
                                                               + " has no required output");
                }
            }
            validateGeneratedProducerTargets(consumers, emitters, grouped, outputChannels);
            validateFailureRoutes(incomingDescriptors, outputChannels, grouped, payloadContributions);
            validateIncomingOutputs(incomingDescriptors, outputChannels);
            validateStreamSourcePaths(consumers, emitters);
            registerProcessorRoutes(consumers);
            configureOutgoingConnectors(outgoingBindings);
            configureIncomingConnectors(incomingDescriptors);
            graph.seal();
        } catch (RuntimeException | Error e) {
            graph.abortPreparation(e);
            throw e;
        }
    }

    DefaultMessagingGraph graph() {
        return graph;
    }

    private static String requireConnectorName(MessagingChannelConfig channelConfig, String direction, String channel) {
        if (!channel.equals(channelConfig.channelName())) {
            throw new IllegalArgumentException("Configured " + direction + " channel " + channel
                                                       + " does not match channel-name " + channelConfig.channelName());
        }
        String connector = channelConfig.connector();
        if (connector.isBlank()) {
            throw new IllegalArgumentException("Configured " + direction + " channel " + channel
                                                       + " must declare a non-blank connector");
        }
        return connector;
    }

    private void ownProgrammaticResources() {
        for (EmitterRegistration registration : config.emitterRegistrations()) {
            if (registration instanceof MessagingConfigSupport.SourceDefinition source) {
                Runnable task = DefaultMessagingChannel.streamSource(source.stream(), value -> {
                    DefaultMessagingChannel<?> channel = runtimeChannel(source.channel());
                    if (source.messages()) {
                        channel.emitMessageObject((Message<?>) value);
                    } else {
                        channel.emitPayloadObject(value);
                    }
                });
                graph.addSource(source.producerId(), task);
            }
        }
        for (ConsumerRegistration registration : config.consumerRegistrations()) {
            if (registration instanceof MessagingConfigSupport.OutgoingDefinition outgoing) {
                graph.addBinding(outgoing.connection());
            }
        }
        config.incomingConnections().forEach((handle, connection) ->
                graph.addIncomingConnector(handle.name() + "-incoming",
                                           connection,
                                           incomingContext(handle.name())));
    }

    private void validateStreamSourcePaths(List<ConsumerRegistration> consumers, List<EmitterRegistration> emitters) {
        Map<String, String> reachedBySource = new LinkedHashMap<>();
        for (EmitterRegistration emitter : emitters) {
            if (!(emitter instanceof MessagingConfigSupport.SourceDefinition source)) {
                continue;
            }
            Set<String> reachable = new LinkedHashSet<>();
            List<String> pending = new ArrayList<>();
            pending.add(source.channel());
            while (!pending.isEmpty()) {
                String channel = pending.removeLast();
                if (!reachable.add(channel)) {
                    continue;
                }
                for (ConsumerRegistration registration : consumers) {
                    if (registration instanceof ProcessorRegistration processor
                            && processor.channel().equals(channel)) {
                        pending.add(processor.outgoingChannel());
                    }
                }
            }
            for (String channel : reachable) {
                String previous = reachedBySource.putIfAbsent(channel, source.producerId());
                if (previous != null) {
                    throw new IllegalArgumentException("Messaging stream source fan-in to channel " + channel
                                                               + " is not supported; " + previous + " and "
                                                               + source.producerId() + " converge");
                }
            }
        }
    }

    private void validateSourceChannels() {
        Set<String> sourceChannels = new LinkedHashSet<>(config.incoming().keySet());
        for (MessagingChannel<?> handle : config.incomingConnections().keySet()) {
            if (!sourceChannels.add(handle.name())) {
                throw new IllegalArgumentException("Messaging channel " + handle.name() + " already has a source");
            }
        }
        for (EmitterRegistration registration : config.emitterRegistrations()) {
            if (registration instanceof MessagingConfigSupport.SourceDefinition source
                    && !sourceChannels.add(source.channel())) {
                throw new IllegalArgumentException("Messaging channel " + source.channel() + " already has a source");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void addEmitter(MessagingChannel<T> handle) {
        graph.addEmitter(handle, (DefaultMessagingChannel<T>) channels.get(handle.name()));
    }

    private DefaultMessagingChannel<?> runtimeChannel(String name) {
        MessagingChannel<?> channel = channels.get(name);
        if (channel instanceof DefaultMessagingChannel<?> result) {
            return result;
        }
        throw new MessagingException("Unknown messaging channel " + name);
    }

    /**
     * Add an outgoing connector to a named channel.
     *
     * @param channel channel name
     * @param connector outgoing connector
     */
    private void addOutgoingConnector(String channel, OutgoingChannel connector) {
        MessagingChannel<?> messagingChannel = channels.get(channel);
        if (messagingChannel == null) {
            throw new IllegalArgumentException("Unknown messaging channel " + channel);
        }
        if (!(messagingChannel instanceof DefaultMessagingChannel<?> defaultMessagingChannel)) {
            throw new IllegalArgumentException("Unsupported channel implementation "
                                                       + messagingChannel.getClass().getName());
        }
        graph.addBinding(connector);
        defaultMessagingChannel.addOutgoingConnector(connector);
    }

    /**
     * Create a context for an incoming connector.
     *
     * @param channel channel name
     * @return incoming connector context
     */
    private IncomingConnectorContext incomingContext(String channel) {
        return incomingContext(channel, FailurePolicy.create());
    }

    private IncomingConnectorContext incomingContext(String channel, FailurePolicy failurePolicy) {
        return new DefaultIncomingConnectorContext(graph, channel, failurePolicy);
    }

    private void validateRegistrationIdentities(List<ConsumerRegistration> consumerRegistrations,
                                                List<EmitterRegistration> emitterRegistrations) {
        Set<String> handlerIds = new LinkedHashSet<>();
        for (ConsumerRegistration registration : consumerRegistrations) {
            String handlerId = requireRegistrationIdentity("Handler", registration.handlerId());
            if (!handlerIds.add(handlerId)) {
                throw new IllegalArgumentException("Duplicate messaging handler registration " + handlerId);
            }
        }

        Set<String> producerIds = new LinkedHashSet<>();
        for (EmitterRegistration registration : emitterRegistrations) {
            String producerId = requireRegistrationIdentity("Producer", registration.producerId());
            if (!producerIds.add(producerId)) {
                throw new IllegalArgumentException("Duplicate messaging producer registration " + producerId);
            }
        }
    }

    private String requireRegistrationIdentity(String kind, String identity) {
        if (identity == null || identity.isBlank()) {
            throw new IllegalArgumentException(kind + " registration identity must not be blank");
        }
        return identity;
    }

    private void validateRegistrationTypeMetadata(List<ConsumerRegistration> consumerRegistrations,
                                                  List<EmitterRegistration> emitterRegistrations) {
        for (ConsumerRegistration registration : consumerRegistrations) {
            String handler = "Messaging handler " + registration.handlerId();
            GenericType<?> payloadType = registration.payloadGenericType();
            GenericType<?> envelopeType = registration.envelopeGenericType();
            validateType(handler + " payload", payloadType);
            validateType(handler + " envelope", envelopeType);
            validateEnvelopePayloadType(handler, payloadType, envelopeType);
            if (registration instanceof ProcessorRegistration processor) {
                String outgoing = "Messaging processor " + processor.handlerId() + " outgoing";
                GenericType<?> outgoingPayloadType = processor.outgoingPayloadGenericType();
                GenericType<?> outgoingEnvelopeType = processor.outgoingEnvelopeGenericType();
                validateType(outgoing + " payload", outgoingPayloadType);
                validateType(outgoing + " envelope", outgoingEnvelopeType);
                validateEnvelopePayloadType(outgoing, outgoingPayloadType, outgoingEnvelopeType);
            }
        }
        for (EmitterRegistration registration : emitterRegistrations) {
            String emitter = "Messaging emitter " + registration.producerId();
            GenericType<?> payloadType = registration.payloadGenericType();
            GenericType<?> envelopeType = registration.envelopeGenericType();
            validateType(emitter + " payload", payloadType);
            validateType(emitter + " envelope", envelopeType);
            validateEnvelopePayloadType(emitter, payloadType, envelopeType);
        }
    }

    private void validateType(String source, GenericType<?> type) {
        GenericType<?> actualType = Objects.requireNonNull(type, source + " type");
        Class<?> rawType = actualType.rawType();
        if (rawType.isPrimitive()) {
            throw new IllegalArgumentException(source + " raw type must not be primitive: "
                                                       + rawType.getName());
        }
    }

    private void validateEnvelopePayloadType(String source,
                                             GenericType<?> payloadType,
                                             GenericType<?> envelopeType) {
        if (!Message.class.isAssignableFrom(envelopeType.rawType())) {
            throw new IllegalArgumentException(source + " envelope type " + typeName(envelopeType)
                                                       + " must implement " + Message.class.getName());
        }
        Type resolvedMessageType = resolveSupertype(envelopeType.type(), Message.class);
        if (!(resolvedMessageType instanceof ParameterizedType messageType)) {
            return;
        }
        Type[] arguments = messageType.getActualTypeArguments();
        if (arguments.length != 1 || hasUnresolvedType(arguments[0])) {
            return;
        }
        Type envelopePayloadType = arguments[0];
        if (!equivalentType(payloadType.type(), envelopePayloadType)) {
            throw new IllegalArgumentException(source + " payload generic type " + typeName(payloadType)
                                                       + " does not match envelope payload type "
                                                       + typeName(envelopePayloadType) + " declared by "
                                                       + typeName(envelopeType));
        }
    }

    private boolean hasUnresolvedType(Type type) {
        if (type instanceof TypeVariable<?>) {
            return true;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            Type owner = parameterizedType.getOwnerType();
            if (owner != null && hasUnresolvedType(owner)) {
                return true;
            }
            return Arrays.stream(parameterizedType.getActualTypeArguments()).anyMatch(this::hasUnresolvedType);
        }
        if (type instanceof GenericArrayType arrayType) {
            return hasUnresolvedType(arrayType.getGenericComponentType());
        }
        if (type instanceof WildcardType wildcardType) {
            return Arrays.stream(wildcardType.getLowerBounds()).anyMatch(this::hasUnresolvedType)
                    || Arrays.stream(wildcardType.getUpperBounds()).anyMatch(this::hasUnresolvedType);
        }
        return false;
    }

    private boolean equivalentType(Type first, Type second) {
        if (sameType(first, second)) {
            return true;
        }
        Type firstComponent = arrayComponent(first);
        Type secondComponent = arrayComponent(second);
        if (firstComponent != null || secondComponent != null) {
            return firstComponent != null
                    && secondComponent != null
                    && equivalentType(firstComponent, secondComponent);
        }
        if (first instanceof ParameterizedType firstParameterized
                && second instanceof ParameterizedType secondParameterized) {
            if (!sameType(firstParameterized.getRawType(), secondParameterized.getRawType())
                    || !equivalentNullableType(firstParameterized.getOwnerType(), secondParameterized.getOwnerType())) {
                return false;
            }
            return equivalentTypes(firstParameterized.getActualTypeArguments(),
                                   secondParameterized.getActualTypeArguments());
        }
        if (first instanceof WildcardType firstWildcard && second instanceof WildcardType secondWildcard) {
            return equivalentTypes(firstWildcard.getLowerBounds(), secondWildcard.getLowerBounds())
                    && equivalentTypes(firstWildcard.getUpperBounds(), secondWildcard.getUpperBounds());
        }
        return false;
    }

    private boolean equivalentNullableType(Type first, Type second) {
        return first == null ? second == null : second != null && equivalentType(first, second);
    }

    private boolean equivalentTypes(Type[] first, Type[] second) {
        if (first.length != second.length) {
            return false;
        }
        for (int i = 0; i < first.length; i++) {
            if (!equivalentType(first[i], second[i])) {
                return false;
            }
        }
        return true;
    }

    private Map<String, PayloadContribution> validateChannelPayloadContributions(
            List<ConsumerRegistration> consumerRegistrations,
            List<EmitterRegistration> emitterRegistrations) {
        Map<String, PayloadContribution> payloadTypes = new LinkedHashMap<>();
        for (ConsumerRegistration registration : consumerRegistrations) {
            addPayloadContribution(payloadTypes,
                                   registration.channel(),
                                   registration.payloadGenericType(),
                                   "handler " + registration.handlerId());
            if (registration instanceof ProcessorRegistration processor) {
                addPayloadContribution(payloadTypes,
                                       processor.outgoingChannel(),
                                       processor.outgoingPayloadGenericType(),
                                       "processor " + processor.handlerId());
            }
        }
        for (EmitterRegistration emitter : emitterRegistrations) {
            addPayloadContribution(payloadTypes,
                                   emitter.channel(),
                                   emitter.payloadGenericType(),
                                   "emitter " + emitter.producerId());
        }
        return Map.copyOf(payloadTypes);
    }

    private void addPayloadContribution(Map<String, PayloadContribution> payloadTypes,
                                        String channel,
                                        GenericType<?> payloadType,
                                        String source) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("Messaging channel contributed by " + source + " must not be blank");
        }
        GenericType<?> actualType = Objects.requireNonNull(payloadType, "Payload type contributed by " + source);
        PayloadContribution contribution = new PayloadContribution(actualType, source);
        PayloadContribution existing = payloadTypes.putIfAbsent(channel, contribution);
        if (existing != null && !existing.payloadType().equals(actualType)) {
            throw new IllegalArgumentException("Channel " + channel + " has conflicting payload types "
                                                       + typeName(existing.payloadType()) + " from "
                                                       + existing.source() + " and " + typeName(actualType)
                                                       + " from " + source);
        }
    }

    private String typeName(GenericType<?> type) {
        return typeName(type.type());
    }

    private String typeName(Type type) {
        if (type instanceof Class<?> classType) {
            return classType.getTypeName();
        }
        if (type instanceof GenericArrayType arrayType) {
            return typeName(arrayType.getGenericComponentType()) + "[]";
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return typeName(parameterizedType.getRawType()) + "<"
                    + Arrays.stream(parameterizedType.getActualTypeArguments())
                    .map(this::typeName)
                    .collect(Collectors.joining(", "))
                    + ">";
        }
        if (type instanceof WildcardType wildcardType) {
            Type[] lowerBounds = wildcardType.getLowerBounds();
            if (lowerBounds.length > 0) {
                return "? super " + Arrays.stream(lowerBounds)
                        .map(this::typeName)
                        .collect(Collectors.joining(" & "));
            }
            Type[] upperBounds = wildcardType.getUpperBounds();
            if (upperBounds.length == 0
                    || (upperBounds.length == 1 && upperBounds[0].equals(Object.class))) {
                return "?";
            }
            return "? extends " + Arrays.stream(upperBounds)
                    .map(this::typeName)
                    .collect(Collectors.joining(" & "));
        }
        return type.getTypeName();
    }

    private void validateGeneratedProducerTargets(List<ConsumerRegistration> consumerRegistrations,
                                                  List<EmitterRegistration> emitterRegistrations,
                                                  Map<String, List<ConsumerRegistration>> groupedConsumers,
                                                  Set<String> outputChannels) {
        for (ConsumerRegistration registration : consumerRegistrations) {
            if (registration instanceof ProcessorRegistration processor) {
                validateGeneratedProducerTarget("processor",
                                                processor.handlerId(),
                                                processor.outgoingChannel(),
                                                processor.outgoingPayloadGenericType(),
                                                processor.outgoingEnvelopeGenericType(),
                                                groupedConsumers,
                                                outputChannels);
            }
        }
        for (EmitterRegistration emitter : emitterRegistrations) {
            validateGeneratedProducerTarget("emitter",
                                            emitter.producerId(),
                                            emitter.channel(),
                                            emitter.payloadGenericType(),
                                            emitter.envelopeGenericType(),
                                            groupedConsumers,
                                            outputChannels);
        }
    }

    private void validateGeneratedProducerTarget(String kind,
                                                 String registrationId,
                                                 String targetChannel,
                                                 GenericType<?> payloadType,
                                                 GenericType<?> envelopeType,
                                                 Map<String, List<ConsumerRegistration>> groupedConsumers,
                                                 Set<String> outputChannels) {
        String target = targetChannel == null ? "" : targetChannel;
        if (target.isBlank()) {
            throw new IllegalArgumentException("Messaging " + kind + " " + registrationId
                                                       + " target channel must not be blank");
        }
        if (!channels.containsKey(target)) {
            throw new IllegalArgumentException("Unknown messaging " + kind + " target channel " + target
                                                       + " for " + registrationId);
        }
        if (!outputChannels.contains(target)) {
            throw new IllegalArgumentException("Messaging " + kind + " target channel " + target
                                                       + " has no outputs for " + registrationId);
        }

        GenericType<?> producedPayload = Objects.requireNonNull(payloadType,
                                                                 kind + " payload type for " + registrationId);
        GenericType<?> producedEnvelope = Objects.requireNonNull(envelopeType,
                                                                  kind + " envelope type for " + registrationId);
        for (ConsumerRegistration targetConsumer : groupedConsumers.getOrDefault(target, List.of())) {
            if (!producedPayload.equals(targetConsumer.payloadGenericType())) {
                throw new IllegalArgumentException("Messaging " + kind + " " + registrationId
                                                           + " produces payload type " + producedPayload.getTypeName()
                                                           + " but target channel " + target + " expects "
                                                           + targetConsumer.payloadGenericType().getTypeName());
            }
            if (!typeAccepts(targetConsumer.envelopeGenericType().type(), producedEnvelope.type())) {
                throw new IllegalArgumentException("Messaging " + kind + " " + registrationId
                                                           + " produces envelope type " + producedEnvelope.getTypeName()
                                                           + " that target channel " + target + " cannot accept as "
                                                           + targetConsumer.envelopeGenericType().getTypeName());
            }
        }
    }

    private void registerProcessorRoutes(List<ConsumerRegistration> consumerRegistrations) {
        for (ConsumerRegistration registration : consumerRegistrations) {
            if (registration instanceof ProcessorRegistration processor) {
                graph.addRoute(processor.channel(), processor.outgoingChannel());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private MessagingChannel<?> createChannel(String channel,
                                              List<ConsumerRegistration> consumerRegistrations) {
        GenericType<Object> payloadType = (GenericType<Object>) payloadType(channel, consumerRegistrations);
        DefaultMessagingChannel.Builder<Object> builder = new DefaultMessagingChannel.Builder<>();
        builder.messagingGraph(graph, channel, MessagingConfigSupport.channelExecution(config, channel))
                .payloadType(payloadType);
        builder.addBatchValidator(messages -> validateMessageTypes(consumerRegistrations, messages));
        for (ConsumerRegistration consumer : consumerRegistrations) {
            if (consumer instanceof ProcessorRegistration processor) {
                builder.addBatchOutput(messages -> processAndRoute(processor, messages));
            } else {
                builder.addBatchOutput(consumer::dispatch);
            }
        }
        return builder.build();
    }

    private void processAndRoute(ProcessorRegistration processor, MessageBatch<?> messages) {
        MessageBatch<?> result = processor.process(messages);
        if (result == null) {
            throw new MessagingException("Messaging processor " + processor.handlerId() + " returned a null batch");
        }
        if (!messages.sameDelivery(result)) {
            throw new MessagingException("Messaging processor " + processor.handlerId()
                                                 + " did not preserve batch delivery lineage");
        }
        Class<?> outgoingEnvelopeType = processor.outgoingEnvelopeGenericType().rawType();
        Class<?> outgoingPayloadType = processor.outgoingPayloadGenericType().rawType();
        for (Message<?> message : result) {
            if (!outgoingEnvelopeType.isInstance(message)) {
                throw new MessagingException("Messaging processor " + processor.handlerId()
                                                     + " declared outgoing envelope type "
                                                     + outgoingEnvelopeType.getName()
                                                     + " but returned " + message.getClass().getName());
            }
            Object entity = message.entity();
            if (entity == null) {
                throw new MessagingException("Messaging processor " + processor.handlerId()
                                                     + " returned a null payload");
            }
            if (!outgoingPayloadType.isInstance(entity)) {
                throw new MessagingException("Messaging processor " + processor.handlerId()
                                                     + " declared outgoing payload type "
                                                     + outgoingPayloadType.getName()
                                                     + " but returned " + entity.getClass().getName());
            }
        }
        MessagingChannel<?> target = channels.get(processor.outgoingChannel());
        if (target == null) {
            throw new MessagingException("Unknown messaging processor target channel "
                                                 + processor.outgoingChannel());
        }
        if (processor instanceof MessagingConfigSupport.RouteRegistration) {
            emitRoutedBatch(target, result);
        } else {
            emitBatch(target, result);
        }
    }

    @SuppressWarnings("unchecked")
    private MessagingChannel<?> createConfiguredChannel(String channel, GenericType<?> payloadType) {
        DefaultMessagingChannel.Builder<Object> builder = new DefaultMessagingChannel.Builder<>();
        builder.messagingGraph(graph, channel, MessagingConfigSupport.channelExecution(config, channel))
                .payloadType((GenericType<Object>) payloadType);
        return builder.build();
    }

    private <T> void emitBatch(MessagingChannel<?> messagingChannel,
                               MessageBatch<? extends T> messages) {
        if (!(messagingChannel instanceof DefaultMessagingChannel<?> defaultMessagingChannel)) {
            throw new MessagingException("Unsupported messaging channel implementation "
                                                 + messagingChannel.getClass().getName());
        }
        defaultMessagingChannel.emitBatchObject(messages);
    }

    private <T> void emitRoutedBatch(MessagingChannel<?> messagingChannel,
                                     MessageBatch<? extends T> messages) {
        if (!(messagingChannel instanceof DefaultMessagingChannel<?> defaultMessagingChannel)) {
            throw new MessagingException("Unsupported messaging channel implementation "
                                                 + messagingChannel.getClass().getName());
        }
        defaultMessagingChannel.emitRoutedBatchObject(messages);
    }

    private GenericType<?> payloadType(String channel, List<ConsumerRegistration> consumerRegistrations) {
        GenericType<?> payloadType = null;
        for (ConsumerRegistration consumer : consumerRegistrations) {
            if (payloadType == null) {
                payloadType = consumer.payloadGenericType();
            } else if (!payloadType.equals(consumer.payloadGenericType())) {
                throw new IllegalArgumentException("Channel " + channel + " has conflicting payload types "
                                                           + payloadType.getTypeName() + " and "
                                                           + consumer.payloadGenericType().getTypeName());
            }
        }
        validateEnvelopeTypes(channel, consumerRegistrations);
        return payloadType == null ? GenericType.OBJECT : payloadType;
    }

    private void validateEnvelopeTypes(String channel, List<ConsumerRegistration> consumers) {
        for (int first = 0; first < consumers.size(); first++) {
            ConsumerRegistration firstConsumer = consumers.get(first);
            for (int second = first + 1; second < consumers.size(); second++) {
                ConsumerRegistration secondConsumer = consumers.get(second);
                if (!compatibleEnvelopeTypes(firstConsumer, secondConsumer)) {
                    throw new IllegalArgumentException("Channel " + channel + " has conflicting message envelope types "
                                                               + firstConsumer.envelopeGenericType().getTypeName() + " and "
                                                               + secondConsumer.envelopeGenericType().getTypeName());
                }
            }
        }
    }

    private boolean compatibleEnvelopeTypes(ConsumerRegistration first, ConsumerRegistration second) {
        Type firstType = first.envelopeGenericType().type();
        Type secondType = second.envelopeGenericType().type();
        return typeAccepts(firstType, secondType) || typeAccepts(secondType, firstType);
    }

    private boolean typeAccepts(Type target, Type candidate) {
        if (sameType(target, candidate)) {
            return true;
        }
        if (target instanceof WildcardType wildcard) {
            return wildcardAccepts(wildcard, candidate);
        }

        Class<?> targetRawType = GenericType.create(target).rawType();
        Class<?> candidateRawType = GenericType.create(candidate).rawType();
        if (!targetRawType.isAssignableFrom(candidateRawType)) {
            return false;
        }
        if (target instanceof Class<?>) {
            return true;
        }
        if (!(target instanceof ParameterizedType targetParameterized)) {
            return false;
        }

        Type resolvedCandidate = resolveSupertype(candidate, targetRawType);
        if (!(resolvedCandidate instanceof ParameterizedType candidateParameterized)) {
            return false;
        }
        return typeArgumentsAccept(targetParameterized.getActualTypeArguments(),
                                   candidateParameterized.getActualTypeArguments());
    }

    private boolean typeArgumentsAccept(Type[] targetArguments, Type[] candidateArguments) {
        if (targetArguments.length != candidateArguments.length) {
            return false;
        }
        for (int i = 0; i < targetArguments.length; i++) {
            if (!typeArgumentAccepts(targetArguments[i], candidateArguments[i])) {
                return false;
            }
        }
        return true;
    }

    private boolean typeArgumentAccepts(Type target, Type candidate) {
        if (sameType(target, candidate)) {
            return true;
        }
        if (target instanceof WildcardType wildcard) {
            return wildcardAccepts(wildcard, candidate);
        }
        if (target instanceof ParameterizedType targetParameterized
                && candidate instanceof ParameterizedType candidateParameterized
                && sameType(targetParameterized.getRawType(), candidateParameterized.getRawType())) {
            return typeArgumentsAccept(targetParameterized.getActualTypeArguments(),
                                       candidateParameterized.getActualTypeArguments());
        }
        Type targetComponent = arrayComponent(target);
        if (targetComponent != null) {
            Type candidateComponent = arrayComponent(candidate);
            return candidateComponent != null && typeArgumentAccepts(targetComponent, candidateComponent);
        }
        return false;
    }

    private Type arrayComponent(Type type) {
        if (type instanceof GenericArrayType genericArray) {
            return genericArray.getGenericComponentType();
        }
        if (type instanceof Class<?> arrayType && arrayType.isArray()) {
            return arrayType.getComponentType();
        }
        return null;
    }

    private boolean wildcardAccepts(WildcardType wildcard, Type candidate) {
        if (candidate instanceof WildcardType) {
            return false;
        }
        for (Type lowerBound : wildcard.getLowerBounds()) {
            if (!typeAccepts(candidate, lowerBound)) {
                return false;
            }
        }
        for (Type upperBound : wildcard.getUpperBounds()) {
            if (!typeAccepts(upperBound, candidate)) {
                return false;
            }
        }
        return true;
    }

    private Type resolveSupertype(Type candidate, Class<?> targetRawType) {
        Class<?> candidateRawType = GenericType.create(candidate).rawType();
        if (candidateRawType.equals(targetRawType)) {
            return candidate;
        }

        Map<TypeVariable<?>, Type> bindings = typeBindings(candidateRawType, candidate);
        for (Type genericInterface : candidateRawType.getGenericInterfaces()) {
            Type resolvedInterface = resolveType(genericInterface, bindings);
            Class<?> interfaceRawType = GenericType.create(resolvedInterface).rawType();
            if (targetRawType.isAssignableFrom(interfaceRawType)) {
                Type resolved = resolveSupertype(resolvedInterface, targetRawType);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        Type genericSuperclass = candidateRawType.getGenericSuperclass();
        if (genericSuperclass != null) {
            Type resolvedSuperclass = resolveType(genericSuperclass, bindings);
            Class<?> superclassRawType = GenericType.create(resolvedSuperclass).rawType();
            if (targetRawType.isAssignableFrom(superclassRawType)) {
                return resolveSupertype(resolvedSuperclass, targetRawType);
            }
        }
        return null;
    }

    private Map<TypeVariable<?>, Type> typeBindings(Class<?> rawType, Type type) {
        Map<TypeVariable<?>, Type> bindings = new HashMap<>();
        if (type instanceof ParameterizedType parameterizedType) {
            TypeVariable<?>[] variables = rawType.getTypeParameters();
            Type[] arguments = parameterizedType.getActualTypeArguments();
            for (int i = 0; i < Math.min(variables.length, arguments.length); i++) {
                bindings.put(variables[i], arguments[i]);
            }
        }
        return bindings;
    }

    private Type resolveType(Type type, Map<TypeVariable<?>, Type> bindings) {
        if (type instanceof TypeVariable<?> variable) {
            return bindings.getOrDefault(variable, variable);
        }
        if (type instanceof ParameterizedType parameterizedType) {
            Type[] arguments = parameterizedType.getActualTypeArguments();
            Type[] resolvedArguments = new Type[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                resolvedArguments[i] = resolveType(arguments[i], bindings);
            }
            Type owner = parameterizedType.getOwnerType();
            return new ResolvedParameterizedType(owner == null ? null : resolveType(owner, bindings),
                                                 parameterizedType.getRawType(),
                                                 resolvedArguments);
        }
        if (type instanceof WildcardType wildcardType) {
            return new ResolvedWildcardType(resolveTypes(wildcardType.getLowerBounds(), bindings),
                                            resolveTypes(wildcardType.getUpperBounds(), bindings));
        }
        if (type instanceof GenericArrayType arrayType) {
            return new ResolvedGenericArrayType(resolveType(arrayType.getGenericComponentType(), bindings));
        }
        return type;
    }

    private Type[] resolveTypes(Type[] types, Map<TypeVariable<?>, Type> bindings) {
        Type[] resolved = new Type[types.length];
        for (int i = 0; i < types.length; i++) {
            resolved[i] = resolveType(types[i], bindings);
        }
        return resolved;
    }

    private boolean sameType(Type first, Type second) {
        return first.equals(second) || second.equals(first);
    }

    private void validateMessageType(ConsumerRegistration consumer, Message<?> message) {
        Class<?> envelopeType = consumer.envelopeGenericType().rawType();
        if (!envelopeType.isInstance(message)) {
            throw new IllegalArgumentException("Channel " + consumer.channel()
                                                       + " expected message envelope type "
                                                       + envelopeType.getName()
                                                       + " but received " + message.getClass().getName());
        }
        // The channel validates payloads before dispatch. Consumer-specific validation only checks the envelope.
    }

    private void validateMessageTypes(ConsumerRegistration consumer, MessageBatch<?> messages) {
        for (Message<?> message : messages.messages()) {
            validateMessageType(consumer, message);
        }
    }

    private void validateMessageTypes(List<ConsumerRegistration> consumers, MessageBatch<?> messages) {
        for (ConsumerRegistration consumer : consumers) {
            validateMessageTypes(consumer, messages);
        }
    }

    private List<OutgoingBinding> prepareOutgoingBindings(Map<String, MessagingOutgoingConfig> configurations,
                                                          Map<String, MessagingConnector> connectors) {
        List<OutgoingBinding> bindings = new ArrayList<>();
        for (String channel : new TreeSet<>(configurations.keySet())) {
            MessagingOutgoingConfig channelConfig = configurations.get(channel);
            String connectorName = requireConnectorName(channelConfig, "outgoing", channel);
            MessagingConnector connector = connectors.get(connectorName);
            if (connector == null) {
                throw new IllegalArgumentException("No configured connector named " + connectorName
                                                           + " for outgoing channel " + channel);
            }
            bindings.add(new OutgoingBinding(channel, connector, channelConfig));
        }
        return List.copyOf(bindings);
    }

    private List<IncomingDescriptor> prepareIncomingDescriptors(
            Map<String, MessagingIncomingConfig> configurations,
            Map<String, MessagingConnector> connectors,
            Map<String, List<ConsumerRegistration>> registrations) {
        List<IncomingDescriptor> descriptors = new ArrayList<>();
        for (String channel : new TreeSet<>(configurations.keySet())) {
            MessagingIncomingConfig channelConfig = configurations.get(channel);
            String connectorName = requireConnectorName(channelConfig, "incoming", channel);
            MessagingConnector connector = connectors.get(connectorName);
            if (connector == null) {
                throw new IllegalArgumentException("No configured connector named " + connectorName
                                                           + " for incoming channel " + channel);
            }
            FailurePolicy failurePolicy = failurePolicy(channel,
                                                        channelConfig.failure(),
                                                        registrations.getOrDefault(channel, List.of()));
            descriptors.add(new IncomingDescriptor(channel,
                                                   failurePolicy,
                                                   connector,
                                                   channelConfig));
        }
        return List.copyOf(descriptors);
    }

    private FailurePolicy failurePolicy(String channel,
                                        MessagingFailureConfig failureConfig,
                                        List<ConsumerRegistration> registrations) {
        List<FailurePolicyContribution> contributions = registrations.stream()
                .map(registration -> registration.declaredFailurePolicy()
                        .map(policy -> new FailurePolicyContribution(registration.handlerId(), policy)))
                .flatMap(Optional::stream)
                .toList();
        if (contributions.isEmpty()) {
            return MessagingConfigSupport.failurePolicy(FailurePolicy.create(), failureConfig);
        }

        FailurePolicyContribution first = contributions.getFirst();
        FailurePolicy effective = MessagingConfigSupport.failurePolicy(first.policy(), failureConfig);
        for (int i = 1; i < contributions.size(); i++) {
            FailurePolicyContribution candidate = contributions.get(i);
            FailurePolicy candidateEffective = MessagingConfigSupport.failurePolicy(candidate.policy(), failureConfig);
            if (!equivalentFailurePolicies(effective, candidateEffective)) {
                throw new IllegalArgumentException("Incoming channel " + channel
                                                           + " has conflicting effective failure policies declared by "
                                                           + "handlers "
                                                           + first.handlerId() + " and " + candidate.handlerId()
                                                           + ": " + effective + " and " + candidateEffective);
            }
        }
        return effective;
    }

    private boolean equivalentFailurePolicies(FailurePolicy first, FailurePolicy second) {
        if (first.onExhausted() != second.onExhausted() || !first.deadLetter().equals(second.deadLetter())) {
            return false;
        }
        if (first.retry() == second.retry()) {
            return true;
        }
        return first.retry().getClass() == STANDARD_RETRY_TYPE
                && second.retry().getClass() == STANDARD_RETRY_TYPE
                && first.retry().prototype().equals(second.retry().prototype());
    }

    private void configureOutgoingConnectors(List<OutgoingBinding> bindings) {
        for (OutgoingBinding binding : bindings) {
            OutgoingChannel connector = Objects.requireNonNull(
                    binding.connector().outgoing(binding.config()),
                    "Outgoing channel result")
                    .orElseThrow(() -> new IllegalArgumentException("Messaging connector " + binding.connector().name()
                                                                           + " does not support outgoing channel "
                                                                           + binding.channel()));
            addOutgoingConnector(binding.channel(), connector);
        }
    }

    private void configureIncomingConnectors(List<IncomingDescriptor> descriptors) {
        for (IncomingDescriptor descriptor : descriptors) {
            IncomingConnectorContext context = incomingContext(descriptor.channel(), descriptor.failurePolicy());
            IncomingChannel connector = Objects.requireNonNull(
                    descriptor.connector().incoming(descriptor.config()),
                    "Incoming channel result")
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Messaging connector " + descriptor.connector().name()
                                    + " does not support incoming channel " + descriptor.channel()));
            graph.addIncomingConnector("connector-" + descriptor.channel(), connector, context);
        }
    }

    private void validateIncomingOutputs(List<IncomingDescriptor> bindings, Set<String> outputChannels) {
        for (IncomingDescriptor binding : bindings) {
            if (!outputChannels.contains(binding.channel())) {
                throw new IllegalArgumentException("Incoming channel " + binding.channel() + " has no outputs");
            }
        }
    }

    private void validateFailureRoutes(List<IncomingDescriptor> bindings,
                                       Set<String> outputChannels,
                                       Map<String, List<ConsumerRegistration>> consumers,
                                       Map<String, PayloadContribution> payloadContributions) {
        Map<String, String> routes = new LinkedHashMap<>();
        for (IncomingDescriptor binding : bindings) {
            FailurePolicy policy = binding.failurePolicy();
            if (policy.onExhausted() != FailureDisposition.DEAD_LETTER) {
                continue;
            }

            String source = binding.channel();
            String target = policy.deadLetter().orElseThrow().channel();
            if (source.equals(target)) {
                throw new IllegalArgumentException("Dead-letter channel must not reference itself: " + source);
            }
            if (!channels.containsKey(target)) {
                throw new IllegalArgumentException("Unknown dead-letter channel " + target
                                                           + " configured for incoming channel " + source);
            }
            if (!outputChannels.contains(target)) {
                throw new IllegalArgumentException("Dead-letter channel " + target
                                                           + " configured for incoming channel " + source
                                                           + " has no outputs");
            }
            validateDeadLetterPayload(source, target, payloadContributions);
            validateDeadLetterConsumers(source, target, consumers.getOrDefault(target, List.of()));
            routes.put(source, target);
        }
        validateFailureRouteCycles(routes);
        routes.forEach(graph::addRoute);
    }

    private void validateDeadLetterPayload(String source,
                                           String target,
                                           Map<String, PayloadContribution> payloadContributions) {
        PayloadContribution sourcePayload = payloadContributions.get(source);
        PayloadContribution targetPayload = payloadContributions.get(target);
        if (sourcePayload != null
                && targetPayload != null
                && !sourcePayload.payloadType().equals(targetPayload.payloadType())) {
            throw new IllegalArgumentException("Dead-letter channel " + target
                                                       + " has payload type " + typeName(targetPayload.payloadType())
                                                       + " but incoming channel " + source
                                                       + " has payload type " + typeName(sourcePayload.payloadType()));
        }
    }

    private void validateDeadLetterConsumers(String source,
                                             String target,
                                             List<ConsumerRegistration> consumers) {
        for (ConsumerRegistration consumer : consumers) {
            Class<?> envelopeType = consumer.envelopeGenericType().rawType();
            if (!envelopeType.isAssignableFrom(DeadLetterMessage.class)) {
                throw new IllegalArgumentException("Dead-letter channel " + target
                                                           + " configured for incoming channel " + source
                                                           + " has consumer envelope "
                                                           + consumer.envelopeGenericType().getTypeName()
                                                           + " that cannot accept "
                                                           + DeadLetterMessage.class.getName());
            }
        }
    }

    private void validateFailureRouteCycles(Map<String, String> routes) {
        Set<String> visited = new LinkedHashSet<>();
        Set<String> visiting = new LinkedHashSet<>();
        List<String> path = new ArrayList<>();
        for (String source : routes.keySet()) {
            visitFailureRoute(source, routes, visited, visiting, path);
        }
    }

    private void visitFailureRoute(String source,
                                   Map<String, String> routes,
                                   Set<String> visited,
                                   Set<String> visiting,
                                   List<String> path) {
        if (visited.contains(source)) {
            return;
        }
        if (!visiting.add(source)) {
            int cycleStart = path.indexOf(source);
            List<String> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
            cycle.add(source);
            throw new IllegalArgumentException("Cyclic dead-letter channel route: " + String.join(" -> ", cycle));
        }
        path.add(source);

        String target = routes.get(source);
        if (target != null) {
            visitFailureRoute(target, routes, visited, visiting, path);
        }

        path.removeLast();
        visiting.remove(source);
        visited.add(source);
    }

    private Map<String, MessagingConnector> connectors(List<MessagingConnector> configuredConnectors) {
        Map<String, MessagingConnector> connectors = new HashMap<>();
        for (MessagingConnector connector : configuredConnectors) {
            Objects.requireNonNull(connector, "Messaging connector");
            String name = Objects.requireNonNull(connector.name(), "Messaging connector name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("Messaging connector name must not be blank");
            }
            MessagingConnector previous = connectors.putIfAbsent(name, connector);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate messaging connector name " + name);
            }
        }
        return Map.copyOf(connectors);
    }

    private void ensureChannel(String channel, Map<String, PayloadContribution> payloadContributions) {
        PayloadContribution contribution = payloadContributions.get(channel);
        GenericType<?> payloadType = contribution == null ? GenericType.OBJECT : contribution.payloadType();
        channels.computeIfAbsent(channel, _ -> createConfiguredChannel(channel, payloadType));
    }

    private static final class ResolvedParameterizedType implements ParameterizedType {
        private final Type ownerType;
        private final Type rawType;
        private final Type[] actualTypeArguments;

        private ResolvedParameterizedType(Type ownerType, Type rawType, Type[] actualTypeArguments) {
            this.ownerType = ownerType;
            this.rawType = rawType;
            this.actualTypeArguments = actualTypeArguments.clone();
        }

        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments.clone();
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return ownerType;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof ParameterizedType other)) {
                return false;
            }
            return Objects.equals(ownerType, other.getOwnerType())
                    && rawType.equals(other.getRawType())
                    && Arrays.equals(actualTypeArguments, other.getActualTypeArguments());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(actualTypeArguments)
                    ^ Objects.hashCode(ownerType)
                    ^ Objects.hashCode(rawType);
        }
    }

    private static final class ResolvedWildcardType implements WildcardType {
        private final Type[] lowerBounds;
        private final Type[] upperBounds;

        private ResolvedWildcardType(Type[] lowerBounds, Type[] upperBounds) {
            this.lowerBounds = lowerBounds.clone();
            this.upperBounds = upperBounds.clone();
        }

        @Override
        public Type[] getUpperBounds() {
            return upperBounds.clone();
        }

        @Override
        public Type[] getLowerBounds() {
            return lowerBounds.clone();
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof WildcardType other)) {
                return false;
            }
            return Arrays.equals(lowerBounds, other.getLowerBounds())
                    && Arrays.equals(upperBounds, other.getUpperBounds());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(lowerBounds) ^ Arrays.hashCode(upperBounds);
        }
    }

    private static final class ResolvedGenericArrayType implements GenericArrayType {
        private final Type componentType;

        private ResolvedGenericArrayType(Type componentType) {
            this.componentType = componentType;
        }

        @Override
        public Type getGenericComponentType() {
            return componentType;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof GenericArrayType other
                    && componentType.equals(other.getGenericComponentType());
        }

        @Override
        public int hashCode() {
            return componentType.hashCode();
        }
    }

    private record PayloadContribution(GenericType<?> payloadType, String source) {
    }

    private record OutgoingBinding(String channel,
                                   MessagingConnector connector,
                                   MessagingOutgoingConfig config) {
    }

    private record FailurePolicyContribution(String handlerId, FailurePolicy policy) {
    }

    private record IncomingDescriptor(String channel,
                                      FailurePolicy failurePolicy,
                                      MessagingConnector connector,
                                      MessagingIncomingConfig config) {
    }

}
