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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import io.helidon.common.GenericType;
import io.helidon.messaging.spi.OutgoingConnector;

final class DefaultMessagingGraphBuilder implements MessagingGraph.Builder {
    private final List<SourceDefinition> sources = new ArrayList<>();
    private final Set<Stream<?>> sourceIdentities = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<OutgoingConnector> connectorIdentities = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<MessagingChannel<?>, DefaultMessagingChannel<?>> channels = new IdentityHashMap<>();
    private final Set<MessagingChannel<?>> outputChannels = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<DefaultMessagingChannel<?>> sourceChannels = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Route> routes = new LinkedHashSet<>();
    private final Set<Route> directRoutes = new LinkedHashSet<>();
    private MessagingExecutionConfig executionConfig = MessagingExecutionConfig.builder().build();
    private DefaultMessagingGraph graph;
    private boolean buildAttempted;
    private int sourceSequence;

    @Override
    public MessagingGraph.Builder executionConfig(MessagingExecutionConfig config) {
        requireUninitialized();
        executionConfig = Objects.requireNonNull(config);
        return this;
    }

    @Override
    public <T> MessagingChannel<T> channel(String name, Class<T> payloadType) {
        return channel(name, GenericType.create(payloadType));
    }

    @Override
    public <T> MessagingChannel<T> channel(String name, GenericType<T> payloadType) {
        return channel(name, payloadType, executionConfig);
    }

    @Override
    public <T> MessagingChannel<T> channel(String name,
                                           GenericType<T> payloadType,
                                           MessagingExecutionConfig channelExecutionConfig) {
        requireMutable();
        String channelName = requireChannelName(name);
        GenericType<T> channelPayloadType = Objects.requireNonNull(payloadType);
        if (channelPayloadType.rawType().isPrimitive()) {
            throw new IllegalArgumentException("Messaging channel payload type must not be primitive: "
                                                       + channelPayloadType.getTypeName());
        }
        MessagingExecutionConfig actualExecutionConfig = Objects.requireNonNull(channelExecutionConfig);
        if (!executionConfig.shutdownTimeout().equals(actualExecutionConfig.shutdownTimeout())) {
            throw new IllegalArgumentException("Every channel in a messaging graph must use the same shutdown-timeout");
        }
        DefaultMessagingChannel<T> runtimeChannel = new DefaultMessagingChannel.Builder<T>()
                .payloadType(channelPayloadType)
                .messagingGraph(graph(), channelName, actualExecutionConfig)
                .build();
        MessagingChannel<T> channel = new DefaultMessagingChannelHandle<>(channelName, channelPayloadType);
        channels.put(channel, runtimeChannel);
        graph.addEmitter(channel, runtimeChannel);
        return channel;
    }

    @Override
    public <T> MessagingGraph.Builder payloadSource(MessagingChannel<T> channel, Stream<? extends T> source) {
        return source(channel, source, false);
    }

    @Override
    public <T> MessagingGraph.Builder messageSource(MessagingChannel<T> channel,
                                                     Stream<? extends Message<? extends T>> source) {
        return source(channel, source, true);
    }

    @Override
    public <T> MessagingGraph.Builder route(MessagingChannel<T> source, MessagingChannel<T> target) {
        DefaultMessagingChannel<T> actualSource = channel(source);
        DefaultMessagingChannel<T> actualTarget = channel(target);
        if (!source.payloadType().equals(target.payloadType())) {
            throw new IllegalArgumentException("Messaging route " + source.name() + " -> " + target.name()
                                                       + " has incompatible payload types "
                                                       + source.payloadType().getTypeName() + " and "
                                                       + target.payloadType().getTypeName());
        }
        Route route = new Route(source.name(), target.name());
        if (!directRoutes.add(route)) {
            throw new IllegalArgumentException("Duplicate messaging route " + source.name() + " -> " + target.name());
        }
        routes.add(route);
        actualSource.addBatchOutput(actualTarget::emitRoutedBatchObject);
        outputChannels.add(source);
        return this;
    }

