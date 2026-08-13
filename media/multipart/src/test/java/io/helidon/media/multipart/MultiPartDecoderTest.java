/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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
package io.helidon.media.multipart;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.BufferedEmittingPublisher;
import io.helidon.common.reactive.Multi;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests {@link MultiPartDecoder}.
 */
public class MultiPartDecoderTest {

    @Test
    public void testOnePartInOneChunk() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "\n"
                + "body 1\n"
                + "--" + boundary + "--").getBytes();

        final CountDownLatch latch = new CountDownLatch(2);
        final CompletableFuture<Void> testDone = new CompletableFuture<>();

        Consumer<ReadableBodyPart> consumer = (part) -> {
            latch.countDown();
            assertThat(part.headers().values("Content-Id"),
                    hasItems("part1"));
            DataChunkSubscriber subscriber = new DataChunkSubscriber();
            part.content().subscribe(subscriber);
            subscriber.content().thenAccept(body -> {
                assertThat(body, is(equalTo("body 1")));
                latch.countDown();
                if (latch.getCount() == 0) {
                    testDone.complete(null);
                }
            }).exceptionally((ex) -> {
                testDone.completeExceptionally(ex);
                return null;
            });
        };
        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(
                SUBSCRIBER_TYPE.INFINITE, consumer);
        partsPublisher(boundary, chunk1).subscribe(testSubscriber);
        testDone.orTimeout(5, TimeUnit.SECONDS).join();
        try {
            boolean b = testSubscriber.complete.orTimeout(200, TimeUnit.MILLISECONDS).join();
            assertThat(b, is(equalTo(true)));
        } catch(CompletionException error) {
            assertThat(error, is(nullValue()));
        }
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
        Consumer<ReadableBodyPart> consumer = (part) -> {
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
        try {
            boolean b = testSubscriber.complete.orTimeout(200, TimeUnit.MILLISECONDS).join();
            assertThat(b, is(equalTo(true)));
        } catch(CompletionException error) {
            assertThat(error, is(nullValue()));
        }
        waitOnLatch(latch);
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
        Consumer<ReadableBodyPart> consumer = (part) -> {
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
        try {
            boolean b = testSubscriber.complete.orTimeout(200, TimeUnit.MILLISECONDS).join();
            assertThat(b, is(equalTo(true)));
        } catch(CompletionException error) {
            assertThat(error, is(nullValue()));
        }
        waitOnLatch(latch);
    }

    @Test
    public void testContentAcrossChunksAsyncRequest() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "\n"
                + "thi").getBytes();
        final byte[] chunk11 = ("s-is-the-1st-slice-of-the-body\n").getBytes();
        final byte[] chunk12 = ("t").getBytes();
        final byte[] chunk2 = ("his-is-the-2nd-slice-of-the-body\n"
                + "--" + boundary + "--").getBytes();

        final CountDownLatch latch = new CountDownLatch(2);
        Consumer<ReadableBodyPart> consumer = (part) -> {
            latch.countDown();
            assertThat(part.headers().values("Content-Id"), hasItems("part1"));
            DataChunkSubscriber subscriber = new DataChunkSubscriber();
            part.content().subscribe(subscriber);
            subscriber.content().thenAccept(body -> {
                assertThat(body, is(equalTo(
                        "this-is-the-1st-slice-of-the-body\n"
                        + "this-is-the-2nd-slice-of-the-body")));
            }).exceptionally((ex) -> {
                System.out.println("UH-OH... " + ex);
                return null;
            }).thenAccept((_i) -> {
                latch.countDown();
            });
        };
        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(SUBSCRIBER_TYPE.ONE_BY_ONE, consumer);
        partsPublisher(boundary, List.of(chunk1, chunk11, chunk12, chunk2)).subscribe(testSubscriber);
        try {
            boolean b = testSubscriber.complete.orTimeout(200, TimeUnit.MILLISECONDS).join();
            assertThat(b, is(equalTo(true)));
        } catch(CompletionException error) {
            assertThat(error, is(nullValue()));
        }
        waitOnLatch(latch);
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
        Consumer<ReadableBodyPart> consumer = (part) -> {
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
        try {
            boolean b = testSubscriber.complete.orTimeout(200, TimeUnit.MILLISECONDS).join();
            assertThat(b, is(equalTo(true)));
        } catch(CompletionException error) {
            assertThat(error, is(nullValue()));
        }
        waitOnLatch(latch);
    }

    @Test
    public void testMultiplePartsWithOneByOneSubscriber() {
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
        Consumer<ReadableBodyPart> consumer = (part) -> {
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
        try {
            boolean b = testSubscriber.complete.orTimeout(200, TimeUnit.MILLISECONDS).join();
            assertThat(b, is(equalTo(true)));
        } catch(CompletionException error) {
            assertThat(error, is(nullValue()));
        }
        waitOnLatch(latch);
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
        Consumer<ReadableBodyPart> consumer = (part) -> {
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
    }

    @Test
    public void testSubscriberCancelAfterOnePartWithFragmentedBody() {
        String boundary = "boundary";
        byte[] headers = ("--" + boundary + "\r\n"
                + "Content-Id: part1\r\n"
                + "\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] body = ("body 1\r\n"
                + "--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        CompletableFuture<String> content = new CompletableFuture<>();
        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(SUBSCRIBER_TYPE.CANCEL_AFTER_ONE, part -> {
            DataChunkSubscriber subscriber = new DataChunkSubscriber();
            part.content().subscribe(subscriber);
            subscriber.content().whenComplete((value, failure) -> {
                if (failure == null) {
                    content.complete(value);
                } else {
                    content.completeExceptionally(failure);
                }
            });
        });

        partsPublisher(boundary, List.of(headers, body)).subscribe(testSubscriber);

        testSubscriber.cancelled.orTimeout(5, TimeUnit.SECONDS).join();
        assertThat(content.orTimeout(5, TimeUnit.SECONDS).join(), is(equalTo("body 1")));
    }

    @Test
    public void testNoClosingBoundary(){
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Type: text/xml; charset=UTF-8\n"
                + "Content-Id: part1\n"
                + "\n"
                + "<foo>bar</foo>\n").getBytes();

        DataChunkSubscriber s1 = new DataChunkSubscriber();
        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(SUBSCRIBER_TYPE.ONE_BY_ONE, p -> p.content().subscribe(s1));
        partsPublisher(boundary, chunk1).subscribe(testSubscriber);
        try {
            s1.future.orTimeout(100, TimeUnit.MILLISECONDS).join();
            throw new IllegalStateException("Should have terminated exceptionally");
        } catch(CompletionException e) {
            Throwable error = e.getCause();
            assertThat(error.getClass(), is(equalTo(MimeParser.ParsingException.class)));
            assertThat(error.getMessage(), is(equalTo("No closing MIME boundary")));
        }

        // CANCEL_AFTER_ONE emits cancel as soon as the first part is arrived.
        // Once testSubscriber notified it is cancelled, no signals are guaranteed to arrive to it, so one cannot
        // expect error to be signalled to it.
        //
        // One should expect the error to arrive to the inner subscriber, but here it is not set at all - the
        // inner subscriber does not request the body, so the absence of any content after what has been published
        // is not guaranteed. (Subscribers are required to eventually either issue a request, or cancel)
        try {
            testSubscriber.complete.orTimeout(200, TimeUnit.MILLISECONDS).join();
            throw new IllegalStateException("Not expecting to terminate normally");
        } catch(CompletionException e) {
            Throwable error = e.getCause();
            assertThat(error, is(notNullValue()));
            assertThat(error.getClass(), is(equalTo(MimeParser.ParsingException.class)));
            assertThat(error.getMessage(), is(equalTo("No closing MIME boundary")));
        }
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
        Consumer<ReadableBodyPart> consumer = (part) -> {
            latch.countDown();
            if (latch.getCount() == 2) {
                assertThat(part.headers().values("Content-Id"), hasItems("part1"));
            }
            part.content().subscribe(new Subscriber<>() {

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
        try {
            testSubscriber.complete.orTimeout(200, TimeUnit.MILLISECONDS).join();
            throw new IllegalStateException("Not expecting to make progress, unless the part is consumed");
        } catch(CompletionException e) {
            Throwable error = e.getCause();
            // This is the expected outcome - the testSubscriber is not making progress
            assertThat(error.getClass(), is(equalTo(TimeoutException.class)));
        }
    }

    @Test
    public void testUpstreamError() {
        MultiPartDecoder decoder = decoder("boundary");
        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(SUBSCRIBER_TYPE.INFINITE, null);
        decoder.subscribe(testSubscriber);
        Multi.<DataChunk>error(new IllegalStateException("oops")).subscribe(decoder);
        try {
            testSubscriber.complete.orTimeout(200, TimeUnit.MILLISECONDS).join();
            throw new IllegalStateException("Normal termination is not expected");
        } catch(CompletionException e) {
            Throwable error = e.getCause();
            assertThat(error, is(notNullValue()));
            assertThat(error.getMessage(), is(equalTo("oops")));
        }
    }

    @Test
    public void testSubscribingMoreThanOnce() {
        MultiPartDecoder decoder = decoder("boundary");
        chunksPublisher("foo".getBytes()).subscribe(decoder);
        try {
            chunksPublisher("bar".getBytes()).subscribe(decoder);
            fail("exception should be thrown");
        } catch(IllegalStateException ex) {
            assertThat(ex.getMessage(), is(equalTo("Flow.Subscription already set.")));
        }
    }

    @Test
    public void testLateSubscriber() {
        String boundary = "boundary";
        byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "\n"
                + "body 1\n"
                + "--" + boundary + "--").getBytes();

        // set up the decoder in an initialized state (upstream and downstream)
        MultiPartDecoder decoder = decoder(boundary);
        List<ReadableBodyPart> parts = new ArrayList<>();
        Multi.create(decoder).subscribe(parts::add);
        BufferedEmittingPublisher<DataChunk> emitter = BufferedEmittingPublisher.create();
        emitter.subscribe(decoder);

        // emit one chunk and complete
        emitter.emit(DataChunk.create(chunk1));
        emitter.complete();
        try {
            // subscribe for part chunks
            parts.forEach(ReadableBodyPart::drain);
        } catch (Throwable ex) {
            fail(ex);
        }
    }

    @Test
    public void testLastEmptyChunk() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "\n"
                + "body 1\n"
                + "--" + boundary + "--").getBytes();

        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(
                SUBSCRIBER_TYPE.INFINITE, ReadableBodyPart::drain);
        partsPublisher(boundary, List.of(chunk1, new byte[0])).subscribe(testSubscriber);
        try {
            boolean b = testSubscriber.complete.orTimeout(200, TimeUnit.MILLISECONDS).join();
            assertThat(b, is(equalTo(true)));
        } catch(CompletionException error) {
            assertThat(error, is(nullValue()));
        }
    }

    @Test
    public void testEpilogueChunksReleasedBeforeUpstreamCompletion() {
        String boundary = "boundary";
        byte[] message = ("--" + boundary + "\r\n"
                + "Content-Id: part1\r\n"
                + "\r\n"
                + "body 1\r\n"
                + "--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        MultiPartDecoder decoder = decoder(boundary);
        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(
                SUBSCRIBER_TYPE.INFINITE, ReadableBodyPart::drain);
        AtomicInteger releasedMessageChunks = new AtomicInteger();
        AtomicInteger releasedEpilogueChunks = new AtomicInteger();

        decoder.subscribe(testSubscriber);
        decoder.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
            }
        });
        decoder.onNext(DataChunk.create(false,
                releasedMessageChunks::incrementAndGet,
                ByteBuffer.wrap(message)));

        assertThat(releasedMessageChunks.get(), is(equalTo(1)));
        for (int i = 0; i < 16; i++) {
            decoder.onNext(DataChunk.create(false,
                    releasedEpilogueChunks::incrementAndGet,
                    ByteBuffer.wrap(("epilogue-" + i).getBytes(StandardCharsets.US_ASCII))));
        }

        assertThat(releasedEpilogueChunks.get(), is(equalTo(16)));
        decoder.onComplete();
        assertThat(testSubscriber.complete.orTimeout(5, TimeUnit.SECONDS).join(), is(equalTo(true)));
    }

    @Test
    public void testLateEpilogueChunkReleasedAfterCancellation() {
        String boundary = "boundary";
        byte[] message = ("--" + boundary + "\r\n"
                + "Content-Id: part1\r\n"
                + "\r\n"
                + "body 1\r\n"
                + "--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        MultiPartDecoder decoder = decoder(boundary);
        CompletableFuture<Subscription> downstreamSubscription = new CompletableFuture<>();
        CountDownLatch epilogueRequestStarted = new CountDownLatch(1);
        CountDownLatch resumeEpilogueRequest = new CountDownLatch(1);
        CountDownLatch upstreamCancelled = new CountDownLatch(1);
        AtomicInteger requestCount = new AtomicInteger();

        decoder.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                downstreamSubscription.complete(subscription);
                subscription.request(1);
            }

            @Override
            public void onNext(ReadableBodyPart item) {
                item.drain();
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });
        decoder.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                if (requestCount.incrementAndGet() == 2) {
                    epilogueRequestStarted.countDown();
                    waitOnLatch(resumeEpilogueRequest);
                }
            }

            @Override
            public void cancel() {
                upstreamCancelled.countDown();
            }
        });

        CompletableFuture<Void> decoding = new CompletableFuture<>();
        Thread decodingThread = new Thread(() -> {
            try {
                decoder.onNext(DataChunk.create(message));
                decoding.complete(null);
            } catch (Throwable ex) {
                decoding.completeExceptionally(ex);
            }
        }, "multipart-epilogue-request");
        decodingThread.start();
        try {
            waitOnLatch(epilogueRequestStarted);
            downstreamSubscription.join().cancel();
        } finally {
            resumeEpilogueRequest.countDown();
        }
        decoding.orTimeout(5, TimeUnit.SECONDS).join();
        waitOnLatch(upstreamCancelled);

        AtomicInteger releasedLateChunks = new AtomicInteger();
        decoder.onNext(DataChunk.create(false,
                releasedLateChunks::incrementAndGet,
                ByteBuffer.wrap("late epilogue".getBytes(StandardCharsets.US_ASCII))));

        assertThat(releasedLateChunks.get(), is(equalTo(1)));
    }

    @Test
    public void testLateChunkAfterCancellationCancelsUpstream() {
        MultiPartDecoder decoder = decoder("boundary");
        CompletableFuture<Subscription> downstreamSubscription = new CompletableFuture<>();
        AtomicInteger upstreamCancellations = new AtomicInteger();

        decoder.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                downstreamSubscription.complete(subscription);
                subscription.request(1);
            }

            @Override
            public void onNext(ReadableBodyPart item) {
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });
        decoder.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
                upstreamCancellations.incrementAndGet();
            }
        });

        downstreamSubscription.join().cancel();

        AtomicInteger releasedLateChunks = new AtomicInteger();
        decoder.onNext(DataChunk.create(false,
                releasedLateChunks::incrementAndGet,
                ByteBuffer.wrap("late chunk".getBytes(StandardCharsets.US_ASCII))));

        assertThat(releasedLateChunks.get(), is(equalTo(1)));
        assertThat(upstreamCancellations.get(), is(equalTo(1)));
    }

    @Test
    public void testTrailingEmptyBufferReleasedBeforeUpstreamCompletion() {
        String boundary = "boundary";
        byte[] headers = ("--" + boundary + "\r\n"
                + "Content-Id: part1\r\n"
                + "\r\n").getBytes(StandardCharsets.US_ASCII);
        MultiPartDecoder decoder = decoder(boundary);
        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(
                SUBSCRIBER_TYPE.INFINITE, ReadableBodyPart::drain);
        AtomicInteger releasedChunks = new AtomicInteger();

        decoder.subscribe(testSubscriber);
        decoder.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
            }
        });
        decoder.onNext(DataChunk.create(headers));
        decoder.onNext(DataChunk.create(false,
                releasedChunks::incrementAndGet,
                ByteBuffer.wrap("a".repeat(64).getBytes(StandardCharsets.US_ASCII)),
                ByteBuffer.allocate(0)));
        decoder.onNext(DataChunk.create("b".repeat(64).getBytes(StandardCharsets.US_ASCII)));

        assertThat(releasedChunks.get(), is(equalTo(1)));
        decoder.onNext(DataChunk.create(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII)));
        decoder.onComplete();
        assertThat(testSubscriber.complete.orTimeout(5, TimeUnit.SECONDS).join(), is(equalTo(true)));
        assertThat(releasedChunks.get(), is(equalTo(1)));
    }

    @Test
    public void testCompletionDoesNotRequireAdditionalPartDemand() {
        String boundary = "boundary";
        byte[] firstChunk = ("--" + boundary + "\r\n"
                + "Content-Id: part1\r\n"
                + "\r\n"
                + "body 1\r\n"
                + "--" + boundary).getBytes(StandardCharsets.US_ASCII);
        MultiPartDecoder decoder = decoder(boundary);
        AtomicInteger receivedParts = new AtomicInteger();
        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(
                SUBSCRIBER_TYPE.ONE, part -> {
                    receivedParts.incrementAndGet();
                    part.drain();
                });
        AtomicInteger releasedChunks = new AtomicInteger();

        decoder.subscribe(testSubscriber);
        decoder.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
            }
        });
        decoder.onNext(DataChunk.create(false,
                releasedChunks::incrementAndGet,
                ByteBuffer.wrap(firstChunk)));
        decoder.onNext(DataChunk.create(false,
                releasedChunks::incrementAndGet,
                ByteBuffer.wrap("--".getBytes(StandardCharsets.US_ASCII))));
        decoder.onComplete();

        assertThat(testSubscriber.complete.orTimeout(5, TimeUnit.SECONDS).join(), is(equalTo(true)));
        assertThat(receivedParts.get(), is(equalTo(1)));
        assertThat(releasedChunks.get(), is(equalTo(2)));
    }

    @Test
    public void testEpilogueDoesNotRequireAdditionalPartDemand() {
        String boundary = "boundary";
        byte[] message = ("--" + boundary + "\r\n"
                + "Content-Id: part1\r\n"
                + "\r\n"
                + "body 1\r\n"
                + "--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        MultiPartDecoder decoder = decoder(boundary);
        AtomicInteger receivedParts = new AtomicInteger();
        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(
                SUBSCRIBER_TYPE.ONE, part -> {
                    receivedParts.incrementAndGet();
                    part.drain();
                });
        AtomicInteger releasedChunks = new AtomicInteger();

        Multi.just(DataChunk.create(false,
                           releasedChunks::incrementAndGet,
                           ByteBuffer.wrap(message)),
                   DataChunk.create(false,
                           releasedChunks::incrementAndGet,
                           ByteBuffer.wrap("epilogue".getBytes(StandardCharsets.US_ASCII))))
                .subscribe(decoder);
        decoder.subscribe(testSubscriber);

        assertThat(testSubscriber.complete.orTimeout(5, TimeUnit.SECONDS).join(), is(equalTo(true)));
        assertThat(receivedParts.get(), is(equalTo(1)));
        assertThat(releasedChunks.get(), is(equalTo(2)));
    }

    @Test
    public void testInterPartBoundaryPaddingLimitReleasesChunks() {
        String boundary = "boundary";
        byte[] firstChunk = ("--" + boundary + "\r\n"
                + "Content-Id: part1\r\n"
                + "\r\n"
                + "body 1\r\n"
                + "--" + boundary).getBytes(StandardCharsets.US_ASCII);
        AtomicInteger releasedChunks = new AtomicInteger();
        List<DataChunk> chunks = new ArrayList<>();
        chunks.add(DataChunk.create(false,
                releasedChunks::incrementAndGet,
                ByteBuffer.wrap(firstChunk)));
        for (int i = 0; i < 8; i++) {
            chunks.add(DataChunk.create(false,
                    releasedChunks::incrementAndGet,
                    ByteBuffer.wrap(" ".repeat(1024).getBytes(StandardCharsets.US_ASCII))));
        }
        chunks.add(DataChunk.create(false,
                releasedChunks::incrementAndGet,
                ByteBuffer.wrap(new byte[] {' '})));

        MultiPartDecoder decoder = decoder(boundary);
        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(
                SUBSCRIBER_TYPE.INFINITE, ReadableBodyPart::drain);
        Multi.just(chunks.toArray(DataChunk[]::new)).subscribe(decoder);
        decoder.subscribe(testSubscriber);

        CompletionException ex = assertThrows(CompletionException.class,
                () -> testSubscriber.complete.orTimeout(5, TimeUnit.SECONDS).join());
        assertThat(ex.getCause().getMessage(), is(equalTo("MIME boundary padding is too long")));
        assertThat(releasedChunks.get(), is(equalTo(chunks.size())));
    }

    @Test
    public void testValidFragmentedBoundaryPaddingReleasesChunksBeforeCompletion() {
        String boundary = "boundary";
        byte[] firstChunk = ("--" + boundary + "\r\n"
                + "Content-Id: part1\r\n"
                + "\r\n"
                + "body 1\r\n"
                + "--" + boundary).getBytes(StandardCharsets.US_ASCII);
        MultiPartDecoder decoder = decoder(boundary);
        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(
                SUBSCRIBER_TYPE.INFINITE, ReadableBodyPart::drain);
        AtomicInteger releasedPaddingChunks = new AtomicInteger();

        decoder.subscribe(testSubscriber);
        decoder.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
            }
        });
        decoder.onNext(DataChunk.create(firstChunk));
        for (int i = 0; i < 65; i++) {
            decoder.onNext(DataChunk.create(false,
                    releasedPaddingChunks::incrementAndGet,
                    ByteBuffer.wrap(new byte[] {' '})));
        }
        decoder.onNext(DataChunk.create(("\r\n"
                + "Content-Id: part2\r\n"
                + "\r\n").getBytes(StandardCharsets.US_ASCII)));
        decoder.onNext(DataChunk.create("body 2".getBytes(StandardCharsets.US_ASCII)));

        assertThat(releasedPaddingChunks.get(), is(equalTo(65)));
        decoder.onNext(DataChunk.create(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII)));
        decoder.onComplete();
        assertThat(testSubscriber.complete.orTimeout(5, TimeUnit.SECONDS).join(), is(equalTo(true)));
        assertThat(releasedPaddingChunks.get(), is(equalTo(65)));
    }

    @Test
    public void testOnCompleteDefersCompletionToActiveDrain() {
        String boundary = "boundary";
        byte[] message = ("--" + boundary + "\r\n"
                + "Content-Id: part1\r\n"
                + "\r\n"
                + "body 1\r\n"
                + "--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        MultiPartDecoder decoder = decoder(boundary);
        CountDownLatch partReceived = new CountDownLatch(1);
        CountDownLatch resumePart = new CountDownLatch(1);
        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(
                SUBSCRIBER_TYPE.INFINITE, part -> {
                    partReceived.countDown();
                    try {
                        if (!resumePart.await(5, TimeUnit.SECONDS)) {
                            fail("timeout waiting to resume part delivery");
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        fail(ex);
                    }
                    part.drain();
                });

        decoder.subscribe(testSubscriber);
        decoder.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
            }
        });
        CompletableFuture<Void> draining = new CompletableFuture<>();
        Thread drainThread = new Thread(() -> {
            try {
                decoder.onNext(DataChunk.create(message));
                draining.complete(null);
            } catch (Throwable ex) {
                draining.completeExceptionally(ex);
            }
        }, "multipart-decoder-drain");
        drainThread.start();
        try {
            assertThat(partReceived.await(5, TimeUnit.SECONDS), is(true));
            decoder.onComplete();
            assertThat(testSubscriber.complete.isDone(), is(false));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            fail(ex);
        } finally {
            resumePart.countDown();
        }

        draining.orTimeout(5, TimeUnit.SECONDS).join();
        assertThat(testSubscriber.complete.orTimeout(5, TimeUnit.SECONDS).join(), is(equalTo(true)));
    }

    @Test
    public void testFilenameWithDirectoryPathUsesOnlyTerminalComponent() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Disposition: form-data; name=\"file[]\"; filename=\"%2e%2e%2f%2e%2e%2fetc%2fpasswd\"\n"
                + "\n"
                + "body 1\n"
                + "--" + boundary + "--").getBytes();

        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(SUBSCRIBER_TYPE.INFINITE, part -> {
            assertThat(part.headers().contentType(), is(MediaType.APPLICATION_OCTET_STREAM));
            assertThat(part.headers().contentDisposition().filename().orElse(null), is("passwd"));
            part.drain();
        });
        partsPublisher(boundary, chunk1).subscribe(testSubscriber);
        try {
            boolean b = testSubscriber.complete.orTimeout(200, TimeUnit.MILLISECONDS).join();
            assertThat(b, is(equalTo(true)));
        } catch (CompletionException error) {
            assertThat(error, is(nullValue()));
        }
    }

    @Test
    public void testMixedCaseFilenameWithDirectoryPathUsesOnlyTerminalComponent() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Disposition: form-data; name=\"file[]\"; Filename=\"%2e%2e%2f%2e%2e%2fetc%2fpasswd\"\n"
                + "\n"
                + "body 1\n"
                + "--" + boundary + "--").getBytes();

        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(SUBSCRIBER_TYPE.INFINITE, part -> {
            assertThat(part.headers().contentType(), is(MediaType.APPLICATION_OCTET_STREAM));
            assertThat(part.headers().contentDisposition().filename().orElse(null), is("passwd"));
            part.drain();
        });
        partsPublisher(boundary, chunk1).subscribe(testSubscriber);
        try {
            boolean b = testSubscriber.complete.orTimeout(200, TimeUnit.MILLISECONDS).join();
            assertThat(b, is(equalTo(true)));
        } catch (CompletionException error) {
            assertThat(error, is(nullValue()));
        }
    }

    @Test
    public void testUnsafeFilenameDoesNotFailPartCreation() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Disposition: form-data; name=\"file[]\"; filename=\"file%0aname.txt\"\n"
                + "\n"
                + "body 1\n"
                + "--" + boundary + "--").getBytes();

        BodyPartSubscriber testSubscriber = new BodyPartSubscriber(SUBSCRIBER_TYPE.INFINITE, part -> {
            assertThat(part.headers().contentType(), is(MediaType.APPLICATION_OCTET_STREAM));
            assertThrows(IllegalArgumentException.class, () -> part.headers().contentDisposition().filename());
            part.drain();
        });
        partsPublisher(boundary, chunk1).subscribe(testSubscriber);
        try {
            boolean b = testSubscriber.complete.orTimeout(200, TimeUnit.MILLISECONDS).join();
            assertThat(b, is(equalTo(true)));
        } catch (CompletionException error) {
            assertThat(error, is(nullValue()));
        }
    }

    /**
     * Create a new decoder.
     *
     * @param boundary boundary delimiter
     * @return decoder
     */
    static MultiPartDecoder decoder(String boundary) {
        return MultiPartDecoder.create(boundary, BodyPartTest.MEDIA_CONTEXT.readerContext());
    }

    /**
     * Create the parts publisher for the specified boundary and request chunk.
     * @param boundary multipart boundary string
     * @param data data for the chunk
     * @return publisher of body parts
     */
    static Publisher<? extends ReadableBodyPart> partsPublisher(String boundary, byte[] data) {
        return partsPublisher(boundary, List.of(data));
    }

    /**
     * Create the parts publisher for the specified boundary and request chunks.
     * @param boundary multipart boundary string
     * @param data data for the chunks
     * @return publisher of body parts
     */
    static Publisher<? extends ReadableBodyPart> partsPublisher(String boundary, List<byte[]> data) {
        MultiPartDecoder decoder = decoder(boundary);
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
     * Types of test subscribers.
     */
    enum SUBSCRIBER_TYPE {
        INFINITE,
        ONE,
        ONE_BY_ONE,
        CANCEL_AFTER_ONE,
    }

    /**
     * A part test subscriber.
     */
    static class BodyPartSubscriber implements Subscriber<ReadableBodyPart>{

        private final SUBSCRIBER_TYPE subscriberType;
        private final Consumer<ReadableBodyPart> consumer;
        private Subscription subscription;
        public CompletableFuture<Boolean> complete = new CompletableFuture<>();
        public CompletableFuture<Void> cancelled = new CompletableFuture<>();

        BodyPartSubscriber(SUBSCRIBER_TYPE subscriberType, Consumer<ReadableBodyPart> consumer) {
            this.subscriberType = subscriberType;
            this.consumer = consumer;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            if (subscriberType == SUBSCRIBER_TYPE.INFINITE) {
                subscription.request(Long.MAX_VALUE);
            } else {
                subscription.request(1);
            }
        }

        @Override
        public void onNext(ReadableBodyPart item) {
            if (consumer == null){
                return;
            }
            consumer.accept(item);
            if (subscriberType == SUBSCRIBER_TYPE.ONE_BY_ONE) {
                subscription.request(1);
            } else if (subscriberType == SUBSCRIBER_TYPE.CANCEL_AFTER_ONE) {
                subscription.cancel();
                cancelled.complete(null);
            }
        }

        @Override
        public void onError(Throwable ex) {
            complete.completeExceptionally(ex);
        }

        @Override
        public void onComplete() {
            complete.complete(true);
        }
    }

    /**
     * A subscriber of data chunk that accumulates bytes to a single String.
     */
    static class DataChunkSubscriber implements Subscriber<DataChunk> {

        private final StringBuilder sb = new StringBuilder();
        public final CompletableFuture<String> future = new CompletableFuture<>();
        private Subscription subscription;

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(DataChunk item) {
            sb.append(new String(item.bytes()));
            CompletableFuture.supplyAsync(() -> {
               try {
                  Thread.sleep(10);
               } catch(Exception e) {
                   e.printStackTrace();
               }
               subscription.request(1);
               return 0;
            });
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
