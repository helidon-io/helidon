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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Priority;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Tap between publisher and subscriber to listen for reactive stream events.
 */
class ChannelProcessors {

    private final Map<String, List<MessagingChannelProcessor>> channelProcessorMap = new HashMap<>();
    private final List<MessagingChannelProcessor> wildCardProcessors = new ArrayList<>();

    void register(MessagingChannelProcessor channelProcessor) {
        channelProcessor.channelName()
                .ifPresentOrElse(
                        channelName ->
                                channelProcessorMap.computeIfAbsent(channelName, s -> new ArrayList<>()).add(channelProcessor),
                        () ->
                                wildCardProcessors.add(channelProcessor)
                );
    }

    private long priority(MessagingChannelProcessor channelProcessor) {
        Priority priority = channelProcessor.getClass().getAnnotation(Priority.class);
        if (priority != null) {
            return priority.value();
        } else {
            return MessagingChannelProcessor.DEFAULT_PRIORITY;
        }
    }

    void init(){
        channelProcessorMap.forEach((channel, processorList) -> processorList.addAll(wildCardProcessors));
    }

    void connect(String channelName,
                 Publisher<Message<?>> publisher,
                 Subscriber<Message<?>> subscriber) {

        List<MessagingChannelProcessor> processorList =
                channelProcessorMap.getOrDefault(channelName, new ArrayList<>(wildCardProcessors));
        processorList.sort(Comparator.comparingLong(this::priority));

        processorList.forEach(p -> p.onInit(channelName));
        publisher.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(final Subscription s) {
                processorList.forEach(p -> p.onSubscribe(channelName, subscriber, s));
                subscriber.onSubscribe(new Subscription() {
                    @Override
                    public void request(final long n) {
                        processorList.forEach(p -> p.onRequest(channelName, n));
                        s.request(n);
                    }

                    @Override
                    public void cancel() {
                        processorList.forEach(p -> p.onCancel(channelName));
                        s.cancel();
                    }
                });
            }

            @Override
            public void onNext(Message<?> o) {

                for (MessagingChannelProcessor p : processorList) {
                    o = p.map(channelName, o);
                }

                subscriber.onNext(o);
            }

            @Override
            public void onError(final Throwable t) {
                processorList.forEach(p -> p.onError(channelName, t));
                subscriber.onError(t);
            }

            @Override
            public void onComplete() {
                processorList.forEach(p -> p.onComplete(channelName));
                subscriber.onComplete();
            }
        });
    }
}
