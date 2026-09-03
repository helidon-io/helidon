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

package io.helidon.openapi.v30;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class OpenApiReferenceResolver {
    private final Map<String, Object> document;
    private final Map<String, Object> components;
    private final URI self;
    private final Map<String, IdentityHashMap<Map<String, Object>, Resolution>> componentResolutions = new HashMap<>();
    private final IdentityHashMap<Map<String, Object>, Resolution> referenceChainResolutions = new IdentityHashMap<>();

    private OpenApiReferenceResolver(Map<String, Object> document) {
        this.document = document;
        this.components = object(document.get("components"));
        this.self = self(document.get("$self"));
    }

    static OpenApiReferenceResolver create(Map<String, Object> document) {
        return new OpenApiReferenceResolver(document);
    }

    static boolean isUriReference(String value) {
        try {
            URI.create(value);
            return true;
        } catch (IllegalArgumentException _) {
            return false;
        }
    }

    static boolean hasIpvFutureHost(String value) {
        int authorityStart = authorityStart(value);
        if (authorityStart < 0) {
            return false;
        }
        int authorityEnd = value.length();
        for (int i = authorityStart; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '/' || ch == '?' || ch == '#') {
                authorityEnd = i;
                break;
            }
        }
        int userInfoEnd = value.lastIndexOf('@', authorityEnd - 1);
        int hostStart = userInfoEnd < authorityStart ? authorityStart : userInfoEnd + 1;
        if (hostStart >= authorityEnd || value.charAt(hostStart) != '[') {
            return false;
        }
        int hostEnd = value.indexOf(']', hostStart + 1);
        if (hostEnd < 0 || hostEnd >= authorityEnd || !validPort(value, hostEnd + 1, authorityEnd)) {
            return false;
        }
        return isIpvFuture(value, hostStart + 1, hostEnd);
    }

    Resolution resolveComponent(Map<String, Object> value, String componentType) {
        IdentityHashMap<Map<String, Object>, Resolution> cache = componentResolutions.computeIfAbsent(
                componentType,
                _ -> new IdentityHashMap<>());
        Resolution cached = cache.get(value);
        if (cached != null) {
            return cached;
        }
        Map<String, Object> componentValues = object(components.get(componentType));
        Map<String, Object> current = value;
        List<Map<String, Object>> pending = new ArrayList<>();
        Set<List<String>> visited = new HashSet<>();
        while (true) {
            cached = cache.get(current);
            if (cached != null) {
                return cacheResolution(cache, pending, cached);
            }
            pending.add(current);
            if (!(current.get("$ref") instanceof String ref)) {
                return cacheResolution(cache, pending, new Resolution(Status.RESOLVED, current));
            }
            Reference reference = reference(ref, self);
            if (reference.status() != Status.RESOLVED) {
                return cacheResolution(cache, pending, new Resolution(reference.status(), Map.of()));
            }
            List<String> tokens = reference.tokens();
            if (tokens.size() != 3
                    || !"components".equals(tokens.get(0))
                    || !componentType.equals(tokens.get(1))) {
                return cacheResolution(cache, pending, new Resolution(Status.MISSING, Map.of()));
            }
            if (!visited.add(tokens)) {
                return cacheResolution(cache, pending, new Resolution(Status.CYCLIC, Map.of()));
            }
            Object target = componentValues.get(tokens.get(2));
            if (!(target instanceof Map<?, ?> targetMap)) {
                return cacheResolution(cache, pending, new Resolution(Status.MISSING, Map.of()));
            }
            current = object(targetMap);
        }
    }

    Resolution resolveReference(Map<String, Object> value) {
        if (!(value.get("$ref") instanceof String ref)) {
            return new Resolution(Status.RESOLVED, value);
        }
        Reference reference = reference(ref, self);
        if (reference.status() != Status.RESOLVED) {
            return new Resolution(reference.status(), Map.of());
        }
        Object target = resolve(reference.tokens());
        if (!(target instanceof Map<?, ?> targetMap)) {
            return new Resolution(Status.MISSING, Map.of());
        }
        return new Resolution(Status.RESOLVED, object(targetMap));
    }

    Resolution resolveReferenceChain(Map<String, Object> value) {
        Resolution cached = referenceChainResolutions.get(value);
        if (cached != null) {
            return cached;
        }
        Map<String, Object> current = value;
        List<Map<String, Object>> pending = new ArrayList<>();
        Set<Map<String, Object>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (true) {
            cached = referenceChainResolutions.get(current);
            if (cached != null) {
                return cacheResolution(referenceChainResolutions, pending, cached);
            }
            if (!visited.add(current)) {
                return cacheResolution(referenceChainResolutions,
                                       pending,
                                       new Resolution(Status.CYCLIC, Map.of()));
            }
            pending.add(current);
            Resolution resolution = resolveReference(current);
            if (resolution.status() != Status.RESOLVED || !(current.get("$ref") instanceof String)) {
                return cacheResolution(referenceChainResolutions, pending, resolution);
            }
            current = resolution.value();
        }
    }

    private static int authorityStart(String value) {
        if (value.startsWith("//")) {
            return 2;
        }
        int colon = value.indexOf(':');
        if (colon < 1 || colon + 2 >= value.length()
                || value.charAt(colon + 1) != '/'
                || value.charAt(colon + 2) != '/') {
            return -1;
        }
        if (!isAlpha(value.charAt(0))) {
            return -1;
        }
        for (int i = 1; i < colon; i++) {
            char ch = value.charAt(i);
            if (!isAlpha(ch) && !isDigit(ch) && ch != '+' && ch != '-' && ch != '.') {
                return -1;
            }
        }
        return colon + 3;
    }

    private static boolean validPort(String value, int portStart, int authorityEnd) {
        if (portStart == authorityEnd) {
            return true;
        }
        if (value.charAt(portStart) != ':') {
            return false;
        }
        for (int i = portStart + 1; i < authorityEnd; i++) {
            if (!isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpvFuture(String value, int start, int end) {
        if (end - start < 4 || (value.charAt(start) != 'v' && value.charAt(start) != 'V')) {
            return false;
        }
        int i = start + 1;
        int versionStart = i;
        while (i < end && isHexDigit(value.charAt(i))) {
            i++;
        }
        if (i == versionStart || i >= end - 1 || value.charAt(i++) != '.') {
            return false;
        }
        while (i < end) {
            char ch = value.charAt(i++);
            if (!isAlpha(ch)
                    && !isDigit(ch)
                    && "-._~!$&'()*+,;=:".indexOf(ch) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAlpha(char ch) {
        return ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z';
    }

    private static boolean isDigit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    private static boolean isHexDigit(char ch) {
        return isDigit(ch) || ch >= 'a' && ch <= 'f' || ch >= 'A' && ch <= 'F';
    }

    private static Resolution cacheResolution(IdentityHashMap<Map<String, Object>, Resolution> cache,
                                              List<Map<String, Object>> pending,
                                              Resolution resolution) {
        pending.forEach(value -> cache.put(value, resolution));
        return resolution;
    }

    private static Reference reference(String ref, URI self) {
        URI reference;
        try {
            reference = URI.create(ref);
        } catch (IllegalArgumentException _) {
            return new Reference(Status.MALFORMED, List.of());
        }
        if (!ref.startsWith("#")) {
            if (self == null) {
                return new Reference(Status.EXTERNAL, List.of());
            }
            reference = self.resolve(reference);
            if (!self.equals(documentUri(reference))) {
                return new Reference(Status.EXTERNAL, List.of());
            }
        }
        String fragment = reference.getFragment();
        if (fragment == null) {
            return new Reference(Status.RESOLVED, List.of());
        }
        Pointer pointer = pointerTokens(fragment);
        if (pointer.status() != Status.RESOLVED) {
            return new Reference(pointer.status(), List.of());
        }
        return new Reference(Status.RESOLVED, pointer.tokens());
    }

    private static Pointer pointerTokens(String fragment) {
        if (fragment == null) {
            return new Pointer(Status.MALFORMED, List.of());
        }
        if (fragment.isEmpty()) {
            return new Pointer(Status.RESOLVED, List.of());
        }
        if (fragment.charAt(0) != '/') {
            return new Pointer(Status.MALFORMED, List.of());
        }
        String[] rawTokens = fragment.substring(1).split("/", -1);
        List<String> result = new ArrayList<>(rawTokens.length);
        for (String rawToken : rawTokens) {
            String token = pointerToken(rawToken);
            if (token == null) {
                return new Pointer(Status.MALFORMED, List.of());
            }
            result.add(token);
        }
        return new Pointer(Status.RESOLVED, result);
    }

    private static String pointerToken(String rawToken) {
        StringBuilder result = new StringBuilder(rawToken.length());
        for (int i = 0; i < rawToken.length(); i++) {
            char ch = rawToken.charAt(i);
            if (ch != '~') {
                result.append(ch);
                continue;
            }
            if (++i == rawToken.length()) {
                return null;
            }
            switch (rawToken.charAt(i)) {
            case '0' -> result.append('~');
            case '1' -> result.append('/');
            default -> {
                return null;
            }
            }
        }
        return result.toString();
    }

    private static URI self(Object value) {
        if (!(value instanceof String uri)) {
            return null;
        }
        try {
            return documentUri(URI.create(uri));
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    private static URI documentUri(URI uri) {
        String value = uri.toString();
        int fragment = value.indexOf('#');
        return URI.create(fragment < 0 ? value : value.substring(0, fragment)).normalize();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private static int arrayIndex(String token) {
        if (token.isEmpty()) {
            return -1;
        }
        if (token.charAt(0) == '0' && token.length() != 1) {
            return -1;
        }
        for (int i = 0; i < token.length(); i++) {
            char ch = token.charAt(i);
            if (ch < '0' || ch > '9') {
                return -1;
            }
        }
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException _) {
            return -1;
        }
    }

    private Object resolve(List<String> tokens) {
        Object current = document;
        for (String token : tokens) {
            if (current instanceof Map<?, ?> map) {
                if (!map.containsKey(token)) {
                    return null;
                }
                current = map.get(token);
            } else if (current instanceof List<?> list) {
                int index = arrayIndex(token);
                if (index < 0 || index >= list.size()) {
                    return null;
                }
                current = list.get(index);
            } else {
                return null;
            }
        }
        return current;
    }

    enum Status {
        RESOLVED,
        EXTERNAL,
        MISSING,
        MALFORMED,
        CYCLIC
    }

    record Resolution(Status status, Map<String, Object> value) {
    }

    private record Reference(Status status, List<String> tokens) {
    }

    private record Pointer(Status status, List<String> tokens) {
    }
}
