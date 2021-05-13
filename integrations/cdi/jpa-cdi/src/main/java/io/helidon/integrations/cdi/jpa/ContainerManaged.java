/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.integrations.cdi.jpa;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;

/**
 * A {@link Qualifier} indicating that the qualified bean's instances
 * are <em>container-managed</em>, in any number of the various senses
 * defined by the JPA specification.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE })
@interface ContainerManaged {

    /**
     * An {@link AnnotationLiteral} that implements {@link
     * ContainerManaged}.
     */
    final class Literal extends AnnotationLiteral<ContainerManaged> implements ContainerManaged {

        /**
         * The version of this class for serialization purposes.
         */
        private static final long serialVersionUID = 1L;

        /**
         * The sole instance of this class.
         */
        static final ContainerManaged INSTANCE = new Literal();

        /**
         * Creates a new {@link Literal}.
         */
        private Literal() {
            super();
        }

    }

}
