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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * A simple collection of small blog articles that will not scale to
 * any appreciable volume and is useful only for unit tests.
 */
@Access(value = AccessType.FIELD)
@Entity(name = "Microblog")
@Table(
    name = "MICROBLOG",
    uniqueConstraints = {
        @UniqueConstraint(
            columnNames = {
                "NAME",
                "AUTHOR_ID"
            }
        )
    }
)
public class Microblog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(
        insertable = true,
        name = "ID",
        updatable = false
    )
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Integer id;

    @JoinColumn(
        insertable = true,
        name = "AUTHOR_ID",
        referencedColumnName = "ID",
        updatable = false
    )
    @ManyToOne(
        cascade = {
            CascadeType.PERSIST
        },
        optional = false,
        targetEntity = Author.class
    )
    private Author author;

    @Basic(optional = false)
    @Column(
        insertable = true,
        name = "NAME",
        updatable = false
    )
    private String name;
    
    @OneToMany(
        cascade = CascadeType.ALL,
        fetch = FetchType.LAZY,
        mappedBy = "microblog",
        targetEntity = Chirp.class
    )
    private List<Chirp> chirps;

    @Deprecated
    protected Microblog() {
        super();
    }

    public Microblog(final Author author, final String name) {
        super();
        this.author = Objects.requireNonNull(author);
        this.name = Objects.requireNonNull(name);
    }

    public Integer getId() {
        return this.id;
    }

    public Author getAuthor() {
        return this.author;
    }

    public String getName() {
        return this.name;
    }

    public List<Chirp> getChirps() {
        return this.chirps;
    }

    public void addChirp(final Chirp chirp) {
        if (chirp != null) {
            List<Chirp> chirps = this.chirps;
            if (chirps == null) {
                chirps = new ArrayList<>();
                this.chirps = chirps;
            }
            chirps.add(chirp);
            chirp.setMicroblog(this);
        }        
    }

    @Override
    public int hashCode() {
        int hashCode = 17;

        final Object name = this.getName();
        int c = name == null ? 0 : name.hashCode();
        hashCode = 37 * hashCode + c;

        final Object author = this.getAuthor();
        c = author == null ? 0 : name.hashCode();
        hashCode = 37 * hashCode + c;
        
        return hashCode;
    }

    @Override
    public boolean equals(final Object other) {
        if (other == this) {
            return true;
        } else if (other instanceof Microblog) {
            final Microblog her = (Microblog) other;
            final Object name = this.getName();
            if (name == null) {
                if (her.getName() != null) {
                    return false;
                }
            } else if (!name.equals(her.getName())) {
                return false;
            }

            final Object author = this.getAuthor();
            if (author == null) {
                if (her.getAuthor() != null) {
                    return false;
                }
            } else if (!author.equals(her.getAuthor())) {
                return false;
            }

            return true;
        } else {
            return false;
        }
    }
  
}
