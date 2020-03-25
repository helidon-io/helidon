/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package io.helidon.microprofile.connectors.kafka;


import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

@ApplicationScoped
// Must be public
public class KafkaNoFullAck1Bean extends AbstractSampleBean {

    private static final Logger LOGGER = Logger.getLogger(KafkaNoFullAck1Bean.class.getName());

    @Incoming("test-channel-4")
    public SubscriberBuilder<Message<ConsumerRecord<Long, String>>, Void> channel4() {
        LOGGER.fine("In channel4");
        return ReactiveStreams.<Message<ConsumerRecord<Long, String>>>builder()
                .to(new Subscriber<Message<ConsumerRecord<Long, String>>>() {
                    @Override
                    public void onSubscribe(Subscription subscription) {
                        subscription.request(3);
                    }
                    @Override
                    public void onNext(final Message<ConsumerRecord<Long, String>> msg) {
                        consumed().add(Integer.toString(Integer.parseInt(msg.getPayload().value())));
                        LOGGER.fine("Added " + msg.getPayload().value());
                        countDown("onNext()");
                    }
                    @Override
                    public void onError(final Throwable t) {
                        LOGGER.fine("Error " + t.getMessage() + ". Adding error in consumed() list");
                        consumed().add("error");
                        countDown("onError()");
                    }
                    @Override
                    public void onComplete() {
                        consumed().add("complete");
                        countDown("onComplete()");
                    }
                });
    }

}
