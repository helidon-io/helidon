/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.media.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.media.common.spi.MediaSupportProvider;

/**
 * Media support.
 */
public final class MediaContext {

    private final MessageBodyReaderContext readerContext;
    private final MessageBodyWriterContext writerContext;

    private MediaContext(MessageBodyReaderContext readerContext, MessageBodyWriterContext writerContext) {
        this.readerContext = readerContext;
        this.writerContext = writerContext;
    }

    /**
     * Create a new instance with default readers and writers registered to the contexts.
     *
     * @return instance with defaults
     */
    public static MediaContext create() {
        return builder().build();
    }

    /**
     * Create a new instance based on the configuration.
     *
     * @param config a {@link Config}
     * @return instance based on config
     */
    public static MediaContext create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Creates new empty instance without registered defaults.
     *
     * @return empty instance
     */
    public static MediaContext empty() {
        return builder().registerDefaults(false).build();
    }

    /**
     * Create a new {@link Builder} instance.
     *
     * @return a new {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the configured reader context.
     *
     * @return MessageBodyReaderContext
     */
    public MessageBodyReaderContext readerContext() {
        return readerContext;
    }

    /**
     * Get the configured writer context.
     *
     * @return MessageBodyWriterContext
     */
    public MessageBodyWriterContext writerContext() {
        return writerContext;
    }

