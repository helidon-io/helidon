/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.webserver.tests;

import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http1.Http1Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Verify that server prevents double compression when the handler has already set
 * the Content-Encoding header to gzip.
 */
@ServerTest
class ContentEncodingDoubleEncodingTest {
  private static final String RESPONSE_STR = "This is the content in the response";

  private final String baseUri;

  ContentEncodingDoubleEncodingTest(Http1Client client) {
    this.baseUri = client.prototype().baseUri().get().toUri().toString();
  }

  @SetUpRoute
  static void routing(HttpRouting.Builder router) {
    router.route(Http1Route.route(Method.GET,"/hello", (req, res) -> {
      try {
        String output = compress(RESPONSE_STR);
        var acceptEncoding = req.headers().value(HeaderNames.ACCEPT_ENCODING);
        if (acceptEncoding.isPresent() && acceptEncoding.get().contains("gzip")) {
          res.headers().add(HeaderNames.CONTENT_ENCODING, "gzip");
          send(res, output, false);
        } else {
          send(res, output, true);
        }
      } catch (Exception ex) {
        throw ex;
      }
    }));
  }

  private static String compress(String input) throws IOException {
    ByteArrayOutputStream obj=new ByteArrayOutputStream();
    GZIPOutputStream gzip = new GZIPOutputStream(obj);
    gzip.write(input.getBytes(StandardCharsets.ISO_8859_1));
    gzip.close();
    return obj.toString(StandardCharsets.ISO_8859_1);
  }

  private static void send(ServerResponse res, String output, boolean decompress) throws IOException {
    res.status(Status.OK_200);
    try (
        var outputStream = res.outputStream();
        var byteInputStream = new ByteArrayInputStream(output.getBytes(StandardCharsets.ISO_8859_1));
        var inputStream = decompress ? new GZIPInputStream(byteInputStream) : byteInputStream) {
      inputStream.transferTo(outputStream);
    }
  }

  @Test
  void testPreventDoubleEncodingDespiteSettingContentEncodingHeader() throws IOException {
    var url = new URL(baseUri + "hello");
    var conn = (HttpURLConnection) url.openConnection();
    conn.setRequestProperty("Accept-Encoding", "gzip");
    assertThat(conn.getResponseCode(), is(200));
    var compressedInputStream = conn.getInputStream();
    var decompressedResponse = new String(new GZIPInputStream(compressedInputStream).readAllBytes());
    assertThat(decompressedResponse, is(RESPONSE_STR));
  }
}
