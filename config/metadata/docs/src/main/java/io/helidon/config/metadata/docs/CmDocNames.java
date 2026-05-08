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

package io.helidon.config.metadata.docs;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.lang.model.SourceVersion;

/**
 * Config docs names.
 */
final class CmDocNames {
    private final MessageDigest digest;
    private final Set<String> typeNames = new LinkedHashSet<>();
    private final Set<String> fileNames = new LinkedHashSet<>();
    private final Map<String, String> anchors = new HashMap<>();
    private final Map<String, Set<String>> anchorsByFileName = new HashMap<>();

    CmDocNames(String... initialFileNames) {
        digest = initDigest();
        fileNames.addAll(List.of(initialFileNames));
    }

    void reserveTypeName(String typeName) {
        typeNames.add(typeName);
    }

    String typeName(String typeName) {
        return uniqueName(typeName, typeNames);
    }

    String syntheticTypeName(String path) {
        return typeName(preferredSyntheticTypeName(path));
    }

    String fileName(String name, String extension) {
        var baseName = normalizedFileName(name);
        var candidate = baseName + extension;
        if (fileNames.add(candidate)) {
            return candidate;
        }
        for (int index = 2; ; index++) {
            candidate = baseName + "_" + index + extension;
            if (fileNames.add(candidate)) {
                return candidate;
            }
        }
    }

    String anchor(String fileName, String key, String identity) {
        var anchorKey = "%s#%s#%s".formatted(fileName, key, identity);
        return anchors.computeIfAbsent(anchorKey, ignored -> {
            var base = "a" + hash(anchorKey).substring(0, 8) + "-" + sanitizeAnchor(key);
            var usedAnchors = anchorsByFileName.computeIfAbsent(fileName, ignoredFileName -> new LinkedHashSet<>());
            if (usedAnchors.add(base)) {
                return base;
            }
            for (int index = 2; ; index++) {
                var candidate = base + "-" + index;
                if (usedAnchors.add(candidate)) {
                    return candidate;
                }
            }
        });
    }

    private String hash(String input) {
        return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
    }

    private static String uniqueName(String preferredName, Set<String> usedNames) {
        if (usedNames.add(preferredName)) {
            return preferredName;
        }
        for (int index = 2; ; index++) {
            var candidate = preferredName + index;
            if (usedNames.add(candidate)) {
                return candidate;
            }
        }
    }

    private static String preferredSyntheticTypeName(String path) {
        var segments = Arrays.stream(path.split("\\."))
                .filter(segment -> !segment.isBlank())
                .toList();

        var parents = new ArrayList<String>();
        for (int index = 0; index < Math.max(0, segments.size() - 1); index++) {
            var normalized = normalizePackageSegment(segments.get(index));
            if (!normalized.isBlank()) {
                parents.add(normalized);
            }
        }

        var prefix = new ArrayList<>(List.of("io", "helidon"));
        int repeated = 0;
        while (repeated < prefix.size()
               && repeated < parents.size()
               && prefix.get(repeated).equals(parents.get(repeated))) {
            repeated++;
        }
        prefix.addAll(parents.subList(repeated, parents.size()));

        var simpleName = segments.isEmpty() ? "" : normalizeTypeSegment(segments.getLast());
        if (simpleName.isBlank()) {
            simpleName = "Config";
        } else {
            simpleName += "Config";
        }
        return String.join(".", prefix) + "." + simpleName;
    }

    private static String sanitizeAnchor(String value) {
        var sanitized = value.replaceAll("[^A-Za-z0-9_-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        return sanitized.isBlank() ? "entry" : sanitized;
    }

    private static String normalizePackageSegment(String value) {
        if (wildcard(value)) {
            return "";
        }
        var words = words(value);
        if (words.isEmpty()) {
            return "";
        }
        var result = new StringBuilder(words.getFirst().toLowerCase(Locale.ROOT));
        for (int index = 1; index < words.size(); index++) {
            result.append(capitalize(words.get(index)));
        }
        var identifier = ensureIdentifier(result.toString());
        return SourceVersion.isKeyword(identifier) ? "_" + identifier : identifier;
    }

    private static String normalizeTypeSegment(String value) {
        if (wildcard(value)) {
            return "";
        }
        return ensureIdentifier(words(value).stream()
                .map(CmDocNames::capitalize)
                .reduce("", String::concat));
    }

    private static boolean wildcard(String value) {
        return value.chars().allMatch(ch -> ch == '*');
    }

    private static List<String> words(String value) {
        return Arrays.stream(value.split("[^A-Za-z0-9]+"))
                .filter(word -> !word.isBlank())
                .toList();
    }

    private static String ensureIdentifier(String value) {
        if (value.isBlank()) {
            return value;
        }
        if (Character.isJavaIdentifierStart(value.charAt(0))) {
            return value;
        }
        return "_" + value;
    }

    private static String capitalize(String value) {
        var lowerCase = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lowerCase.charAt(0)) + lowerCase.substring(1);
    }

    private static String normalizedFileName(String value) {
        var normalized = value.replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+", "")
                .replaceAll("_+$", "");
        return normalized.isBlank() ? "config" : normalized;
    }

    private static MessageDigest initDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
