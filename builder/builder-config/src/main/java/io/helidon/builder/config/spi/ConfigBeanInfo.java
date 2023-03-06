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
     * The attribute name for {@link #value()} ()}.
     */
    String TAG_KEY = "value";

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
     * The attribute name for {@link #levelType()}.
     */
    String TAG_LEVEL_TYPE = "levelType";

    /**
     * Builds meta information appropriate for config integration from a
     * {@link io.helidon.builder.config.ConfigBean} instance. This will use the key if {@link #value()} is present, and
     * if not present will default to the simple class name of the bean type.
     *
     * @param val           the config bean instance
     * @param cfgBeanType   the config bean type
     * @return the meta information for the config bean
     */
    static MetaConfigBeanInfo toMetaConfigBeanInfo(ConfigBean val,
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
                .value((String) meta.get("value"))
                .repeatable(Boolean.parseBoolean((String) meta.get(TAG_REPEATABLE)))
                .drivesActivation(Boolean.parseBoolean((String) meta.get(TAG_DRIVES_ACTIVATION)))
                .atLeastOne(Boolean.parseBoolean((String) meta.get(TAG_AT_LEAST_ONE)))
                .wantDefaultConfigBean(Boolean.parseBoolean((String) meta.get(TAG_WANT_DEFAULT_CONFIG_BEAN)))
                .levelType(LevelType.valueOf((String) meta.get(TAG_LEVEL_TYPE)))
                .build();
    }

    /**
     * Same as calling {@code toConfigKey(name, true)}.
     *
     * @param name the input name
     * @return the config key
     * @see #toConfigKey(String, boolean)
     */
    static String toConfigKey(String name) {
        return toConfigKey(name, true);
    }

    /**
     * Converts the name (i.e., simple class name or method name) into a config key.
     * <p>
     * Method name is camel case (such as maxInitialLineLength)
     * result is dash separated and lower cased (such as max-initial-line-length).
     * <p>
     * The behavior is modified slightly for config bean type names (i.e., when {@code isElement=false}) in that any
     * configuration ending in "-config" is stripped off as a general convention (e.g., "Http2Config" maps to "http2").
     *
     * @param name      the input name
     * @param isElement true if the input name is a method attribute element, false for simple config bean class type names
     * @return the config key
     */
    // note: a similar method is also found in ConfigMetadataHandler.
    static String toConfigKey(String name,
                              boolean isElement) {
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

        String res = result.toString();
        if (!isElement) {
            int pos = res.lastIndexOf("-config");
            if (pos > 0) {
                res = res.substring(0, pos);
            }
        }

        return res;
    }

}
