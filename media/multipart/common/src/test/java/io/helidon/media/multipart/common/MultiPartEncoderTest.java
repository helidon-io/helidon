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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import io.helidon.common.http.MediaType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Multi;
import io.helidon.media.common.ContentReaders;
import io.helidon.media.multipart.common.MultiPartDecoderTest.DataChunkSubscriber;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static io.helidon.media.multipart.common.BodyPartTest.MEDIA_CONTEXT;

/**
 * Test {@link MultiPartEncoder}.
 */
public class MultiPartEncoderTest {

    private static final List<WriteableBodyPart> EMPTY_PARTS = List.of();

    // TODO test throttling

    @Test
    public void testEncodeOnePart() throws Exception {
        String boundary = "boundary";
        String message = encodeParts(boundary,
                WriteableBodyPart.builder()
                        .entity("part1")
                        .build());
        assertThat(message, is(equalTo(
                "--" + boundary + "\r\n"
                + "\r\n"
                + "part1\n"
                + "--" + boundary + "--")));
    }

    @Test
    public void testEncodeOnePartWithHeaders() throws Exception {
        String boundary = "boundary";
        String message = encodeParts(boundary,
                WriteableBodyPart.builder()
                        .headers(WriteableBodyPartHeaders.builder()
                                .contentType(MediaType.TEXT_PLAIN)
                                .build())
                        .entity("part1")
                        .build());
        assertThat(message, is(equalTo(
                "--" + boundary + "\r\n"
                + "Content-Type:text/plain\r\n"
                + "\r\n"
                + "part1\n"
                + "--" + boundary + "--")));
    }

    @Test
    public void testEncodeTwoParts() throws Exception {
        String boundary = "boundary";
        String message = encodeParts(boundary,
                WriteableBodyPart.builder()
                        .entity("part1")
                        .build(),
                WriteableBodyPart.builder()
                        .entity("part2")
                        .build());
        assertThat(message, is(equalTo(
                "--" + boundary + "\r\n"
                + "\r\n"
                + "part1\n"
                + "--" + boundary + "\r\n"
                + "\r\n"
                + "part2\n"
                + "--" + boundary + "--")));
    }

    @Test
    public void testReqests() throws Exception {
        AtomicInteger nexts = new AtomicInteger(0);
        AtomicInteger requested = new AtomicInteger(0);
        AtomicInteger receivedFromUpstream = new AtomicInteger(0);

        MultiPartEncoder enc = MultiPartEncoder.create("boundary", MEDIA_CONTEXT.writerContext());
        Multi.from(LongStream.range(1, 500)
                .mapToObj(i ->
                        WriteableBodyPart.builder()
                                .entity("part" + i)
                                .build()
                ))
                .peek(i -> receivedFromUpstream.incrementAndGet())
                .subscribe(enc);
        var subs = new Subscriber<DataChunk>() {

            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(final Flow.Subscription subscription) {
                this.subscription = subscription;
                //req(129);
//                req(555);
                req(3);
                //req(1241);
//                req(Long.MAX_VALUE);
            }

            @Override
            public void onNext(final DataChunk item) {
                nexts.incrementAndGet();
//                System.out.println(nexts.incrementAndGet() + " Down. next");
//                System.out.println();
//                req(20);
            }

            void req(long l) {
//                System.out.println("requesting " + l);
                requested.addAndGet((int)l);
                subscription.request(l);
            }

            @Override
            public void onError(final Throwable throwable) {
                throw new RuntimeException(throwable);
            }

            @Override
            public void onComplete() {
                System.out.println("completed");
            }
        };

        enc.subscribe(subs);

        System.out.println();
        System.out.println("Requested   -> "+requested);
        System.out.println("Intercepted -> "+nexts);
        System.out.println("Ups. sent   -> "+receivedFromUpstream);
    }

    @Test
    public void testSubscribingMoreThanOnce() {
        MultiPartEncoder encoder = MultiPartEncoder.create("boundary", MEDIA_CONTEXT.writerContext());
        Multi.just(EMPTY_PARTS).subscribe(encoder);
        try {
            Multi.just(EMPTY_PARTS).subscribe(encoder);
            fail("exception should be thrown");
        } catch(IllegalStateException ex) {
            assertThat(ex.getMessage(), is(equalTo("Flow.Subscription already set.")));
        }
    }

    @Test
    public void testUpstreamError() {
        MultiPartEncoder decoder = MultiPartEncoder.create("boundary", MEDIA_CONTEXT.writerContext());
        Multi.<WriteableBodyPart>error(new IllegalStateException("oops")).subscribe(decoder);
        DataChunkSubscriber subscriber = new DataChunkSubscriber();
        decoder.subscribe(subscriber);
        CompletableFuture<String> future = subscriber.content().toCompletableFuture();
        assertThat(future.isCompletedExceptionally(), is(equalTo(true)));
        try {
            future.getNow(null);
            fail("exception should be thrown");
        } catch(CompletionException ex) {
            assertThat(ex.getCause(), is(instanceOf(IllegalStateException.class)));
            assertThat(ex.getCause().getMessage(), is(equalTo("oops")));
        }
    }

    @Test
    public void testPartContentPublisherError() {
        MultiPartEncoder decoder = MultiPartEncoder.create("boundary", MEDIA_CONTEXT.writerContext());
        Multi.just(WriteableBodyPart.builder()
                .publisher((Subscriber<? super DataChunk> subscriber) -> {
                    subscriber.onError(new IllegalStateException("oops"));
                })
                .build()).subscribe(decoder);
        DataChunkSubscriber subscriber = new DataChunkSubscriber();
        decoder.subscribe(subscriber);
        CompletableFuture<String> future = subscriber.content().toCompletableFuture();
        assertThat(future.isCompletedExceptionally(), is(equalTo(true)));
        try {
            future.getNow(null);
            fail("exception should be thrown");
        } catch(CompletionException ex) {
            assertThat(ex.getCause(), is(instanceOf(IllegalStateException.class)));
            assertThat(ex.getCause().getMessage(), is(equalTo("oops")));
        }
    }

    private static String encodeParts(String boundary, WriteableBodyPart... parts) throws Exception {
        MultiPartEncoder encoder = MultiPartEncoder.create(boundary, MEDIA_CONTEXT.writerContext());
        Multi.just(parts).subscribe(encoder);
        return ContentReaders.readString(encoder, StandardCharsets.UTF_8).get(10, TimeUnit.SECONDS);
    }
}
