/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.http.media.gson;

import io.helidon.common.GenericType;
import io.helidon.common.config.Config;
import io.helidon.http.WritableHeaders;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class GsonSupportTest {

    record Book(String title, int pages) {
    }

    @Test
    void test() {
        var support = GsonSupport.create(Config.empty(), "gson");
        var headers = WritableHeaders.create();
        var type = GenericType.create(Book.class);
        var outputStream = new ByteArrayOutputStream();
        var instance = new Book("some-title", 123);

        support.writer(type, headers)
                .supplier()
                .get()
                .write(type, instance, outputStream, headers);

        assertThat(GsonSupportTestBookTypeAdapterFactory.writeCount.get(), is(1));

        Book sanity = support.reader(type, headers)
                .supplier()
                .get()
                .read(type, new ByteArrayInputStream(outputStream.toByteArray()), headers);

        assertThat(GsonSupportTestBookTypeAdapterFactory.readCount.get(), is(1));

        assertThat(sanity.title(), is("some-title"));
        assertThat(sanity.pages(), is(123));
        assertThat(sanity, is(instance));
    }
}
