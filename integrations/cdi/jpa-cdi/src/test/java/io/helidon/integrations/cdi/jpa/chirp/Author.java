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
import javax.persistence.Table;

@Access(value = AccessType.FIELD)
@Entity(name = "Author")
@Table(name = "AUTHOR")
public class Author implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "ID",
            insertable = true,
            nullable = false,
            updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Basic(optional = false)
    @Column(name = "NAME",
            insertable = true,
            nullable = false,
            unique = true,
            updatable = true)
    private String name;

    @Deprecated
    protected Author() {
        super();
    }
    
    public Author(final String name) {
        super();
        this.setName(name);
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = Objects.requireNonNull(name);
    }

    @Override
    public int hashCode() {
        final Object name = this.getName();
        return name == null ? 0 : name.hashCode();
    }

    @Override
    public boolean equals(final Object other) {
        if (other == this) {
            return true;
        } else if (other instanceof Author) {
            final Author her = (Author) other;
            final Object name = this.getName();
            if (name == null) {
                if (her.getName() != null) {
                    return false;
                }
            } else if (!name.equals(her.getName())) {
                return false;
            }
            return true;
        } else {
            return false;
        }
    }
  
}
