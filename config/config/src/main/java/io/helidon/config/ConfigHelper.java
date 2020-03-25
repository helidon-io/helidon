/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.helidon.config.spi.ConfigNode;

/**
 * Common Configuration utilities.
 */
public final class ConfigHelper {
    private ConfigHelper() {
        throw new AssertionError("Instantiation not allowed.");
    }

    /**
     * Creates a {@link ConfigHelper#subscriber(Function) Flow.Subscriber} that
     * will delegate {@link Flow.Subscriber#onNext(Object)} to the specified
     * {@code onNextFunction} function.
     * <p>
     * The new subscriber's
     * {@link Flow.Subscriber#onSubscribe(Flow.Subscription)} method
     * automatically invokes {@link Flow.Subscription#request(long)} to request
     * all events that are available in the subscription.
     * <p>
     * The caller-provided {@code onNextFunction} should return {@code false} in
     * order to {@link Flow.Subscription#cancel() cancel} current subscription.
     *
     * @param onNextFunction function to be invoked during {@code onNext}
     * processing
     * @param <T> the type of the items provided by the subscription
     * @return {@code Subscriber} that delegates its {@code onNext} to the
     * caller-provided function
     */
    public static <T> Flow.Subscriber<T> subscriber(Function<T, Boolean> onNextFunction) {
        return new OnNextFunctionSubscriber<>(onNextFunction);
    }

    static Map<ConfigKeyImpl, ConfigNode> createFullKeyToNodeMap(ConfigNode.ObjectNode objectNode) {
        Map<ConfigKeyImpl, ConfigNode> result;

        Stream<Map.Entry<ConfigKeyImpl, ConfigNode>> flattenNodes = objectNode.entrySet()
                .stream()
                .map(node -> flattenNodes(ConfigKeyImpl.of(node.getKey()), node.getValue()))
                .reduce(Stream.empty(), Stream::concat);
        result = flattenNodes.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        result.put(ConfigKeyImpl.of(), objectNode);

        return result;
    }

    static Stream<Map.Entry<ConfigKeyImpl, ConfigNode>> flattenNodes(ConfigKeyImpl key, ConfigNode node) {
        switch (node.nodeType()) {
        case OBJECT:
            return ((ConfigNode.ObjectNode) node).entrySet().stream()
                    .map(e -> flattenNodes(key.child(e.getKey()), e.getValue()))
                    .reduce(Stream.of(new AbstractMap.SimpleEntry<>(key, node)), Stream::concat);
        case LIST:
            return IntStream.range(0, ((ConfigNode.ListNode) node).size())
                    .boxed()
                    .map(i -> flattenNodes(key.child(Integer.toString(i)), ((ConfigNode.ListNode) node).get(i)))
                    .reduce(Stream.of(new AbstractMap.SimpleEntry<>(key, node)), Stream::concat);
        case VALUE:
            return Stream.of(new AbstractMap.SimpleEntry<>(key, node));
        default:
            throw new IllegalArgumentException("Invalid node type.");
        }
    }

    /**
     * Implementation of {@link ConfigHelper#subscriber(Function)}.
     *
     * @param <T> the subscribed item type
     * @see ConfigHelper#subscriber(Function)
     */
    private static class OnNextFunctionSubscriber<T> implements Flow.Subscriber<T> {
        private final Function<T, Boolean> onNextFunction;
        private final Logger logger;
        private Flow.Subscription subscription;

        private OnNextFunctionSubscriber(Function<T, Boolean> onNextFunction) {
            this.onNextFunction = onNextFunction;
            this.logger = Logger.getLogger(OnNextFunctionSubscriber.class.getName() + "."
                                                   + Integer.toHexString(System.identityHashCode(onNextFunction)));
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            logger.finest(() -> "onSubscribe: " + subscription);

            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T item) {
            boolean cancel = !onNextFunction.apply(item);

            logger.finest(() -> "onNext: " + item + " => " + (cancel ? "CANCEL" : "FOLLOW"));

            if (cancel) {
                subscription.cancel();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            logger.log(Level.WARNING,
                       throwable,
                       () -> "Config Changes support failed. " + throwable.getLocalizedMessage());
        }

        @Override
        public void onComplete() {
            logger.config("Config Changes support finished. There will no other Config reload.");
        }

    }

}
