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

package io.helidon.webserver;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import io.helidon.common.configurable.AllowList;
import io.helidon.common.configurable.AllowListConfig;

final class TrustedProxyMatcher implements Predicate<SocketAddress> {
    private static final Pattern IPV4_LITERAL = Pattern.compile("[0-9]+(?:\\.[0-9]+){0,3}");

    private final AllowList allowed;
    private final AllowList notDenied;
    private final boolean matchNamedScopeAllowed;
    private final boolean matchNamedScopeDenied;

    TrustedProxyMatcher(AllowList trustedProxies) {
        AllowListConfig config = trustedProxies.prototype();
        // An all-digit scope can be either an interface index or a valid platform interface name.
        this.matchNamedScopeAllowed = !config.allowAll()
                && (hasScope(config.allowed())
                || hasScope(config.allowedPrefixes())
                || !config.allowedSuffixes().isEmpty()
                || !config.allowedPatterns().isEmpty()
                || !config.allowedPredicates().isEmpty());
        this.matchNamedScopeDenied = hasScope(config.denied())
                || hasScope(config.deniedPrefixes())
                || !config.deniedSuffixes().isEmpty()
                || !config.deniedPatterns().isEmpty()
                || !config.deniedPredicates().isEmpty();
        var allowedBuilder = AllowListConfig.builder(config)
                .clearDenied()
                .clearDeniedPrefixes()
                .clearDeniedSuffixes()
                .clearDeniedPatterns()
                .clearDeniedPredicates();
        if (config.allowAll()) {
            allowedBuilder.clearAllowed()
                    .clearAllowedPrefixes()
                    .clearAllowedSuffixes()
                    .clearAllowedPatterns()
                    .clearAllowedPredicates();
        } else {
            // Exact IP rules must apply to the peer identity, not to one textual representation.
            allowedBuilder.addAllowed(canonicalAddressAliases(config.allowed()));
        }
        this.allowed = allowedBuilder.build();

        this.notDenied = AllowListConfig.builder(config)
                .allowAll(true)
                .clearAllowed()
                .clearAllowedPrefixes()
                .clearAllowedSuffixes()
                .clearAllowedPatterns()
                .clearAllowedPredicates()
                // Preserve the original exact values while adding canonical IP aliases.
                .addDenied(canonicalAddressAliases(config.denied()))
                .build();
    }

    @Override
    public boolean test(SocketAddress remoteAddress) {
        if (remoteAddress instanceof InetSocketAddress remoteInetAddress) {
            String hostString = remoteInetAddress.getHostString();
            boolean allowedPeer = allowed.test(hostString);
            if (!notDenied.test(hostString)) {
                return false;
            }

            InetAddress inetAddress = remoteInetAddress.getAddress();
            if (inetAddress == null) {
                return allowedPeer;
            }

            String hostAddress = inetAddress.getHostAddress();
            if (!hostString.equals(hostAddress)) {
                if (!allowedPeer) {
                    allowedPeer = allowed.test(hostAddress);
                }
                if (!notDenied.test(hostAddress)) {
                    return false;
                }
            }

            if (inetAddress instanceof Inet6Address ipv6Address
                    && ipv6Address.getScopeId() > 0
                    && (matchNamedScopeDenied
                    || (!allowedPeer && matchNamedScopeAllowed))) {
                try {
                    NetworkInterface scopedInterface = NetworkInterface.getByIndex(ipv6Address.getScopeId());
                    if (scopedInterface == null) {
                        return false;
                    }
                    int scopeIndex = hostAddress.lastIndexOf('%');
                    if (scopeIndex < 0) {
                        return false;
                    }
                    // Numeric scope IDs can change when interfaces are recreated, so resolve the current interface name.
                    String namedScopeAddress = hostAddress.substring(0, scopeIndex + 1) + scopedInterface.getName();
                    if (!namedScopeAddress.equals(hostString)
                            && !namedScopeAddress.equals(hostAddress)) {
                        if (!allowedPeer) {
                            allowedPeer = allowed.test(namedScopeAddress);
                        }
                        if (!notDenied.test(namedScopeAddress)) {
                            return false;
                        }
                    }
                } catch (SocketException e) {
                    return false;
                }
            }
            return allowedPeer;
        }

        if (remoteAddress == null) {
            return false;
        }
        String address = remoteAddress.toString();
        return allowed.test(address) && notDenied.test(address);
    }

    private static boolean hasScope(List<String> values) {
        for (String value : values) {
            if (value.indexOf('%') >= 0) {
                return true;
            }
        }
        return false;
    }

