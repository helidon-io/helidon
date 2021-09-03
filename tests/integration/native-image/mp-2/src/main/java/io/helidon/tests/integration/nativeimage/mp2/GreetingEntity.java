/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.nativeimage.mp2;

import java.util.Objects;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * A database entity.
 */
@Access(AccessType.PROPERTY)
@Entity(name = "Greeting")
@Table(name = "GREETING")
public class GreetingEntity {
    private String firstPart;
    private String secondPart;

    /**
     * Creates a new {@link io.helidon.tests.integration.nativeimage.mp2.GreetingEntity}; required by the JPA
     * specification and for no other purpose.
     *
     * @deprecated Please use the {@link #GreetingEntity(String, String)} constructor instead.
     *
     * @see #GreetingEntity(String, String)
     */
    @Deprecated
    protected GreetingEntity() {
        super();
    }

    /**
     * Creates a new {@link io.helidon.tests.integration.nativeimage.mp2.GreetingEntity}.
     *
     * @param firstPart the first part of the greeting; must not be
     * {@code null}
     *
     * @param secondPart the second part of the greeting; must not be
     * {@code null}
     *
     * @exception NullPointerException if {@code firstPart} or {@code
     * secondPart} is {@code null}
     */
    public GreetingEntity(final String firstPart, final String secondPart) {
        super();
        this.firstPart = Objects.requireNonNull(firstPart);
        this.secondPart = Objects.requireNonNull(secondPart);
    }

    @Id
    @Column(name = "FIRSTPART", insertable = true, nullable = false, updatable = false)
    public String getFirstPart() {
        return firstPart;
    }

    public void setFirstPart(String firstPart) {
        this.firstPart = firstPart;
    }

    @Basic(optional = false)
    @Column(name = "SECONDPART", insertable = true, nullable = false, updatable = true)
    public String getSecondPart() {
        return secondPart;
    }

    /**
     * Sets the second part of this greeting.
     *
     * @param secondPart the second part of this greeting; must not be
     * {@code null}
     *
     * @exception NullPointerException if {@code secondPart} is {@code
     * null}
     */
    public void setSecondPart(final String secondPart) {
        this.secondPart = Objects.requireNonNull(secondPart);
    }

    /**
     * Returns a {@link String} representation of the second part of
     * this {@link io.helidon.tests.integration.nativeimage.mp2.GreetingEntity}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return a non-{@code null} {@link String} representation of the
     * second part of this {@link io.helidon.tests.integration.nativeimage.mp2.GreetingEntity}
     */
    @Override
    public String toString() {
        return this.secondPart;
    }
}
