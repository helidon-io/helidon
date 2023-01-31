/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.tools;

import java.lang.annotation.Annotation;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * This class will load only if supporting (enterprise) types are on the classpath.
 *
 * @deprecated
 */
public class JakartaEnterpriseTypeToolsImpl implements JakartaEnterpriseTypeTools {

    /**
     * Service loader based constructor.
     *
     * @deprecated
     */
    public JakartaEnterpriseTypeToolsImpl() {
    }

    @Override
    public boolean isJakartaEnterpriseOnTheClasspath() {
        assert (loadAnnotationClass(ApplicationScoped.class.getName()).isPresent());
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Class<? extends Annotation>> loadAnnotationClass(
            String annotationTypeName) {
        try {
            return Optional.of((Class<? extends Annotation>) Class.forName(annotationTypeName));
        } catch (Throwable t) {
            // expected in most circumstances
            Throwable debugMe = t;
        }
        return Optional.empty();
    }
}