    // Avoid quadratic duplicate scans when listeners have large exact proxy inventories.
    private static List<String> canonicalAddressAliases(List<String> exactValues) {
        List<String> aliases = new ArrayList<>();
        Set<String> known = new HashSet<>(exactValues);
        values:
        for (String exactValue : exactValues) {
            int valueEnd = ipLiteralEnd(exactValue);
            int literalStart = valueEnd < exactValue.length() ? 1 : 0;
            boolean ipv6Literal = exactValue.indexOf(':') >= 0;
            int scopeIndex = exactValue.indexOf('%', literalStart);
            boolean numericScope = scopeIndex >= 0;
            int addressEnd = scopeIndex < 0 ? valueEnd : scopeIndex;
            for (int i = literalStart; ipv6Literal && i < addressEnd; i++) {
                char character = exactValue.charAt(i);
                ipv6Literal = character >= '0' && character <= '9'
                        || character >= 'a' && character <= 'f'
                        || character >= 'A' && character <= 'F'
                        || character == ':'
                        || character == '.';
            }
            if (scopeIndex >= 0) {
                ipv6Literal &= scopeIndex < valueEnd - 1;
                for (int i = scopeIndex + 1; i < valueEnd; i++) {
                    char character = exactValue.charAt(i);
                    numericScope &= character >= '0' && character <= '9';
                }
            }
            if (!ipv6Literal
                    && (!IPV4_LITERAL.matcher(exactValue).matches() || exactValue.length() > 15)) {
                continue;
            }
            try {
                String addressLiteral = exactValue.substring(literalStart, addressEnd);
                byte[] addressBytes;
                if (!ipv6Literal) {
                    String[] components = addressLiteral.split("\\.", -1);
                    long addressValue = 0;
                    for (int i = 0; i < components.length; i++) {
                        long component = Long.parseLong(components[i]);
                        int componentBits = i == components.length - 1 ? 8 * (5 - components.length) : 8;
                        long maxComponent = (1L << componentBits) - 1;
                        if (component > maxComponent) {
                            continue values;
                        }
                        addressValue = addressValue << componentBits | component;
                    }
                    addressBytes = new byte[] {(byte) (addressValue >> 24), (byte) (addressValue >> 16),
                            (byte) (addressValue >> 8), (byte) addressValue};
                } else {
                    int compressionIndex = addressLiteral.indexOf("::");
                    if (compressionIndex != addressLiteral.lastIndexOf("::")) {
                        continue;
                    }
                    String left = compressionIndex < 0 ? addressLiteral : addressLiteral.substring(0, compressionIndex);
                    String right = compressionIndex < 0 ? "" : addressLiteral.substring(compressionIndex + 2);
                    String[] leftSegments = left.isEmpty() ? new String[0] : left.split(":", -1);
                    String[] rightSegments = right.isEmpty() ? new String[0] : right.split(":", -1);
                    String[] segments = new String[leftSegments.length + rightSegments.length];
                    System.arraycopy(leftSegments, 0, segments, 0, leftSegments.length);
                    System.arraycopy(rightSegments, 0, segments, leftSegments.length, rightSegments.length);
                    int[] words = new int[8];
                    int wordCount = 0;
                    int leftWordCount = 0;
                    for (int i = 0; i < segments.length; i++) {
                        String segment = segments[i];
                        if (segment.isEmpty()) {
                            continue values;
                        }
                        if (segment.indexOf('.') < 0) {
                            int word = Integer.parseInt(segment, 16);
                            if (word > 0xffff || wordCount == words.length) {
                                continue values;
                            }
                            words[wordCount++] = word;
                        } else {
                            if (i != segments.length - 1
                                    || !addressLiteral.endsWith(segment)
                                    || segment.length() > 15) {
                                continue values;
                            }
                            String[] ipv4Components = segment.split("\\.", -1);
                            if (ipv4Components.length != 4 || wordCount > words.length - 2) {
                                continue values;
                            }
                            int ipv4Word = 0;
                            for (int j = 0; j < ipv4Components.length; j++) {
                                int component = Integer.parseInt(ipv4Components[j]);
                                if (component > 255) {
                                    continue values;
                                }
                                ipv4Word = ipv4Word << 8 | component;
                                if (j == 1) {
                                    words[wordCount++] = ipv4Word;
                                    ipv4Word = 0;
                                }
                            }
                            words[wordCount++] = ipv4Word;
                        }
                        if (i == leftSegments.length - 1) {
                            leftWordCount = wordCount;
                        }
                    }
                    if (compressionIndex < 0) {
                        if (wordCount != words.length) {
                            continue;
                        }
                        leftWordCount = wordCount;
                    } else if (wordCount >= words.length) {
                        continue;
                    }
                    addressBytes = new byte[16];
                    for (int i = 0; i < leftWordCount; i++) {
                        addressBytes[i * 2] = (byte) (words[i] >> 8);
                        addressBytes[i * 2 + 1] = (byte) words[i];
                    }
                    int rightWordCount = wordCount - leftWordCount;
                    for (int i = 0; i < rightWordCount; i++) {
                        int word = words[leftWordCount + i];
                        int byteIndex = (words.length - rightWordCount + i) * 2;
                        addressBytes[byteIndex] = (byte) (word >> 8);
                        addressBytes[byteIndex + 1] = (byte) word;
                    }
                }
                InetAddress address = InetAddress.getByAddress(addressBytes);
                String alias = address.getHostAddress();
                if (scopeIndex >= 0) {
                    int scopeStart = scopeIndex + 1;
                    String originalScopeAlias = alias + "%" + exactValue.substring(scopeStart, valueEnd);
                    if (known.add(originalScopeAlias)) {
                        aliases.add(originalScopeAlias);
                    }
                    if (!numericScope) {
                        continue;
                    }
                    while (scopeStart < valueEnd - 1 && exactValue.charAt(scopeStart) == '0') {
                        scopeStart++;
                    }
                    if (address instanceof Inet6Address && exactValue.charAt(scopeStart) == '0' && known.add(alias)) {
                        aliases.add(alias);
                    }
                    alias += "%" + exactValue.substring(scopeStart, valueEnd);
                }
                if (known.add(alias)) {
                    aliases.add(alias);
                }
            } catch (NumberFormatException | UnknownHostException e) {
                // Invalid numeric-looking values remain available as their original exact strings.
            }
        }
        return aliases;
    }

    private static int ipLiteralEnd(String value) {
        int lastIndex = value.length() - 1;
        return lastIndex > 0
                && value.charAt(0) == '['
                && value.charAt(lastIndex) == ']'
                ? lastIndex
                : value.length();
    }
}
