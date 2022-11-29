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

package io.helidon.pico.builder.config.spi;

import java.util.Objects;
import java.util.Optional;

import io.helidon.common.config.Config;

/**
 * Minimal implementation for the {@link ConfigBeanCommon}. This is the base for generated config beans.
 *
 * @deprecated this is for internal use only
 */
public abstract class ConfigBeanBase implements ConfigBeanCommon {
    private Config cfg;
    private String instanceId;

    /**
     * Protected constructor for initializing the generated config bean instance variables.
     */
    protected ConfigBeanBase() {
    }

    @Override
    public Optional<Config> __config() {
        return Optional.ofNullable(cfg);
    }

    /**
     * Sets the config instance.
     *
     * @param cfg the config instance
     */
    public void __config(Config cfg) {
        this.cfg = Objects.requireNonNull(cfg);
    }

    /**
     * Returns the instance id assigned to this bean.
     *
     * @return the instance id assigned to this bean
     */
    public String __instanceId() {
        return instanceId;
    }

    /**
     * Assigns the instance id assigned to this bean.
     *
     * @param val the new instance id for this bean
     */
    public void __instanceId(String val) {
        instanceId = val;
    }

}
