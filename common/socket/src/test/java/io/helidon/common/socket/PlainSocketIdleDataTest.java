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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PlainSocketIdleDataTest {

    @Test
    void zeroBytePreservesConnectionAndRestoresTimeout() throws Exception {
        assertIdleRead(0, 30_000);
    }

    @Test
    void zeroBytePreservesConnectionAndRestoresUnlimitedTimeout() throws Exception {
        assertIdleRead(0, 0);
    }

    @Test
    void nonzeroBytePreservesConnectionAndRestoresTimeout() throws Exception {
        assertIdleRead(42, 30_000);
    }

    @Test
    void unsignedBytePreservesConnectionAndRestoresTimeout() throws Exception {
        assertIdleRead(255, 30_000);
    }

    @Test
    void endOfInputClosesConnectionAndRestoresTimeout() throws Exception {
        assertIdleRead(-1, 30_000);
    }

    private static void assertIdleRead(int next, int originalTimeout) throws Exception {
        var monitorReadStarted = new CountDownLatch(1);
        var monitorReadCompleted = new CountDownLatch(1);
        var loopback = InetAddress.getLoopbackAddress();
        try (var listener = new ServerSocket(0, 1, loopback);
             var delegate = new Socket() {
                 @Override
                 public InputStream getInputStream() throws IOException {
                     return new FilterInputStream(super.getInputStream()) {
                         @Override
                         public int read() throws IOException {
                             monitorReadStarted.countDown();
                             int result = super.read();
                             monitorReadCompleted.countDown();
                             return result;
                         }
                     };
                 }
             }) {
            delegate.connect(new InetSocketAddress(loopback, listener.getLocalPort()), 5_000);
            try (var peer = listener.accept()) {
                delegate.setSoTimeout(originalTimeout);
                var socket = PlainSocket.client(delegate, "idle-data-test");
                // Queue input before monitoring so the monitor does not depend on producer-thread scheduling.
                if (next == -1) {
                    peer.shutdownOutput();
                } else {
                    peer.getOutputStream().write(next);
                }
                socket.idle();
                // Only the monitor uses single-byte reads. Do not let the application reader cancel it before entry.
                assertThat("Idle monitor did not enter its socket read",
                           monitorReadStarted.await(5, TimeUnit.SECONDS), is(true));
                assertThat("Idle monitor did not receive the peer's input",
                           monitorReadCompleted.await(5, TimeUnit.SECONDS), is(true));

                // The public read joins the monitor, making its byte, connection state, and restored timeout observable.
                byte[] received = socket.get();
                assertAll("Idle monitor result for " + next,
                          () -> assertThat("Received data", received,
                                           next == -1 ? nullValue() : is(new byte[] {(byte) next})),
                          () -> assertThat("Connected state", socket.isConnected(), is(next != -1)),
                          () -> assertThat("Restored socket timeout", delegate.getSoTimeout(), is(originalTimeout)));

                if (next != -1) {
                    peer.getOutputStream().write(73);
                    assertThat("Buffered byte was lost or delivered twice", socket.get(), is(new byte[] {73}));
                    assertThat("Live peer was disconnected", socket.isConnected(), is(true));
                }
            }
        }
    }
}
