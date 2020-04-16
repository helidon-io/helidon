/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.tests.apps.bookstore.common;

import javax.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provider for greeting message.
 */
@ApplicationScoped
public class BookStore {

    private final ConcurrentHashMap<String, Book> store = new ConcurrentHashMap<>();

    public BookStore() {
        String size = System.getProperty("bookstore.size");
        if (size != null) {
            System.out.println("BookStore creating " + size +  " books");
            Collection<Book> init = new BookGenerator().apply(Integer.parseInt(size));
            init.forEach(book -> {
                store.put(book.getIsbn(), book);
            });
        }
    }

    public Collection<Book> getAll() {
        return store.values();
    }

    public Book find(String isbn) { return store.get(isbn); }

    public void store(Book book) {
        store.put(book.getIsbn(), book);
    }

    public void remove(String isbn) {
        store.remove(isbn);
    }

    public boolean contains(String isbn) { return store.containsKey(isbn); }

}

