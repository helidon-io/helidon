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

import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;

class IncomingPublisher implements IncomingMember {

    private final String channelName;
    private final Processor<Object, Object> processor;
    private final String fieldName;

    IncomingPublisher(String channelName, String fieldName, boolean wrap) {
        this.channelName = channelName;
        this.fieldName = fieldName;
        this.processor = ReactiveStreams.builder()
                .map(o -> {
                    if (!wrap) {
                        return MessageUtils.unwrap(o);
                    }
                    return o;
                })
                .buildRs();
    }

    Processor<Object, Object> getProcessor() {
        return processor;
    }

    String getChannelName() {
        return channelName;
    }

    String getFieldName() {
        return fieldName;
    }

    @Override
    public Subscriber<? super Object> getSubscriber(String channelName) {
        return getProcessor();
    }

    @Override
    public String getDescription() {
        return "publisher " + getFieldName();
    }
}
