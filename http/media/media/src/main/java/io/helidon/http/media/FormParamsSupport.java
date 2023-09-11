/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.http.media;

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
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.parameters.Parameters;
import io.helidon.common.uri.UriEncoding;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.WritableHeaders;

/**
 * Media support for {@link io.helidon.common.media.type.MediaTypes#APPLICATION_FORM_URLENCODED} and its plaintext counterpart.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class FormParamsSupport implements MediaSupport {
    private static final EntityReader URL_READER = new FormParamsUrlReader();
    private static final EntityWriter URL_WRITER = new FormParamsUrlWriter();
    private static final EntityReader PLAINTEXT_READER = new FormParamsPlaintextReader();
    private static final EntityWriter PLAINTEXT_WRITER = new FormParamsPlaintextWriter();

    private FormParamsSupport() {
    }

    /**
     * Create a new media support for application form processing.
     *
     * @return a new media support
     */
    public static MediaSupport create() {
        return new FormParamsSupport();
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type, Headers requestHeaders) {
        if (!Parameters.class.isAssignableFrom(type.rawType())) {
            return ReaderResponse.unsupported();
        }

        return requestHeaders.contentType()
                .map(it -> {
                    ReaderResponse<T> response;
                    if (it.test(MediaTypes.APPLICATION_FORM_URLENCODED)) {
                        response = new ReaderResponse<>(SupportLevel.SUPPORTED, FormParamsSupport::urlEncodedReader);
                    } else if (it.test(MediaTypes.TEXT_PLAIN)) {
                        response = new ReaderResponse<>(SupportLevel.SUPPORTED, FormParamsSupport::textReader);
                    } else {
                        // different than supported media type
                        response = ReaderResponse.unsupported();
                    }
                    return response;
                }).orElseGet(() -> new ReaderResponse<>(SupportLevel.COMPATIBLE, FormParamsSupport::urlEncodedReader));
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type,
                                        Headers requestHeaders,
                                        WritableHeaders<?> responseHeaders) {

        if (!Parameters.class.isAssignableFrom(type.rawType())) {
            return WriterResponse.unsupported();
        }

        return responseHeaders.contentType()
                .map(it -> {
                    WriterResponse<T> response;
                    if (it.test(MediaTypes.APPLICATION_FORM_URLENCODED)) {
                        response = new WriterResponse<>(SupportLevel.SUPPORTED, FormParamsSupport::urlEncodedWriter);
                    } else if (it.test(MediaTypes.TEXT_PLAIN)) {
                        response = new WriterResponse<>(SupportLevel.SUPPORTED, FormParamsSupport::textWriter);
                    } else {
                        // different than supported media type
                        response = WriterResponse.unsupported();
                    }
                    return response;
                }).orElseGet(() -> new WriterResponse<T>(SupportLevel.COMPATIBLE, FormParamsSupport::urlEncodedWriter));
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type,
                                        Headers requestHeaders,
                                        Headers responseHeaders) {
        if (!Parameters.class.isAssignableFrom(type.rawType())) {
            return ReaderResponse.unsupported();
        }

        return responseHeaders.contentType()
                .map(it -> {
                    ReaderResponse<T> response;
                    if (it.test(MediaTypes.APPLICATION_FORM_URLENCODED)) {
                        response = new ReaderResponse<>(SupportLevel.SUPPORTED, FormParamsSupport::urlEncodedReader);
                    } else if (it.test(MediaTypes.TEXT_PLAIN)) {
                        response = new ReaderResponse<>(SupportLevel.SUPPORTED, FormParamsSupport::textReader);
                    } else {
                        // different than supported media type
                        response = ReaderResponse.unsupported();
                    }
                    return response;
                }).orElseGet(() -> new ReaderResponse<>(SupportLevel.COMPATIBLE, FormParamsSupport::urlEncodedReader));
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
        if (!Parameters.class.isAssignableFrom(type.rawType())) {
            return WriterResponse.unsupported();
        }
        return requestHeaders.contentType()
                .map(it -> {
                    WriterResponse<T> response;
                    if (it.test(MediaTypes.APPLICATION_FORM_URLENCODED)) {
                        response = new WriterResponse<>(SupportLevel.SUPPORTED, FormParamsSupport::urlEncodedWriter);
                    } else if (it.test(MediaTypes.TEXT_PLAIN)) {
                        response = new WriterResponse<>(SupportLevel.SUPPORTED, FormParamsSupport::textWriter);
                    } else {
                        // different than supported media type
                        response = WriterResponse.unsupported();
                    }
                    return response;
                }).orElseGet(() -> new WriterResponse<T>(SupportLevel.COMPATIBLE, FormParamsSupport::urlEncodedWriter));
    }

    @Override
    public String name() {
        return "form-params";
    }

    @Override
    public String type() {
        return "form-params";
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
        private final Header contentTypeHeader;

        private FormParamsWriter(String separator,
                                 Function<String, String> nameEncoder,
                                 Function<String, String> valueEncoder,
                                 Header contentTypeHeader) {
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
                          WritableHeaders<?> responseHeaders) {
            write(object, outputStream, responseHeaders);
        }

        @Override
        public void write(GenericType<Parameters> type,
                          Parameters object,
                          OutputStream outputStream,
                          WritableHeaders<?> headers) {
            write(object, outputStream, headers);
        }

        private void write(Parameters toWrite,
                           OutputStream outputStream,
                           WritableHeaders<?> writableHeaders) {

            Charset charset;
            if (writableHeaders.contains(HeaderNames.CONTENT_TYPE)) {
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
                List<String> all = toWrite.all(name)
                        .stream()
                        .map(valueEncoder)
                        .toList();
                // write each value as a separate line, so we can support multiple values that contain commas
                // and so we can safely parse multiple values without splitting by comma
                String encodedName = nameEncoder.apply(name);
                if (all.isEmpty()) {
                    allParams.add(encodedName + "=");
                } else {
                    for (String value : all) {
                        allParams.add(encodedName + "=" + value);
                    }
                }
            }
            try (outputStream) {
                outputStream.write(String.join(separator, allParams).getBytes(charset));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static class FormParamsUrlWriter extends FormParamsWriter {
        private static final Header CONTENT_TYPE_URL_ENCODED =
                HeaderValues.createCached(HeaderNames.CONTENT_TYPE,
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
        private static final Header CONTENT_TYPE_TEXT =
                HeaderValues.createCached(HeaderNames.CONTENT_TYPE,
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
        private final System.Logger logger;
        private final Pattern pattern;
        private final BiFunction<Charset, String, String> decoder;

        private FormParamsReader(System.Logger logger, Pattern pattern, BiFunction<Charset, String, String> decoder) {
            this.logger = logger;
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
                if (logger.isLoggable(System.Logger.Level.DEBUG)) {
                    logger.log(System.Logger.Level.DEBUG, "Reading encoded form parameters: {0}", encodedString);
                }
                Matcher matcher = pattern.matcher(encodedString);
                while (matcher.find()) {
                    String key = decoder.apply(charset, matcher.group(1));
                    String encodedValue = matcher.group(2);
                    if (encodedValue == null || encodedValue.isEmpty()) {
                        builder.add(key);
                    } else {
                        String value = decoder.apply(charset, encodedValue);
                        builder.add(key, value);
                    }
                }
                return builder.build();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    static final class FormParamsUrlReader extends FormParamsReader {
        static final Pattern PATTERN = Pattern.compile("([^=&]+)=([^&]*)&?");
        private static final System.Logger LOGGER = System.getLogger(FormParamsUrlReader.class.getName());
        private static final BiFunction<Charset, String, String> DECODER = (charset, value) -> URLDecoder.decode(value, charset);

        private FormParamsUrlReader() {
            super(LOGGER, PATTERN, DECODER);
        }
    }

    private static final class FormParamsPlaintextReader extends FormParamsReader {
        private static final System.Logger LOGGER = System.getLogger(FormParamsPlaintextReader.class.getName());
        private static final Pattern PATTERN = Pattern.compile("([^=]+)=([^\\n]*)\\n?");
        private static final BiFunction<Charset, String, String> DECODER = (charset, value) -> value;

        private FormParamsPlaintextReader() {
            super(LOGGER, PATTERN, DECODER);
        }
    }

}
