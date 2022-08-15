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

package io.helidon.nima.http.media;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.GenericType;
import io.helidon.common.http.Headers;
import io.helidon.common.http.HeadersWritable;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.parameters.Parameters;
import io.helidon.common.uri.UriEncoding;
import io.helidon.nima.http.media.spi.MediaSupportProvider;

/**
 * Media support for {@link io.helidon.common.media.type.MediaTypes#APPLICATION_FORM_URLENCODED} and its plaintext counterpart.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class FormParamsSupportProvider implements MediaSupportProvider {
    private static final EntityReader URL_READER = new FormParamsUrlReader();
    private static final EntityWriter URL_WRITER = new FormParamsUrlWriter();
    private static final EntityReader PLAINTEXT_READER = new FormParamsPlaintextReader();
    private static final EntityWriter PLAINTEXT_WRITER = new FormParamsPlaintextWriter();

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type, Headers requestHeaders) {
        if (!type.equals(Parameters.GENERIC_TYPE)) {
            return ReaderResponse.unsupported();
        }

        return requestHeaders.contentType()
                .map(it -> {
                    ReaderResponse<T> response;
                    if (it.test(MediaTypes.APPLICATION_FORM_URLENCODED)) {
                        response = new ReaderResponse<>(SupportLevel.SUPPORTED, FormParamsSupportProvider::urlEncodedReader);
                    } else if (it.test(MediaTypes.TEXT_PLAIN)) {
                        response = new ReaderResponse<>(SupportLevel.SUPPORTED, FormParamsSupportProvider::textReader);
                    } else {
                        // different than supported media type
                        response = ReaderResponse.unsupported();
                    }
                    return response;
                }).orElseGet(() -> new ReaderResponse<>(SupportLevel.COMPATIBLE, FormParamsSupportProvider::urlEncodedReader));
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type,
                                        Headers requestHeaders,
                                        HeadersWritable<?> responseHeaders) {

        if (!type.equals(Parameters.GENERIC_TYPE)) {
            return WriterResponse.unsupported();
        }

        return responseHeaders.contentType()
                .map(it -> {
                    WriterResponse<T> response;
                    if (it.test(MediaTypes.APPLICATION_FORM_URLENCODED)) {
                        response = new WriterResponse<>(SupportLevel.SUPPORTED, FormParamsSupportProvider::urlEncodedWriter);
                    } else if (it.test(MediaTypes.TEXT_PLAIN)) {
                        response = new WriterResponse<>(SupportLevel.SUPPORTED, FormParamsSupportProvider::textWriter);
                    } else {
                        // different than supported media type
                        response = WriterResponse.unsupported();
                    }
                    return response;
                }).orElseGet(() -> new WriterResponse<T>(SupportLevel.COMPATIBLE, FormParamsSupportProvider::urlEncodedWriter));
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type,
                                        Headers requestHeaders,
                                        Headers responseHeaders) {
        if (!type.equals(Parameters.GENERIC_TYPE)) {
            return ReaderResponse.unsupported();
        }

        return responseHeaders.contentType()
                .map(it -> {
                    ReaderResponse<T> response;
                    if (it.test(MediaTypes.APPLICATION_FORM_URLENCODED)) {
                        response = new ReaderResponse<>(SupportLevel.SUPPORTED, FormParamsSupportProvider::urlEncodedReader);
                    } else if (it.test(MediaTypes.TEXT_PLAIN)) {
                        response = new ReaderResponse<>(SupportLevel.SUPPORTED, FormParamsSupportProvider::textReader);
                    } else {
                        // different than supported media type
                        response = ReaderResponse.unsupported();
                    }
                    return response;
                }).orElseGet(() -> new ReaderResponse<>(SupportLevel.COMPATIBLE, FormParamsSupportProvider::urlEncodedReader));
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type, HeadersWritable<?> requestHeaders) {
        if (!type.equals(Parameters.GENERIC_TYPE)) {
            return WriterResponse.unsupported();
        }
        return requestHeaders.contentType()
                .map(it -> {
                    WriterResponse<T> response;
                    if (it.test(MediaTypes.APPLICATION_FORM_URLENCODED)) {
                        response = new WriterResponse<>(SupportLevel.SUPPORTED, FormParamsSupportProvider::urlEncodedWriter);
                    } else if (it.test(MediaTypes.TEXT_PLAIN)) {
                        response = new WriterResponse<>(SupportLevel.SUPPORTED, FormParamsSupportProvider::textWriter);
                    } else {
                        // different than supported media type
                        response = WriterResponse.unsupported();
                    }
                    return response;
                }).orElseGet(() -> new WriterResponse<T>(SupportLevel.COMPATIBLE, FormParamsSupportProvider::urlEncodedWriter));
    }

    private static <T> EntityReader<T> urlEncodedReader() {
        return URL_READER;
    }

    private static <T> EntityReader<T> textReader() {
        return PLAINTEXT_READER;
    }

    private static <T> EntityWriter<T> urlEncodedWriter() {
        return URL_WRITER;
    }

    private static <T> EntityWriter<T> textWriter() {
        return PLAINTEXT_WRITER;
    }

    private static class FormParamsWriter implements EntityWriter<Parameters> {
        private final String separator;
        private final Function<String, String> nameEncoder;
        private final Function<String, String> valueEncoder;
        private final HeaderValue contentTypeHeader;

        private FormParamsWriter(String separator,
                                 Function<String, String> nameEncoder,
                                 Function<String, String> valueEncoder,
                                 HeaderValue contentTypeHeader) {
            this.separator = separator;
            this.nameEncoder = nameEncoder;
            this.valueEncoder = valueEncoder;
            this.contentTypeHeader = contentTypeHeader;
        }

        @Override
        public void write(GenericType<Parameters> type,
                          Parameters object,
                          OutputStream outputStream,
                          Headers requestHeaders,
                          HeadersWritable<?> responseHeaders) {
            write(object, outputStream, responseHeaders);
        }

        @Override
        public void write(GenericType<Parameters> type,
                          Parameters object,
                          OutputStream outputStream,
                          HeadersWritable<?> headers) {
            write(object, outputStream, headers);
        }

        private void write(Parameters toWrite,
                           OutputStream outputStream,
                           HeadersWritable<?> writableHeaders) {

            Charset charset;
            if (writableHeaders.contains(Http.Header.CONTENT_TYPE)) {
                charset = writableHeaders.contentType()
                        .flatMap(HttpMediaType::charset)
                        .map(Charset::forName)
                        .orElse(StandardCharsets.UTF_8);
            } else {
                writableHeaders.set(contentTypeHeader);
                charset = StandardCharsets.UTF_8;
            }
            // a=b&c=d,e
            List<String> allParams = new ArrayList<>(toWrite.size());
            for (String name : toWrite.names()) {
                List<String> all = toWrite.all(name);
                all.replaceAll(valueEncoder::apply);
                allParams.add(nameEncoder.apply(name) + "=" + String.join(",", all));
            }
            try (outputStream) {
                outputStream.write(String.join(separator, allParams).getBytes(charset));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static class FormParamsUrlWriter extends FormParamsWriter {
        private static final HeaderValue CONTENT_TYPE_URL_ENCODED =
                HeaderValue.createCached(Http.Header.CONTENT_TYPE,
                                         HttpMediaType.create(MediaTypes.APPLICATION_FORM_URLENCODED)
                                                 .withCharset("utf-8")
                                                 .text());
        private static final String SEPARATOR = "&";
        private static final Function<String, String> NAME_ENCODER = it -> UriEncoding.encode(it, UriEncoding.Type.QUERY);
        private static final Function<String, String> VALUE_ENCODER = it -> UriEncoding.encode(it, UriEncoding.Type.QUERY_PARAM);

        private FormParamsUrlWriter() {
            super(SEPARATOR, NAME_ENCODER, VALUE_ENCODER, CONTENT_TYPE_URL_ENCODED);
        }
    }

    private static class FormParamsPlaintextWriter extends FormParamsWriter {
        private static final HeaderValue CONTENT_TYPE_TEXT =
                HeaderValue.createCached(Http.Header.CONTENT_TYPE,
                                         HttpMediaType.create(MediaTypes.TEXT_PLAIN)
                                                 .withCharset("utf-8")
                                                 .text());
        private static final String SEPARATOR = "\n";
        private static final Function<String, String> NAME_ENCODER = Function.identity();
        private static final Function<String, String> VALUE_ENCODER = Function.identity();

        private FormParamsPlaintextWriter() {
            super(SEPARATOR, NAME_ENCODER, VALUE_ENCODER, CONTENT_TYPE_TEXT);
        }
    }

    private static class FormParamsReader implements EntityReader<Parameters> {
        private final Pattern pattern;
        private final BiFunction<Charset, String, String> decoder;

        private FormParamsReader(Pattern pattern, BiFunction<Charset, String, String> decoder) {
            this.pattern = pattern;
            this.decoder = decoder;
        }

        @Override
        public Parameters read(GenericType<Parameters> type, InputStream stream, Headers headers) {
            return read(stream, headers.contentType());
        }

        @Override
        public Parameters read(GenericType<Parameters> type,
                               InputStream stream,
                               Headers requestHeaders,
                               Headers responseHeaders) {
            return read(stream, responseHeaders.contentType());
        }

        private Parameters read(InputStream stream, Optional<HttpMediaType> contentType) {
            Charset charset = contentType
                    .flatMap(HttpMediaType::charset)
                    .map(Charset::forName)
                    .orElse(StandardCharsets.UTF_8);

            try (stream) {
                Parameters.Builder builder = Parameters.builder("form-params");
                String encodedString = new String(stream.readAllBytes(), charset);
                Matcher matcher = pattern.matcher(encodedString);
                while (matcher.find()) {
                    String key = decoder.apply(charset, matcher.group(1));
                    String value = decoder.apply(charset, matcher.group(2));
                    if (value == null) {
                        builder.add(key);
                    } else {
                        builder.add(key, value);
                    }
                }
                return builder.build();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static final class FormParamsUrlReader extends FormParamsReader {
        private static final Pattern PATTERN = Pattern.compile("([^=]+)=([^&]+)&?");
        private static final BiFunction<Charset, String, String> DECODER = (charset, value) -> URLDecoder.decode(value, charset);

        private FormParamsUrlReader() {
            super(PATTERN, DECODER);
        }
    }

    private static final class FormParamsPlaintextReader extends FormParamsReader {
        private static final Pattern PATTERN = Pattern.compile("([^=]+)=([^\\n]+)\\n?");
        private static final BiFunction<Charset, String, String> DECODER = (charset, value) -> value;

        private FormParamsPlaintextReader() {
            super(PATTERN, DECODER);
        }
    }

}
