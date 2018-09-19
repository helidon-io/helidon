/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.svm;

import io.helidon.config.Config;
import io.helidon.config.ConfigMapper;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.MissingValueException;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Graal VM substitutions for Config.
 */
public final class ConfigSubstitutions {
    @TargetClass(className = "io.helidon.config.ConfigMapperManager")
    static final class ConfigMapperSvmExtension {
        @Substitute
        private <T> ConfigMapper<T> fallbackConfigMapper(Class<T> type) {
            return new ConfigMapper<T>() {
                @Override
                public T apply(Config config) throws ConfigMappingException, MissingValueException {
                    throw new ConfigMappingException(config.key(),
                                                     type,
                                                     "Unsupported Java type, no compatible config value mapper found.");
                }
            };
        }
    }
}
