package io.helidon.microprofile.messaging;

import io.helidon.common.reactive.microprofile.HelidonReactiveStreamEngine;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.spi.ReactiveStreamsEngine;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ReactiveEngineTest {

    @Test
    void testSmallRye() {
//        testEngine(new io.smallrye.reactive.streams.Engine());
    }

    @Test
    void testTestHelidon() {
        testEngine(new HelidonReactiveStreamEngine());
    }

    private void testEngine(ReactiveStreamsEngine engine) {
        Publisher<String> publisher = ReactiveStreams.of("test1", "test2", "test3")
                .buildRs(engine);
        LatchSubscriber<String> subscriber = new LatchSubscriber<>();

        ReactiveStreams
                .fromPublisher(publisher)
                .to(ReactiveStreams.fromSubscriber(subscriber))
                .run(engine)
                .toCompletableFuture();
        subscriber.assertNextCalled();
    }

    private class LatchSubscriber<T> extends CountDownLatch implements Subscriber<T> {

        public LatchSubscriber() {
            super(1);
        }


        @Override
        public void onSubscribe(Subscription s) {

        }

        @Override
        public void onNext(T t) {
            countDown();
        }

        @Override
        public void onError(Throwable t) {

        }

        @Override
        public void onComplete() {

        }

        public void assertNextCalled() {
            try {
                assertTrue(this.await(2, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                fail(e);
            }
        }
    }
}
