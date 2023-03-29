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
import java.util.function.Function;

import io.helidon.common.config.Config;

/**
 * Minimal implementation for the {@link GeneratedConfigBeanBuilder}.
 */
public abstract class GeneratedConfigBeanBuilderBase implements GeneratedConfigBeanBuilder {
    private Config cfg;

    /**
     * Constructor.
     *
     * @deprecated not intended to be created directly
     */
    @Deprecated
    protected GeneratedConfigBeanBuilderBase() {
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
     * @param mappers           the known config bean mappers related to this config bean context
     * @return the resolution context
     */
    protected ResolutionContext createResolutionContext(Class<?> configBeanType,
                                                        Config cfg,
                                                        ConfigResolver resolver,
                                                        ConfigBeanBuilderValidator<?> validator,
                                                        Map<Class<?>, Function<Config, ?>> mappers) {
        return ResolutionContext.create(configBeanType, cfg, resolver, validator, mappers);
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
