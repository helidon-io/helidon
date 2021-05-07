/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.core;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import io.helidon.grpc.core.MethodHandler;

import io.grpc.MethodDescriptor;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
public class ClientStreamingMethodHandlerSupplierTest {

    @Test
    public void shouldSupplyClientStreamingMethods() {
        ClientStreamingMethodHandlerSupplier supplier = new ClientStreamingMethodHandlerSupplier();
        AnnotatedMethod method = getClientStreamingMethod();
        assertThat(supplier.supplies(method), is(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldSupplyClientStreamingHandler() {
        ClientStreamingMethodHandlerSupplier supplier = new ClientStreamingMethodHandlerSupplier();
        AnnotatedMethod method = getClientStreamingMethod();
        StreamObserver<Long> responseObserver = mock(StreamObserver.class);
        Service service = mock(Service.class);

        when(service.clientStreaming(any(StreamObserver.class))).thenReturn(responseObserver);

        MethodHandler<Long, String> handler = supplier.get("foo", method, () -> service);
        assertThat(handler, is(notNullValue()));
        assertThat(handler.getRequestType(), equalTo(Long.class));
        assertThat(handler.getResponseType(), equalTo(String.class));
        assertThat(handler.type(), equalTo(MethodDescriptor.MethodType.CLIENT_STREAMING));

        StreamObserver<String> observer = mock(StreamObserver.class);
        StreamObserver<Long> result = handler.invoke(observer);
        assertThat(result, is(sameInstance(responseObserver)));
        verify(service).clientStreaming(any(StreamObserver.class));
    }

    @Test
    public void shouldHandleClientCall() {
        ClientStreamingMethodHandlerSupplier supplier = new ClientStreamingMethodHandlerSupplier();
        AnnotatedMethod method = getClientStreamingMethod();
        StreamObserver<Long> responseObserver = mock(StreamObserver.class);
        Service service = mock(Service.class);

        when(service.clientStreaming(any(StreamObserver.class))).thenReturn(responseObserver);

        MethodHandler<Long, String> handler = supplier.get("foo", method, () -> service);
        assertThat(handler, is(notNullValue()));

        StreamObserver<String> observer = mock(StreamObserver.class);
        StreamObserver<String> response = mock(StreamObserver.class);
        MethodHandler.ClientStreaming client = mock(MethodHandler.ClientStreaming.class);

        when(client.clientStreaming(anyString(), any(StreamObserver.class))).thenReturn(response);

        Object result = handler.clientStreaming(new Object[]{observer}, client);

        assertThat(result, is(sameInstance(response)));
        verify(client).clientStreaming(eq("foo"), same(observer));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldSupplyClientStreamingHandlerForMethodTakingFuture() {
        ClientStreamingMethodHandlerSupplier supplier = new ClientStreamingMethodHandlerSupplier();
        AnnotatedMethod method = getMethod("future", CompletableFuture.class);
        StreamObserver<Long> responseObserver = mock(StreamObserver.class);
        Service service = mock(Service.class);

        when(service.future(any(CompletableFuture.class))).thenReturn(responseObserver);

        MethodHandler<Long, String> handler = supplier.get("foo", method, () -> service);
        assertThat(handler, is(notNullValue()));
        assertThat(handler.getRequestType(), equalTo(Long.class));
        assertThat(handler.getResponseType(), equalTo(String.class));
        assertThat(handler.type(), equalTo(MethodDescriptor.MethodType.CLIENT_STREAMING));

        StreamObserver<String> observer = mock(StreamObserver.class);
        StreamObserver<Long> result = handler.invoke(observer);
        assertThat(result, is(sameInstance(responseObserver)));
        verify(service).future(any(CompletableFuture.class));
    }

    @Test
    public void shouldHandleClientCallForMethodTakingFuture() throws Exception {
        ClientStreamingMethodHandlerSupplier supplier = new ClientStreamingMethodHandlerSupplier();
        AnnotatedMethod method = getMethod("future", CompletableFuture.class);
        StreamObserver<Long> responseObserver = mock(StreamObserver.class);
        Service service = mock(Service.class);

        when(service.future(any(CompletableFuture.class))).thenReturn(responseObserver);

        MethodHandler<Long, String> handler = supplier.get("foo", method, () -> service);
        assertThat(handler, is(notNullValue()));

        CompletableFuture<String> future = new CompletableFuture<>();
        StreamObserver<String> response = mock(StreamObserver.class);
        MethodHandler.ClientStreaming client = mock(MethodHandler.ClientStreaming.class);

        when(client.clientStreaming(anyString(), any(StreamObserver.class))).thenReturn(response);

        Object result = handler.clientStreaming(new Object[]{future}, client);

        assertThat(result, is(sameInstance(response)));
        ArgumentCaptor<StreamObserver> captor = ArgumentCaptor.forClass(StreamObserver.class);
        verify(client).clientStreaming(eq("foo"), captor.capture());

        StreamObserver observer = captor.getValue();
        observer.onNext("bar");
        observer.onCompleted();

        assertThat(future.isDone(), is(true));
        assertThat(future.get(), is("bar"));
    }

    @Test
    public void shouldHandleClientCallForMethodTakingFutureAndHandleError() throws Exception {
        ClientStreamingMethodHandlerSupplier supplier = new ClientStreamingMethodHandlerSupplier();
        AnnotatedMethod method = getMethod("future", CompletableFuture.class);
        StreamObserver<Long> responseObserver = mock(StreamObserver.class);
        Service service = mock(Service.class);

        when(service.future(any(CompletableFuture.class))).thenReturn(responseObserver);

        MethodHandler<Long, String> handler = supplier.get("foo", method, () -> service);
        assertThat(handler, is(notNullValue()));

        CompletableFuture<String> future = new CompletableFuture<>();
        StreamObserver<String> response = mock(StreamObserver.class);
        MethodHandler.ClientStreaming client = mock(MethodHandler.ClientStreaming.class);

        when(client.clientStreaming(anyString(), any(StreamObserver.class))).thenReturn(response);

        Object result = handler.clientStreaming(new Object[]{future}, client);

        assertThat(result, is(sameInstance(response)));
        ArgumentCaptor<StreamObserver> captor = ArgumentCaptor.forClass(StreamObserver.class);
        verify(client).clientStreaming(eq("foo"), captor.capture());

        StreamObserver observer = captor.getValue();
        RuntimeException exception = new RuntimeException("Oops...");
        observer.onError(exception);

        assertThat(future.isCompletedExceptionally(), is(true));
    }

    @Test
    public void shouldHandleClientCallForIterable() throws Exception {
        ClientStreamingMethodHandlerSupplier supplier = new ClientStreamingMethodHandlerSupplier();
        AnnotatedMethod method = getMethod("iterable", Iterable.class);
        StreamObserver<Long> responseObserver = mock(StreamObserver.class);
        Service service = mock(Service.class);

        when(service.future(any(CompletableFuture.class))).thenReturn(responseObserver);

        MethodHandler<Long, String> handler = supplier.get("foo", method, () -> service);
        assertThat(handler, is(notNullValue()));

        List<String> list = Arrays.asList("A", "B", "C");
        StreamObserver<String> response = mock(StreamObserver.class);
        MethodHandler.ClientStreaming client = mock(MethodHandler.ClientStreaming.class);

        when(client.clientStreaming(anyString(), any(StreamObserver.class))).thenReturn(response);

        Object result = handler.clientStreaming(new Object[]{list}, client);

        assertThat(result, is(instanceOf(CompletableFuture.class)));

        ArgumentCaptor<StreamObserver> captor = ArgumentCaptor.forClass(StreamObserver.class);
        verify(client).clientStreaming(eq("foo"), captor.capture());

        StreamObserver observer = captor.getValue();
        observer.onNext("bar");
        observer.onCompleted();

        CompletableFuture<Object> future = (CompletableFuture<Object>) result;
        assertThat(future.isDone(), is(true));
        assertThat(future.get(), is("bar"));
    }

    @Test
    public void shouldHandleClientCallForStream() throws Exception {
        ClientStreamingMethodHandlerSupplier supplier = new ClientStreamingMethodHandlerSupplier();
        AnnotatedMethod method = getMethod("stream", Stream.class);
        StreamObserver<Long> responseObserver = mock(StreamObserver.class);
        Service service = mock(Service.class);

        when(service.future(any(CompletableFuture.class))).thenReturn(responseObserver);

        MethodHandler<Long, String> handler = supplier.get("foo", method, () -> service);
        assertThat(handler, is(notNullValue()));

        Stream<String> stream = Stream.of("A", "B", "C");
        StreamObserver<String> response = mock(StreamObserver.class);
        MethodHandler.ClientStreaming client = mock(MethodHandler.ClientStreaming.class);

        when(client.clientStreaming(anyString(), any(StreamObserver.class))).thenReturn(response);

        Object result = handler.clientStreaming(new Object[]{stream}, client);

        assertThat(result, is(instanceOf(CompletableFuture.class)));

        ArgumentCaptor<StreamObserver> captor = ArgumentCaptor.forClass(StreamObserver.class);
        verify(client).clientStreaming(eq("foo"), captor.capture());

        StreamObserver observer = captor.getValue();
        observer.onNext("bar");
        observer.onCompleted();

        CompletableFuture<Object> future = (CompletableFuture<Object>) result;
        assertThat(future.isDone(), is(true));
        assertThat(future.get(), is("bar"));
    }

    @Test
    public void shouldHandleClientCallForIterableReturningCompletionStage() throws Exception {
        ClientStreamingMethodHandlerSupplier supplier = new ClientStreamingMethodHandlerSupplier();
        AnnotatedMethod method = getMethod("iterableCompletionStage", Iterable.class);
        Service service = mock(Service.class);

        MethodHandler<Long, String> handler = supplier.get("foo", method, () -> service);
        assertThat(handler, is(notNullValue()));

        List<String> list = Arrays.asList("A", "B", "C");
        StreamObserver<String> response = mock(StreamObserver.class);
        MethodHandler.ClientStreaming client = mock(MethodHandler.ClientStreaming.class);

        when(client.clientStreaming(anyString(), any(StreamObserver.class))).thenReturn(response);

        Object result = handler.clientStreaming(new Object[]{list}, client);

        assertThat(result, is(instanceOf(CompletionStage.class)));

        ArgumentCaptor<StreamObserver> captor = ArgumentCaptor.forClass(StreamObserver.class);
        verify(client).clientStreaming(eq("foo"), captor.capture());

        StreamObserver observer = captor.getValue();
        observer.onNext("bar");
        observer.onCompleted();

        CompletionStage<Object> stage = (CompletionStage<Object>) result;
        CompletableFuture<Object> future = stage.toCompletableFuture();
        assertThat(future.isDone(), is(true));
        assertThat(future.get(), is("bar"));
    }

    @Test
    public void shouldHandleClientCallForStreamReturningCompletionStage() throws Exception {
        ClientStreamingMethodHandlerSupplier supplier = new ClientStreamingMethodHandlerSupplier();
        AnnotatedMethod method = getMethod("streamCompletionStage", Stream.class);
        Service service = mock(Service.class);

        MethodHandler<Long, String> handler = supplier.get("foo", method, () -> service);
        assertThat(handler, is(notNullValue()));

        Stream<String> stream = Stream.of("A", "B", "C");
        StreamObserver<String> response = mock(StreamObserver.class);
        MethodHandler.ClientStreaming client = mock(MethodHandler.ClientStreaming.class);

        when(client.clientStreaming(anyString(), any(StreamObserver.class))).thenReturn(response);

        Object result = handler.clientStreaming(new Object[]{stream}, client);

        assertThat(result, is(instanceOf(CompletionStage.class)));

        ArgumentCaptor<StreamObserver> captor = ArgumentCaptor.forClass(StreamObserver.class);
        verify(client).clientStreaming(eq("foo"), captor.capture());

        StreamObserver observer = captor.getValue();
        observer.onNext("bar");
        observer.onCompleted();

        CompletionStage<Object> stage = (CompletionStage<Object>) result;
        CompletableFuture<Object> future = stage.toCompletableFuture();
        assertThat(future.isDone(), is(true));
        assertThat(future.get(), is("bar"));
    }

    @Test
    public void shouldSupplyClientStreamingHandlerWithTypesFromAnnotation() {
        ClientStreamingMethodHandlerSupplier supplier = new ClientStreamingMethodHandlerSupplier();
        AnnotatedMethod method = getMethod("reqResp", StreamObserver.class);
        Service service = mock(Service.class);

        MethodHandler<String, String> handler = supplier.get("foo", method, () -> service);
        assertThat(handler, is(notNullValue()));
        assertThat(Long.class.equals(handler.getRequestType()), is(true));
        assertThat(String.class.equals(handler.getResponseType()), is(true));
    }

    @Test
    public void shouldNotSupplyNullMethod() {
        ClientStreamingMethodHandlerSupplier supplier = new ClientStreamingMethodHandlerSupplier();
        assertThat(supplier.supplies(null), is(false));
    }

    @Test
    public void shouldThrowExceptionSupplingNullMethod() {
        ClientStreamingMethodHandlerSupplier supplier = new ClientStreamingMethodHandlerSupplier();
        Service service = mock(Service.class);

        assertThrows(IllegalArgumentException.class, () -> supplier.get("foo", null, () -> service));
    }

    @Test
    public void shouldNotSupplyNoneClientStreamingHandler() {
        ClientStreamingMethodHandlerSupplier supplier = new ClientStreamingMethodHandlerSupplier();
        AnnotatedMethod method = getUnaryMethod();
        Service service = mock(Service.class);

        assertThrows(IllegalArgumentException.class, () -> supplier.get("foo", method, () -> service));
    }

    @Test
    public void shouldNotSupplyMethodAnnotatedMethodWithWrongArgType() {
        ClientStreamingMethodHandlerSupplier supplier = new ClientStreamingMethodHandlerSupplier();
        AnnotatedMethod method = getMethod("badArg", String.class);
        Service service = mock(Service.class);

        assertThrows(IllegalArgumentException.class, () -> supplier.get("foo", method, () -> service));
    }

    @Test
    public void shouldNotSupplyMethodAnnotatedMethodWithTooManyArgs() {
        ClientStreamingMethodHandlerSupplier supplier = new ClientStreamingMethodHandlerSupplier();
        AnnotatedMethod method = getMethod("tooManyArgs", StreamObserver.class, String.class);
        Service service = mock(Service.class);

        assertThrows(IllegalArgumentException.class, () -> supplier.get("foo", method, () -> service));
    }

    @Test
    public void shouldNotSupplyBidiStreamingMethods() {
        ClientStreamingMethodHandlerSupplier supplier = new ClientStreamingMethodHandlerSupplier();
        AnnotatedMethod method = getBidiMethod();
        assertThat(supplier.supplies(method), is(false));
    }

    @Test
    public void shouldNotSupplyServerStreamingMethods() {
        ClientStreamingMethodHandlerSupplier supplier = new ClientStreamingMethodHandlerSupplier();
        AnnotatedMethod method = getServerStreamingMethod();
        assertThat(supplier.supplies(method), is(false));
    }

    @Test
    public void shouldNotSupplyUnaryMethods() {
        ClientStreamingMethodHandlerSupplier supplier = new ClientStreamingMethodHandlerSupplier();
        AnnotatedMethod method = getUnaryMethod();
        assertThat(supplier.supplies(method), is(false));
    }

    // ----- helper methods -------------------------------------------------

    private AnnotatedMethod getBidiMethod() {
            return getMethod("bidi", StreamObserver.class);
    }

    private AnnotatedMethod getUnaryMethod() {
            return getMethod("unary", String.class, StreamObserver.class);
    }

    private AnnotatedMethod getServerStreamingMethod() {
            return getMethod("serverStreaming", String.class, StreamObserver.class);
    }

    private AnnotatedMethod getClientStreamingMethod() {
            return getMethod("clientStreaming", StreamObserver.class);
    }

    private AnnotatedMethod getMethod(String name, Class<?>... args) {
        try {
            return AnnotatedMethod.create(Service.class.getMethod(name, args));
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * The test service with bi-directional streaming methods.
     */
    public interface Service {
        @ClientStreaming
        StreamObserver<Long> clientStreaming(StreamObserver<String> observer);

        @ClientStreaming
        StreamObserver<Long> future(CompletableFuture<String> future);

        @ClientStreaming
        CompletableFuture<Long> iterable(Iterable<String> requests);

        @ClientStreaming
        CompletableFuture<Long> stream(Stream<String> requests);

        @ClientStreaming
        CompletionStage<Long> iterableCompletionStage(Iterable<String> requests);

        @ClientStreaming
        CompletionStage<Long> streamCompletionStage(Stream<String> requests);

        @ClientStreaming
        @RequestType(Long.class)
        @ResponseType(String.class)
        StreamObserver reqResp(StreamObserver observer);

        @ClientStreaming
        StreamObserver<Long> badArg(String bad);

        @ClientStreaming
        StreamObserver<Long> tooManyArgs(StreamObserver<String> observer, String bad);

        @Bidirectional
        StreamObserver<Long> bidi(StreamObserver<String> observer);

        @Unary
        void unary(String request, StreamObserver<String> observer);

        @ServerStreaming
        void serverStreaming(String request, StreamObserver<String> observer);
    }
}
