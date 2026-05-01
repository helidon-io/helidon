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

package io.helidon.lra.coordinator;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.config.Config;

class ParticipantUriValidator {

    private static final String CONFIG_KEY = "participant-url.validation";
    private static final String ENABLED_KEY = "enabled";
    private static final String ALLOW_LOCAL_ADDRESSES_KEY = "allow-local-addresses";
    private static final String ALLOWED_HOSTS_KEY = "allowed-hosts";
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private final boolean enabled;
    private final boolean allowLocalAddresses;
    private final Set<String> allowedHosts;

    private ParticipantUriValidator(boolean enabled, boolean allowLocalAddresses, Set<String> allowedHosts) {
        this.enabled = enabled;
        this.allowLocalAddresses = allowLocalAddresses;
        this.allowedHosts = allowedHosts;
    }

    static ParticipantUriValidator create(Config config) {
        Config validationConfig = config.get(CONFIG_KEY);
        Set<String> allowedHosts = validationConfig.get(ALLOWED_HOSTS_KEY)
                .asList(String.class)
                .orElse(List.of())
                .stream()
                .map(host -> host.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());

        return new ParticipantUriValidator(
                validationConfig.get(ENABLED_KEY).asBoolean().orElse(true),
                validationConfig.get(ALLOW_LOCAL_ADDRESSES_KEY).asBoolean().orElse(false),
                allowedHosts);
    }

    void validate(URI uri) {
        if (!enabled) {
            return;
        }
        if (uri == null) {
            throw new IllegalArgumentException("Participant URI is missing");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Participant URI must use HTTP or HTTPS");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Participant URI must not include user info");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Participant URI must include a host");
        }
        if (allowedHosts.contains(host.toLowerCase(Locale.ROOT))) {
            return;
        }

        if (!allowLocalAddresses) {
            InetAddress[] addresses;
            try {
                addresses = InetAddress.getAllByName(host);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Participant URI host cannot be resolved", e);
            }

            for (InetAddress address : addresses) {
                byte[] bytes = address.getAddress();
                boolean specialUseIpv4Address = false;
                if (bytes.length == 4) {
                    int first = bytes[0] & 0xff;
                    int second = bytes[1] & 0xff;
                    int third = bytes[2] & 0xff;
                    specialUseIpv4Address =
                            first == 0
                                    || (first == 100 && second >= 64 && second <= 127)
                                    || (first == 192 && second == 0 && (third == 0 || third == 2))
                                    || (first == 192 && second == 88 && third == 99)
                                    || (first == 198 && (second == 18 || second == 19))
                                    || (first == 198 && second == 51 && third == 100)
                                    || (first == 203 && second == 0 && third == 113)
                                    || first >= 240;
                }
                boolean uniqueLocalAddress = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()
                        || specialUseIpv4Address
                        || uniqueLocalAddress) {
                    throw new IllegalArgumentException(
                            "Participant URI host must not resolve to a restricted address");
                }
            }
        }
    }
}