    /**
     * MediaSupport builder.
     */
    public static class Builder implements io.helidon.common.Builder<MediaContext>,
                                           MediaContextBuilder<Builder> {

        private static final String SERVICE_NAME = "name";
        private static final String DEFAULTS_NAME = "defaults";
        private static final String DEFAULTS_INCLUDE_STACK_TRACES = "include-stack-traces";

        private static final int DEFAULTS_PRIORITY = 100;
        private static final int BUILDER_PRIORITY = 200;
        private static final int LOADER_PRIORITY = 300;

        private final HelidonServiceLoader.Builder<MediaSupportProvider> services = HelidonServiceLoader
                .builder(ServiceLoader.load(MediaSupportProvider.class));

        private final List<MessageBodyReader<?>> builderReaders = new ArrayList<>();
        private final List<MessageBodyStreamReader<?>> builderStreamReaders = new ArrayList<>();
        private final List<MessageBodyWriter<?>> builderWriters = new ArrayList<>();
        private final List<MessageBodyStreamWriter<?>> builderStreamWriter = new ArrayList<>();
        private final List<MediaSupport> mediaSupports = new ArrayList<>();
        private final Map<String, Map<String, String>> servicesConfig = new HashMap<>();
        private final MessageBodyReaderContext readerContext;
        private final MessageBodyWriterContext writerContext;
        private boolean registerDefaults = true;
        private boolean discoverServices = false;
        private boolean filterServices = false;
        private Config config = Config.empty();

        private Builder() {
            this.readerContext = MessageBodyReaderContext.create();
            this.writerContext = MessageBodyWriterContext.create();
        }

        /**
         * Configures this {@link Builder} from the supplied {@link Config}.
         * <table class="config">
         * <caption>Optional configuration parameters</caption>
         * <tr>
         *     <th>key</th>
         *     <th>description</th>
         * </tr>
         * <tr>
         *     <td>register-defaults</td>
         *     <td>Whether to register default reader and writers</td>
         * </tr>
         * <tr>
         *     <td>discover-services</td>
         *     <td>Whether to discover services via service loader</td>
         * </tr>
         * <tr>
         *     <td>filter-services</td>
         *     <td>Whether to filter discovered services by service names in services section</td>
         * </tr>
         * <tr>
         *     <td>services</td>
         *     <td></td>
         * </tr>
         * </table>
         *
         * @param config a {@link Config}
         * @return this {@link Builder}
         */
        public Builder config(Config config) {
            config.get("register-defaults").asBoolean().ifPresent(this::registerDefaults);
            config.get("discover-services").asBoolean().ifPresent(this::discoverServices);
            config.get("filter-services").asBoolean().ifPresent(this::filterServices);
            config.get("services")
                    .asNodeList()
                    .ifPresent(it -> it.forEach(serviceConfig -> {
                        String name = serviceConfig.get(SERVICE_NAME).asString().get();
                        servicesConfig.merge(name,
                                             serviceConfig.detach().asMap().orElseGet(Map::of),
                                             (first, second) -> {
                                                 HashMap<String, String> result = new HashMap<>(first);
                                                 result.putAll(second);
                                                 return result;
                                             });
                    }));
            this.config = config;
            return this;
        }

        @Override
        public Builder addMediaSupport(MediaSupport mediaSupport) {
            Objects.requireNonNull(mediaSupport);
            mediaSupports.add(mediaSupport);
            return this;
        }

        /**
         * Adds new instance of {@link MediaSupport} with specific priority.
         *
         * @param mediaSupport media support
         * @param priority priority
         * @return updated instance of the builder
         */
        public Builder addMediaSupport(MediaSupport mediaSupport, int priority) {
            Objects.requireNonNull(mediaSupport);
            services.addService((config) -> mediaSupport, priority);
            return this;
        }

        @Override
        public Builder addReader(MessageBodyReader<?> reader) {
            builderReaders.add(reader);
            return this;
        }

        @Override
        public Builder addStreamReader(MessageBodyStreamReader<?> streamReader) {
            builderStreamReaders.add(streamReader);
            return this;
        }

        @Override
        public Builder addWriter(MessageBodyWriter<?> writer) {
            builderWriters.add(writer);
            return this;
        }

        @Override
        public Builder addStreamWriter(MessageBodyStreamWriter<?> streamWriter) {
            builderStreamWriter.add(streamWriter);
            return this;
        }

        /**
         * Whether defaults should be included.
         *
         * @param registerDefaults register defaults
         * @return this builder instance
         */
        public Builder registerDefaults(boolean registerDefaults) {
            this.registerDefaults = registerDefaults;
            return this;
        }

        /**
         * Whether stack traces should be included in response.
         *
         * This is server side setting.
         *
         * @param includeStackTraces include stack traces
         * @return this builder instance
         */
        public Builder includeStackTraces(boolean includeStackTraces) {
            servicesConfig.computeIfAbsent(DEFAULTS_NAME, k -> new HashMap<>())
                    .put(DEFAULTS_INCLUDE_STACK_TRACES, Boolean.toString(includeStackTraces));
            return this;
        }

        /**
         * Whether system loader should be used to load {@link MediaSupportProvider} from classpath.
         *
         * @param discoverServices use system loader
         * @return this builder instance
         */
        public Builder discoverServices(boolean discoverServices) {
            this.discoverServices = discoverServices;
            return this;
        }

        /**
         * Whether services loaded by system loader should be filtered.
         * All of the services which should pass the filter, have to be present under {@code services} section of configuration.
         *
         * @param filterServices filter services
         * @return this builder instance
         */
        public Builder filterServices(boolean filterServices) {
            this.filterServices = filterServices;
            return this;
        }

        @Override
        public MediaContext build() {
            //Remove all service names from the obtained service configurations
            servicesConfig.forEach((key, values) -> values.remove(SERVICE_NAME));
            if (filterServices) {
                this.services.useSystemServiceLoader(false);
                filterClassPath();
            } else {
                this.services.useSystemServiceLoader(discoverServices);
            }
            if (registerDefaults) {
                this.services.addService(new DefaultsProvider(), DEFAULTS_PRIORITY);
            }
            this.services.defaultPriority(LOADER_PRIORITY)
                    .addService(config -> new MediaSupport() {
                        @Override
                        public void register(MessageBodyReaderContext readerContext, MessageBodyWriterContext writerContext) {
                            builderReaders.forEach(readerContext::registerReader);
                            builderStreamReaders.forEach(readerContext::registerReader);
                            builderWriters.forEach(writerContext::registerWriter);
                            builderStreamWriter.forEach(writerContext::registerWriter);
                        }
                    }, BUILDER_PRIORITY)
                    .addService(config -> new MediaSupport() {
                        @Override
                        public void register(MessageBodyReaderContext readerContext, MessageBodyWriterContext writerContext) {
                            mediaSupports.forEach(it -> it.register(readerContext, writerContext));
                        }
                    }, BUILDER_PRIORITY)
                    .build()
                    .asList()
                    .stream()
                    .map(it -> it.create(Config.just(ConfigSources.create(servicesConfig.getOrDefault(it.configKey(),
                                                                                                        new HashMap<>())))))
                    .collect(Collectors.toCollection(LinkedList::new))
                    .descendingIterator()
                    .forEachRemaining(mediaService -> mediaService.register(readerContext, writerContext));

            return new MediaContext(readerContext, writerContext);
        }

        private void filterClassPath() {
            Config servicesConfig = config.get("services");
            HelidonServiceLoader.builder(ServiceLoader.load(MediaSupportProvider.class))
                    .defaultPriority(LOADER_PRIORITY)
                    .build()
                    .asList()
                    .stream()
                    .filter(provider -> servicesConfig.get(provider.configKey()).exists())
                    .forEach(services::addService);
        }
    }

    private static final class DefaultsProvider implements MediaSupportProvider {

        @Override
        public String configKey() {
            return Builder.DEFAULTS_NAME;
        }

        @Override
        public MediaSupport create(Config config) {
            boolean includeStackTraces = config.get(Builder.DEFAULTS_INCLUDE_STACK_TRACES).asBoolean().orElse(false);
            return DefaultMediaSupport.create(includeStackTraces);
        }
    }

}
