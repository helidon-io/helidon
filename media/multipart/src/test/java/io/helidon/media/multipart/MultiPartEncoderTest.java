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
package io.helidon.media.multipart;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Multi;
import io.helidon.media.common.ContentReaders;
import io.helidon.media.multipart.MultiPartDecoderTest.DataChunkSubscriber;

import org.junit.jupiter.api.Test;

import static io.helidon.media.multipart.BodyPartTest.MEDIA_CONTEXT;
import static io.helidon.media.multipart.MultiPartDecoderTest.waitOnLatch;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test {@link MultiPartEncoder}.
 */
public class MultiPartEncoderTest {

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
                + "part1\r\n"
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
                + "part1\r\n"
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
                + "part1\r\n"
                + "--" + boundary + "\r\n"
                + "\r\n"
                + "part2\r\n"
                + "--" + boundary + "--")));
    }

    @Test
    public void testRequests() throws Exception {
        MultiPartEncoder enc = MultiPartEncoder.create("boundary", MEDIA_CONTEXT.writerContext());
        Multi.create(LongStream.range(1, 500)
                .mapToObj(i ->
                        WriteableBodyPart.builder()
                                .entity("part" + i)
                                .build()
                ))
                .subscribe(enc);
        final CountDownLatch latch = new CountDownLatch(3);
        Subscriber<DataChunk> subscriber = new Subscriber<DataChunk>() {

            @Override
            public void onSubscribe(final Flow.Subscription subscription) {
                subscription.request(3L);
            }

            @Override
            public void onNext(final DataChunk item) {
                latch.countDown();
            }

            @Override
            public void onComplete() {
            }

            @Override
            public void onError(Throwable throwable) {
            }
        };

        enc.subscribe(subscriber);
        waitOnLatch(latch);
    }

    @Test
    public void testSubscribingMoreThanOnce() {
        MultiPartEncoder encoder = MultiPartEncoder.create("boundary", MEDIA_CONTEXT.writerContext());
        Multi.<WriteableBodyPart>just(List.of()).subscribe(encoder);
        try {
            Multi.<WriteableBodyPart>just(List.of()).subscribe(encoder);
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
        MultiPartEncoder encoder = MultiPartEncoder.create("boundary", MEDIA_CONTEXT.writerContext());
        DataChunkSubscriber subscriber = new DataChunkSubscriber();
        encoder.subscribe(subscriber);
        Multi.just(WriteableBodyPart.builder()
                .publisher(Multi.<DataChunk>error(new IllegalStateException("oops")))
                .build())
                .subscribe(encoder);
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
