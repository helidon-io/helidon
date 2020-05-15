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
package io.helidon.media.multipart.common;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Multi;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.fail;
import static io.helidon.media.multipart.common.BodyPartTest.MEDIA_CONTEXT;

/**
 * Tests {@link MultiPartDecoder}.
 */
public class MultiPartDecoderTest {

    private static final Logger LOGGER = Logger.getLogger(MultiPartDecoder.class.getName());

    @BeforeAll
    public static void before() {
        LOGGER.setLevel(Level.ALL);
    }

    @AfterAll
    public static void after() {
        LOGGER.setLevel(Level.INFO);
    }

    @Test
    public void testOnePartInOneChunk() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "\n"
                + "body 1\n"
                + "--" + boundary + "--").getBytes();

        final CountDownLatch latch = new CountDownLatch(2);
        Consumer<BodyPart> consumer = (part) -> {
            latch.countDown();
            assertThat(part.headers().values("Content-Id"),
                    hasItems("part1"));
            DataChunkSubscriber subscriber = new DataChunkSubscriber();
            part.content().subscribe(subscriber);
            subscriber.content().thenAccept(body -> {
                latch.countDown();
                assertThat(body, is(equalTo("body 1")));
            });
        };
        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(
                SUBSCRIBER_TYPE.INFINITE, consumer);
        partsPublisher(boundary, chunk1).subscribe(testSubscriber);
        waitOnLatch(latch);
        assertThat(testSubscriber.error, is(nullValue()));
        assertThat(testSubscriber.complete, is(equalTo(true)));
    }

    @Test
    public void testTwoPartsInOneChunk() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "\n"
                + "body 1\n"
                + "--" + boundary + "\n"
                + "Content-Id: part2\n"
                + "\n"
                + "body 2\n"
                + "--" + boundary + "--").getBytes();

        final CountDownLatch latch = new CountDownLatch(4);
        Consumer<BodyPart> consumer = (part) -> {
            latch.countDown();
            if (latch.getCount() == 3) {
                assertThat(part.headers().values("Content-Id"), hasItems("part1"));
                DataChunkSubscriber subscriber = new DataChunkSubscriber();
                part.content().subscribe(subscriber);
                subscriber.content().thenAccept(body -> {
                    latch.countDown();
                    assertThat(body, is(equalTo("body 1")));
                });
            } else {
                assertThat(part.headers().values("Content-Id"), hasItems("part2"));
                DataChunkSubscriber subscriber = new DataChunkSubscriber();
                part.content().subscribe(subscriber);
                subscriber.content().thenAccept(body -> {
                    latch.countDown();
                    assertThat(body, is(equalTo("body 2")));
                });
            }
        };
        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(SUBSCRIBER_TYPE.INFINITE, consumer);
        partsPublisher(boundary, chunk1).subscribe(testSubscriber);
        waitOnLatch(latch);
        assertThat(testSubscriber.error, is(nullValue()));
        assertThat(testSubscriber.complete, is(equalTo(true)));
    }

    @Test
    public void testContentAcrossChunks() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "\n"
                + "this-is-the-1st-slice-of-the-body\n").getBytes();
        final byte[] chunk2 = ("this-is-the-2nd-slice-of-the-body\n"
                + "--" + boundary + "--").getBytes();

        final CountDownLatch latch = new CountDownLatch(2);
        Consumer<BodyPart> consumer = (part) -> {
            latch.countDown();
            assertThat(part.headers().values("Content-Id"), hasItems("part1"));
            DataChunkSubscriber subscriber = new DataChunkSubscriber();
            part.content().subscribe(subscriber);
            subscriber.content().thenAccept(body -> {
                latch.countDown();
                assertThat(body, is(equalTo(
                        "this-is-the-1st-slice-of-the-body\n"
                        + "this-is-the-2nd-slice-of-the-body")));
            });
        };
        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(SUBSCRIBER_TYPE.INFINITE, consumer);
        partsPublisher(boundary, List.of(chunk1, chunk2)).subscribe(testSubscriber);
        waitOnLatch(latch);
        assertThat(testSubscriber.error, is(nullValue()));
        assertThat(testSubscriber.complete, is(equalTo(true)));
    }

    @Test
    public void testMultipleChunksBeforeContent() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n").getBytes();
        final byte[] chunk2 = "Content-Type: text/plain\n".getBytes();
        final byte[] chunk3 = "Set-Cookie: bob=alice\n".getBytes();
        final byte[] chunk4 = "Set-Cookie: foo=bar\n".getBytes();
        final byte[] chunk5 = ("\n"
                + "body 1\n"
                + "--" + boundary + "--").getBytes();

        final CountDownLatch latch = new CountDownLatch(2);
        Consumer<BodyPart> consumer = (part) -> {
            latch.countDown();
            assertThat(part.headers().values("Content-Id"), hasItems("part1"));
            assertThat(part.headers().values("Content-Type"), hasItems("text/plain"));
            assertThat(part.headers().values("Set-Cookie"), hasItems("bob=alice", "foo=bar"));
            DataChunkSubscriber subscriber = new DataChunkSubscriber();
            part.content().subscribe(subscriber);
            subscriber.content().thenAccept(body -> {
                latch.countDown();
                assertThat(body, is(equalTo("body 1")));
            });
        };
        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(SUBSCRIBER_TYPE.INFINITE, consumer);
        partsPublisher(boundary, List.of(chunk1, chunk2, chunk3, chunk4, chunk5)).subscribe(testSubscriber);
        waitOnLatch(latch);
        assertThat(testSubscriber.error, is(nullValue()));
        assertThat(testSubscriber.complete, is(equalTo(true)));
    }

    @Test
    public void testMulitiplePartsWithOneByOneSubscriber() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "\n"
                + "body 1\n"
                + "--" + boundary + "\n"
                + "Content-Id: part2\n"
                + "\n"
                + "body 2\n"
                + "--" + boundary + "--").getBytes();

        final CountDownLatch latch = new CountDownLatch(4);
        Consumer<BodyPart> consumer = (part) -> {
            latch.countDown();
            if (latch.getCount()== 3) {
                assertThat(part.headers().values("Content-Id"), hasItems("part1"));
                DataChunkSubscriber subscriber = new DataChunkSubscriber();
                part.content().subscribe(subscriber);
                subscriber.content().thenAccept(body -> {
                    latch.countDown();
                    assertThat(body, is(equalTo("body 1")));
                });
            } else {
                assertThat(part.headers().values("Content-Id"), hasItems("part2"));
                DataChunkSubscriber subscriber = new DataChunkSubscriber();
                part.content().subscribe(subscriber);
                subscriber.content().thenAccept(body -> {
                    latch.countDown();
                    assertThat(body, is(equalTo("body 2")));
                });
            }
        };
        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(SUBSCRIBER_TYPE.ONE_BY_ONE, consumer);
        partsPublisher(boundary, chunk1).subscribe(testSubscriber);
        waitOnLatch(latch);
        assertThat(testSubscriber.error, is(nullValue()));
        assertThat(testSubscriber.complete, is(equalTo(true)));
    }

    @Test
    public void testSubscriberCancelAfterOnePart() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "\n"
                + "body 1\n"
                + "--" + boundary + "\n"
                + "Content-Id: part2\n"
                + "\n"
                + "body 2\n"
                + "--" + boundary + "--").getBytes();

        final CountDownLatch latch = new CountDownLatch(2);
        Consumer<BodyPart> consumer = (part) -> {
            latch.countDown();
            if (latch.getCount()== 1) {
                assertThat(part.headers().values("Content-Id"), hasItems("part1"));
                DataChunkSubscriber subscriber1 = new DataChunkSubscriber();
                part.content().subscribe(subscriber1);
                subscriber1.content().thenAccept(body -> {
                    latch.countDown();
                    assertThat(body, is(equalTo("body 1")));
                });
            }
        };

        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(SUBSCRIBER_TYPE.CANCEL_AFTER_ONE, consumer);
        partsPublisher(boundary, chunk1).subscribe(testSubscriber);
        waitOnLatch(latch);
        assertThat(testSubscriber.error, is(nullValue()));
        assertThat(testSubscriber.complete, is(equalTo(false)));
    }

    @Test
    public void testNoClosingBoundary(){
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Type: text/xml; charset=UTF-8\n"
                + "Content-Id: part1\n"
                + "\n"
                + "<foo>bar</foo>\n").getBytes();

        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(SUBSCRIBER_TYPE.CANCEL_AFTER_ONE, null);
        partsPublisher(boundary, chunk1).subscribe(testSubscriber);
        assertThat(testSubscriber.complete, is(equalTo(false)));
        assertThat(testSubscriber.error, is(notNullValue()));
        assertThat(testSubscriber.error.getClass(), is(equalTo(MimeParser.ParsingException.class)));
        assertThat(testSubscriber.error.getMessage(), is(equalTo("No closing MIME boundary")));
    }

    @Test
    public void testCanceledPartSubscription() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "\n"
                + "body 1.aaaa\n").getBytes();
        final byte[] chunk2 = "body 1.bbbb\n".getBytes();
        final byte[] chunk3 = ("body 1.cccc\n"
                + "--" + boundary + "\n"
                + "Content-Id: part2\n"
                + "\n"
                + "This is the 2nd").getBytes();
        final byte[] chunk4 = ("body.\n"
                + "--" + boundary + "--").getBytes();

        final CountDownLatch latch = new CountDownLatch(4);
        Consumer<BodyPart> consumer = (part) -> {
            latch.countDown();
            if (latch.getCount() == 3) {
                assertThat(part.headers().values("Content-Id"), hasItems("part1"));
                part.content().subscribe(new Subscriber<DataChunk>() {
                    Subscription subscription;

                    @Override
                    public void onSubscribe(Subscription subscription) {
                        this.subscription = subscription;
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(DataChunk item) {
                        latch.countDown();
                        subscription.cancel();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                    }

                    @Override
                    public void onComplete() {
                    }
                });
            } else {
                assertThat(part.headers().values("Content-Id"), hasItems("part2"));
                DataChunkSubscriber subscriber = new DataChunkSubscriber();
                part.content().subscribe(subscriber);
                subscriber.content().thenAccept(body -> {
                    latch.countDown();
                    assertThat(body, is(equalTo("This is the 2nd body.")));
                });
            }
        };
        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(SUBSCRIBER_TYPE.ONE_BY_ONE, consumer);
        partsPublisher(boundary, List.of(chunk1, chunk2, chunk3, chunk4)).subscribe(testSubscriber);
        waitOnLatch(latch);
        assertThat(testSubscriber.error, is(nullValue()));
        assertThat(testSubscriber.complete, is(equalTo(true)));
    }

    @Test
    public void testPartContentSubscriberThrottling() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "\n"
                + "body 1.aaaa\n").getBytes();
        final byte[] chunk2 = "body 1.bbbb\n".getBytes();
        final byte[] chunk3 = ("body 1.cccc\n"
                + "--" + boundary + "\n"
                + "Content-Id: part2\n"
                + "\n"
                + "This is the 2nd").getBytes();
        final byte[] chunk4 = ("body.\n"
                + "--" + boundary + "--").getBytes();

        final CountDownLatch latch = new CountDownLatch(3);
        Consumer<BodyPart> consumer = (part) -> {
            latch.countDown();
            if (latch.getCount() == 2) {
                assertThat(part.headers().values("Content-Id"), hasItems("part1"));
            }
            part.content().subscribe(new Subscriber<DataChunk>() {

                @Override
                public void onSubscribe(Subscription subscription) {
                    subscription.request(1);
                }

                @Override
                public void onNext(DataChunk item) {
                    latch.countDown();
                }

                @Override
                public void onError(Throwable throwable) {
                }

                @Override
                public void onComplete() {
                }
            });
        };
        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(SUBSCRIBER_TYPE.ONE_BY_ONE, consumer);
        partsPublisher(boundary, List.of(chunk1, chunk2, chunk3, chunk4)).subscribe(testSubscriber);
        waitOnLatchNegative(latch, "the 2nd part should not be processed");
        assertThat(latch.getCount(), is(equalTo(1L)));
        assertThat(testSubscriber.error, is(nullValue()));
        assertThat(testSubscriber.complete, is(equalTo(false)));
    }

    @Test
    public void testUpstreamError() {
        MultiPartDecoder decoder = MultiPartDecoder.create("boundary", MEDIA_CONTEXT.readerContext());
        new Publisher<DataChunk>(){
            @Override
            public void subscribe(Subscriber<? super DataChunk> subscriber) {
                subscriber.onError(new IllegalStateException("oops"));
            }
        }.subscribe(decoder);
        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(SUBSCRIBER_TYPE.INFINITE, null);
        decoder.subscribe(testSubscriber);
        assertThat(testSubscriber.complete, is(equalTo(false)));
        assertThat(testSubscriber.error, is(notNullValue()));
        assertThat(testSubscriber.error.getMessage(), is(equalTo("oops")));
    }

    @Test
    public void testSubcribingMoreThanOnce() {
        MultiPartDecoder decoder = MultiPartDecoder.create("boundary", MEDIA_CONTEXT.readerContext());
        chunksPublisher("foo".getBytes()).subscribe(decoder);
        try {
            chunksPublisher("bar".getBytes()).subscribe(decoder);
            fail("exception should be thrown");
        } catch(IllegalStateException ex) {
            assertThat(ex.getMessage(), is(equalTo("Flow.Subscription already set.")));
        }
    }

    /**
     * Types of test subscribers.
     */
    enum SUBSCRIBER_TYPE {
        INFINITE,
        ONE_BY_ONE,
        CANCEL_AFTER_ONE,
    }

    /**
     * A part test subscriber.
     */
    static class BodyPartSubscriber implements Subscriber<BodyPart>{

        private final SUBSCRIBER_TYPE subscriberType;
        private final Consumer<BodyPart> consumer;
        private Subscription subcription;
        private Throwable error;
        private boolean complete;

        BodyPartSubscriber(SUBSCRIBER_TYPE subscriberType, Consumer<BodyPart> consumer) {
            this.subscriberType = subscriberType;
            this.consumer = consumer;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subcription = subscription;
            if (subscriberType == SUBSCRIBER_TYPE.INFINITE) {
                subscription.request(Long.MAX_VALUE);
            } else {
                subscription.request(1);
            }
        }

        @Override
        public void onNext(BodyPart item) {
            if (consumer == null){
                return;
            }
            consumer.accept(item);
            if (subscriberType == SUBSCRIBER_TYPE.ONE_BY_ONE) {
                subcription.request(1);
            } else if (subscriberType == SUBSCRIBER_TYPE.CANCEL_AFTER_ONE) {
                subcription.cancel();
            }
        }

        @Override
        public void onError(Throwable ex) {
            error = ex;
        }

        @Override
        public void onComplete() {
            complete = true;
        }
    }

    /**
     * Create the parts publisher for the specified boundary and request chunk.
     * @param boundary multipart boundary string
     * @param data data for the chunk
     * @return publisher of body parts
     */
    static Publisher<? extends BodyPart> partsPublisher(String boundary, byte[] data) {
        return partsPublisher(boundary, List.of(data));
    }

    /**
     * Create the parts publisher for the specified boundary and request chunks.
     * @param boundary multipart boundary string
     * @param data data for the chunks
     * @return publisher of body parts
     */
    static Publisher<? extends BodyPart> partsPublisher(String boundary, List<byte[]> data) {
        MultiPartDecoder decoder = MultiPartDecoder.create(boundary, MEDIA_CONTEXT.readerContext());
        chunksPublisher(data).subscribe(decoder);
        return decoder;
    }

    /**
     * Wait on the given latch for {@code 5 seconds} and emit an assertion
     * failure if the latch countdown is not zero.
     *
     * @param latch the latch
     */
    static void waitOnLatch(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                fail("timeout");
            }
        } catch (InterruptedException ex) {
            fail(ex);
        }
    }

    /**
     * Wait on the given latch for {@code 5 seconds} and emit an assertion
     * failure if the latch countdown is zero.
     *
     * @param latch the latch
     * @param failMsg message to the assertion failure
     */
    static void waitOnLatchNegative(CountDownLatch latch, String failMsg) {
        try {
            if (latch.await(5, TimeUnit.SECONDS)) {
                fail(failMsg);
            }
        } catch (InterruptedException ex) {
            fail(ex);
        }
    }

    /**
     * Build a publisher of {@link DataChunk} from a single {@code byte[]}.
     * @param bytes data for the chunk to create
     * @return publisher
     */
    static Publisher<DataChunk> chunksPublisher(byte[] bytes) {
        return chunksPublisher(List.of(bytes));
    }

    /**
     * Build a publisher of {@link DataChunk} from a list of {@code byte[]}.
     * @param data data for the chunks to create
     * @return publisher
     */
    static Publisher<DataChunk> chunksPublisher(List<byte[]> data) {
        DataChunk[] chunks = new DataChunk[data.size()];
        int i = 0;
        for (byte[] bytes : data) {
            chunks[i++] = DataChunk.create(bytes);
        }
        return Multi.just(chunks);
    }

    /**
     * A subscriber of data chunk that accumulates bytes to a single String.
     */
    static final class DataChunkSubscriber implements Subscriber<DataChunk> {

        private final StringBuilder sb = new StringBuilder();
        private final CompletableFuture<String> future = new CompletableFuture<>();

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(DataChunk item) {
            sb.append(item.bytes());
        }

        @Override
        public void onError(Throwable ex) {
            future.completeExceptionally(ex);
        }

        @Override
        public void onComplete() {
            future.complete(sb.toString());
        }

        CompletionStage<String> content() {
            return future;
        }
    }
}
