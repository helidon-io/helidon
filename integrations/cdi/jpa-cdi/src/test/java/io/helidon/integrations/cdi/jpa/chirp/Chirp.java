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
package io.helidon.integrations.cdi.jpa.chirp;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Access(value = AccessType.FIELD)
@Entity(name = "Chirp")
@Table(name = "CHIRP")
public class Chirp implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(
        insertable = true,
        name = "ID",
        nullable = false,
        updatable = false
    )
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Integer id;

    @JoinColumn(
        insertable = true,
        name = "MICROBLOG_ID",
        nullable = false,
        referencedColumnName = "ID",
        updatable = false
    )
    @ManyToOne(
        optional = false,
        targetEntity = Microblog.class
    )
    private Microblog microblog;

    @Basic(optional = false)
    @Column(
        name = "CONTENT",
        insertable = true,
        nullable = false,
        updatable = true)
    private String contents;

    /**
     * This constructor exists to fulfil the requirement that all JPA
     * entities have a zero-argument constructor and for no other
     * purpose.
     *
     * @deprecated Please use the {@link #Chirp(Microblog, String)}
     * constructor instead.
     */
    @Deprecated
    protected Chirp() {
        super();
    }

    public Chirp(final Microblog microblog, final String contents) {
        super();
        this.setMicroblog(microblog);
        this.setContents(contents);
    }

    public Integer getId() {
        return this.id;
    }

    public String getContents() {
        return this.contents;
    }

    public void setContents(final String contents) {
        this.contents = Objects.requireNonNull(contents);
    }

    public Microblog getMicroblog() {
        return this.microblog;
    }

    void setMicroblog(final Microblog microblog) {
        this.microblog = Objects.requireNonNull(microblog);
    }

    @Override
    public int hashCode() {
        int hashCode = 17;

        final Object contents = this.getContents();
        int c = contents == null ? 0 : contents.hashCode();
        hashCode = 37 * hashCode + c;
        
        return hashCode;
    }

    @Override
    public boolean equals(final Object other) {
        if (other == this) {
            return true;
        } else if (other instanceof Chirp) {
            final Chirp her = (Chirp) other;
            final Object contents = this.getContents();
            if (contents == null) {
                if (her.getContents() != null) {
                    return false;
                }
            } else if (!contents.equals(her.getContents())) {
                return false;
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return this.getContents();
    }
    
}
