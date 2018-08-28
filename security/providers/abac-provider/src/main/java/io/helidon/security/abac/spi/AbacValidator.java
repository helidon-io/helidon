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

package io.helidon.security.abac.spi;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import io.helidon.common.Errors;
import io.helidon.config.Config;
import io.helidon.security.ProviderRequest;
import io.helidon.security.abac.AbacAnnotation;
import io.helidon.security.abac.AbacValidatorConfig;

/**
 * Service interface for attribute based access control (ABAC) validator.
 * The validator provides information about itself:
 * <ul>
 * <li>Annotations it expects - should be meta-annotated with {@link AbacAnnotation}, so we can check all are processed even
 * if validator is missing</li>
 * <li>Configuration key expected when configured through a file (or other config source)</li>
 * <li>Class of configuration of this attribute validator (also the type parameter of this class)</li>
 * </ul>
 *
 * @param <T> type of configuration used by this validator. Each validator must have its own {@link AbacValidatorConfig} class, so
 *            we can uniquely identify the one to call
 */
public interface AbacValidator<T extends AbacValidatorConfig> {
    /**
     * Provide extension annotations supported by this validator (e.g. {@link javax.annotation.security.RolesAllowed}).
     * Annotations will be collected according to framework in use. For JAX-RS, annotations from application class, resource
     * class and resource methods will be collected.
     * The annotations will be transformed to configuration by {@link #fromAnnotations(List)}.
     *
     * @return Collection of annotations this provider expects.
     */
    default Collection<Class<? extends Annotation>> supportedAnnotations() {
        return Collections.emptyList();
    }

    /**
     * Class of the configuration type.
     *
     * @return class of the type
     */
    Class<T> configClass();

    /**
     * Key of a configuration entry that maps to this validator's configuration.
     *
     * @return key in a config {@link Config}
     */
    String configKey();

    /**
     * Load configuration class instance from {@link Config}.
     *
     * @param config configuration located on the key this validator expects in {@link #configKey()}
     * @return instance of configuration class
     */
    T fromConfig(Config config);

    /**
     * Load configuration class instance from annotations this validator expects.
     *
     * @param annotations annotations collected from resource if annotations are supported
     * @return instance of configuration class
     */
    T fromAnnotations(List<? extends Annotation> annotations);

    /**
     * Combine two configuration (such as one obtained from annotation and one from config).
     *
     * @param parent The parent configuration (e.g. obtained from annotation)
     * @param child  The child configuration (e.g. obtained from explicit object)
     * @return combined configuration
     */
    T combine(T parent, T child);

    /**
     * Validate that the configuration provided would grant access to the resource.
     * Update collector with errors, if access should be denied using {@link Errors.Collector#fatal(Object, String)}.
     *
     * @param config    configuration of this validator
     * @param collector error collector to gather issues with this request (e.g. "service not in role ABC")
     * @param request   ABAC context containing subject(s), object(s) and environment
     */
    void validate(T config,
                  Errors.Collector collector,
                  ProviderRequest request);
}
