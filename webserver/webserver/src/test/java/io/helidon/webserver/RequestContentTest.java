/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.reactive.media.common.ContentReaders;
import io.helidon.reactive.media.common.MediaContext;
import io.helidon.reactive.media.common.MessageBodyFilter;
import io.helidon.reactive.media.common.MessageBodyReader;
import io.helidon.reactive.media.common.MessageBodyReaderContext;
import io.helidon.webserver.utils.TestUtils;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import org.junit.jupiter.api.Test;

import static io.helidon.webserver.utils.TestUtils.requestChunkAsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * The RequestContentTest.
 */
@SuppressWarnings("unchecked")
public class RequestContentTest {

    @Test
    public void directSubscriptionTest() {
        Request request = requestTestStub(Multi.just("first", "second", "third").map(s -> DataChunk.create(s.getBytes())));
        StringBuilder sb = new StringBuilder();
        Multi.create(request.content()).subscribe(chunk -> sb.append(requestChunkAsString(chunk)).append("-"));
        assertThat(sb.toString(), is("first-second-third-"));
    }

    @Test
    public void upperCaseFilterTest() {
        Request request = requestTestStub(Multi.just("first", "second", "third").map(s -> DataChunk.create(s.getBytes())));
        StringBuilder sb = new StringBuilder();
        request.content().registerFilter((Publisher<DataChunk> publisher) -> {
            sb.append("apply_filter-");
            return Multi.create(publisher)
                    .map(TestUtils::requestChunkAsString)
                    .map(String::toUpperCase)
                    .map(s -> DataChunk.create(s.getBytes()));
        });

        assertThat("Apply filter is expected to be called after a subscription!", sb.toString(), is(""));

        Multi.create(request.content()).subscribe(chunk -> sb.append(requestChunkAsString(chunk)).append("-"));
        assertThat(sb.toString(), is("apply_filter-FIRST-SECOND-THIRD-"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void multiThreadingFilterAndReaderTest() throws Exception {
        CountDownLatch subscribedLatch = new CountDownLatch(1);
        SubmissionPublisher<DataChunk> publisher = new SubmissionPublisher<>(Runnable::run, 10);
        ForkJoinPool.commonPool().submit(() -> {
            try {
                if (!subscribedLatch.await(10, TimeUnit.SECONDS)) {
                    fail("Subscriber didn't subscribe in timely manner!");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted!", e);
            }

            publisher.submit(DataChunk.create("first".getBytes()));
            publisher.submit(DataChunk.create("second".getBytes()));
            publisher.submit(DataChunk.create("third".getBytes()));
            publisher.close();
        });

        Request request = requestTestStub(Multi.create(publisher));

        request.content().registerFilter(originalPublisher -> subscriberDelegate
                -> originalPublisher.subscribe(new Subscriber<DataChunk>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscriberDelegate.onSubscribe(subscription);
                subscribedLatch.countDown();
            }

            @Override
            public void onNext(DataChunk item) {
                // mapping the on next call only
                subscriberDelegate.onNext(
                        DataChunk.create(requestChunkAsString(item)
                                                 .toUpperCase().getBytes()));
            }

            @Override
            public void onError(Throwable throwable) {
                subscriberDelegate.onError(throwable);
            }

            @Override
            public void onComplete() {
                subscriberDelegate.onComplete();
            }
        }));

        request.content().registerReader(new MessageBodyReader<List>() {
            @Override
            public PredicateResult accept(GenericType<?> type, MessageBodyReaderContext context) {
                if (type.rawType().equals(List.class)) {
                    return PredicateResult.SUPPORTED;
                }
                return PredicateResult.NOT_SUPPORTED;
            }

            @Override
            public <U extends List> Single<U> read(Publisher<DataChunk> publisher,
                                                   GenericType<U> type,
                                                   MessageBodyReaderContext context) {

                CompletableFuture future = new CompletableFuture<>();
                List<String> list = new CopyOnWriteArrayList<>();

                publisher.subscribe(new Subscriber<>() {
                    @Override
                    public void onSubscribe(Subscription subscription) {
                        subscription.request(Long.MAX_VALUE);
                        subscribedLatch.countDown();
                    }

                    @Override
                    public void onNext(DataChunk item) {
                        list.add(TestUtils.requestChunkAsString(item));
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        fail("Received an exception: " + throwable.getMessage());
                    }

                    @Override
                    public void onComplete() {
                        future.complete(list);
                    }
                });
                return Single.create(future);
            }
        });

        List<String> result = (List<String>) request.content().as(List.class).toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        assertThat(result, hasItems(
                is("FIRST"),
                is("SECOND"),
                is("THIRD")));
    }

    @Test
    public void failingFilter() throws Exception {
        Request request = requestTestStub(Single.never());

        request.content().registerFilter(publisher -> {
            throw new IllegalStateException("failed-publisher-transformation");
        });

        request.content().registerReader(new MessageBodyReader<Object>() {
            @Override
            public <U> Single<U> read(Publisher<DataChunk> publisher, GenericType<U> type,
                                      MessageBodyReaderContext context) {
                fail("Should not be called");
                throw new IllegalStateException("unreachable code");
            }

            @Override
            public PredicateResult accept(GenericType<?> type, MessageBodyReaderContext context) {
                if (type.equals(GenericType.create(Duration.class))) {
                    return PredicateResult.SUPPORTED;
                }
                return PredicateResult.NOT_SUPPORTED;
            }
        });

        CompletableFuture<?> future = request.content().as(Duration.class)
                .toCompletableFuture();
        try {
            future.get(10, TimeUnit.SECONDS);
            fail("Should have thrown an exception");
        } catch (ExecutionException e) {
            assertThat(e.getCause(),
                       allOf(instanceOf(IllegalStateException.class),
                             hasProperty("message", containsString("Transformation failed!"))));
            assertThat(e.getCause().getCause(),
                       hasProperty("message", containsString("failed-publisher-transformation")));
        }
    }

    @Test
    public void failingReader() throws Exception {
        Request request = requestTestStub(Single.never());

        request.content().registerReader(new MessageBodyReader<Object>() {
            @Override
            public <U> Single<U> read(Publisher<DataChunk> publisher, GenericType<U> type,
                                      MessageBodyReaderContext context) {
                throw new IllegalStateException("failed-read");
            }

            @Override
            public PredicateResult accept(GenericType<?> type, MessageBodyReaderContext context) {
                if (type.equals(GenericType.create(Duration.class))) {
                    return PredicateResult.SUPPORTED;
                }
                return PredicateResult.NOT_SUPPORTED;
            }
        });

        try {
            request.content().as(Duration.class).toCompletableFuture().get(10, TimeUnit.SECONDS);
            fail("Should have thrown an exception");
        } catch (ExecutionException e) {
            assertThat(e.getCause(),
                       allOf(instanceOf(IllegalStateException.class),
                             hasProperty("message", containsString("Transformation failed!"))));
            assertThat(e.getCause().getCause(),
                       hasProperty("message", containsString("failed-read")));
        }
    }

    @Test
    public void missingReaderTest() throws Exception {
        Request request = requestTestStub(Single.just(DataChunk.create("hello".getBytes())));

        CompletableFuture<?> future = request.content().as(Duration.class).toCompletableFuture();
        try {
            future.get(10, TimeUnit.SECONDS);
            fail("Should have thrown an exception");
        } catch (ExecutionException e) {
            assertThat(e.getCause(), instanceOf(IllegalStateException.class));
        }
    }

    @Test
    public void nullFilter() {
        Request request = requestTestStub(Single.never());
        assertThrows(NullPointerException.class, () -> {
            request.content().registerFilter((MessageBodyFilter) null);
        });
    }

    @Test
    public void failingSubscribe() {
        Request request = requestTestStub(Multi.singleton(DataChunk.create("data".getBytes())));

        request.content().registerFilter((Publisher<DataChunk> publisher) -> {
            throw new IllegalStateException("failed-publisher-transformation");
        });

        AtomicReference<Throwable> receivedThrowable = new AtomicReference<>();

        Multi.create(request.content())
                .subscribe(byteBuffer -> {
                    fail("Should not have been called!");
                }, receivedThrowable::set);

        Throwable throwable = receivedThrowable.get();
        assertThat(throwable, allOf(instanceOf(IllegalArgumentException.class),
                                    hasProperty("message",
                                                containsString("Unexpected exception occurred during publishers chaining"))));
        assertThat(throwable.getCause(), hasProperty("message", containsString("failed-publisher-transformation")));
    }

    @Test
    public void readerTest() throws Exception {
        Request request = requestTestStub(Multi.singleton(DataChunk.create("2010-01-02".getBytes())));

        request.content().registerReader(new MessageBodyReader<Object>() {
            @Override
            public <U> Single<U> read(Publisher<DataChunk> publisher, GenericType<U> type, MessageBodyReaderContext context) {
                return (Single<U>) Single.create(ContentReaders
                        .readString(publisher, Request.contentCharset(request))
                        .toStage()
                        .thenApply(LocalDate::parse));
            }

            @Override
            public PredicateResult accept(GenericType<?> type, MessageBodyReaderContext context) {
                if (type.rawType().equals(LocalDate.class)) {
                    return PredicateResult.SUPPORTED;
                }
                return PredicateResult.NOT_SUPPORTED;
            }
        });

        CompletionStage<String> complete = request.content().as(LocalDate.class)
                .thenApply(o -> o.getDayOfMonth() + "/" + o.getMonthValue() + "/" + o.getYear());

        String result = complete.toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertThat(result, is("2/1/2010"));
    }

    @Test
    public void implicitByteArrayContentReader() throws Exception {
        Request request = requestTestStub(Multi.singleton(DataChunk.create("test-string".getBytes())));
        CompletionStage<String> complete = request.content().as(byte[].class).thenApply(String::new);
        assertThat(complete.toCompletableFuture().get(10, TimeUnit.SECONDS), is("test-string"));
    }

    @Test
    public void implicitStringContentReader() throws Exception {
        Request request = requestTestStub(Multi.singleton(DataChunk.create("test-string".getBytes())));
        CompletionStage<? extends String> complete = request.content().as(String.class);
        assertThat(complete.toCompletableFuture().get(10, TimeUnit.SECONDS), is("test-string"));
    }

    @Test
    public void overridingStringContentReader() throws Exception {
        Request request = requestTestStub(Multi.singleton(DataChunk.create("test-string".getBytes())));

        request.content().registerReader(new MessageBodyReader<Object>() {
            @Override
            public <U> Single<U> read(Publisher<DataChunk> publisher, GenericType<U> type, MessageBodyReaderContext context) {
                fail("Should not be called");
                throw new IllegalStateException("unreachable code");
            }

            @Override
            public PredicateResult accept(GenericType<?> type, MessageBodyReaderContext context) {
                if (type.equals(GenericType.STRING)) {
                    return PredicateResult.SUPPORTED;
                }
                return PredicateResult.NOT_SUPPORTED;
            }
        });
        request.content().registerReader(new MessageBodyReader<Object>() {
            @Override
            public <U> Single<U> read(Publisher<DataChunk> publisher, GenericType<U> type, MessageBodyReaderContext context) {
                return (Single<U>) Multi.create(publisher)
                        .map(TestUtils::requestChunkAsString)
                        .map(String::toUpperCase)
                        .collectList()
                        .map((strings -> strings.get(0)));
            }

            @Override
            public PredicateResult accept(GenericType<?> type, MessageBodyReaderContext context) {
                if (type.equals(GenericType.STRING)) {
                    return PredicateResult.SUPPORTED;
                }
                return PredicateResult.NOT_SUPPORTED;
            }
        });

        CompletionStage<? extends String> complete = request.content().as(String.class);
        assertThat(complete.toCompletableFuture().get(10, TimeUnit.SECONDS), is("TEST-STRING"));
    }

    private static Request requestTestStub(Publisher<DataChunk> flux) {
        BareRequest bareRequestMock = mock(BareRequest.class);
        doReturn(URI.create("http://0.0.0.0:1234")).when(bareRequestMock).uri();
        doReturn(flux).when(bareRequestMock).bodyPublisher();
        WebServer webServer = mock(WebServer.class);
        MediaContext mediaContext = MediaContext.create();
        doReturn(mediaContext.readerContext()).when(webServer).readerContext();
        doReturn(mediaContext.writerContext()).when(webServer).writerContext();
        doReturn(new NettyRequestHeaders(new DefaultHttpHeaders())).when(bareRequestMock).headers();
        return new RequestTestStub(bareRequestMock, webServer);
    }
}
