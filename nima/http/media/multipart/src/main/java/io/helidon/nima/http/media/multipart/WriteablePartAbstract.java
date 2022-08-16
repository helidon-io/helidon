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

package io.helidon.nima.http.media.multipart;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.Headers;
import io.helidon.common.http.HeadersWritable;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.media.type.MediaTypes;

import static java.nio.charset.StandardCharsets.UTF_8;

abstract class WriteablePartAbstract implements WriteablePart {
    private final String partName;
    private final Optional<String> fileName;
    private final HttpMediaType contentType;
    private final Headers headers;

    protected WriteablePartAbstract(String partName, Optional<String> fileName, HttpMediaType contentType, Headers headers) {
        this.partName = partName;
        this.fileName = fileName;
        this.contentType = contentType;
        this.headers = headers;
    }

    @Override
    public String name() {
        return partName;
    }

    @Override
    public Optional<String> fileName() {
        return fileName;
    }

    @Override
    public HttpMediaType contentType() {
        return contentType;
    }

    @Override
    public Headers headers() {
        return headers;
    }

    protected void sendHeaders(OutputStream outputStream, Headers headers) throws IOException {
        BufferData bufferData = BufferData.growing(128);
        for (Http.HeaderValue header : headers) {
            header.writeHttp1Header(bufferData);
        }
        bufferData.writeTo(outputStream);
        outputStream.write('\r');
        outputStream.write('\n');
    }

    protected void send(OutputStream outputStream, HeadersWritable<?> headers, byte[] bytes) {
        headers.set(Http.Header.CONTENT_LENGTH.withValue(true, false, String.valueOf(bytes.length)));

        try (outputStream) {
            sendHeaders(outputStream, headers);
            outputStream.write(bytes);
            outputStream.write('\r');
            outputStream.write('\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void contentType(HeadersWritable<?> headers) {
        // we support form-data and byte-ranges, falling back to form data if unknown
        if (contentType().test(MediaTypes.MULTIPART_BYTERANGES)) {
            headers.remove(Http.Header.CONTENT_DISPOSITION);
        } else {
            if (!headers.contains(Http.Header.CONTENT_DISPOSITION)) {
                List<String> disposition = new LinkedList<>();
                disposition.add("form-data");
                disposition.add("name=\"" + URLEncoder.encode(name(), UTF_8) + "\"");
                fileName().ifPresent(it -> disposition.add("filename=\"" + URLEncoder.encode(it, UTF_8) + "\""));
                headers.setIfAbsent(Http.Header.CONTENT_DISPOSITION.withValue(String.join("; ", disposition)));
            }
        }
        headers.setIfAbsent(Http.Header.CONTENT_TYPE.withValue(contentType().text()));
    }
}
