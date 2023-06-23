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
import java.util.Optional;

import io.helidon.common.config.Config;

/**
 * Minimal implementation for the {@link GeneratedConfigBeanCommon}. This is the base for generated config beans.
 */
public abstract class GeneratedConfigBeanBase implements GeneratedConfigBean {
    private final Config config;
    private String instanceId;

    /**
     * Protected constructor for initializing the generated config bean instance variables.
     *
     * @param b             the builder
     * @param instanceId    the instance id
     * @deprecated not intended to be created directly
     */
    @Deprecated
    protected GeneratedConfigBeanBase(GeneratedConfigBeanBuilder b,
                                      String instanceId) {
        this.config = b.__config().orElse(null);
        this.instanceId = instanceId;
    }

    @Override
    public Optional<Config> __config() {
        return Optional.ofNullable(config);
    }

    @Override
    public Optional<String> __name() {
        if (__config().isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(__config().get().name());
    }

    /**
     * The meta attributes for this generated config bean.
     *
     * @return meta attributes for this config bean
     */
    public abstract Map<String, Map<String, Object>> __metaProps();

    @Override
    public MetaConfigBeanInfo __metaInfo() {
        Map<String, Object> meta = __metaProps().get(ConfigBeanInfo.TAG_META);
        MetaConfigBeanInfo metaInfo = (MetaConfigBeanInfo) meta.get(ConfigBeanInfo.class.getName());
        return Objects.requireNonNull(metaInfo);
    }

    @Override
    public String __instanceId() {
        return instanceId;
    }

    /**
     * Assigns the instance id assigned to this bean.
     * Note that the instance id is typically assigned the {@link Config#key()} in most circumstances.
     *
     * @param val the new instance id for this bean
     */
    public void __instanceId(String val) {
        this.instanceId = val;
    }

}
