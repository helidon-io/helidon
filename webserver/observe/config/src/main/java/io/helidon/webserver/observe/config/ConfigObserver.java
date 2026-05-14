/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe.config;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.helidon.builder.api.RuntimeType;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.DisabledObserverFeature;
import io.helidon.webserver.observe.spi.Observer;
import io.helidon.webserver.spi.ServerFeature;

/**
 * Config Observer configuration.
 */
public class ConfigObserver implements Observer, RuntimeType.Api<ConfigObserverConfig> {
    private final ConfigObserverConfig config;
    private final List<Pattern> secretPatterns;
    private final List<Pattern> safeKeyPatterns;

    private ConfigObserver(ConfigObserverConfig config, List<Pattern> secretPatterns, List<Pattern> safeKeyPatterns) {
        this.config = config;
        this.secretPatterns = secretPatterns;
        this.safeKeyPatterns = safeKeyPatterns;
    }

    /**
     * Create a new builder to configure Config observer.
     *
     * @return a new builder
     */
    public static ConfigObserverConfig.Builder builder() {
        return ConfigObserverConfig.builder();
    }

    /**
     * Create a new Config observer using the provided configuration.
     *
     * @param config configuration
     * @return a new observer
     */
    public static ConfigObserver create(ConfigObserverConfig config) {
        List<Pattern> secretPatterns = Stream.concat(ConfigObserverConfigDefaults.SECRETS.stream(),
                                                     config.secrets().stream())
                .distinct()
                .map(it -> Pattern.compile(it, Pattern.CASE_INSENSITIVE))
                .toList();
        List<Pattern> safeKeyPatterns = config.safeKeys()
                .stream()
                .map(it -> Pattern.compile(it, Pattern.CASE_INSENSITIVE))
                .toList();
        return new ConfigObserver(config, secretPatterns, safeKeyPatterns);
    }

    /**
     * Create a new Config observer customizing its configuration.
     *
     * @param consumer configuration consumer
     * @return a new observer
     */
    public static ConfigObserver create(Consumer<ConfigObserverConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }

    /**
     * Create a new Config observer with default configuration.
     *
     * @return a new observer
     */
    public static ConfigObserver create() {
        return builder()
                .build();
    }

    @Override
    public ConfigObserverConfig prototype() {
        return config;
    }

    @Override
    public String type() {
        return "config";
    }

    @Override
    public void register(ServerFeature.ServerFeatureContext featureContext,
                         List<HttpRouting.Builder> observeEndpointRouting,
                         UnaryOperator<String> endpointFunction) {

        String endpoint = endpointFunction.apply(config.endpoint());
        if (config.enabled()) {
            for (HttpRouting.Builder routing : observeEndpointRouting) {
                // register the service itself
                routing.addFeature(new ConfigHttpFeature(endpoint,
                                                         secretPatterns,
                                                         safeKeyPatterns,
                                                         findProfile(),
                                                         config.permitAll(),
                                                         config.unsafeValues()));
            }
        } else {
            for (HttpRouting.Builder builder : observeEndpointRouting) {
                builder.addFeature(DisabledObserverFeature.create("Config", endpoint + "/*"));
            }
        }
    }

    private static String findProfile() {
        // we may want to have this directly in config
        String name = System.getenv("HELIDON_CONFIG_PROFILE");
        if (name != null) {
            return name;
        }
        name = System.getProperty("helidon.config.profile");
        if (name != null) {
            return name;
        }
        name = System.getProperty("config.profile");
        if (name != null) {
            return name;
        }
        return "";
    }

    private static class ConfigHttpFeature implements HttpFeature {
        private final String endpoint;
        private final List<Pattern> secretPatterns;
        private final List<Pattern> safeKeyPatterns;
        private final String profile;
        private final boolean permitAll;
        private final boolean unsafeValues;

        private ConfigHttpFeature(String endpoint,
                                  List<Pattern> secretPatterns,
                                  List<Pattern> safeKeyPatterns,
                                  String profile,
                                  boolean permitAll,
                                  boolean unsafeValues) {
            this.endpoint = endpoint;
            this.secretPatterns = secretPatterns;
            this.safeKeyPatterns = safeKeyPatterns;
            this.profile = profile;
            this.permitAll = permitAll;
            this.unsafeValues = unsafeValues;
        }

        @Override
        public void setup(HttpRouting.Builder routing) {
            routing.register(endpoint, new ConfigService(secretPatterns, safeKeyPatterns, profile, permitAll, unsafeValues));
        }
    }
}
