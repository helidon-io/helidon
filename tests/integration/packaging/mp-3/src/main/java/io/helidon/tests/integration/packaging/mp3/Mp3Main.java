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
package io.helidon.tests.integration.packaging.mp3;

import java.util.Map;

import io.helidon.Main;
import io.helidon.common.Errors;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.security.providers.common.spi.AnnotationAnalyzer;

/**
 * When using  module-info, we must have a main class in the same module, as we cannot
 * have a module main class in a package that does not belong to this module.
 */
public class Mp3Main {
    /**
     * Main method.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        validateAnnotationAnalyzerInit();
        Main.main(args);
    }

    @SuppressWarnings("removal")
    private static void validateAnnotationAnalyzerInit() {
        Config config = Config.just(ConfigSources.create(Map.of("analyzer-message", "configured")));
        Errors.Collector collector = Errors.collector();

        // Both inherited overloads must remain a no-op, including in native images.
        AnnotationAnalyzer defaultAnalyzer = new DefaultAnalyzer();
        defaultAnalyzer.init(config);
        defaultAnalyzer.init((io.helidon.common.config.Config) config);

        var legacyAnalyzer = new LegacyInitAnalyzer();
        ((AnnotationAnalyzer) legacyAnalyzer).init(config);
        if (legacyAnalyzer.initCount != 1 || !"configured".equals(legacyAnalyzer.configuredValue)) {
            collector.fatal("Legacy analyzer expected one init with configured value, but received "
                                    + legacyAnalyzer.initCount + " calls and value " + legacyAnalyzer.configuredValue);
        }

        var modernAnalyzer = new ModernInitAnalyzer();
        ((AnnotationAnalyzer) modernAnalyzer).init((io.helidon.common.config.Config) config);
        if (modernAnalyzer.initCount != 1 || !"configured".equals(modernAnalyzer.configuredValue)) {
            collector.fatal("Modern analyzer expected one init with configured value, but received "
                                    + modernAnalyzer.initCount + " calls and value " + modernAnalyzer.configuredValue);
        }

        LegacyInitAnalyzer inheritedAnalyzer = new InheritedLegacyInitAnalyzer();
        ((AnnotationAnalyzer) inheritedAnalyzer).init(config);
        if (inheritedAnalyzer.initCount != 1 || !"configured".equals(inheritedAnalyzer.configuredValue)) {
            collector.fatal("Inherited analyzer expected one init with configured value, but received "
                                    + inheritedAnalyzer.initCount + " calls and value " + inheritedAnalyzer.configuredValue);
        }

        collector.collect().checkValid();
    }

    private static class DefaultAnalyzer implements AnnotationAnalyzer {
        @Override
        public AnalyzerResponse analyze(Class<?> maybeAnnotated) {
            return AnalyzerResponse.abstain();
        }
    }

    private static class LegacyInitAnalyzer extends DefaultAnalyzer {
        private String configuredValue;
        private int initCount;

        @Override
        @SuppressWarnings("removal")
        public void init(io.helidon.common.config.Config config) {
            initCount++;
            configuredValue = config.get("analyzer-message").asString().get();
        }
    }

    private static class ModernInitAnalyzer extends DefaultAnalyzer {
        private String configuredValue;
        private int initCount;

        @Override
        public void init(Config config) {
            initCount++;
            configuredValue = config.get("analyzer-message").asString().get();
        }
    }

    private static class InheritedLegacyInitAnalyzer extends LegacyInitAnalyzer {
    }
}
