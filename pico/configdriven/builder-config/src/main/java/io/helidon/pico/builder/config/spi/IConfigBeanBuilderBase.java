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

package io.helidon.pico.builder.config.spi;

import java.util.Objects;
import java.util.Optional;

import io.helidon.common.config.Config;

/**
 * Minimal implementation for the {@link IConfigBeanBuilder}.
 *
 * @deprecated this is for internal use only
 */
public abstract class IConfigBeanBuilderBase implements IConfigBeanBuilder {
    private Config cfg;

    /**
     * Default constructor. Reserved for internal use.
     */
    protected IConfigBeanBuilderBase() {
    }

    @Override
    public void acceptConfig(Config cfg,
                             boolean resolveAndValidate) {
        if (resolveAndValidate) {
            // note: do not simplify this code by calling createResolutionContext - we want that to come from the generated code
            acceptConfig(cfg,
                         ConfigResolverHolder.configResolver().get(),
                         ConfigBeanBuilderValidatorHolder.configBeanValidatorFor(__configBeanType()).orElse(null));
        } else {
            __config(cfg);
        }
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
     * Creates a resolution context.
     *
     * @param configBeanType    the config bean type
     * @param cfg               the config
     * @param resolver          the resolver
     * @param validator         the config bean builder validator
     * @return the resolution context
     */
    protected ResolutionContext createResolutionContext(Class<?> configBeanType,
                                                        Config cfg,
                                                        ConfigResolver resolver,
                                                        ConfigBeanBuilderValidator<?> validator) {
        // note to self: that in the future we should probably accept a code-generated 'version id' here --jtrent
        return ResolutionContext.create(configBeanType, cfg, resolver, validator);
    }

    /**
     * Called when finished the resolution process.
     *
     * @param ctx the resolution context
     */
    protected void finishedResolution(ResolutionContext ctx) {
        // note to self: need to add validation here --jtrent
    }

}
