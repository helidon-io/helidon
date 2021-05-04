/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.providers.abac;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.helidon.common.Errors;
import io.helidon.config.Config;
import io.helidon.security.EndpointConfig;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityLevel;
import io.helidon.security.providers.abac.spi.AbacValidator;

/**
 * Test validator.
 */
public class Attrib1Validator implements AbacValidator<Attrib1Validator.Attrib1Config> {
    @Override
    public Collection<Class<? extends Annotation>> supportedAnnotations() {
        return Set.of(Attrib1.class);
    }

    @Override
    public Class<Attrib1Config> configClass() {
        return Attrib1Config.class;
    }

    @Override
    public String configKey() {
        return "attrib1";
    }

    @Override
    public Attrib1Config fromConfig(Config config) {
        return new Attrib1Config(true);
    }

    @Override
    public Attrib1Config fromAnnotations(EndpointConfig endpointConfig) {
        for (SecurityLevel securityLevel : endpointConfig.securityLevels()) {
            for (EndpointConfig.AnnotationScope value : EndpointConfig.AnnotationScope.values()) {
                List<Annotation> annotations = new ArrayList<>();
                for (Class<? extends Annotation> annotation : supportedAnnotations()) {
                    List<? extends Annotation> list = securityLevel.filterAnnotations(annotation, value);
                    annotations.addAll(list);
                }
                for (Annotation annotation : annotations) {
                    if (annotation instanceof Attrib1) {
                        return new Attrib1Config(((Attrib1) annotation).value());
                    }
                }
            }
        }
        return new Attrib1Config(false);
    }

    @Override
    public void validate(Attrib1Config config, Errors.Collector collector, ProviderRequest request) {
        if (!config.succeed) {
            collector.fatal("Intentional unit test failure");
        }
    }

    public static class Attrib1Config implements AbacValidatorConfig {
        private boolean succeed;

        public Attrib1Config(boolean value) {
            this.succeed = value;
        }
    }
}
