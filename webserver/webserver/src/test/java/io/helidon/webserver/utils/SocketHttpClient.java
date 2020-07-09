/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.webserver.WebServer;

import org.hamcrest.core.Is;
import org.hamcrest.core.StringEndsWith;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * The SocketHttpClient provides means to simply pass any bytes into the WebServer
 * and to see how it deals with such case.
 */
public class SocketHttpClient implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(SocketHttpClient.class.getName());

    private final Socket socket;

    /**
     * Creates the instance linked with the provided webserver.
     *
     * @param webServer the webserver to link this client with
     * @throws IOException in case of an error
     */
    public SocketHttpClient(WebServer webServer) throws IOException {
        socket = new Socket(InetAddress.getLocalHost(), webServer.port());
    }

    /**
     * A helper method that sends the given payload with the provided method to the webserver.
     *
     * @param method    the http method
     * @param payload   the payload to send (must be without the newlines;
     *                  otherwise it's not a valid payload)
     * @param webServer the webserver where to send the payload
     * @return the exact string returned by webserver (including {@code HTTP/1.1 200 OK} line for instance)
     * @throws Exception in case of an error
     */
    public static String sendAndReceive(Http.Method method, String payload, WebServer webServer) throws Exception {
        return sendAndReceive("/", method, payload, webServer);
    }

    /**
     * A helper method that sends the given payload at the given path with the provided method and headers to the webserver.
     *
     * @param path      the path to access
     * @param method    the http method
     * @param payload   the payload to send (must be without the newlines;
     *                  otherwise it's not a valid payload)
     * @param webServer the webserver where to send the payload
     * @return the exact string returned by webserver (including {@code HTTP/1.1 200 OK} line for instance)
     * @throws Exception in case of an error
     */
    public static String sendAndReceive(String path, Http.Method method, String payload, WebServer webServer) throws Exception {
        return sendAndReceive(path, method, payload, Collections.emptyList(), webServer);
    }

    /**
     * A helper method that sends the given payload at the given path with the provided method to the webserver.
     *
     * @param path      the path to access
     * @param method    the http method
     * @param payload   the payload to send (must be without the newlines;
     *                  otherwise it's not a valid payload)
     * @param headers   HTTP request headers
     * @param webServer the webserver where to send the payload
     * @return the exact string returned by webserver (including {@code HTTP/1.1 200 OK} line for instance)
     * @throws Exception in case of an error
     */
    public static String sendAndReceive(String path,
                                        Http.Method method,
                                        String payload,
                                        Iterable<String> headers,
                                        WebServer webServer) throws Exception {
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            s.request(method, path, payload, headers);

            return s.receive();
        }
    }

    /**
     * Assert that the socket associated with the provided client is working and open.
     *
     * @param s the socket client
     * @throws IOException in case of an IO error
     */
    public static void assertConnectionIsOpen(SocketHttpClient s) throws IOException {
        // get
        s.request(Http.Method.GET);
        // assert
        assertThat(s.receive(), StringEndsWith.endsWith("\n9\nIt works!\n0\n\n"));
    }

    /**
     * Assert that the socket associated with the provided client is closed.
     *
     * @param s the socket client
     * @throws IOException in case of an IO error
     */
    public static void assertConnectionIsClosed(SocketHttpClient s) throws IOException {
        // get
        s.request(Http.Method.POST, null);
        // assert
        try {
            // when the connection is closed before we start reading, just "" is returned by receive()
            assertThat(s.receive(), Is.is(""));
        } catch (SocketException e) {
            // "Connection reset" exception is thrown in case we were fast enough and started receiving the response
            // before it was closed
            LOGGER.finer("Received: " + e.getMessage());
        }
    }

    /**
     * Generates at least {@code bytes} number of bytes as a sequence of decimal numbers delimited
     * by newlines.
     *
     * @param bytes the amount of bytes to generate (might get little bit more than that)
     * @return the generated bytes as a sequence of decimal numbers delimited by newlines
     */
    public static StringBuilder longData(int bytes) {
        StringBuilder data = new StringBuilder(bytes);
        for (int i = 0; data.length() < bytes; ++i) {
            data.append(i)
                .append("\n");
        }
        return data;
    }

    /**
     * Read the data from the socket. If socket is closed, an empty string is returned.
     *
     * @return the read data
     * @throws IOException in case of an IO error
     */
    public String receive() throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String t;
        boolean ending = false;
        int contentLength = -1;
        while ((t = br.readLine()) != null) {

            LOGGER.finest("Received: " + t);

            if (t.toLowerCase().startsWith("content-length")) {
                int k = t.indexOf(':');
                contentLength = Integer.parseInt(t.substring(k + 1).trim());
            }

            sb.append(t)
              .append("\n");

            if ("".equalsIgnoreCase(t) && contentLength >= 0) {
                char[] content = new char[contentLength];
                br.read(content);
                sb.append(content);
                break;
            }
            if (ending && "".equalsIgnoreCase(t)) {
                break;
            }
            if (!ending && ("0".equalsIgnoreCase(t))) {
                ending = true;
            }
        }
        return sb.toString();
    }

    /**
     * Sends a request to the webserver.
     *
     * @param method the http method
     * @throws IOException in case of an IO error
     */
    public void request(Http.Method method) throws IOException {
        request(method, null);
    }

    /**
     * Sends a request to the webserver.
     *
     * @param method  the http method
     * @param payload the payload to send (must be without the newlines;
     *                otherwise it's not a valid payload)
     * @throws IOException in case of an IO error
     */
    public void request(Http.Method method, String payload) throws IOException {
        request(method, "/", payload);
    }

    /**
     * Sends a request to the webserver.
     *
     * @param path    the path to access
     * @param method  the http method
     * @param payload the payload to send (must be without the newlines;
     *                otherwise it's not a valid payload)
     * @throws IOException in case of an IO error
     */
    public void request(Http.Method method, String path, String payload) throws IOException {
        request(method, path, payload, List.of("Content-Type: application/x-www-form-urlencoded"));
    }

    /**
     * Sends a request to the webserver.
     *
     * @param path    the path to access
     * @param method  the http method
     * @param payload the payload to send (must be without the newlines;
     *                otherwise it's not a valid payload)
     * @param headers the headers (e.g., {@code Content-Type: application/json})
     * @throws IOException in case of an IO error
     */
    public void request(Http.Method method, String path, String payload, Iterable<String> headers) throws IOException {
        if (headers == null) {
            headers = Collections.emptyList();
        }
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        pw.print(method.name());
        pw.print(" ");
        pw.print(path);
        pw.println(" HTTP/1.1");
        pw.println("Host: 127.0.0.1");

        for (String header : headers) {
            pw.println(header);
        }

        sendPayload(pw, payload);

        pw.println("");
        pw.flush();
    }

    /**
     * Override this to send a specific payload.
     *
     * @param pw      the print writer where to write the payload
     * @param payload the payload as provided in the {@link #receive()} methods.
     */
    protected void sendPayload(PrintWriter pw, String payload) {
        if (payload != null) {
            pw.println("Content-Length: " + payload.length());
            pw.println("");
            pw.println(payload);
        }
    }

    @Override
    public void close() throws Exception {
        socket.close();
    }
}
