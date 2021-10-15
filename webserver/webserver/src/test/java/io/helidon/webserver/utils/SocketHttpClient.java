/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.http.Http;
import io.helidon.webserver.WebServer;

import org.hamcrest.core.Is;
import org.hamcrest.core.StringEndsWith;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * The SocketHttpClient provides means to simply pass any bytes into the WebServer
 * and to see how it deals with such case.
 */
public class SocketHttpClient implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(SocketHttpClient.class.getName());
    private static final String EOL = "\r\n";
    private static final Pattern FIRST_LINE_PATTERN = Pattern.compile("HTTP/\\d+\\.\\d+ (\\d\\d\\d) (.*)");

    private final Socket socket;

    /**
     * Creates the instance linked with the provided webserver.
     *
     * @param webServer the webserver to link this client with
     * @throws IOException in case of an error
     */
    public SocketHttpClient(WebServer webServer) throws IOException {
        socket = new Socket("localhost", webServer.port());
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
    public static String sendAndReceive(String path, Http.RequestMethod method, String payload, WebServer webServer)
            throws Exception {
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
                                        Http.RequestMethod method,
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
     * Find headers in response and parse them.
     *
     * @param response full HTTP response
     * @return headers map
     */
    public static Map<String, String> headersFromResponse(String response) {

        assertThat(response, notNullValue());
        int index = response.indexOf("\n\n");
        if (index < 0) {
            throw new AssertionError("Missing end of headers in response!");
        }
        String hdrsPart = response.substring(0, index);
        String[] lines = hdrsPart.split("\\n");
        if (lines.length <= 1) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new HashMap<>(lines.length - 1);
        boolean first = true;
        for (String line : lines) {
            if (first) {
                first = false;
                continue;
            }
            int i = line.indexOf(':');
            if (i < 0) {
                throw new AssertionError("Header without semicolon - " + line);
            }
            result.put(line.substring(0, i).trim(), line.substring(i + 1).trim());
        }
        return result;
    }

    /**
     * Find the status line and return response HTTP status.
     *
     * @param response full HTTP response
     * @return status
     */
    public static Http.ResponseStatus statusFromResponse(String response) {
        // response should start with HTTP/1.1 000 reasonPhrase\n
        int eol = response.indexOf('\n');
        assertThat("There must be at least a line end after first line: " + response, eol > -1);
        String firstLine = response.substring(0, eol).trim();

        Matcher matcher = FIRST_LINE_PATTERN.matcher(firstLine);
        assertThat("Status line must match the patter of 'HTTP/0.0 000 ReasonPhrase', but is: " + response,
                   matcher.matches());

        int statusCode = Integer.parseInt(matcher.group(1));
        String phrase = matcher.group(2);

        return Http.ResponseStatus.create(statusCode, phrase);
    }

    /**
     * Get entity from response.
     *
     * @param response response with initial line, headers, and entity
     * @param validateHeaderFormat whether to validate headers are correctly formatted
     * @return entity string
     */
    public static String entityFromResponse(String response, boolean validateHeaderFormat) {
        assertThat(response, notNullValue());
        int index = response.indexOf("\n\n");
        if (index < 0) {
            throw new AssertionError("Missing end of headers in response!");
        }
        if (validateHeaderFormat) {
            String headers = response.substring(0, index);
            String[] lines = headers.split("\\n");
            assertThat(lines[0], startsWith("HTTP/"));
            for (int i = 1; i < lines.length; i++) {
                assertThat(lines[i], containsString(":"));
            }
        }

        return response.substring(index + 2);
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
    public void request(Http.RequestMethod method) throws IOException {
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
    public void request(Http.RequestMethod method, String payload) throws IOException {
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
    public void request(Http.RequestMethod method, String path, String payload) throws IOException {
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
    public void request(Http.RequestMethod method, String path, String payload, Iterable<String> headers) throws IOException {
        request(method.name(), path, "HTTP/1.1", "127.0.0.1", headers, payload);
    }

    /**
     * Send raw data to the server.
     *
     * @param method HTTP Method
     * @param path path
     * @param protocol protocol
     * @param host host header value (if null, host header is not sent)
     * @param headers headers (if null, additional headers are not sent)
     * @param payload entity (if null, entity is not sent)
     *
     * @throws IOException in case of an IO error
     */
    public void request(String method, String path, String protocol, String host, Iterable<String> headers, String payload)
            throws IOException {
        List<String> usedHeaders = new LinkedList<>();
        if (headers != null) {
            headers.forEach(usedHeaders::add);
        }
        if (host != null) {
            usedHeaders.add(0, "Host: " + host);
        }
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        pw.print(method);
        pw.print(" ");
        pw.print(path);
        pw.print(" ");
        pw.print(protocol);
        pw.print(EOL);

        for (String header : usedHeaders) {
            pw.print(header);
            pw.print(EOL);
        }

        sendPayload(pw, payload);

        pw.print(EOL);
        pw.print(EOL);
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
            pw.print("Content-Length: " + payload.length());
            pw.print(EOL);
            pw.print(EOL);
            pw.print(payload);
            pw.print(EOL);
        }
    }

    @Override
    public void close() throws Exception {
        socket.close();
    }
}
