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

package io.helidon.builder.config.spi;

import java.util.Map;
import java.util.Objects;

import io.helidon.builder.Builder;
import io.helidon.builder.config.ConfigBean;

/**
 * Represents all the attributes belonging to {@link io.helidon.builder.config.ConfigBean} available in a
 * {@link io.helidon.builder.Builder} style usage pattern.
 */
@Builder(implPrefix = "Meta")
public interface ConfigBeanInfo extends ConfigBean {

    /**
     * The attribute name for {@link #key()}.
     */
    String TAG_KEY = "key";

    /**
     * The attribute name for {@link #drivesActivation()}.
     */
    String TAG_DRIVES_ACTIVATION = "drivesActivation";

    /**
     * The attribute name for {@link #atLeastOne()}.
     */
    String TAG_AT_LEAST_ONE = "atLeastOne";

    /**
     * The attribute name for {@link #repeatable()}.
     */
    String TAG_REPEATABLE = "repeatable";

    /**
     * The attribute name for {@link #wantDefaultConfigBean()}.
     */
    String TAG_WANT_DEFAULT_CONFIG_BEAN = "wantDefaultConfigBean";

    /**
     * Builds meta information appropriate for config integration from a
     * {@link io.helidon.builder.config.ConfigBean} instance. This will use the key if {@link #key()} is present, and
     * if not present will default to the simple class name of the bean type.
     *
     * @param val           the config bean instance
     * @param cfgBeanType   the config bean type
     * @return the meta information for the config bean
     */
    static MetaConfigBeanInfo toMetaConfigBeanInfo(
            ConfigBean val,
            Class<?> cfgBeanType) {
        Objects.requireNonNull(val);
        Objects.requireNonNull(cfgBeanType);
        MetaConfigBeanInfo.Builder builder = MetaConfigBeanInfo.toBuilder(val);
        String key = val.value();
        if (!key.isBlank()) {
            builder.value(toConfigKey(cfgBeanType.getSimpleName()));
        }
        return builder.build();
    }

    /**
     * Builds meta information appropriate for config integration from a
     * meta attribute map.
     *
     * @param meta the meta attribute map
     * @return the meta information for the config bean
     */
    static MetaConfigBeanInfo toMetaConfigBeanInfo(Map<String, Object> meta) {
        return MetaConfigBeanInfo.builder()
                .value((String) meta.get("key"))
                .repeatable(Boolean.valueOf((String) meta.get("repeatable")))
                .drivesActivation(Boolean.valueOf((String) meta.get("drivesActivation")))
                .atLeastOne(Boolean.valueOf((String) meta.get("atLeastOne")))
                .wantDefaultConfigBean(Boolean.valueOf((String) meta.get("atLeastOne")))
                .build();
    }

    /**
     * Converts the name (i.e., simple class name or method name) into a config key.
     * <p>
     * Method name is camel case (such as maxInitialLineLength)
     * result is dash separated and lower cased (such as max-initial-line-length).
     *
     * @param name the input name
     * @return the config key
     */
    // note: this method is also found in ConfigMetadataHandler.
    static String toConfigKey(
            String name) {
        StringBuilder result = new StringBuilder(name.length() + 5);

        char[] chars = name.toCharArray();
        for (char aChar : chars) {
            if (Character.isUpperCase(aChar)) {
                if (result.length() == 0) {
                    result.append(Character.toLowerCase(aChar));
                } else {
                    result.append('-')
                            .append(Character.toLowerCase(aChar));
                }
            } else {
                result.append(aChar);
            }
        }

        return result.toString();
    }

}
