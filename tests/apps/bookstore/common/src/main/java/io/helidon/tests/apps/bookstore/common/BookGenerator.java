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

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Class BookGenerator.
 */
public class BookGenerator implements Function<Integer, Collection<Book>> {

    public Collection<Book> apply(Integer n) {
        return IntStream.range(0, n)
                .mapToObj(i -> newBook(i, random(1, 10)))
                .collect(Collectors.toList());
    }

    private Book newBook(int isbn, int authors) {
        Book book = new Book();
        book.setIsbn(Integer.toString(isbn));
        book.setTitle("The hunt for Red October");
        book.setDescription("387 p., hardback");
        book.setSummary("The Soviets' new ballistic-missile submarine is attempting to defect to the United States, " +
                "but the Soviet Atlantic fleet has been ordered to find and destroy her at all costs. " +
                "Can Red October reach the U.S. safely?");
        book.setGenre("spy stories");
        book.setCategory("fiction");
        book.setPublisher("Naval Institute Press");
        book.setCopyright("c1984");
        book.setGenre("spy stories");
        book.setAuthors(IntStream.range(0, authors)
                .mapToObj(i -> {
                    Author author = new Author();
                    author.setFirst("Tom");
                    author.setLast("Clancy");
                    return author;
                }).collect(Collectors.toList()));
        return book;
    }

    private int random(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max);
    }
}
