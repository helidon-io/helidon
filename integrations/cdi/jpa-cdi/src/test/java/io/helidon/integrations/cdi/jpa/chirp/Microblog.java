/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Access(value = AccessType.FIELD)
@Entity(name = "Microblog")
@Table(name = "MICROBLOG")
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
        updatable = false
    )
    @ManyToOne(
        optional = false,
        targetEntity = Author.class
    )
    private Author author;

    @Deprecated
    protected Microblog() {
        super();
    }

    public Microblog(final Author author) {
        super();
        this.author = Objects.requireNonNull(author);
    }

    public Integer getId() {
        return this.id;
    }

    public Author getAuthor() {
        return this.author;
    }
  
}
