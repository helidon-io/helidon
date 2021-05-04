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
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.stub.StreamObserver;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SuppressWarnings("unchecked")
public class MethodHandlerTest {

    @Test
    public void shouldNotSupportStreamingMethod() {
        MethodHandler handler = new Stub();
        StreamObserver observer = mock(StreamObserver.class);
        handler.invoke(observer);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(captor.capture());
        Mockito.verifyNoMoreInteractions(observer);

        Throwable throwable = captor.getValue();
        assertThat(throwable.getClass().equals(StatusException.class), is(true));
        Status status = ((StatusException) throwable).getStatus();
        MatcherAssert.assertThat(status, CoreMatchers.is(Status.UNIMPLEMENTED));
    }


    @Test
    public void shouldNotSupportNonStreamingMethod() {
        MethodHandler handler = new Stub();
        StreamObserver observer = mock(StreamObserver.class);
        handler.invoke("foo", observer);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(captor.capture());
        Mockito.verifyNoMoreInteractions(observer);

        Throwable throwable = captor.getValue();
        assertThat(throwable.getClass().equals(StatusException.class), is(true));
        Status status = ((StatusException) throwable).getStatus();
        MatcherAssert.assertThat(status, CoreMatchers.is(Status.UNIMPLEMENTED));
    }

    private class Stub
            implements MethodHandler {

        @Override
        public MethodDescriptor.MethodType type() {
            return null;
        }

        @Override
        public Class<?> getRequestType() {
            return null;
        }

        @Override
        public Class<?> getResponseType() {
            return null;
        }

        @Override
        public String javaMethodName() {
            return null;
        }
    }
}
