/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.config.services;

import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.pico.ServiceInfoCriteria;
import io.helidon.pico.builder.config.spi.MetaConfigBeanInfo;

class Utils {

    private Utils() {
    }

    static boolean isBlank(
            ServiceInfoCriteria criteria) {
        assert (criteria.externalContractsImplemented().isEmpty());
        return criteria.serviceTypeName().isEmpty()
                && criteria.contractsImplemented().isEmpty()
                && criteria.qualifiers().isEmpty();
    }

    static boolean hasValue(
            String val) {
        return (val != null) && !val.isBlank();
    }

    static String validatedConfigKey(
            String configKey) {
        if (!hasValue(configKey)) {
            throw new IllegalStateException("key was expected to be non-blank");
        }
        return configKey;
    }

    static String validatedConfigKey(
            MetaConfigBeanInfo metaConfigBeanInfo) {
        return validatedConfigKey(metaConfigBeanInfo.key());
    }

    static Config safeDowncastOf(
            io.helidon.common.config.Config config) {
        if (!(config instanceof Config)) {
            throw new IllegalStateException(config.getClass() + " is not supported - the only type supported is " + Config.class);
        }

        return (Config) config;
    }

    static Optional<Integer> toNumeric(
            String val) {
        if (val == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(Integer.parseInt(val));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

}
