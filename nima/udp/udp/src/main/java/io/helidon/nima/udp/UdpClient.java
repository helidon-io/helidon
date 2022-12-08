/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.udp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;

/**
 * A UDP client that has sent a message to a UDP endpoint.
 */
public interface UdpClient extends UdpMessageSender {

    /**
     * IP address of the client.
     *
     * @return the IP address
     */
    InetAddress inetAddress();

    /**
     * IP port of the client.
     *
     * @return the IP port
     */
    int port();

    /**
     * Calls connect on the underlying I/O channel. When connected, messages can
     * only be sent to an individual client.
     *
     * @throws IOException if an I/O exception occurs
     * @see DatagramChannel#connect(SocketAddress)
     */
    void connect() throws IOException;

    /**
     * Disconnects the underlying I/O channel. No effect if disconnected or closed.
     *
     * @throws IOException if an I/O exception occurs
     * @see DatagramChannel#disconnect()
     */
    void disconnect() throws IOException;

    /**
     * Checks if underlying I/O channel is connected.
     *
     * @return outcome of test
     * @see DatagramChannel#isConnected()
     */
    boolean isConnected();
}
