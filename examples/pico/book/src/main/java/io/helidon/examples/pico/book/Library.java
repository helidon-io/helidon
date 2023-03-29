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

package io.helidon.examples.pico.book;

import java.util.Collection;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

/**
 * Demonstrates constructor injection of various types.
 */
@org.jvnet.hk2.annotations.Service
@jakarta.inject.Singleton
public class Library implements BookHolder {

    private List<Provider<Book>> books;
    private List<BookHolder> bookHolders;
    private ColorWheel colorWheel;

    @Inject
    public Library(
            List<Provider<Book>> books,
            List<BookHolder> bookHolders,
            ColorWheel colorWheel) {
        this.books = books;
        this.bookHolders = bookHolders;
        this.colorWheel = colorWheel;
    }

    @Override
    public Collection<?> getBooks() {
        return books;
    }

    public Collection<BookHolder> getBookHolders() {
        return bookHolders;
    }

    public ColorWheel getColorWheel() {
        return colorWheel;
    }

    @PostConstruct
    public void postConstruct() {
        System.out.println("library is open: " + this);
    }

    @PreDestroy
    public void preDestroy() {
        System.out.println("library is closed: " + this);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(books=" + getBooks() + ")";
    }

}
