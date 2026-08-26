/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.common.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlainSocketIdleTest {

    @Test
    void idleMonitorIoFailurePropagatesWithoutAdditionalWrapping() throws Exception {
        var monitorReadStarted = new CountDownLatch(1);
        var expected = new IOException("Expected monitor failure");
        InputStream inputStream = mock(InputStream.class);
        doAnswer(_ -> {
            monitorReadStarted.countDown();
            throw expected;
        }).when(inputStream).read();
        PlainSocket socket = socket(inputStream);

        try {
            socket.idle();
            assertThat("Socket monitor did not start", monitorReadStarted.await(10, TimeUnit.SECONDS), is(true));

            UncheckedIOException actual = assertThrows(UncheckedIOException.class, socket::get);

            assertThat(actual.getCause(), sameInstance(expected));
        } finally {
            socket.close();
        }
    }

    @Test
    void interruptedIdleWaitRestoresFlagAndPropagatesAsIo() throws Exception {
        var monitorReadStarted = new CountDownLatch(1);
        var releaseMonitor = new CountDownLatch(1);
        InputStream inputStream = mock(InputStream.class);
        doAnswer(_ -> {
            monitorReadStarted.countDown();
            try {
                releaseMonitor.await();
                return -1;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }).when(inputStream).read();
        doAnswer(_ -> {
            releaseMonitor.countDown();
            return null;
        }).when(inputStream).close();
        PlainSocket socket = socket(inputStream);
        UncheckedIOException actual;
        boolean interrupted;

        try {
            socket.idle();
            assertThat("Socket monitor did not start", monitorReadStarted.await(10, TimeUnit.SECONDS), is(true));

            Thread.currentThread().interrupt();
            actual = assertThrows(UncheckedIOException.class, socket::get);
            interrupted = Thread.currentThread().isInterrupted();
        } finally {
            Thread.interrupted();
            socket.close();
        }

        assertThat("Interrupted status was not restored", interrupted, is(true));
        assertThat(actual.getCause(), instanceOf(InterruptedIOException.class));
        assertThat(actual.getCause().getCause(), instanceOf(InterruptedException.class));
    }

    private static PlainSocket socket(InputStream inputStream) throws IOException {
        Socket delegate = mock(Socket.class);
        when(delegate.getInputStream()).thenReturn(inputStream);
        when(delegate.getOutputStream()).thenReturn(OutputStream.nullOutputStream());
        when(delegate.getSoTimeout()).thenReturn(30_000);
        return PlainSocket.client(delegate, "test");
    }
}
