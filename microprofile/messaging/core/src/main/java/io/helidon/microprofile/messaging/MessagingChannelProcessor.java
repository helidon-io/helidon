/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.microprofile.messaging;


import java.util.Optional;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Application scoped bean implementing MessagingChannelProcessor can peek on reactive events
 * in the messaging channel.
 */
public interface MessagingChannelProcessor {

    /**
     * Default priority for channel processor.
     */
    int DEFAULT_PRIORITY = 100;

    /**
     * Map messages going through the onNext signal in the messaging channel.
     *
     * @param channelName name of the messaging channel
     * @param message     message coming from the upstream
     * @return mapped message to be delivered to the downstream
     */
    default Message<?> map(String channelName, Message<?> message) {
        // noop
        return message;
    }

    /**
     * Listener for the onError event coming from the upstream.
     *
     * @param channelName name of the messaging channel
     * @param t           error causing the onError signal
     */
    default void onError(String channelName, Throwable t) {
        // noop
    }

    /**
     * Listener for the request coming from the downstream.
     *
     * @param channelName name of the messaging channel
     * @param req         number of items requested by the downstream
     */
    default void onRequest(String channelName, long req) {
        // noop
    }

    /**
     * Listener for the cancel coming from the downstream.
     *
     * @param channelName name of the messaging channel
     */
    default void onCancel(String channelName) {
        // noop
    }

    /**
     * Listener for the onComplete signal coming from the upstream.
     *
     * @param channelName name of the messaging channel
     */
    default void onComplete(String channelName) {
        // noop
    }

    /**
     * Listener for the onSubscribe signal coming from the upstream.
     *
     * @param channelName name of the messaging channel
     * @param subscriber subscriber causing this onSubscribe signal
     * @param subscription assigned subscription
     */
    default void onSubscribe(String channelName, Subscriber<Message<?>> subscriber, Subscription subscription) {
        // noop
    }

    /**
     * Listener for the initial event before actual subscribing.
     *
     * @param channelName name of the messaging channel
     */
    default void onInit(String channelName) {
        // noop
    }

    /**
     * Messaging channel name associated with this processor.
     * If the channel name is empty this processor is applied to the all channels.
     *
     * @return Channel name or empty for all channels
     */
    default Optional<String> channelName() {
        // any channel
        return Optional.empty();
    }
}
