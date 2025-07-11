/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.integrations.langchain4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;

import io.helidon.common.Weighted;

/**
 * AiProvider serves as a container for defining AI model configuration annotations and related metadata.
 * These annotations help specify the configuration, provider details for code-generation of various AI
 * models and their associated configuration.
 */
public final class AiProvider {

    private AiProvider() {
    }

    /**
     * Annotation used to define a configuration for a specific LangChain4j model class.
     * This configuration is used during code generation for creating service bean providers
     * and configuration blueprints.
     * <br/>
     * Configuration example:
     * <pre>{@code
     * langchain4j:
     *   # provider key
     *   open-ai:
     *     # model type
     *     chat-model:
     *       # properties introspected from
     *       api-key: api-key
     *       model-name: model-name
     * }</pre>
     */
    @Target({ElementType.TYPE})
    @Repeatable(ModelConfigs.class)
    public @interface ModelConfig {

        /**
         * LangChain4j model class to be introspected for code-generated service bean providers.
         *
         * @return LangChain4j model class
         */
        Class<?> value();

        /**
         * Specifies the builder class associated with the LangChain4j model, for configuration introspection.
         *
         * @return the builder class, or {@code Void.class} if no builder is specified.
         */
        Class<?> builder() default Void.class;

        /**
         * Specifies property names or configurations to skip in the configuration blueprint.
         *
         * @return an array of property names or configurations to exclude
         */
        String[] skip() default {};

        /**
         * Specifies a unique configuration key to identify the service provider for the associated LangChain4j model.
         * If not specified, key is derived from the configuration parent class, example:
         * {@code OpenAiConfiguration.java} -> {@code open-ai}
         *
         * @return the unique provider key, or an empty string if not specified.
         */
        String providerKey() default "";

        /**
         * Defines the weight of the generated model factory bean.
         * A higher weight indicates higher significance.
         *
         * @return the weight value, default is {@link Weighted#DEFAULT_WEIGHT} in no constant
         *         of the {@code double} type is defined and annotated with {@link AiProvider.DefaultWeight} in the same class.
         */
        double weight() default Weighted.DEFAULT_WEIGHT;
    }

    /**
     * The ModelConfigs annotation serves as a container for multiple {@link ModelConfig} annotations.
     * It allows for the aggregation of multiple model configuration definitions on a single target.
     * Typically used on classes requiring multiple LangChain4j model configurations to be introspected
     * for generating service provider beans and configuration blueprints.
     */
    @Target({ElementType.TYPE})
    public @interface ModelConfigs {

        /**
         * Retrieves an array of {@link ModelConfig} annotations contained within this annotation.
         *
         * @return an array of {@link ModelConfig} annotations representing multiple model configuration definitions
         */
        ModelConfig[] value();
    }

    /**
     * Default weight used for generated model factory if it is not defined by
     * {@link io.helidon.integrations.langchain4j.AiProvider.ModelConfig#weight()}.
     * Annotated field needs to be a {@code double} constant.
     */
    @Target({ElementType.FIELD})
    public @interface DefaultWeight {
    }

    /**
     * Marks nested properties which types should be introspected too.
     */
    @Target({ElementType.METHOD})
    public @interface NestedConfig {
        /**
         * Model class that is having method {@code builder()}
         * (or custom alternative that needs to be configured with
         * {@link io.helidon.integrations.langchain4j.AiProvider.NestedConfig#builderMethod()} )
         * for accessing introspected builder.
         *
         * @return model class
         */
        Class<?> value();

        /**
         * Parent AiProvider codegen interface which can be used for further nesting and customizing.
         *
         * @return parent interface
         */
        Class<?> parent() default Void.class;

        /**
         * Custom name for builder method on model class.
         *
         * @return builder method name, {@code builder} is default.
         */
        String builderMethod() default "builder";
    }

    /**
     * Skip builder mapping generation as it is done manually in {@code configuredBuilder()} method.
     */
    @Target({ElementType.METHOD})
    public @interface CustomBuilderMapping {
    }
}
