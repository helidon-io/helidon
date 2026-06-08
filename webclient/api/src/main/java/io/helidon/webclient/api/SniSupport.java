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

package io.helidon.webclient.api;

import java.util.List;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;

import io.helidon.common.tls.Tls;
import io.helidon.common.uri.UriAuthority;
import io.helidon.common.uri.UriHost;
import io.helidon.http.ClientRequestHeaders;

final class SniSupport {
    private SniSupport() {
    }

    static Selection resolve(ClientUri uri,
                             SniConfig sni,
                             Tls tls,
                             ClientRequestHeaders headers) {
        if (!tls.enabled()) {
            return cleartext(uri);
        }

        return resolveFirstClass(uri, sni, headers);
    }

    static Selection uriHost(ClientUri uri) {
        UriHost host = normalizedUriHost(uri);
        return selection(host, uri.port());
    }

    static Selection tlsDefault(ClientUri uri, Tls tls) {
        if (!tls.enabled()) {
            return cleartext(uri);
        }
        return new Selection(uri.host(), uri.port(), State.defaults());
    }

    private static Selection cleartext(ClientUri uri) {
        return new Selection(uri.host(), uri.port(), State.disabled());
    }

    private static Selection disabled(ClientUri uri) {
        UriHost host = normalizedUriHost(uri);
        return new Selection(host.value(), uri.port(), State.disabled());
    }

    private static Selection resolveFirstClass(ClientUri uri, SniConfig sni, ClientRequestHeaders headers) {
        SniMode mode = sni.mode();
        return switch (mode) {
        case URI_HOST -> uriHost(uri);
        case HOST_HEADER -> {
            String authority = ClientRequestHeaderSupport.hostAuthority(uri, headers);
            UriAuthority normalized = UriAuthority.create(authority);
            yield selection(normalized.host(), normalized.portOrDefault(uri.port()));
        }
        case EXPLICIT -> selection(normalizedHost(sni.host().orElseThrow()), uri.port());
        case DISABLED -> disabled(uri);
        };
    }

    private static Selection selection(UriHost host, int port) {
        if (host.kind() == UriHost.Kind.DNS) {
            return new Selection(host.value(), port, State.host(host.value()));
        }
        return new Selection(host.value(), port, State.empty(host.value()));
    }

    private static UriHost normalizedUriHost(ClientUri uri) {
        return UriAuthority.create(uri.authority()).host();
    }

    private static UriHost normalizedHost(String host) {
        if (host.startsWith("[")) {
            return UriAuthority.create(host).host();
        }
        return UriHost.create(host);
    }

    static List<SNIServerName> serverNamesOverride(State state) {
        return switch (state.kind()) {
        case DEFAULT -> null;
        case HOST -> List.of(new SNIHostName(state.host()));
        case EMPTY, DISABLED -> List.of();
        };
    }

    record Selection(String tlsPeerHost,
                     int tlsPeerPort,
                     State state) {
    }

    record State(Kind kind, String host) {
        private static final State DEFAULT = new State(Kind.DEFAULT, "");
        private static final State DISABLED = new State(Kind.DISABLED, "");

        static State defaults() {
            return DEFAULT;
        }

        static State host(String host) {
            return new State(Kind.HOST, host);
        }

        static State empty(String host) {
            return new State(Kind.EMPTY, host);
        }

        static State disabled() {
            return DISABLED;
        }
    }

    enum Kind {
        DEFAULT,
        HOST,
        EMPTY,
        DISABLED
    }
}
