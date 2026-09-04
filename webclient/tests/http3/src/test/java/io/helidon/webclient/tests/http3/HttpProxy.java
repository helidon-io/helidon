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

package io.helidon.webclient.tests.http3;

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
 * Minimal HTTP proxy used by webclient tests.
 */
class HttpProxy {
    private static final System.Logger LOGGER = System.getLogger(HttpProxy.class.getName());
    private static final int TIMEOUT = 60000;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final int port;
    private final String user;
    private final String password;
    private final AtomicInteger counter = new AtomicInteger(0);

    private volatile boolean stop;
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
                ready.countDown();
                while (!stop) {
                    Socket origin = server.accept();
                    LOGGER.log(Level.DEBUG, "Open: " + origin);
                    counter.incrementAndGet();
                    origin.setSoTimeout(TIMEOUT);

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

        try {
            if (!ready.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("HTTP proxy server is not ready.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting the HTTP proxy server.", e);
        }
    }

    int counter() {
        return counter.get();
    }

    boolean stop() {
        stop = true;
        try {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(connectedPort), 10000);
            } catch (IOException ignored) {
            }
            return executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    int connectedPort() {
        return connectedPort;
    }

    private class MiddleCommunicator {
        private static final System.Logger LOGGER = System.getLogger(MiddleCommunicator.class.getName());
        private static final int BUFFER_SIZE = 1024 * 1024;

        private final ExecutorService executor;
        private final Socket readerSocket;
        private final Socket writerSocket;
        private final boolean originToRemote;
        private final Reader reader;
        private final MiddleCommunicator callback;

        private MiddleCommunicator(ExecutorService executor,
                                   Socket readerSocket,
                                   Socket writerSocket,
                                   MiddleCommunicator callback) {
            this.executor = executor;
            this.readerSocket = readerSocket;
            this.writerSocket = writerSocket;
            this.originToRemote = callback != null;
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
                byte[] buffer = new byte[BUFFER_SIZE];
                Exception exception = null;
                try {
                    boolean handleFirstRequest = true;
                    int read;
                    while ((read = readerSocket.getInputStream().read(buffer)) != -1) {
                        final int readBytes = read;
                        LOGGER.log(Level.DEBUG,
                                   () -> readerSocket + " read " + readBytes + " bytes\n" + new String(buffer, 0, readBytes));
                        if (originToRemote && handleFirstRequest) {
                            handleFirstRequest = false;
                            OriginInfo originInfo = getOriginInfo(buffer, readBytes);
                            LOGGER.log(Level.DEBUG, "Incoming request: " + originInfo);
                            if (authenticate(originInfo)) {
                                String response = "HTTP/1.1 200 Connection established\r\n\r\n";
                                writerSocket.connect(new InetSocketAddress(originInfo.host, originInfo.port));
                                LOGGER.log(Level.DEBUG, "Open: " + writerSocket);
                                readerSocket.getOutputStream().write(response.getBytes());
                                callback.start();
                                readerSocket.getOutputStream().flush();
                            } else {
                                LOGGER.log(Level.WARNING, "Invalid " + originInfo.user + ":" + originInfo.password);
                                String response = "HTTP/1.1 401 Unauthorized\r\n\r\n";
                                readerSocket.getOutputStream().write(response.getBytes());
                                readerSocket.getOutputStream().flush();
                                readerSocket.close();
                            }
                        } else {
                            writerSocket.getOutputStream().write(buffer, 0, readBytes);
                            writerSocket.getOutputStream().flush();
                        }
                    }
                } catch (Exception e) {
                    exception = e;
                } finally {
                    stop(readerSocket, exception);
                    stop(writerSocket, exception);
                }
            }
        }

        private boolean authenticate(OriginInfo originInfo) {
            if (HttpProxy.this.user == null) {
                return true;
            }
            return HttpProxy.this.user.equals(originInfo.user)
                    && HttpProxy.this.password.equals(originInfo.password);
        }

        private OriginInfo getOriginInfo(byte[] buffer, int read) throws MalformedURLException {
            byte[] content = Arrays.copyOf(buffer, read);
            String request = new String(content);
            String[] lines = request.split("\r\n");
            OriginInfo originInfo = new OriginInfo();
            for (String line : lines) {
                if (line.startsWith(OriginInfo.CONNECT)) {
                    originInfo.parseFirstLine(line);
                } else if (line.startsWith(OriginInfo.AUTHORIZATION)) {
                    originInfo.parseAuthorization(line);
                }
            }
            return originInfo;
        }

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
    }

    private static final class OriginInfo {
        private static final String CONNECT = "CONNECT ";
        private static final String AUTHORIZATION = "Proxy-Authorization:";

        private String host;
        private int port;
        private String user;
        private String password;

        private void parseFirstLine(String line) throws MalformedURLException {
            String[] split = line.split(" ");
            if (split.length < 2) {
                throw new MalformedURLException("Cannot parse CONNECT line: " + line);
            }
            String authority = split[1];
            int colon = authority.lastIndexOf(':');
            if (colon < 1 || colon + 1 >= authority.length()) {
                throw new MalformedURLException("Cannot parse CONNECT authority: " + authority);
            }
            host = authority.substring(0, colon);
            port = Integer.parseInt(authority.substring(colon + 1));
        }

        private void parseAuthorization(String line) {
            String[] split = line.split(" ", 3);
            if (split.length < 3) {
                return;
            }
            String credentials = new String(Base64.getDecoder().decode(split[2]));
            int colon = credentials.indexOf(':');
            if (colon < 0) {
                return;
            }
            user = credentials.substring(0, colon);
            password = credentials.substring(colon + 1);
        }

        @Override
        public String toString() {
            return "OriginInfo{"
                    + "host='" + host + '\''
                    + ", port=" + port
                    + ", user='" + user + '\''
                    + ", password='" + password + '\''
                    + '}';
        }
    }
}
