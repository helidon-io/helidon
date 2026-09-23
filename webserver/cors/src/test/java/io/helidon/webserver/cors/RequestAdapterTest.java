/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.webserver.cors;

import java.util.List;
import java.util.Optional;

import io.helidon.webserver.ServerRequest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestAdapterTest {

    @Test
    void schemeUsesConnectionSecurity() {
        ServerRequest request = mock(ServerRequest.class);
        RequestAdapterSe adapter = new RequestAdapterSe(request);

        when(request.isSecure()).thenReturn(false);
        assertThat(adapter.scheme(), is(Optional.of("http")));

        when(request.isSecure()).thenReturn(true);
        assertThat(adapter.scheme(), is(Optional.of("https")));
    }

    @Test
    void defaultSchemeIsUnknown() {
        CorsSupportBase.RequestAdapter<?> adapter = new LegacyRequestAdapter(mock(ServerRequest.class));
        assertThat(adapter.scheme(), is(Optional.empty()));
    }

    private static final class LegacyRequestAdapter implements CorsSupportBase.RequestAdapter<ServerRequest> {

        private final RequestAdapterSe delegate;

        private LegacyRequestAdapter(ServerRequest request) {
            delegate = new RequestAdapterSe(request);
        }

        @Override
        public String path() {
            return delegate.path();
        }

        @Override
        public Optional<String> firstHeader(String key) {
            return delegate.firstHeader(key);
        }

        @Override
        public boolean headerContainsKey(String key) {
            return delegate.headerContainsKey(key);
        }

        @Override
        public List<String> allHeaders(String key) {
            return delegate.allHeaders(key);
        }

        @Override
        public String method() {
            return delegate.method();
        }

        @Override
        public void next() {
            delegate.next();
        }

        @Override
        public ServerRequest request() {
            return delegate.request();
        }
    }
}