    @Override
    public <I, O> MessagingGraph.Builder payloadProcessor(MessagingChannel<I> source,
                                                          MessagingChannel<O> target,
                                                          Function<? super I, ? extends O> processor) {
        DefaultMessagingChannel<I> actualSource = channel(source);
        DefaultMessagingChannel<O> actualTarget = channel(target);
        Function<? super I, ? extends O> actualProcessor = Objects.requireNonNull(processor);
        actualSource.addBatchOutput(batch -> {
            List<Message<O>> results = new ArrayList<>(batch.size());
            for (int i = 0; i < batch.size(); i++) {
                try {
                    Message<I> message = batch.get(i);
                    results.add(Message.create(actualProcessor.apply(source.payloadType().cast(message.entity()))));
                } catch (RuntimeException e) {
                    throw BatchDeliveryExceptionSupport.attemptedPrefix("Messaging payload processor", batch, i, e);
                }
            }
            actualTarget.emitBatchObject(batch.derive(results));
        });
        routes.add(new Route(source.name(), target.name()));
        outputChannels.add(source);
        return this;
    }

    @Override
    public <I, O> MessagingGraph.Builder messageProcessor(
            MessagingChannel<I> source,
            MessagingChannel<O> target,
            Function<? super Message<I>, ? extends Message<? extends O>> processor) {
        DefaultMessagingChannel<I> actualSource = channel(source);
        DefaultMessagingChannel<O> actualTarget = channel(target);
        Function<? super Message<I>, ? extends Message<? extends O>> actualProcessor = Objects.requireNonNull(processor);
        actualSource.addBatchOutput(batch -> {
            List<Message<? extends O>> results = new ArrayList<>(batch.size());
            for (int i = 0; i < batch.size(); i++) {
                try {
                    results.add(Objects.requireNonNull(actualProcessor.apply(batch.get(i)), "Message processor result"));
                } catch (RuntimeException e) {
                    throw BatchDeliveryExceptionSupport.attemptedPrefix("Messaging message processor", batch, i, e);
                }
            }
            actualTarget.emitBatchObject(batch.derive(results));
        });
        routes.add(new Route(source.name(), target.name()));
        outputChannels.add(source);
        return this;
    }

    @Override
    public <T> MessagingGraph.Builder payloadSink(MessagingChannel<T> source, Consumer<? super T> sink) {
        DefaultMessagingChannel<T> actualSource = channel(source);
        Consumer<? super T> actualSink = Objects.requireNonNull(sink);
        actualSource.addOutput(message -> actualSink.accept(source.payloadType().cast(message.entity())));
        outputChannels.add(source);
        return this;
    }

    @Override
    public <T> MessagingGraph.Builder messageSink(MessagingChannel<T> source,
                                                   Consumer<? super Message<T>> sink) {
        DefaultMessagingChannel<T> actualSource = channel(source);
        Consumer<? super Message<T>> actualSink = Objects.requireNonNull(sink);
        actualSource.addOutput(message -> actualSink.accept(castMessage(message)));
        outputChannels.add(source);
        return this;
    }

    @Override
    public <T> MessagingGraph.Builder batchSink(MessagingChannel<T> source,
                                                Consumer<MessageBatch<T>> sink) {
        DefaultMessagingChannel<T> actualSource = channel(source);
        Consumer<MessageBatch<T>> actualSink = Objects.requireNonNull(sink);
        actualSource.addBatchOutput(actualSink);
        outputChannels.add(source);
        return this;
    }

    @Override
    public <T> MessagingGraph.Builder outgoingConnector(MessagingChannel<T> source, OutgoingConnector connector) {
        DefaultMessagingChannel<T> actualSource = channel(source);
        OutgoingConnector actualConnector = Objects.requireNonNull(connector);
        if (!connectorIdentities.add(actualConnector)) {
            throw new IllegalArgumentException("Outgoing connector is already owned by this messaging graph builder");
        }
        try {
            graph().addBinding(actualConnector);
        } catch (RuntimeException | Error e) {
            connectorIdentities.remove(actualConnector);
            throw e;
        }
        actualSource.addOutgoingConnector(actualConnector);
        outputChannels.add(source);
        return this;
    }

    @Override
    public MessagingGraph build() {
        requireMutable();
        buildAttempted = true;
        DefaultMessagingGraph actualGraph = graph();
        try {
            validateStreamSourcePaths();
            validateOutputs();
            routes.forEach(route -> actualGraph.addRoute(route.source(), route.target()));
            actualGraph.seal();
            return actualGraph;
        } catch (RuntimeException | Error e) {
            closeAfterBuildFailure(actualGraph, e);
            throw e;
        }
    }

