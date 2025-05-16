/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webclient.tests;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HttpProxy implementation. It has to handle two sockets per each connection to it:
 * 1. The socket that starts the connection to the HTTP Proxy, known as origin.
 * 2. The socket that will connect from the HTTP Proxy to the desired remote host, known as remote.
 *
 * An HTTP Proxy has to primarily pass data from both sides, origin to remote and remote to origin.
 * Before doing this, it has to handle a first request from the origin to know where is the remote host.
 *
 * An instance of HttpProxy can not be re-used after stopping it.
 */
class HttpProxy {

    private static final System.Logger LOGGER = System.getLogger(HttpProxy.class.getName());
    // 1 minute
    private static final int TIMEOUT = 60000;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean stop = false;
    private final int port;
    private final String user;
    private final String password;
    // Starts with -1 because there is one first test connection to verify the HttpProxy is available
    private final AtomicInteger counter = new AtomicInteger(-1);
    private int connectedPort;

    HttpProxy(int port, String user, String password) {
        this.port = port;
        this.user = user;
        this.password = password;
    }

    HttpProxy(int port) {
        this(port, null, null);
    }

    void start() {
        CountDownLatch ready = new CountDownLatch(1);
        executor.submit(() -> {
            try (ServerSocket server = new ServerSocket(port)) {
                this.connectedPort = server.getLocalPort();
                LOGGER.log(Level.INFO, "Listening connections in port: " + connectedPort);
                while (!stop) {
                    // Origin is the socket that starts the connection
                    Socket origin = server.accept();
                    LOGGER.log(Level.DEBUG, "Open: " + origin);
                    counter.incrementAndGet();
                    ready.countDown();
                    origin.setSoTimeout(TIMEOUT);
                    // Remote is the socket that will connect to the desired host, for example www.google.com
                    // It is not connected yet because we need to wait for the HTTP CONNECT to know where.
                    Socket remote = new Socket();
                    remote.setSoTimeout(TIMEOUT);
                    MiddleCommunicator remoteToOrigin = new MiddleCommunicator(executor, remote, origin, null);
                    MiddleCommunicator originToRemote = new MiddleCommunicator(executor, origin, remote, remoteToOrigin);
                    originToRemote.start();
                }
                LOGGER.log(Level.INFO, "Shutting down HTTP Proxy server");
                executor.shutdownNow();
            } catch (IOException e) {
                LOGGER.log(Level.ERROR, "Error in HTTP Proxy", e);
                stop();
            }
        });
        // Makes sure that HttpProxy is ready
        boolean responding = false;
        while (!responding) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(connectedPort), 10000);
                responding = true;
                // Wait for counter is set to 0
                ready.await(5, TimeUnit.SECONDS);
            } catch (IOException | InterruptedException e) {}
        }
    }

    int counter() {
        return counter.get();
    }

    boolean stop() {
        stop = true;
        try {
            // Make the server to check stop boolean
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(connectedPort), 10000);
            } catch (IOException e) {}
            return executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    int connectedPort() {
        return connectedPort;
    }

    /**
     * This is the core of the HTTP Proxy. One HTTP Proxy must run 2 instances of this, as it will be explained here.
     *
     * Its goal is to forward what arrives. There are two workflows:
     * 1. Listen from origin to forward it to remote.
     * 2. Listen from remote to forward it to origin.
     *
     * One instance of MiddleCommunicator can only handle 1 workflow, then you need 2 instances of it because
     * it is needed to send data to remote, and also read data from it.
     *
     * The workflow number 1 (originToRemote) also requires one additional step. It has to handle
     * an HTTP CONNECT request before start forwarding. This is needed to know where is the remote and to authenticate.
     */
    private class MiddleCommunicator {

        private static final System.Logger LOGGER = System.getLogger(MiddleCommunicator.class.getName());
        private static final int BUFFER_SIZE = 1024 * 1024;
        private final ExecutorService executor;
        private final Socket readerSocket;
        private final Socket writerSocket;
        private final boolean originToRemote;
        private final Reader reader;
        private final MiddleCommunicator callback;

        private MiddleCommunicator(ExecutorService executor, Socket readerSocket, Socket writerSocket, MiddleCommunicator callback) {
            this.executor = executor;
            this.readerSocket = readerSocket;
            this.writerSocket = writerSocket;
            this.originToRemote = callback != null;
            // Both are the same thing with different name. The only purpose of this is to understand better stack traces.
            this.reader = originToRemote ? new OriginToRemoteReader() : new RemoteToOriginReader();
            this.callback = callback;
        }

        private void start() {
            executor.submit(reader);
        }

        private void stop(Socket socket, Exception exception) {
            if (!socket.isClosed()) {
                try {
                    socket.close();
                    if (exception == null) {
                        LOGGER.log(Level.DEBUG, "Close: " + socket);
                    } else {
                        LOGGER.log(Level.DEBUG, "Close: " + socket + ". Reason: " + exception);
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.ERROR, "Cannot close " + socket + ": " + e.getMessage());
                }
            }
        }

        private abstract class Reader implements Runnable {

            @Override
            public void run() {
                // 1 MB
                byte[] buffer = new byte[BUFFER_SIZE];
                Exception exception = null;
                try {
                    boolean handleFirstRequest = true;
                    int read;
                    while ((read = readerSocket.getInputStream().read(buffer)) != -1) {
                        final int readb = read;
                        LOGGER.log(Level.DEBUG, () -> readerSocket + " read " + readb + " bytes\n" + new String(buffer, 0, readb));
                        // Handling workflow number 1
                        if (originToRemote && handleFirstRequest) {
                            handleFirstRequest = false;
                            // It is expected the first request is HTTP CONNECT
                            OriginInfo originInfo = getOriginInfo(buffer, readb);
                            LOGGER.log(Level.DEBUG, "Incoming request: " + originInfo);
                            if (authenticate(originInfo)) {
                                // Respond origin
                                String response = "HTTP/1.1 200 Connection established\r\n\r\n";
                                writerSocket.connect(new InetSocketAddress(originInfo.host, originInfo.port));
                                LOGGER.log(Level.DEBUG, "Open: " + writerSocket);
                                readerSocket.getOutputStream()
                                        .write(response.getBytes());
                                // Now we know where to connect, so we can connect the socket to the remote.
                                callback.start();
                                readerSocket.getOutputStream().flush();
                            } else {
                                LOGGER.log(Level.WARNING, "Invalid " + originInfo.user + ":" + originInfo.password);
                                originInfo = null;
                                String response = "HTTP/1.1 401 Unauthorized\r\n\r\n";
                                readerSocket.getOutputStream().write(response.getBytes());
                                readerSocket.getOutputStream().flush();
                                readerSocket.close();
                            }
                        } else {
                            writerSocket.getOutputStream().write(buffer, 0, readb);
                            writerSocket.getOutputStream().flush();
                        }
                    }
                } catch (Exception e) {
                    exception = e;
//                    LOGGER.log(Level.SEVERE, e.getMessage(), e);
                } finally {
                    stop(readerSocket, exception);
                    stop(writerSocket, exception);
                }
            }
        }

        private boolean authenticate(OriginInfo originInfo) {
            if (HttpProxy.this.user == null) {
                return true;
            } else {
                return HttpProxy.this.user.equals(originInfo.user)
                        && HttpProxy.this.password.equals(originInfo.password);
            }
        }

        private OriginInfo getOriginInfo(byte[] buffer, int read) throws MalformedURLException {
            byte[] content = Arrays.copyOf(buffer, read);
            String req = new String(content);
            String[] lines = req.split("\r\n");
            OriginInfo request = new OriginInfo();
            for (String line : lines) {
                if (line.startsWith(OriginInfo.CONNECT)) {
                    request.parseFirstLine(line);
                } else if (line.startsWith(OriginInfo.AUTHORIZATION)) {
                    request.parseAuthorization(line);
                }
            }
            return request;
        }

        // Make it easy to understand stack traces
        private class OriginToRemoteReader extends Reader {
            @Override
            public void run() {
                super.run();
            }
        }

        private class RemoteToOriginReader extends Reader {
            @Override
            public void run() {
                super.run();
            }
        }

        private static class OriginInfo {
            private static final String CONNECT = "CONNECT";
            private static final String AUTHORIZATION = "Proxy-Authorization:";
            private String host;
            private int port = 80;
            private String protocol;
            private String method;
            private String user;
            private String password;

            // CONNECT host:port HTTP/1.1
            private void parseFirstLine(String line) {
                String[] parts = line.split(" ");
                this.method = parts[0].trim();
                this.protocol = parts[2].trim();
                String[] hostPort = parts[1].split(":");
                this.host = hostPort[0];
                if (hostPort.length > 1) {
                    this.port = Integer.parseInt(hostPort[1]);
                }
            }

            // Proxy-Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==
            private void parseAuthorization(String line) {
                String[] parts = line.split(" ");
                String base64 = parts[2];
                String[] userPass = new String(Base64.getDecoder().decode(base64)).split(":");
                user = userPass[0];
                password = userPass[1];
            }

            @Override
            public String toString() {
                return "OriginInfo [host=" + host + ", port=" + port + ", protocol=" + protocol + ", method=" + method
                        + ", user=" + user + ", password=" + password + "]";
            }
        }
    }
}
