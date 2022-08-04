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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Old style messaging health probe for backward compatibility.
 *
 * @deprecated Use {@link MessagingChannelProcessor} instead.
 */
@Deprecated(since = "3.0.0", forRemoval = true)
class FormerHealthProbe implements MessagingChannelProcessor {

    private final Map<String, Boolean> liveChannels = new HashMap<>();
    private final Map<String, Boolean> readyChannels = new HashMap<>();

    @Override
    public void onInit(String channelName) {
        liveChannels.put(channelName, true);
        readyChannels.put(channelName, false);
    }

    @Override
    public void onSubscribe(String channelName, Subscriber<Message<?>> subscriber, Subscription subscription) {
        readyChannels.put(channelName, true);
    }

    @Override
    public void onError(String channelName, Throwable t) {
        liveChannels.put(channelName, false);
    }

    @Override
    public void onCancel(String channelName) {
        liveChannels.put(channelName, false);
    }

    @Override
    public void onComplete(String channelName) {
        liveChannels.put(channelName, false);
    }

    Map<String, Boolean> getLiveChannels() {
        return liveChannels;
    }

    Map<String, Boolean> getReadyChannels() {
        return readyChannels;
    }
}