    @Override
    public void close() {
        if (buildAttempted) {
            return;
        }
        buildAttempted = true;
        if (graph != null) {
            graph.close();
        }
    }

    private <T> MessagingGraph.Builder source(MessagingChannel<T> channel, Stream<?> source, boolean messageSource) {
        DefaultMessagingChannel<T> actualChannel = channel(channel);
        Stream<?> actualSource = Objects.requireNonNull(source);
        if (sourceIdentities.contains(actualSource)) {
            throw new IllegalArgumentException("Stream source is already owned by this messaging graph builder");
        }
        if (sourceChannels.contains(actualChannel)) {
            throw new IllegalArgumentException("Messaging channel " + channel.name()
                                                       + " already has a stream source");
        }
        String sourceName = channel.name() + "-source-" + ++sourceSequence;
        Consumer<Object> consumer = messageSource
                ? value -> actualChannel.emitMessageObject((Message<?>) value)
                : actualChannel::emitPayloadObject;
        Runnable streamSource = DefaultMessagingChannel.streamSource(actualSource, consumer);
        graph().addSource(sourceName, streamSource);
        sourceIdentities.add(actualSource);
        sourceChannels.add(actualChannel);
        sources.add(new SourceDefinition(sourceName, actualChannel));
        return this;
    }

    private void validateOutputs() {
        for (MessagingChannel<?> channel : channels.keySet()) {
            if (!outputChannels.contains(channel)) {
                throw new IllegalArgumentException("Messaging channel " + channel.name()
                                                           + " has no required output");
            }
        }
    }

    private void validateStreamSourcePaths() {
        Map<String, String> reachedBySource = new LinkedHashMap<>();
        for (SourceDefinition source : sources) {
            Set<String> reachable = new LinkedHashSet<>();
            List<String> pending = new ArrayList<>();
            pending.add(source.channel().name());
            while (!pending.isEmpty()) {
                String channel = pending.removeLast();
                if (!reachable.add(channel)) {
                    continue;
                }
                for (Route route : routes) {
                    if (route.source().equals(channel)) {
                        pending.add(route.target());
                    }
                }
            }
            for (String channel : reachable) {
                String previousSource = reachedBySource.putIfAbsent(channel, source.name());
                if (previousSource != null) {
                    throw new IllegalArgumentException("Messaging stream source fan-in to channel " + channel
                                                               + " is not supported; " + previousSource + " and "
                                                               + source.name() + " converge");
                }
            }
        }
    }

    private DefaultMessagingGraph graph() {
        if (graph == null) {
            graph = new DefaultMessagingGraph(new DeliveryEngine(executionConfig));
        }
        return graph;
    }

    private void requireUninitialized() {
        requireMutable();
        if (graph != null) {
            throw new IllegalStateException("Graph execution must be configured before declaring channels");
        }
    }

    private void requireMutable() {
        if (buildAttempted) {
            throw new IllegalStateException("Messaging graph builder cannot be reused after build or close");
        }
    }

    private String requireChannelName(String name) {
        String actualName = Objects.requireNonNull(name);
        if (actualName.isBlank()) {
            throw new IllegalArgumentException("Messaging channel name must not be blank");
        }
        return actualName;
    }

    @SuppressWarnings("unchecked")
    private <T> DefaultMessagingChannel<T> channel(MessagingChannel<T> channel) {
        requireMutable();
        Objects.requireNonNull(channel);
        DefaultMessagingChannel<?> defaultChannel = channels.get(channel);
        if (defaultChannel == null || defaultChannel.graph() != graph()) {
            throw new IllegalArgumentException("Messaging channel " + channel.name()
                                                       + " belongs to another messaging graph builder");
        }
        return (DefaultMessagingChannel<T>) defaultChannel;
    }

    private void closeAfterBuildFailure(DefaultMessagingGraph graph, Throwable failure) {
        try {
            graph.close();
        } catch (RuntimeException | Error closeFailure) {
            if (failure != closeFailure) {
                failure.addSuppressed(closeFailure);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Message<T> castMessage(Message<?> message) {
        return (Message<T>) message;
    }

    private record SourceDefinition(String name,
                                    DefaultMessagingChannel<?> channel) {
    }

    private record Route(String source, String target) {
    }
}
