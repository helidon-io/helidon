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

import io.helidon.grpc.core.MethodHandler;

import io.grpc.MethodDescriptor;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
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
public class BidirectionalMethodHandlerSupplierTest {

    @Test
    public void shouldSupplyBidirectionalMethods() {
        BidirectionalMethodHandlerSupplier supplier = new BidirectionalMethodHandlerSupplier();
        AnnotatedMethod method = getBidiMethod();
        assertThat(supplier.supplies(method), is(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldSupplyBidiHandler() {
        BidirectionalMethodHandlerSupplier supplier = new BidirectionalMethodHandlerSupplier();
        AnnotatedMethod method = getBidiMethod();
        StreamObserver<Long> responseObserver = mock(StreamObserver.class);
        Service service = mock(Service.class);

        when(service.bidi(any(StreamObserver.class))).thenReturn(responseObserver);

        MethodHandler<Long, String> handler = supplier.get("foo", method, () -> service);
        assertThat(handler, is(notNullValue()));
        assertThat(handler.getRequestType(), equalTo(Long.class));
        assertThat(handler.getResponseType(), equalTo(String.class));
        assertThat(handler.type(), equalTo(MethodDescriptor.MethodType.BIDI_STREAMING));

        StreamObserver<String> observer = mock(StreamObserver.class);
        StreamObserver<Long> result = handler.invoke(observer);
        assertThat(result, is(sameInstance(responseObserver)));
        verify(service).bidi(any(StreamObserver.class));
    }

    @Test
    public void shouldHandleClientCall() {
        BidirectionalMethodHandlerSupplier supplier = new BidirectionalMethodHandlerSupplier();
        AnnotatedMethod method = getBidiMethod();
        StreamObserver<Long> responseObserver = mock(StreamObserver.class);
        Service service = mock(Service.class);

        when(service.bidi(any(StreamObserver.class))).thenReturn(responseObserver);

        MethodHandler<Long, String> handler = supplier.get("foo", method, () -> service);
        assertThat(handler, is(notNullValue()));

        StreamObserver<String> observer = mock(StreamObserver.class);
        StreamObserver<String> response = mock(StreamObserver.class);
        MethodHandler.BidirectionalClient client = mock(MethodHandler.BidirectionalClient.class);

        when(client.bidiStreaming(anyString(), any(StreamObserver.class))).thenReturn(response);

        Object result = handler.bidirectional(new Object[]{observer}, client);

        assertThat(result, is(sameInstance(response)));
        verify(client).bidiStreaming(eq("foo"), same(observer));
    }

    @Test
    public void shouldSupplyBidiHandlerWithTypesFromAnnotation() {
        BidirectionalMethodHandlerSupplier supplier = new BidirectionalMethodHandlerSupplier();
        AnnotatedMethod method = getMethod("bidiReqResp", StreamObserver.class);
        Service service = mock(Service.class);

        MethodHandler<String, String> handler = supplier.get("foo", method, () -> service);
        assertThat(handler, is(notNullValue()));
        assertThat(handler.getRequestType(), equalTo(Long.class));
        assertThat(handler.getResponseType(), equalTo(String.class));
        assertThat(handler.type(), equalTo(MethodDescriptor.MethodType.BIDI_STREAMING));
    }

    @Test
    public void shouldNotSupplyNullMethod() {
        BidirectionalMethodHandlerSupplier supplier = new BidirectionalMethodHandlerSupplier();
        assertThat(supplier.supplies(null), is(false));
    }

    @Test
    public void shouldThrowExceptionSupplingNullMethod() {
        BidirectionalMethodHandlerSupplier supplier = new BidirectionalMethodHandlerSupplier();
        Service service = mock(Service.class);

        assertThrows(IllegalArgumentException.class, () -> supplier.get("foo", null, () -> service));
    }

    @Test
    public void shouldNotSupplyNoneBidiHandler() {
        BidirectionalMethodHandlerSupplier supplier = new BidirectionalMethodHandlerSupplier();
        AnnotatedMethod method = getUnaryMethod();
        Service service = mock(Service.class);

        assertThrows(IllegalArgumentException.class, () -> supplier.get("foo", method, () -> service));
    }

    @Test
    public void shouldNotSupplyBidiAnnotatedMethodWithWrongArgType() {
        BidirectionalMethodHandlerSupplier supplier = new BidirectionalMethodHandlerSupplier();
        AnnotatedMethod method = getMethod("badArg", String.class);
        Service service = mock(Service.class);

        assertThrows(IllegalArgumentException.class, () -> supplier.get("foo", method, () -> service));
    }

    @Test
    public void shouldNotSupplyBidiAnnotatedMethodWithTooManyArgs() {
        BidirectionalMethodHandlerSupplier supplier = new BidirectionalMethodHandlerSupplier();
        AnnotatedMethod method = getMethod("tooManyArgs", StreamObserver.class, String.class);
        Service service = mock(Service.class);

        assertThrows(IllegalArgumentException.class, () -> supplier.get("foo", method, () -> service));
    }

    @Test
    public void shouldNotSupplyClientStreamingMethods() {
        BidirectionalMethodHandlerSupplier supplier = new BidirectionalMethodHandlerSupplier();
        AnnotatedMethod method = getClientStreamingMethod();
        assertThat(supplier.supplies(method), is(false));
    }

    @Test
    public void shouldNotSupplyServerStreamingMethods() {
        BidirectionalMethodHandlerSupplier supplier = new BidirectionalMethodHandlerSupplier();
        AnnotatedMethod method = getServerStreamingMethod();
        assertThat(supplier.supplies(method), is(false));
    }

    @Test
    public void shouldNotSupplyUnaryMethods() {
        BidirectionalMethodHandlerSupplier supplier = new BidirectionalMethodHandlerSupplier();
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
        @Bidirectional
        StreamObserver<Long> bidi(StreamObserver<String> observer);

        @Bidirectional
        @RequestType(Long.class)
        @ResponseType(String.class)
        StreamObserver bidiReqResp(StreamObserver observer);

        @Bidirectional
        StreamObserver<Long> badArg(String bad);

        @Bidirectional
        StreamObserver<Long> tooManyArgs(StreamObserver<String> observer, String bad);

        @Unary
        void unary(String request, StreamObserver<String> observer);

        @ServerStreaming
        void serverStreaming(String request, StreamObserver<String> observer);

        @ClientStreaming
        StreamObserver<String> clientStreaming(StreamObserver<String> request);
    }
}
