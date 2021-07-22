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

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A utility class providing access to all JPA-related qualifiers
 * defined by this package.
 *
 * @see #JPA_CDI_QUALIFIERS
 */
final class JpaCdiQualifiers {


    /*
     * Static fields
     */


    /**
     * An {@linkplain Collections#unmodifiableSet(Set) unmodifiable
     * <code>Set</code>} of qualifier annotations defined by this
     * class' package.
     *
     * <p>This field is never {@code null}.</p>
     */
    static final Set<Annotation> JPA_CDI_QUALIFIERS;

    static {
        final Set<Annotation> jpaCdiQualifiers = new HashSet<>();
        jpaCdiQualifiers.add(CdiTransactionScoped.Literal.INSTANCE);
        jpaCdiQualifiers.add(ContainerManaged.Literal.INSTANCE);
        jpaCdiQualifiers.add(Extended.Literal.INSTANCE);
        jpaCdiQualifiers.add(JpaTransactionScoped.Literal.INSTANCE);
        jpaCdiQualifiers.add(NonTransactional.Literal.INSTANCE);
        jpaCdiQualifiers.add(Synchronized.Literal.INSTANCE);
        jpaCdiQualifiers.add(Unsynchronized.Literal.INSTANCE);
        JPA_CDI_QUALIFIERS = Collections.unmodifiableSet(jpaCdiQualifiers);
    }


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link JpaCdiQualifiers}.
     */
    private JpaCdiQualifiers() {
        super();
    }

}
