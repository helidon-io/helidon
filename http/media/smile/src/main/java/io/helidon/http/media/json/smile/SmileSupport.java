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

package io.helidon.http.media.json.smile;

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Api;
import io.helidon.common.GenericType;
import io.helidon.config.Config;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.MediaSupportBase;
import io.helidon.json.binding.JsonBinding;

/**
 * Smile media support implementation.
 * <p>
 * This class provides Smile binary media support for Helidon HTTP.
 */
@Api.Preview
@SuppressWarnings({"rawtypes", "unchecked"})
public class SmileSupport extends MediaSupportBase<SmileSupportConfig> implements RuntimeType.Api<SmileSupportConfig> {

    static final String ID = "smile";

    private final SmileReader reader;
    private final SmileWriter writer;

    private SmileSupport(SmileSupportConfig supportConfig) {
        super(supportConfig, ID);

        JsonBinding jsonBinding = supportConfig.jsonBinding();

        this.reader = new SmileReader(jsonBinding);
        this.writer = new SmileWriter(supportConfig, jsonBinding);
    }

    /**
     * Create a new Smile media support from configuration.
     *
     * @param config the configuration to use
     * @return a new {@link SmileSupport} instance
     */
    public static SmileSupport create(Config config) {
        return create(config, ID);
    }

    /**
     * Create a new Smile media support with default configuration.
     *
     * @return a new {@link SmileSupport} instance
     */
    public static SmileSupport create() {
        return create(SmileSupportConfig.create());
    }

    /**
     * Create a new Smile media support from configuration with a custom name.
     *
     * @param config the configuration to use
     * @param name the name for this media support instance
     * @return a new MediaSupport instance
     */
    public static SmileSupport create(Config config, String name) {
        return builder()
                .name(name)
                .config(config)
                .build();
    }

    /**
     * Create a new Smile support from a configuration object.
     *
     * @param config the configuration object
     * @return a new {@link SmileSupport} instance
     */
    public static SmileSupport create(SmileSupportConfig config) {
        return new SmileSupport(config);
    }

    /**
     * Create a new Smile support using a configuration consumer.
     *
     * @param consumer the consumer to configure the builder
     * @return a new {@link SmileSupport} instance
     */
    public static SmileSupport create(Consumer<SmileSupportConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    /**
     * Create a new builder for {@link SmileSupport}.
     *
     * @return a new builder instance
     */
    public static SmileSupportConfig.Builder builder() {
        return SmileSupportConfig.builder();
    }

    @Override
    public String type() {
        return ID;
    }

    @Override
    public SmileSupportConfig prototype() {
        return config();
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type, Headers requestHeaders) {
        if (matchesServerRequest(type, requestHeaders)) {
            return new ReaderResponse<>(SupportLevel.COMPATIBLE, this::reader);
        }

        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type,
                                        Headers requestHeaders,
                                        WritableHeaders<?> responseHeaders) {
        if (matchesServerResponse(type, requestHeaders, responseHeaders)) {
            return new WriterResponse<>(SupportLevel.COMPATIBLE, this::writer);
        }

        return WriterResponse.unsupported();
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type,
                                        Headers requestHeaders,
                                        Headers responseHeaders) {
        if (matchesClientResponse(type, responseHeaders)) {
            return new ReaderResponse<>(SupportLevel.COMPATIBLE, this::reader);
        }

        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
        if (matchesClientRequest(type, requestHeaders)) {
            return new WriterResponse<>(SupportLevel.COMPATIBLE, this::writer);
        }
        return WriterResponse.unsupported();
    }

    @Override
    protected boolean canSerialize(GenericType<?> type) {
        return true;
    }

    @Override
    protected boolean canDeserialize(GenericType<?> type) {
        return true;
    }

    <T> EntityReader<T> reader() {
        return reader;
    }

    <T> EntityWriter<T> writer() {
        return writer;
    }
}
