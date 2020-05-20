/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.messaging.connectors.kafka;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * This class contains the outputs of the tests. In order to avoid that one test mess up in the results
 * of other tests (this could happen when some data is produced in one test and it is not committed),
 * there are many subclasses of AbstractSampleBean.
 */
abstract class AbstractSampleBean {

    private static final Logger LOGGER = Logger.getLogger(AbstractSampleBean.class.getName());
    private final List<String> consumed = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong expectedRequests = new AtomicLong(Long.MAX_VALUE);
    private CountDownLatch testChannelLatch = new CountDownLatch(1);

    protected List<String> consumed() {
        return consumed;
    }

    void expectedRequests(long expectedRequests) {
        this.expectedRequests.getAndSet(expectedRequests);
    }

    boolean await() {
        try {
            return testChannelLatch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    void restart() {
        requests.getAndSet(0);
        expectedRequests.getAndSet(Long.MAX_VALUE);
        testChannelLatch = new CountDownLatch(1);
        consumed.clear();
    }

    protected void countDown(String method) {
        LOGGER.fine(() -> "Count down triggered by " + method);
        if (requests.incrementAndGet() >= expectedRequests.get()) {
            testChannelLatch.countDown();
        }
    }

    @ApplicationScoped
    public static class Channel1 extends AbstractSampleBean {

        @Incoming("test-channel-1")
        @Acknowledgment(Acknowledgment.Strategy.MANUAL)
        public CompletionStage<String> channel1(Message<ConsumerRecord<Long, String>> msg)
                throws InterruptedException, ExecutionException {
            LOGGER.fine(() -> String.format("Received %s", msg.getPayload().value()));
            consumed().add(msg.getPayload().value());
            msg.ack();
            countDown("channel1()");
            return CompletableFuture.completedFuture(null);
        }
    }

    @ApplicationScoped
    public static class ChannelProcessor extends AbstractSampleBean {

        @Incoming("test-channel-2")
        @Outgoing("test-channel-3")
        @Acknowledgment(Acknowledgment.Strategy.MANUAL)
        public Message<String> channel2ToChannel3(Message<ConsumerRecord<Long, String>> msg)
                throws InterruptedException, ExecutionException {
            msg.ack();
            return Message.of("Processed" + msg.getPayload().value());
        }

        @Incoming("test-channel-7")
        @Acknowledgment(Acknowledgment.Strategy.MANUAL)
        public CompletionStage<String> channel7(Message<ConsumerRecord<Long, String>> msg)
                throws InterruptedException, ExecutionException {
            LOGGER.fine(() -> String.format("Received %s", msg.getPayload().value()));
            consumed().add(msg.getPayload().value());
            msg.ack().whenComplete((a, b) -> countDown("channel7()"));
            return CompletableFuture.completedFuture(null);
        }
    }

    @ApplicationScoped
    public static class ChannelError extends AbstractSampleBean {
        @Incoming("test-channel-error")
        @Acknowledgment(Acknowledgment.Strategy.MANUAL)
        public CompletionStage<String> error(Message<ConsumerRecord<Long, String>> msg) {
            try {
                LOGGER.fine(() -> String.format("Received possible error %s", msg.getPayload().value()));
                consumed().add(Integer.toString(Integer.parseInt(msg.getPayload().value())));
            } finally {
                msg.ack().whenComplete((a, b) -> countDown("error()"));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    @ApplicationScoped
    public static class Channel4 extends AbstractSampleBean {

        @Incoming("test-channel-4")
        public SubscriberBuilder<Message<ConsumerRecord<Long, String>>, Void> channel4() {
            LOGGER.fine(() -> "In channel4");
            return ReactiveStreams.<Message<ConsumerRecord<Long, String>>>builder()
                    .to(new Subscriber<Message<ConsumerRecord<Long, String>>>() {
                        @Override
                        public void onSubscribe(Subscription subscription) {
                            subscription.request(3);
                        }
                        @Override
                        public void onNext(Message<ConsumerRecord<Long, String>> msg) {
                            consumed().add(Integer.toString(Integer.parseInt(msg.getPayload().value())));
                            LOGGER.fine(() -> "Added " + msg.getPayload().value());
                            msg.ack();
                            countDown("onNext()");
                        }
                        @Override
                        public void onError(Throwable t) {
                            LOGGER.fine(() -> "Error " + t.getMessage() + ". Adding error in consumed() list");
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

    @ApplicationScoped
    public static class Channel5 extends AbstractSampleBean {

        @Incoming("test-channel-5")
        public SubscriberBuilder<Message<ConsumerRecord<Long, String>>, Void> channel5() {
            LOGGER.fine(() -> "In channel5");
            return ReactiveStreams.<Message<ConsumerRecord<Long, String>>>builder()
                    .to(new Subscriber<Message<ConsumerRecord<Long, String>>>() {
                        @Override
                        public void onSubscribe(Subscription subscription) {
                            LOGGER.fine(() -> "onSubscribe()");
                            subscription.request(3);
                        }
                        @Override
                        public void onNext(Message<ConsumerRecord<Long, String>> msg) {
                            consumed().add(Integer.toString(Integer.parseInt(msg.getPayload().value())));
                            LOGGER.fine(() -> "Added " + msg.getPayload().value());
                            msg.ack();
                            countDown("onNext()");
                        }
                        @Override
                        public void onError(Throwable t) {
                            LOGGER.fine(() -> "Error " + t.getMessage() + ". Adding error in consumed() list");
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

    @ApplicationScoped
    public static class Channel6 extends AbstractSampleBean {
        static final String NO_ACK = "noAck";

        @Incoming("test-channel-6")
        @Acknowledgment(Acknowledgment.Strategy.MANUAL)
        public CompletionStage<String> channel6(Message<ConsumerRecord<Long, String>> msg) {
            LOGGER.fine(() -> String.format("Received %s", msg.getPayload().value()));
            consumed().add(msg.getPayload().value());
            // Certain messages are not ACK. We can check later that they will be sent again.
            if (!NO_ACK.equals(msg.getPayload().value())) {
                LOGGER.fine(() -> "ACK sent");
                msg.ack();
            } else {
                LOGGER.fine(() -> "ACK is not sent");
            }
			countDown("channel6()");
            return CompletableFuture.completedFuture(null);
        }
    }
    
    @ApplicationScoped
    public static class Channel8 extends AbstractSampleBean {
        static final String NO_ACK = "noAck";
        private final AtomicInteger partitionIdOfNoAck = new AtomicInteger(-1);
        private final AtomicInteger uncommitted = new AtomicInteger();

        @Incoming("test-channel-8")
        @Acknowledgment(Acknowledgment.Strategy.MANUAL)
        public CompletionStage<String> channel6(Message<ConsumerRecord<Long, String>> msg) {
            LOGGER.fine(() -> String.format("Received %s", msg.getPayload().value()));
            ConsumerRecord<Long, String> record = msg.unwrap(ConsumerRecord.class);
            consumed().add(record.value());
            // Certain messages are not ACK. We can check later that they will be sent again.
            if (!NO_ACK.equals(msg.getPayload().value())) {
                LOGGER.fine(() -> "ACK sent");
                msg.ack().whenComplete((a, b) -> countDown("channel8()"));
            } else {
                partitionIdOfNoAck.set(record.partition());
                LOGGER.fine(() -> "ACK is not sent");
            }
            if (record.partition() == partitionIdOfNoAck.get()) {
                LOGGER.fine(() -> record.value() + " will not be committed to Kafka");
                uncommitted.getAndIncrement();
                countDown("channel8()");
            }
            return CompletableFuture.completedFuture(null);
        }

        int uncommitted(){
            return uncommitted.get();
        }
    }

    @ApplicationScoped
    public static class Channel9 extends AbstractSampleBean {

        @Incoming("test-channel-10")
        @Outgoing("test-channel-9")
        @Acknowledgment(Acknowledgment.Strategy.MANUAL)
        public Message<String> channel10ToChannel9(Message<ConsumerRecord<Long, String>> msg) {
            msg.ack().whenComplete((a, b) -> {
                consumed().add(msg.getPayload().value());
                LOGGER.fine(() -> "Added " + msg.getPayload().value());
                countDown("channel10ToChannel9()");
            });
            return Message.of(msg.getPayload().value());
        }
    }

    @ApplicationScoped
    public static class Channel11 extends AbstractSampleBean {

        @Incoming("test-channel-11")
        @Acknowledgment(Acknowledgment.Strategy.MANUAL)
        public CompletionStage<String> channel1(Message<ConsumerRecord<Long, String>> msg) {
            // Unreachable because the Kafka connection is invalid
            return CompletableFuture.completedFuture(null);
        }
    }

    @ApplicationScoped
    public static class Channel12 extends AbstractSampleBean {

        @Incoming("test-channel-13")
        @Outgoing("test-channel-12")
        @Acknowledgment(Acknowledgment.Strategy.MANUAL)
        public Message<String> channel13ToChannel12(Message<ConsumerRecord<Long, String>> msg) {
            msg.ack().whenComplete((a, b) -> {
                consumed().add(msg.getPayload().value());
                LOGGER.fine(() -> "Added " + msg.getPayload().value());
                countDown("channel13ToChannel12()");
            });
            return Message.of(msg.getPayload().value());
        }
    }
}
