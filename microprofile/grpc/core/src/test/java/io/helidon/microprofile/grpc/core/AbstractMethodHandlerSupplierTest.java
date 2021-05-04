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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Supplier;

import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SuppressWarnings("unchecked")
public class AbstractMethodHandlerSupplierTest {

    @Test
    public void shouldHandleBidirectionalInvocationError() throws Exception {
        AnnotatedMethod method = AnnotatedMethod.create(getClientStreamingMethod());
        AbstractMethodHandlerSupplierTest instance = mock(AbstractMethodHandlerSupplierTest.class);
        StreamObserver observer = mock(StreamObserver.class);
        BadHandlerStub<String, String> stub = new BadHandlerStub<>(method,
                                                                   () -> instance,
                                                                   MethodDescriptor.MethodType.CLIENT_STREAMING);

        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> stub.invoke(observer));

        Mockito.verifyNoMoreInteractions(observer);
        assertThat(exception, is(notNullValue()));
        MatcherAssert.assertThat(exception.getStatus().getCode(), CoreMatchers.is(Status.INTERNAL.getCode()));
    }

    @Test
    public void shouldHandleClientStreamingInvocationError() throws Exception {
        AnnotatedMethod method = AnnotatedMethod.create(getClientStreamingMethod());
        AbstractMethodHandlerSupplierTest instance = mock(AbstractMethodHandlerSupplierTest.class);
        StreamObserver observer = mock(StreamObserver.class);
        BadHandlerStub<String, String> stub = new BadHandlerStub<>(method,
                                                                   () -> instance,
                                                                   MethodDescriptor.MethodType.CLIENT_STREAMING);

        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> stub.invoke(observer));

        Mockito.verifyNoMoreInteractions(observer);
        assertThat(exception, is(notNullValue()));
        MatcherAssert.assertThat(exception.getStatus().getCode(), CoreMatchers.is(Status.INTERNAL.getCode()));
    }

    @Test
    public void shouldHandleServerStreamingInvocationError() throws Exception {
        AnnotatedMethod method = AnnotatedMethod.create(getServerStreamingMethod());
        AbstractMethodHandlerSupplierTest instance = mock(AbstractMethodHandlerSupplierTest.class);
        StreamObserver observer = mock(StreamObserver.class);
        BadHandlerStub<String, String> stub = new BadHandlerStub<>(method,
                                                                   () -> instance,
                                                                   MethodDescriptor.MethodType.SERVER_STREAMING);

        stub.invoke("foo", observer);

        ArgumentCaptor<StatusException> captor = ArgumentCaptor.forClass(StatusException.class);
        verify(observer).onError(captor.capture());
        Mockito.verifyNoMoreInteractions(observer);
        StatusException exception = captor.getValue();
        assertThat(exception, is(notNullValue()));
        MatcherAssert.assertThat(exception.getStatus().getCode(), CoreMatchers.is(Status.INTERNAL.getCode()));
    }

    @Test
    public void shouldHandleUnaryInvocationError() throws Exception {
        AnnotatedMethod method = AnnotatedMethod.create(getUnaryMethod());
        AbstractMethodHandlerSupplierTest instance = mock(AbstractMethodHandlerSupplierTest.class);
        StreamObserver observer = mock(StreamObserver.class);
        BadHandlerStub<String, String> stub = new BadHandlerStub<>(method, () -> instance, MethodDescriptor.MethodType.UNARY);

        stub.invoke("foo", observer);

        ArgumentCaptor<StatusException> captor = ArgumentCaptor.forClass(StatusException.class);
        verify(observer).onError(captor.capture());
        Mockito.verifyNoMoreInteractions(observer);
        StatusException exception = captor.getValue();
        assertThat(exception, is(notNullValue()));
        MatcherAssert.assertThat(exception.getStatus().getCode(), CoreMatchers.is(Status.INTERNAL.getCode()));
    }

    @Test
    public void shouldHandleFutureCompletion() throws Exception {
        AnnotatedMethod method = AnnotatedMethod.create(getUnaryMethod());
        AbstractMethodHandlerSupplierTest instance = mock(AbstractMethodHandlerSupplierTest.class);
        StreamObserver observer = mock(StreamObserver.class);
        HandlerStub<String, String> stub = new HandlerStub<>(method, () -> instance, MethodDescriptor.MethodType.UNARY);

        stub.handleFuture("foo", null, observer);

        InOrder inOrder = Mockito.inOrder(observer);
        inOrder.verify(observer).onNext("foo");
        inOrder.verify(observer).onCompleted();
    }

    @Test
    public void shouldHandleFutureExceptionalCompletion() throws Exception {
        AnnotatedMethod method = AnnotatedMethod.create(getUnaryMethod());
        AbstractMethodHandlerSupplierTest instance = mock(AbstractMethodHandlerSupplierTest.class);
        StreamObserver observer = mock(StreamObserver.class);
        HandlerStub<String, String> stub = new HandlerStub<>(method, () -> instance, MethodDescriptor.MethodType.UNARY);

        stub.handleFuture(null, new RuntimeException(), observer);

        ArgumentCaptor<StatusException> captor = ArgumentCaptor.forClass(StatusException.class);
        verify(observer).onError(captor.capture());

        StatusException exception = captor.getValue();
        assertThat(exception, is(notNullValue()));
        MatcherAssert.assertThat(exception.getStatus().getCode(), CoreMatchers.is(Status.INTERNAL.getCode()));
    }


    private Method getBidiMethod() throws Exception {
        return getClass().getMethod("bidi", StreamObserver.class);
    }

    @Bidirectional
    public StreamObserver<String> bidi(StreamObserver<String> observer) {
        return null;
    }

    private Method getClientStreamingMethod() throws Exception {
        return getClass().getMethod("clientStreaming", StreamObserver.class);
    }

    @ClientStreaming
    public StreamObserver<String> clientStreaming(StreamObserver<String> observer) {
        return null;
    }

    private Method getServerStreamingMethod() throws Exception {
        return getClass().getMethod("serverStreaming", String.class, StreamObserver.class);
    }

    @ServerStreaming
    public void serverStreaming(String request, StreamObserver<String> observer) {
    }

    private Method getUnaryMethod() throws Exception {
        return getClass().getMethod("unary", String.class, StreamObserver.class);
    }

    @Unary
    public void unary(String request, StreamObserver<String> observer) {
    }


    /**
     * A stub method handler.
     */
    public static class HandlerStub<ReqT, RespT>
            extends AbstractMethodHandlerSupplier.AbstractHandler<ReqT, RespT> {

        public HandlerStub(AnnotatedMethod method, Supplier<?> instance, MethodDescriptor.MethodType methodType) {
            super("foo", method, instance, methodType);
        }

        @Override
        protected void invoke(Method method, Object instance, ReqT request, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
        }

        @Override
        protected StreamObserver<ReqT> invoke(Method method, Object instance, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
            return null;
        }
    }


    /**
     * A stub method handler.
     */
    public static class BadHandlerStub<ReqT, RespT>
            extends AbstractMethodHandlerSupplier.AbstractHandler<ReqT, RespT> {

        public BadHandlerStub(AnnotatedMethod method, Supplier<?> instance, MethodDescriptor.MethodType methodType) {
            super("foo", method, instance, methodType);
        }

        @Override
        protected void invoke(Method method, Object instance, ReqT request, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
            throw new RuntimeException();
        }

        @Override
        protected StreamObserver<ReqT> invoke(Method method, Object instance, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
            throw new RuntimeException();
        }
    }
}
