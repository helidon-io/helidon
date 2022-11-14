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

package io.helidon.pico.config.spi;

import java.util.Objects;

import io.helidon.pico.builder.Builder;
import io.helidon.pico.types.AnnotationAndValue;

/**
 * Represents all the attributes that belong to {@link io.helidon.pico.config.api.ConfigBean}.
 */

// TODO: 1) rework this to not derive off of the ConfigBean.
// TODO: 2) get rid of all class.getAnnotation*() in the code.
// TODO: 3) ensure all usages of config-api are provided dependencies.

@Builder(implPrefix = "Meta")
public interface ConfigBeanInfo extends io.helidon.pico.config.api.ConfigBean {

    /**
     * Builds meta information appropriate for config integration from a
     * {@link io.helidon.pico.config.api.ConfigBean} instance.
     *
     * @param val           the config bean instance
     * @param cfgBeanType   the config bean type
     * @return the meta information for the config bean
     */
    static MetaConfigBeanInfo<?> toMetaConfigBeanInfo(io.helidon.pico.config.api.ConfigBean val, Class<?> cfgBeanType) {
        Objects.requireNonNull(cfgBeanType);
        MetaConfigBeanInfo.Builder builder = MetaConfigBeanInfo.toBuilder(Objects.requireNonNull(val));
        if (!AnnotationAndValue.hasNonBlankValue(val.key())) {
            builder.key(ConfigUtils.toConfigKey(cfgBeanType.getSimpleName()));
        }
        return builder.build();
    }

    /**
     * @return the validated config key
     */
    default String validatedConfigKey() {
        String key = key();
        if (!AnnotationAndValue.hasNonBlankValue(key)) {
            throw new IllegalStateException("key was expected to be non-blank: " + this);
        }
        return key;
    }

}
