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

package io.helidon.nima.tests.integration.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

class HttpProxy {

    private static final Logger LOGGER = Logger.getLogger(HttpProxy.class.getName());
    // 1 minute
    private static final int TIMEOUT = 60000;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean stop = false;
    private final int port;
    private final String user;
    private final String password;
    
    HttpProxy(int port, String user, String password) {
        this.port = port;
        this.user = user;
        this.password = password;
    }

    HttpProxy(int port) {
        this(port, null, null);
    }


    boolean start() {
        executor.submit(() -> {
            LOGGER.info("Listening connections in port: " + port);
            try (ServerSocket server = new ServerSocket(port)) {
                while (!stop) {
                    Socket origin = server.accept();
                    LOGGER.info(() -> "Open: " + origin);
                    origin.setSoTimeout(TIMEOUT);
                    Socket remote = new Socket();
                    remote.setSoTimeout(TIMEOUT);
                    MiddleCommunicator remoteToOrigin = new MiddleCommunicator(executor, remote, origin, null);
                    MiddleCommunicator originToRemote = new MiddleCommunicator(executor, origin, remote, remoteToOrigin);
                    originToRemote.start();
                }
                LOGGER.info("Shutting down HTTP Proxy server");
                executor.shutdownNow();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error in HTTP Proxy", e);
                stop();
            }
        });
        // Makes sure that HttpProxy is ready
        boolean responding = false;
        while (!responding) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(port), 10000);
                responding = true;
            } catch (IOException e) {}
        }
        return true;
    }

    boolean stop() {
        stop = true;
        try {
            // Make the server to check stop boolean
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(port), 10000);
            } catch (IOException e) {}
            return executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    private class MiddleCommunicator {

        private static final Logger LOGGER = Logger.getLogger(MiddleCommunicator.class.getName());
        private static final int BUFFER_SIZE = 1024 * 1024;
        private static final String HOST = "HOST: ";
        private static final byte NEW_LINE = (byte) '\n';
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
                        LOGGER.info("Close: " + socket);
                    } else {
                        LOGGER.info("Close: " + socket + ". Reason: " + exception);
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Cannot close " + socket + ": " + e.getMessage());
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
                    int read;
                    OriginInfo originInfo = null;
                    while ((read = readerSocket.getInputStream().read(buffer)) != -1) {
                        final int readB = read;
                        LOGGER.info(() -> readerSocket + " read " + readB + " bytes");
                        LOGGER.info(() -> new String(buffer, 0, readB));
                        if (originToRemote) {
                            if (originInfo == null) {
                                originInfo = getOriginInfo(buffer, read);
                                LOGGER.info("Incoming request: " + originInfo);
                                if (originInfo.respondOrigin()) {
                                    if (authenticate(originInfo)) {
                                        // Respond origin
                                        String response = "HTTP/1.0 200 Connection established\r\n\r\n";
                                        writerSocket.connect(new InetSocketAddress(originInfo.host, originInfo.port));
                                        LOGGER.info(() -> "Open: " + writerSocket);
                                        readerSocket.getOutputStream()
                                                .write(response.getBytes());
                                        // Start listening from origin
                                        callback.start();
                                        readerSocket.getOutputStream().flush();
                                    } else {
                                        LOGGER.warning("Invalid " + originInfo.user + ":" + originInfo.password);
                                        originInfo = null;
                                        String response = "HTTP/1.0 401 Unauthorized\r\n\r\n";
                                        readerSocket.getOutputStream().write(response.getBytes());
                                        readerSocket.getOutputStream().flush();
                                        readerSocket.close();
                                    }
                                }
                            } else {
                                writerSocket.getOutputStream().write(buffer, 0, read);
                                writerSocket.getOutputStream().flush();
                            }
                        } else {
                            writerSocket.getOutputStream().write(buffer, 0, read);
                            writerSocket.getOutputStream().flush();
                        }
                    }
                } catch (IOException e) {
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
                } else if (line.toUpperCase().startsWith(HOST)) {
                    request.parseHost(line);
                } else if (line.toUpperCase().startsWith(OriginInfo.AUTHORIZATION)) {
                    request.parseAuthorization(line);
                }
            }
            return request;
        }
        
        // Make it easy to understand stacktraces
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
            private static final String AUTHORIZATION = "AUTHORIZATION:";
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
            }

            // Host: host:port
            private void parseHost(String line) {
                line = line.substring(HOST.length()).trim();
                String[] hostPort = line.split(":");
                this.host = hostPort[0];
                if (hostPort.length > 1) {
                    this.port = Integer.parseInt(hostPort[1]);
                }
            }

            // Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==
            private void parseAuthorization(String line) {
                String[] parts = line.split(" ");
                String base64 = parts[2];
                String[] userPass = new String(Base64.getDecoder().decode(base64)).split(":");
                user = userPass[0];
                password = userPass[1];
            }

            private boolean respondOrigin() {
                return CONNECT.equals(method);
            }

            @Override
            public String toString() {
                return "OriginInfo [host=" + host + ", port=" + port + ", protocol=" + protocol + ", method=" + method
                        + ", user=" + user + ", password=" + password + "]";
            }
        }
    }
}
