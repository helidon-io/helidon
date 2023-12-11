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

package io.helidon.common.tls.spi;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.tls.TlsManager;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class TlsManagerProviderTest {

    @Test
    void caching() {
        TlsManager mock = Mockito.mock(TlsManager.class);
        AtomicInteger count = new AtomicInteger();

        // we are using "1" and "2" here abstractly to stand in for Config beans, which would hash properly
        TlsManager manager1 = TlsManagerProvider.getOrCreate("1", (c) -> {
            count.incrementAndGet();
            return mock;
        });
        assertThat(manager1, sameInstance(mock));
        assertThat(count.get(), is(1));

        TlsManager manager2 = TlsManagerProvider.getOrCreate("1", (c) -> {
            count.incrementAndGet();
            return Mockito.mock(TlsManager.class);
        });
        assertThat(manager2, sameInstance(mock));
        assertThat(count.get(), is(1));

        TlsManager manager3 = TlsManagerProvider.getOrCreate("2", (c) -> {
            count.incrementAndGet();
            return Mockito.mock(TlsManager.class);
        });
        assertThat(manager3, notNullValue());
        assertThat(manager3, not(sameInstance(mock)));
        assertThat(count.get(), is(2));
    }

}
