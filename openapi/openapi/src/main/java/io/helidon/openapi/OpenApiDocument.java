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

package io.helidon.openapi;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import io.helidon.common.Api;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonBoolean;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonNumber;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;
import io.helidon.json.JsonValue;

/**
 * Version-neutral OpenAPI document model.
 * <p>
 * The model is based on the latest supported OpenAPI shape. Version implementations render this model to their target
 * OpenAPI version, omitting or translating fields that do not exist in that target version.
 */
@Api.Preview
public final class OpenApiDocument {
    private static final Set<String> FIXED_PATH_OPERATION_FIELDS = Set.of("get",
                                                                          "put",
                                                                          "post",
                                                                          "delete",
                                                                          "options",
                                                                          "head",
                                                                          "patch",
                                                                          "trace",
                                                                          "query");

    private final Map<String, Object> node;
    private final String openapi;
    private final String self;
    private final String jsonSchemaDialect;
    private final Info info;
    private final List<Server> servers;
    private final Map<String, PathItem> paths;
    private final Map<String, PathItem> webhooks;
    private final Components components;
    private final List<SecurityRequirement> securityRequirements;
    private final List<Tag> tags;
    private final ExternalDocs externalDocs;

    private OpenApiDocument(Map<String, Object> node) {
        this.node = immutableMap(node);
        this.openapi = stringValue(this.node.get("openapi")).orElse(null);
        this.self = stringValue(this.node.get("$self")).orElse(null);
        this.jsonSchemaDialect = stringValue(this.node.get("jsonSchemaDialect")).orElse(null);
        this.info = objectValue(this.node.get("info"))
                .map(Info::new)
                .orElse(null);
        this.servers = servers(this.node.get("servers"));
        this.paths = pathItems(this.node.get("paths"));
        this.webhooks = pathItems(this.node.get("webhooks"));
        this.components = objectValue(this.node.get("components"))
                .map(Components::new)
                .orElse(null);
        this.securityRequirements = securityRequirements(this.node.get("security"));
        this.tags = tags(this.node.get("tags"));
        this.externalDocs = objectValue(this.node.get("externalDocs"))
                .map(ExternalDocs::new)
                .orElse(null);
    }

    /**
     * Create a new builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * OpenAPI specification version declared by this document.
     *
     * @return OpenAPI version
     */
    public Optional<String> openapi() {
        return Optional.ofNullable(openapi);
    }

    /**
     * Document identity URI.
     *
     * @return document identity URI
     */
    public Optional<String> self() {
        return Optional.ofNullable(self);
    }

    /**
     * JSON Schema dialect URI.
     *
     * @return JSON Schema dialect URI
     */
    public Optional<String> jsonSchemaDialect() {
        return Optional.ofNullable(jsonSchemaDialect);
    }

    /**
     * Info object.
     *
     * @return info object
     */
    public Optional<Info> info() {
        return Optional.ofNullable(info);
    }

    /**
     * Servers.
     *
     * @return servers
     */
    public List<Server> servers() {
        return servers;
    }

    /**
     * Path items keyed by OpenAPI path template.
     *
     * @return path items
     */
    public Map<String, PathItem> paths() {
        return paths;
    }

    /**
     * Webhooks keyed by name.
     *
     * @return webhooks
     */
    public Map<String, PathItem> webhooks() {
        return webhooks;
    }

    /**
     * Components object.
     *
     * @return components object
     */
    public Optional<Components> components() {
        return Optional.ofNullable(components);
    }

    /**
     * Security requirements.
     *
     * @return security requirements
     */
    public List<SecurityRequirement> securityRequirements() {
        return securityRequirements;
    }

    /**
     * Document tags.
     *
     * @return tags
     */
    public List<Tag> tags() {
        return tags;
    }

    /**
     * External documentation.
     *
     * @return external documentation
     */
    public Optional<ExternalDocs> externalDocs() {
        return Optional.ofNullable(externalDocs);
    }

    /**
     * Whether this document has no model content.
     *
     * @return whether this document is empty
     */
    public boolean isEmpty() {
        return node.isEmpty();
    }

    /**
     * Convert this document model to a structured JSON object.
     *
     * @return JSON object representation of this document
     */
    public JsonObject toJsonObject() {
        return jsonObject(node);
    }

    private Map<String, Object> toNode() {
        return node;
    }

    Map<String, Object> mutableNode() {
        return mutableMap(node);
    }

    private static List<Server> servers(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Server> result = new ArrayList<>();
        list.forEach(item -> objectValue(item).ifPresent(node -> result.add(new Server(node))));
        return Collections.unmodifiableList(result);
    }

    private static Map<String, PathItem> pathItems(Object value) {
        return objectValue(value)
                .map(paths -> {
                    Map<String, PathItem> result = new LinkedHashMap<>();
                    paths.forEach((path, pathItem) -> {
                        if (!path.startsWith("x-")) {
                            objectValue(pathItem).ifPresent(node -> result.put(path, new PathItem(path, node)));
                        }
                    });
                    return Collections.unmodifiableMap(result);
                })
                .orElseGet(Map::of);
    }

    private static List<Tag> tags(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Tag> result = new ArrayList<>();
        list.forEach(item -> objectValue(item).ifPresent(node -> result.add(new Tag(node))));
        return Collections.unmodifiableList(result);
    }

    private static List<SecurityRequirement> securityRequirements(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<SecurityRequirement> result = new ArrayList<>();
        list.forEach(item -> objectValue(item).ifPresent(node -> result.add(new SecurityRequirement(node))));
        return Collections.unmodifiableList(result);
    }

    private static Optional<Map<String, Object>> objectValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return Optional.of(result);
        }
        return Optional.empty();
    }

    private static Optional<String> stringValue(Object value) {
        return value instanceof String string ? Optional.of(string) : Optional.empty();
    }

    private static Optional<Boolean> booleanValue(Object value) {
        return value instanceof Boolean bool ? Optional.of(bool) : Optional.empty();
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, immutableValue(value)));
        return Collections.unmodifiableMap(result);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), immutableValue(item)));
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            list.forEach(item -> result.add(immutableValue(item)));
            return Collections.unmodifiableList(result);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    static void merge(Map<String, Object> target, Map<String, Object> source, String path) {
        merge(target, source, path, null);
    }

    @SuppressWarnings("unchecked")
    private static void merge(Map<String, Object> target,
                              Map<String, Object> source,
                              String path,
                              Map<String, String> pathTemplates) {
        source.forEach((key, value) -> {
            String childPath = path.isEmpty() ? key : path + "." + key;
            boolean existingKey = target.containsKey(key);
            Object existing = target.get(key);
            if (!existingKey) {
                target.put(key, value);
                indexPathItems(templateIndex(childPath, pathTemplates), value);
            } else if (("paths".equals(childPath) || "webhooks".equals(childPath))
                    && existing instanceof Map<?, ?> existingMap
                    && value instanceof Map<?, ?> valueMap) {
                mergePathItems((Map<String, Object>) existingMap,
                               (Map<String, Object>) valueMap,
                               childPath,
                               templateIndex(childPath, pathTemplates));
            } else if (mergeableTopLevelArray(childPath)
                    && existing instanceof List<?> existingList
                    && value instanceof List<?> valueList) {
                mergeArray((List<Object>) existingList, valueList, childPath);
            } else if (existing instanceof Map<?, ?> existingMap && value instanceof Map<?, ?> valueMap) {
                merge((Map<String, Object>) existingMap,
                      (Map<String, Object>) valueMap,
                      childPath,
                      pathTemplates);
            } else if (!Objects.equals(existing, value)) {
                throw new IllegalStateException("Conflicting OpenAPI document value at " + childPath);
            }
        });
    }

    private static boolean mergeableTopLevelArray(String path) {
        return "tags".equals(path) || "security".equals(path);
    }

    private static void mergeArray(List<Object> target, List<?> source, String path) {
        for (Object item : source) {
            if ("tags".equals(path)) {
                mergeTag(target, item);
            } else if (!target.contains(item)) {
                target.add(item);
            }
        }
    }

    private static void mergeTag(List<Object> target, Object source) {
        Optional<String> sourceName = tagName(source);
        if (sourceName.isEmpty()) {
            if (!target.contains(source)) {
                target.add(source);
            }
            return;
        }

        for (Object existing : target) {
            if (sourceName.equals(tagName(existing))) {
                if (!Objects.equals(existing, source)) {
                    throw new IllegalStateException("Conflicting OpenAPI tag at tags." + sourceName.get());
                }
                return;
            }
        }
        target.add(source);
    }

    private static Optional<String> tagName(Object tag) {
        if (tag instanceof Map<?, ?> map) {
            return Optional.ofNullable(map.get("name")).map(String::valueOf);
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static void mergePathItems(Map<String, Object> target,
                                       Map<String, Object> source,
                                       String fieldName,
                                       Map<String, String> pathTemplates) {
        source.forEach((path, value) -> {
            String existingPathTemplate = equivalentPathTemplate(target, path, pathTemplates);
            if (existingPathTemplate == null) {
                target.put(path, value);
                indexPathTemplate(pathTemplates, path);
                return;
            }
            Object existing = target.get(existingPathTemplate);
            if (!existingPathTemplate.equals(path)) {
                throw new IllegalStateException("Conflicting OpenAPI path template at " + fieldName + "."
                                                        + existingPathTemplate + " and " + fieldName + "." + path);
            }
            if (existing == null || value == null) {
                if (!Objects.equals(existing, value)) {
                    throw new IllegalStateException("Conflicting OpenAPI document value at " + fieldName + "." + path);
                }
                return;
            }
            if (!(existing instanceof Map<?, ?> existingPath) || !(value instanceof Map<?, ?> sourcePath)) {
                throw new IllegalStateException("Conflicting OpenAPI document value at " + fieldName + "." + path);
            }
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) sourcePath).entrySet()) {
                String methodOrField = entry.getKey();
                if (isFixedPathOperationField(methodOrField)
                        && existingPath.containsKey(methodOrField)) {
                    throw new IllegalStateException("Conflicting OpenAPI operation at " + fieldName + "." + path + "."
                                                            + methodOrField);
                }
                if ("additionalOperations".equals(methodOrField)) {
                    mergeAdditionalOperations((Map<String, Object>) existingPath, entry.getValue(), fieldName, path);
                    continue;
                }
                Map<String, Object> operation = new LinkedHashMap<>();
                operation.put(methodOrField, entry.getValue());
                merge((Map<String, Object>) existingPath, operation, fieldName + "." + path);
            }
        });
    }

    private static String equivalentPathTemplate(Map<String, Object> target,
                                                 String path,
                                                 Map<String, String> pathTemplates) {
        if (target.containsKey(path)) {
            return path;
        }
        if (pathTemplates == null) {
            return null;
        }
        String normalizedPath = normalizedPathTemplate(path);
        return pathTemplates.get(normalizedPath);
    }

    private static Map<String, String> templateIndex(String fieldName,
                                                     Map<String, String> pathTemplates) {
        return switch (fieldName) {
            case "paths" -> pathTemplates;
            default -> null;
        };
    }

    private static void indexPathItems(Map<String, String> pathTemplates, Object value) {
        if (pathTemplates == null || !(value instanceof Map<?, ?> pathItems)) {
            return;
        }
        pathItems.keySet()
                .stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .forEach(path -> indexPathTemplate(pathTemplates, path));
    }

    private static void indexPathTemplate(Map<String, String> pathTemplates, String path) {
        if (pathTemplates != null) {
            pathTemplates.putIfAbsent(normalizedPathTemplate(path), path);
        }
    }

    private static String normalizedPathTemplate(String path) {
        StringBuilder result = new StringBuilder(path.length());
        boolean inTemplate = false;
        for (int i = 0; i < path.length(); i++) {
            char current = path.charAt(i);
            if (current == '{') {
                inTemplate = true;
                result.append("{}");
            } else if (current == '}') {
                inTemplate = false;
            } else if (!inTemplate) {
                result.append(current);
            }
        }
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    private static void mergeAdditionalOperations(Map<String, Object> targetPath,
                                                  Object sourceValue,
                                                  String fieldName,
                                                  String path) {
        if (!(sourceValue instanceof Map<?, ?> sourceOperations)) {
            Map<String, Object> additionalOperations = new LinkedHashMap<>();
            additionalOperations.put("additionalOperations", sourceValue);
            merge(targetPath, additionalOperations, fieldName + "." + path);
            return;
        }

        boolean existingKey = targetPath.containsKey("additionalOperations");
        Object existing = targetPath.get("additionalOperations");
        if (!existingKey) {
            targetPath.put("additionalOperations", sourceValue);
            return;
        }
        if (existing == null) {
            throw new IllegalStateException("Conflicting OpenAPI document value at " + fieldName + "." + path
                                                    + ".additionalOperations");
        }
        if (!(existing instanceof Map<?, ?> existingOperations)) {
            throw new IllegalStateException("Conflicting OpenAPI document value at " + fieldName + "." + path
                                                    + ".additionalOperations");
        }

        sourceOperations.forEach((method, operation) -> {
            String methodName = String.valueOf(method);
            if (existingOperations.containsKey(methodName)) {
                throw new IllegalStateException("Conflicting OpenAPI operation at " + fieldName + "." + path
                                                        + ".additionalOperations." + methodName);
            }
            ((Map<String, Object>) existingOperations).put(methodName, operation);
        });
    }

    private static Map<String, Object> mutableMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, mutableValue(value)));
        return result;
    }

    private static Object mutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), mutableValue(item)));
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            list.forEach(item -> result.add(mutableValue(item)));
            return result;
        }
        return value;
    }

    private static boolean isFixedPathOperationField(String value) {
        return FIXED_PATH_OPERATION_FIELDS.contains(value);
    }

    private static String fixedPathOperationField(String method) {
        String field = method.toLowerCase(Locale.ROOT);
        return isFixedPathOperationField(field) ? field : null;
    }

    private static void extension(Map<String, Object> node, String name, JsonValue value) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(value);
        if (!name.startsWith("x-")) {
            throw new IllegalArgumentException("OpenAPI extension names must start with x-: " + name);
        }
        node.put(name, jsonValue(value));
    }

    private static void requireString(Map<String, Object> node, String field, String objectName) {
        if (stringValue(node.get(field)).filter(it -> !it.isBlank()).isEmpty()) {
            throw new IllegalStateException(objectName + " requires " + field);
        }
    }

    private static void requireValue(Map<String, Object> node, String field, String objectName) {
        if (!node.containsKey(field) || node.get(field) == null) {
            throw new IllegalStateException(objectName + " requires " + field);
        }
    }

    private static boolean isReference(Map<String, Object> node) {
        return node.containsKey("$ref");
    }

    /**
     * OpenAPI Info Object.
     */
    @Api.Preview
    public static final class Info {
        private final Map<String, Object> node;
        private final String title;
        private final String version;
        private final Contact contact;
        private final License license;

        private Info(Map<String, Object> node) {
            this.node = immutableMap(node);
            this.title = stringValue(this.node.get("title")).orElse("");
            this.version = stringValue(this.node.get("version")).orElse("");
            this.contact = objectValue(this.node.get("contact")).map(Contact::new).orElse(null);
            this.license = objectValue(this.node.get("license")).map(License::new).orElse(null);
        }

        /**
         * Create a new info builder.
         *
         * @return builder
         */
        public static InfoBuilder builder() {
            return new InfoBuilder();
        }

        /**
         * API title.
         *
         * @return title
         */
        public String title() {
            return title;
        }

        /**
         * API version.
         *
         * @return version
         */
        public String version() {
            return version;
        }

        /**
         * API summary.
         *
         * @return summary
         */
        public Optional<String> summary() {
            return stringValue(node.get("summary"));
        }

        /**
         * API description.
         *
         * @return description
         */
        public Optional<String> description() {
            return stringValue(node.get("description"));
        }

        /**
         * API terms of service.
         *
         * @return terms of service
         */
        public Optional<String> termsOfService() {
            return stringValue(node.get("termsOfService"));
        }

        /**
         * Contact.
         *
         * @return contact
         */
        public Optional<Contact> contact() {
            return Optional.ofNullable(contact);
        }

        /**
         * License.
         *
         * @return license
         */
        public Optional<License> license() {
            return Optional.ofNullable(license);
        }

        private Map<String, Object> toNode() {
            return node;
        }
    }

    /**
     * OpenAPI info builder.
     */
    @Api.Preview
    public static final class InfoBuilder implements io.helidon.common.Builder<InfoBuilder, Info> {
        private final Map<String, Object> node = new LinkedHashMap<>();

        private InfoBuilder() {
        }

        /**
         * Set title.
         *
         * @param title title
         * @return updated builder
         */
        public InfoBuilder title(String title) {
            node.put("title", Objects.requireNonNull(title));
            return this;
        }

        /**
         * Set version.
         *
         * @param version version
         * @return updated builder
         */
        public InfoBuilder version(String version) {
            node.put("version", Objects.requireNonNull(version));
            return this;
        }

        /**
         * Set summary.
         *
         * @param summary summary
         * @return updated builder
         */
        public InfoBuilder summary(String summary) {
            node.put("summary", Objects.requireNonNull(summary));
            return this;
        }

        /**
         * Set description.
         *
         * @param description description
         * @return updated builder
         */
        public InfoBuilder description(String description) {
            node.put("description", Objects.requireNonNull(description));
            return this;
        }

        /**
         * Set terms of service.
         *
         * @param termsOfService terms of service
         * @return updated builder
         */
        public InfoBuilder termsOfService(String termsOfService) {
            node.put("termsOfService", Objects.requireNonNull(termsOfService));
            return this;
        }

        /**
         * Set contact.
         *
         * @param contact contact
         * @return updated builder
         */
        public InfoBuilder contact(Contact contact) {
            node.put("contact", Objects.requireNonNull(contact).toNode());
            return this;
        }

        /**
         * Set contact.
         *
         * @param contact consumer to update contact builder
         * @return updated builder
         */
        public InfoBuilder contact(Consumer<ContactBuilder> contact) {
            ContactBuilder builder = Contact.builder();
            contact.accept(builder);
            return contact(builder.build());
        }

        /**
         * Set license.
         *
         * @param license license
         * @return updated builder
         */
        public InfoBuilder license(License license) {
            node.put("license", Objects.requireNonNull(license).toNode());
            return this;
        }

        /**
         * Set license.
         *
         * @param license consumer to update license builder
         * @return updated builder
         */
        public InfoBuilder license(Consumer<LicenseBuilder> license) {
            LicenseBuilder builder = License.builder();
            license.accept(builder);
            return license(builder.build());
        }

        /**
         * Add an extension.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public InfoBuilder extension(String name, JsonValue value) {
            OpenApiDocument.extension(node, name, value);
            return this;
        }

        @Override
        public Info build() {
            requireString(node, "title", "OpenAPI Info");
            requireString(node, "version", "OpenAPI Info");
            return new Info(node);
        }
    }

    /**
     * OpenAPI Contact Object.
     */
    @Api.Preview
    public static final class Contact {
        private final Map<String, Object> node;

        private Contact(Map<String, Object> node) {
            this.node = immutableMap(node);
        }

        /**
         * Create a new contact builder.
         *
         * @return builder
         */
        public static ContactBuilder builder() {
            return new ContactBuilder();
        }

        private Map<String, Object> toNode() {
            return node;
        }
    }

    /**
     * OpenAPI contact builder.
     */
    @Api.Preview
    public static final class ContactBuilder implements io.helidon.common.Builder<ContactBuilder, Contact> {
        private final Map<String, Object> node = new LinkedHashMap<>();

        private ContactBuilder() {
        }

        /**
         * Set name.
         *
         * @param name name
         * @return updated builder
         */
        public ContactBuilder name(String name) {
            node.put("name", Objects.requireNonNull(name));
            return this;
        }

        /**
         * Set URL.
         *
         * @param url URL
         * @return updated builder
         */
        public ContactBuilder url(String url) {
            node.put("url", Objects.requireNonNull(url));
            return this;
        }

        /**
         * Set email.
         *
         * @param email email
         * @return updated builder
         */
        public ContactBuilder email(String email) {
            node.put("email", Objects.requireNonNull(email));
            return this;
        }

        /**
         * Add an extension.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public ContactBuilder extension(String name, JsonValue value) {
            OpenApiDocument.extension(node, name, value);
            return this;
        }

        @Override
        public Contact build() {
            return new Contact(node);
        }
    }

    /**
     * OpenAPI License Object.
     */
    @Api.Preview
    public static final class License {
        private final Map<String, Object> node;

        private License(Map<String, Object> node) {
            this.node = immutableMap(node);
        }

        /**
         * Create a new license builder.
         *
         * @return builder
         */
        public static LicenseBuilder builder() {
            return new LicenseBuilder();
        }

        private Map<String, Object> toNode() {
            return node;
        }
    }

    /**
     * OpenAPI license builder.
     */
    @Api.Preview
    public static final class LicenseBuilder implements io.helidon.common.Builder<LicenseBuilder, License> {
        private final Map<String, Object> node = new LinkedHashMap<>();

        private LicenseBuilder() {
        }

        /**
         * Set license name.
         *
         * @param name license name
         * @return updated builder
         */
        public LicenseBuilder name(String name) {
            node.put("name", Objects.requireNonNull(name));
            return this;
        }

        /**
         * Set license identifier.
         *
         * @param identifier SPDX license identifier
         * @return updated builder
         */
        public LicenseBuilder identifier(String identifier) {
            node.put("identifier", Objects.requireNonNull(identifier));
            return this;
        }

        /**
         * Set license URL.
         *
         * @param url license URL
         * @return updated builder
         */
        public LicenseBuilder url(String url) {
            node.put("url", Objects.requireNonNull(url));
            return this;
        }

        /**
         * Add an extension.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public LicenseBuilder extension(String name, JsonValue value) {
            OpenApiDocument.extension(node, name, value);
            return this;
        }

        @Override
        public License build() {
            requireString(node, "name", "OpenAPI License");
            return new License(node);
        }
    }

    /**
     * OpenAPI External Documentation Object.
     */
    @Api.Preview
    public static final class ExternalDocs {
        private final Map<String, Object> node;

        private ExternalDocs(Map<String, Object> node) {
            this.node = immutableMap(node);
        }

        /**
         * Create a new external documentation builder.
         *
         * @return builder
         */
        public static ExternalDocsBuilder builder() {
            return new ExternalDocsBuilder();
        }

        private Map<String, Object> toNode() {
            return node;
        }
    }

    /**
     * OpenAPI external documentation builder.
     */
    @Api.Preview
    public static final class ExternalDocsBuilder implements io.helidon.common.Builder<ExternalDocsBuilder, ExternalDocs> {
        private final Map<String, Object> node = new LinkedHashMap<>();

        private ExternalDocsBuilder() {
        }

        /**
         * Set URL.
         *
         * @param url URL
         * @return updated builder
         */
        public ExternalDocsBuilder url(String url) {
            node.put("url", Objects.requireNonNull(url));
            return this;
        }

        /**
         * Set description.
         *
         * @param description description
         * @return updated builder
         */
        public ExternalDocsBuilder description(String description) {
            node.put("description", Objects.requireNonNull(description));
            return this;
        }

        /**
         * Add an extension.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public ExternalDocsBuilder extension(String name, JsonValue value) {
            OpenApiDocument.extension(node, name, value);
            return this;
        }

        @Override
        public ExternalDocs build() {
            requireString(node, "url", "OpenAPI ExternalDocs");
            return new ExternalDocs(node);
        }
    }

    /**
     * OpenAPI Server Object.
     */
    @Api.Preview
    public static final class Server {
        private final Map<String, Object> node;
        private final Map<String, ServerVariable> variables;

        private Server(Map<String, Object> node) {
            this.node = immutableMap(node);
            this.variables = objectValue(this.node.get("variables"))
                    .map(values -> {
                        Map<String, ServerVariable> result = new LinkedHashMap<>();
                        values.forEach((name, value) -> objectValue(value)
                                .ifPresent(item -> result.put(name, new ServerVariable(item))));
                        return Collections.unmodifiableMap(result);
                    })
                    .orElseGet(Map::of);
        }

        /**
         * Create a new server builder.
         *
         * @return builder
         */
        public static ServerBuilder builder() {
            return new ServerBuilder();
        }

        /**
         * Server variables.
         *
         * @return server variables
         */
        public Map<String, ServerVariable> variables() {
            return variables;
        }

        private Map<String, Object> toNode() {
            return node;
        }
    }

    /**
     * OpenAPI server builder.
     */
    @Api.Preview
    public static final class ServerBuilder implements io.helidon.common.Builder<ServerBuilder, Server> {
        private final Map<String, Object> node = new LinkedHashMap<>();

        private ServerBuilder() {
        }

        /**
         * Set server URL.
         *
         * @param url URL
         * @return updated builder
         */
        public ServerBuilder url(String url) {
            node.put("url", Objects.requireNonNull(url));
            return this;
        }

        /**
         * Set server description.
         *
         * @param description description
         * @return updated builder
         */
        public ServerBuilder description(String description) {
            node.put("description", Objects.requireNonNull(description));
            return this;
        }

        /**
         * Set server name.
         *
         * @param name server name
         * @return updated builder
         */
        public ServerBuilder name(String name) {
            node.put("name", Objects.requireNonNull(name));
            return this;
        }

        /**
         * Add a server variable.
         *
         * @param name variable name
         * @param variable variable
         * @return updated builder
         */
        public ServerBuilder variable(String name, ServerVariable variable) {
            object(node, "variables").put(Objects.requireNonNull(name), Objects.requireNonNull(variable).toNode());
            return this;
        }

        /**
         * Add a server variable.
         *
         * @param name variable name
         * @param variable consumer to update server variable builder
         * @return updated builder
         */
        public ServerBuilder variable(String name, Consumer<ServerVariableBuilder> variable) {
            ServerVariableBuilder builder = ServerVariable.builder();
            variable.accept(builder);
            return variable(name, builder.build());
        }

        /**
         * Add an extension.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public ServerBuilder extension(String name, JsonValue value) {
            OpenApiDocument.extension(node, name, value);
            return this;
        }

        @Override
        public Server build() {
            requireString(node, "url", "OpenAPI Server");
            return new Server(node);
        }
    }

    /**
     * OpenAPI Server Variable Object.
     */
    @Api.Preview
    public static final class ServerVariable {
        private final Map<String, Object> node;

        private ServerVariable(Map<String, Object> node) {
            this.node = immutableMap(node);
        }

        /**
         * Create a new server variable builder.
         *
         * @return builder
         */
        public static ServerVariableBuilder builder() {
            return new ServerVariableBuilder();
        }

        private Map<String, Object> toNode() {
            return node;
        }
    }

    /**
     * OpenAPI server variable builder.
     */
    @Api.Preview
    public static final class ServerVariableBuilder implements io.helidon.common.Builder<ServerVariableBuilder, ServerVariable> {
        private final Map<String, Object> node = new LinkedHashMap<>();

        private ServerVariableBuilder() {
        }

        /**
         * Set default value.
         *
         * @param value default value
         * @return updated builder
         */
        public ServerVariableBuilder value(String value) {
            node.put("default", Objects.requireNonNull(value));
            return this;
        }

        /**
         * Set allowed values.
         *
         * @param values allowed values
         * @return updated builder
         */
        public ServerVariableBuilder allowedValues(List<String> values) {
            node.put("enum", List.copyOf(values));
            return this;
        }

        /**
         * Set description.
         *
         * @param description description
         * @return updated builder
         */
        public ServerVariableBuilder description(String description) {
            node.put("description", Objects.requireNonNull(description));
            return this;
        }

        /**
         * Add an extension.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public ServerVariableBuilder extension(String name, JsonValue value) {
            OpenApiDocument.extension(node, name, value);
            return this;
        }

        @Override
        public ServerVariable build() {
            requireString(node, "default", "OpenAPI ServerVariable");
            return new ServerVariable(node);
        }
    }

    /**
     * OpenAPI Tag Object.
     */
    @Api.Preview
    public static final class Tag {
        private final Map<String, Object> node;
        private final String name;

        private Tag(Map<String, Object> node) {
            this.node = immutableMap(node);
            this.name = stringValue(this.node.get("name")).orElse("");
        }

        /**
         * Create a new tag builder.
         *
         * @return builder
         */
        public static TagBuilder builder() {
            return new TagBuilder();
        }

        /**
         * Tag name.
         *
         * @return name
         */
        public String name() {
            return name;
        }

        /**
         * Tag description.
         *
         * @return description
         */
        public Optional<String> description() {
            return stringValue(node.get("description"));
        }

        private Map<String, Object> toNode() {
            return node;
        }
    }

    /**
     * OpenAPI tag builder.
     */
    @Api.Preview
    public static final class TagBuilder implements io.helidon.common.Builder<TagBuilder, Tag> {
        private final Map<String, Object> node = new LinkedHashMap<>();

        private TagBuilder() {
        }

        /**
         * Set tag name.
         *
         * @param name tag name
         * @return updated builder
         */
        public TagBuilder name(String name) {
            node.put("name", Objects.requireNonNull(name));
            return this;
        }

        /**
         * Set tag description.
         *
         * @param description description
         * @return updated builder
         */
        public TagBuilder description(String description) {
            if (!description.isBlank()) {
                node.put("description", description);
            }
            return this;
        }

        /**
         * Set tag summary.
         *
         * @param summary summary
         * @return updated builder
         */
        public TagBuilder summary(String summary) {
            node.put("summary", Objects.requireNonNull(summary));
            return this;
        }

        /**
         * Set external documentation.
         *
         * @param externalDocs external documentation
         * @return updated builder
         */
        public TagBuilder externalDocs(ExternalDocs externalDocs) {
            node.put("externalDocs", Objects.requireNonNull(externalDocs).toNode());
            return this;
        }

        /**
         * Set external documentation.
         *
         * @param externalDocs consumer to update external documentation builder
         * @return updated builder
         */
        public TagBuilder externalDocs(Consumer<ExternalDocsBuilder> externalDocs) {
            ExternalDocsBuilder builder = ExternalDocs.builder();
            externalDocs.accept(builder);
            return externalDocs(builder.build());
        }

        /**
         * Set tag kind.
         *
         * @param kind kind
         * @return updated builder
         */
        public TagBuilder kind(String kind) {
            node.put("kind", Objects.requireNonNull(kind));
            return this;
        }

        /**
         * Set tag parent.
         *
         * @param parent parent tag
         * @return updated builder
         */
        public TagBuilder parent(String parent) {
            node.put("parent", Objects.requireNonNull(parent));
            return this;
        }

        /**
         * Add an extension.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public TagBuilder extension(String name, JsonValue value) {
            OpenApiDocument.extension(node, name, value);
            return this;
        }

        @Override
        public Tag build() {
            requireString(node, "name", "OpenAPI Tag");
            return new Tag(node);
        }
    }

    /**
     * OpenAPI Path Item Object.
     */
    @Api.Preview
    public static final class PathItem {
        private final String name;
        private final Map<String, Object> node;
        private final Map<String, Operation> operations;
        private final Map<String, Operation> additionalOperations;
        private final List<Parameter> parameters;

        private PathItem(String name, Map<String, Object> node) {
            this.name = name;
            this.node = immutableMap(node);
            this.operations = operations(this.node);
            this.additionalOperations = additionalOperations(this.node.get("additionalOperations"));
            this.parameters = parameterList(this.node.get("parameters"));
        }

        /**
         * Create a new path item builder.
         *
         * @return builder
         */
        public static PathItemBuilder builder() {
            return new PathItemBuilder();
        }

        /**
         * OpenAPI path template or webhook name.
         *
         * @return path template or webhook name
         */
        public String name() {
            return name;
        }

        /**
         * Operations keyed by lowercase HTTP method or {@code query}.
         *
         * @return operations
         */
        public Map<String, Operation> operations() {
            return operations;
        }

        /**
         * Additional operations keyed by method name.
         *
         * @return additional operations
         */
        public Map<String, Operation> additionalOperations() {
            return additionalOperations;
        }

        /**
         * Common parameters.
         *
         * @return common parameters
         */
        public List<Parameter> parameters() {
            return parameters;
        }

        private Map<String, Object> toNode() {
            return node;
        }

        private static Map<String, Operation> operations(Map<String, Object> node) {
            Map<String, Operation> result = new LinkedHashMap<>();
            node.forEach((key, value) -> {
                if (isFixedPathOperationField(key)) {
                    objectValue(value).ifPresent(operation -> result.put(key, new Operation(operation)));
                }
            });
            return Collections.unmodifiableMap(result);
        }

        private static Map<String, Operation> additionalOperations(Object value) {
            return objectValue(value)
                    .map(operations -> {
                        Map<String, Operation> result = new LinkedHashMap<>();
                        operations.forEach((key, operation) -> objectValue(operation)
                                .ifPresent(node -> result.put(key, new Operation(node))));
                        return Collections.unmodifiableMap(result);
                    })
                    .orElseGet(Map::of);
        }
    }

    /**
     * OpenAPI path item builder.
     */
    @Api.Preview
    public static final class PathItemBuilder implements io.helidon.common.Builder<PathItemBuilder, PathItem> {
        private final Map<String, Object> node = new LinkedHashMap<>();

        private PathItemBuilder() {
        }

        /**
         * Set reference.
         *
         * @param ref reference
         * @return updated builder
         */
        public PathItemBuilder ref(String ref) {
            node.put("$ref", Objects.requireNonNull(ref));
            return this;
        }

        /**
         * Set summary.
         *
         * @param summary summary
         * @return updated builder
         */
        public PathItemBuilder summary(String summary) {
            node.put("summary", Objects.requireNonNull(summary));
            return this;
        }

        /**
         * Set description.
         *
         * @param description description
         * @return updated builder
         */
        public PathItemBuilder description(String description) {
            node.put("description", Objects.requireNonNull(description));
            return this;
        }

        /**
         * Add an HTTP operation.
         *
         * @param method HTTP method
         * @param operation operation
         * @return updated builder
         */
        public PathItemBuilder operation(String method, Operation operation) {
            String methodName = Objects.requireNonNull(method);
            String fixedField = fixedPathOperationField(methodName);
            if (fixedField == null) {
                return additionalOperation(methodName, operation);
            }
            failIfOperationExists(fixedField);
            node.put(fixedField, Objects.requireNonNull(operation).toNode());
            return this;
        }

        /**
         * Add an HTTP operation.
         *
         * @param method HTTP method
         * @param operation consumer to update operation builder
         * @return updated builder
         */
        public PathItemBuilder operation(String method, Consumer<OperationBuilder> operation) {
            OperationBuilder builder = Operation.builder();
            operation.accept(builder);
            return operation(method, builder.build());
        }

        /**
         * Set the OpenAPI 3.2 query operation.
         *
         * @param operation query operation
         * @return updated builder
         */
        public PathItemBuilder query(Operation operation) {
            failIfOperationExists("query");
            node.put("query", Objects.requireNonNull(operation).toNode());
            return this;
        }

        /**
         * Set the OpenAPI 3.2 query operation.
         *
         * @param operation consumer to update operation builder
         * @return updated builder
         */
        public PathItemBuilder query(Consumer<OperationBuilder> operation) {
            OperationBuilder builder = Operation.builder();
            operation.accept(builder);
            return query(builder.build());
        }

        /**
         * Add an OpenAPI 3.2 additional operation.
         *
         * @param method method name
         * @param operation operation
         * @return updated builder
         */
        public PathItemBuilder additionalOperation(String method, Operation operation) {
            String methodName = Objects.requireNonNull(method);
            Map<String, Object> operations = object(node, "additionalOperations");
            if (operations.containsKey(methodName)) {
                throw new IllegalStateException("Conflicting OpenAPI operation: additionalOperations." + methodName);
            }
            operations.put(methodName, Objects.requireNonNull(operation).toNode());
            return this;
        }

        /**
         * Add an OpenAPI 3.2 additional operation.
         *
         * @param method method name
         * @param operation consumer to update operation builder
         * @return updated builder
         */
        public PathItemBuilder additionalOperation(String method, Consumer<OperationBuilder> operation) {
            OperationBuilder builder = Operation.builder();
            operation.accept(builder);
            return additionalOperation(method, builder.build());
        }

        /**
         * Add server.
         *
         * @param server server
         * @return updated builder
         */
        public PathItemBuilder server(Server server) {
            array(node, "servers").add(Objects.requireNonNull(server).toNode());
            return this;
        }

        /**
         * Add server.
         *
         * @param server consumer to update server builder
         * @return updated builder
         */
        public PathItemBuilder server(Consumer<ServerBuilder> server) {
            ServerBuilder builder = Server.builder();
            server.accept(builder);
            return server(builder.build());
        }

        /**
         * Add common parameter.
         *
         * @param parameter parameter
         * @return updated builder
         */
        public PathItemBuilder parameter(Parameter parameter) {
            array(node, "parameters").add(Objects.requireNonNull(parameter).toNode());
            return this;
        }

        /**
         * Add common parameter.
         *
         * @param parameter consumer to update parameter builder
         * @return updated builder
         */
        public PathItemBuilder parameter(Consumer<ParameterBuilder> parameter) {
            ParameterBuilder builder = Parameter.builder();
            parameter.accept(builder);
            return parameter(builder.build());
        }

        /**
         * Add an extension.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public PathItemBuilder extension(String name, JsonValue value) {
            OpenApiDocument.extension(node, name, value);
            return this;
        }

        @Override
        public PathItem build() {
            return new PathItem("", node);
        }

        private void failIfOperationExists(String field) {
            if (node.containsKey(field)) {
                throw new IllegalStateException("Conflicting OpenAPI operation: " + field);
            }
        }
    }

    /**
     * OpenAPI Operation Object.
     */
    @Api.Preview
    public static final class Operation {
        private final Map<String, Object> node;
        private final String operationId;
        private final List<Parameter> parameters;
        private final RequestBody requestBody;
        private final Map<String, Response> responses;
        private final Map<String, PathItem> callbacks;

        private Operation(Map<String, Object> node) {
            this.node = immutableMap(node);
            this.operationId = stringValue(this.node.get("operationId")).orElse("");
            this.parameters = parameterList(this.node.get("parameters"));
            this.requestBody = objectValue(this.node.get("requestBody")).map(RequestBody::new).orElse(null);
            this.responses = responses(this.node.get("responses"));
            this.callbacks = pathItems(this.node.get("callbacks"));
        }

        /**
         * Create a new operation builder.
         *
         * @return builder
         */
        public static OperationBuilder builder() {
            return new OperationBuilder();
        }

        /**
         * Operation id.
         *
         * @return operation id
         */
        public Optional<String> operationId() {
            return operationId.isBlank() ? Optional.empty() : Optional.of(operationId);
        }

        /**
         * Operation parameters.
         *
         * @return operation parameters
         */
        public List<Parameter> parameters() {
            return parameters;
        }

        /**
         * Request body.
         *
         * @return request body
         */
        public Optional<RequestBody> requestBody() {
            return Optional.ofNullable(requestBody);
        }

        /**
         * Operation responses.
         *
         * @return responses keyed by status code or {@code default}
         */
        public Map<String, Response> responses() {
            return responses;
        }

        /**
         * Operation callbacks.
         *
         * @return callbacks keyed by callback expression
         */
        public Map<String, PathItem> callbacks() {
            return callbacks;
        }

        private Map<String, Object> toNode() {
            return node;
        }

        private static Map<String, Response> responses(Object value) {
            return objectValue(value)
                    .map(responses -> {
                        Map<String, Response> result = new LinkedHashMap<>();
                        responses.forEach((status, response) -> {
                            if (!status.startsWith("x-")) {
                                objectValue(response).ifPresent(node -> result.put(status, new Response(node)));
                            }
                        });
                        return Collections.unmodifiableMap(result);
                    })
                    .orElseGet(Map::of);
        }
    }

    /**
     * OpenAPI operation builder.
     */
    @Api.Preview
    public static final class OperationBuilder implements io.helidon.common.Builder<OperationBuilder, Operation> {
        private final Map<String, Object> node = new LinkedHashMap<>();

        private OperationBuilder() {
        }

        /**
         * Add an operation tag.
         *
         * @param tag tag name
         * @return updated builder
         */
        public OperationBuilder tag(String tag) {
            array(node, "tags").add(Objects.requireNonNull(tag));
            return this;
        }

        /**
         * Set summary.
         *
         * @param summary summary
         * @return updated builder
         */
        public OperationBuilder summary(String summary) {
            node.put("summary", Objects.requireNonNull(summary));
            return this;
        }

        /**
         * Set description.
         *
         * @param description description
         * @return updated builder
         */
        public OperationBuilder description(String description) {
            node.put("description", Objects.requireNonNull(description));
            return this;
        }

        /**
         * Set external documentation.
         *
         * @param externalDocs external documentation
         * @return updated builder
         */
        public OperationBuilder externalDocs(ExternalDocs externalDocs) {
            node.put("externalDocs", Objects.requireNonNull(externalDocs).toNode());
            return this;
        }

        /**
         * Set external documentation.
         *
         * @param externalDocs consumer to update external documentation builder
         * @return updated builder
         */
        public OperationBuilder externalDocs(Consumer<ExternalDocsBuilder> externalDocs) {
            ExternalDocsBuilder builder = ExternalDocs.builder();
            externalDocs.accept(builder);
            return externalDocs(builder.build());
        }

        /**
         * Set operation id.
         *
         * @param operationId operation id
         * @return updated builder
         */
        public OperationBuilder operationId(String operationId) {
            node.put("operationId", Objects.requireNonNull(operationId));
            return this;
        }

        /**
         * Add parameter.
         *
         * @param parameter parameter
         * @return updated builder
         */
        public OperationBuilder parameter(Parameter parameter) {
            array(node, "parameters").add(Objects.requireNonNull(parameter).toNode());
            return this;
        }

        /**
         * Add parameter.
         *
         * @param parameter consumer to update parameter builder
         * @return updated builder
         */
        public OperationBuilder parameter(Consumer<ParameterBuilder> parameter) {
            ParameterBuilder builder = Parameter.builder();
            parameter.accept(builder);
            return parameter(builder.build());
        }

        /**
         * Set request body.
         *
         * @param requestBody request body
         * @return updated builder
         */
        public OperationBuilder requestBody(RequestBody requestBody) {
            node.put("requestBody", Objects.requireNonNull(requestBody).toNode());
            return this;
        }

        /**
         * Set request body.
         *
         * @param requestBody consumer to update request body builder
         * @return updated builder
         */
        public OperationBuilder requestBody(Consumer<RequestBodyBuilder> requestBody) {
            RequestBodyBuilder builder = RequestBody.builder();
            requestBody.accept(builder);
            return requestBody(builder.build());
        }

        /**
         * Add a response.
         *
         * @param status status code or {@code default}
         * @param description response description
         * @return updated builder
         */
        public OperationBuilder response(String status, String description) {
            return response(status, Response.builder()
                    .description(description)
                    .build());
        }

        /**
         * Add a response.
         *
         * @param status status code or {@code default}
         * @param response response
         * @return updated builder
         */
        public OperationBuilder response(String status, Response response) {
            object(node, "responses").put(Objects.requireNonNull(status), Objects.requireNonNull(response).toNode());
            return this;
        }

        /**
         * Add a response.
         *
         * @param status status code or {@code default}
         * @param response consumer to update response builder
         * @return updated builder
         */
        public OperationBuilder response(String status, Consumer<ResponseBuilder> response) {
            ResponseBuilder builder = Response.builder();
            response.accept(builder);
            return response(status, builder.build());
        }

        /**
         * Add an extension to the responses object.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public OperationBuilder responseExtension(String name, JsonValue value) {
            OpenApiDocument.extension(object(node, "responses"), name, value);
            return this;
        }

        /**
         * Add an extension to the callbacks object.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public OperationBuilder callbackExtension(String name, JsonValue value) {
            OpenApiDocument.extension(object(node, "callbacks"), name, value);
            return this;
        }

        /**
         * Add callback.
         *
         * @param expression callback expression
         * @param callback callback path item
         * @return updated builder
         */
        public OperationBuilder callback(String expression, PathItem callback) {
            object(node, "callbacks").put(Objects.requireNonNull(expression), Objects.requireNonNull(callback).toNode());
            return this;
        }

        /**
         * Add callback.
         *
         * @param expression callback expression
         * @param callback consumer to update callback path item builder
         * @return updated builder
         */
        public OperationBuilder callback(String expression, Consumer<PathItemBuilder> callback) {
            PathItemBuilder builder = PathItem.builder();
            callback.accept(builder);
            return callback(expression, builder.build());
        }

        /**
         * Set deprecated flag.
         *
         * @param deprecated deprecated flag
         * @return updated builder
         */
        public OperationBuilder deprecated(boolean deprecated) {
            node.put("deprecated", deprecated);
            return this;
        }

        /**
         * Add security requirement.
         *
         * @param requirement security requirement
         * @return updated builder
         */
        public OperationBuilder securityRequirement(SecurityRequirement requirement) {
            array(node, "security").add(Objects.requireNonNull(requirement).toNode());
            return this;
        }

        /**
         * Add security requirement.
         *
         * @param requirement consumer to update security requirement builder
         * @return updated builder
         */
        public OperationBuilder securityRequirement(Consumer<SecurityRequirementBuilder> requirement) {
            SecurityRequirementBuilder builder = SecurityRequirement.builder();
            requirement.accept(builder);
            return securityRequirement(builder.build());
        }

        /**
         * Set security requirements.
         * <p>
         * An empty list declares that this operation overrides document-level security requirements with no security.
         *
         * @param requirements security requirements
         * @return updated builder
         */
        public OperationBuilder security(List<SecurityRequirement> requirements) {
            List<Object> result = new ArrayList<>();
            Objects.requireNonNull(requirements).forEach(requirement -> result.add(requirement.toNode()));
            node.put("security", result);
            return this;
        }

        /**
         * Add server.
         *
         * @param server server
         * @return updated builder
         */
        public OperationBuilder server(Server server) {
            array(node, "servers").add(Objects.requireNonNull(server).toNode());
            return this;
        }

        /**
         * Add server.
         *
         * @param server consumer to update server builder
         * @return updated builder
         */
        public OperationBuilder server(Consumer<ServerBuilder> server) {
            ServerBuilder builder = Server.builder();
            server.accept(builder);
            return server(builder.build());
        }

        /**
         * Add an extension.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public OperationBuilder extension(String name, JsonValue value) {
            OpenApiDocument.extension(node, name, value);
            return this;
        }

        @Override
        public Operation build() {
            requireValue(node, "responses", "OpenAPI Operation");
            return new Operation(node);
        }
    }

    /**
     * OpenAPI Parameter Object.
     */
    @Api.Preview
    public static final class Parameter {
        private final Map<String, Object> node;

        private Parameter(Map<String, Object> node) {
            this.node = immutableMap(node);
        }

        /**
         * Create a new parameter builder.
         *
         * @return builder
         */
        public static ParameterBuilder builder() {
            return new ParameterBuilder();
        }

        /**
         * Create a reference parameter.
         *
         * @param ref reference
         * @return parameter
         */
        public static Parameter reference(String ref) {
            return new ParameterBuilder().ref(ref).build();
        }

        private Map<String, Object> toNode() {
            return node;
        }
    }

    /**
     * OpenAPI parameter builder.
     */
    @Api.Preview
    public static final class ParameterBuilder implements io.helidon.common.Builder<ParameterBuilder, Parameter> {
        private final Map<String, Object> node = new LinkedHashMap<>();

        private ParameterBuilder() {
        }

        /**
         * Set reference.
         *
         * @param ref reference
         * @return updated builder
         */
        public ParameterBuilder ref(String ref) {
            node.put("$ref", Objects.requireNonNull(ref));
            return this;
        }

        /**
         * Set reference summary.
         *
         * @param summary summary
         * @return updated builder
         */
        public ParameterBuilder summary(String summary) {
            node.put("summary", Objects.requireNonNull(summary));
            return this;
        }

        /**
         * Set name.
         *
         * @param name name
         * @return updated builder
         */
        public ParameterBuilder name(String name) {
            node.put("name", Objects.requireNonNull(name));
            return this;
        }

        /**
         * Set location.
         *
         * @param in location
         * @return updated builder
         */
        public ParameterBuilder in(String in) {
            node.put("in", Objects.requireNonNull(in));
            return this;
        }

        /**
         * Set description.
         *
         * @param description description
         * @return updated builder
         */
        public ParameterBuilder description(String description) {
            node.put("description", Objects.requireNonNull(description));
            return this;
        }

        /**
         * Set required flag.
         *
         * @param required required flag
         * @return updated builder
         */
        public ParameterBuilder required(boolean required) {
            node.put("required", required);
            return this;
        }

        /**
         * Set deprecated flag.
         *
         * @param deprecated deprecated flag
         * @return updated builder
         */
        public ParameterBuilder deprecated(boolean deprecated) {
            node.put("deprecated", deprecated);
            return this;
        }

        /**
         * Set allow empty value flag.
         *
         * @param allowEmptyValue allow empty value flag
         * @return updated builder
         */
        public ParameterBuilder allowEmptyValue(boolean allowEmptyValue) {
            node.put("allowEmptyValue", allowEmptyValue);
            return this;
        }

        /**
         * Set style.
         *
         * @param style style
         * @return updated builder
         */
        public ParameterBuilder style(String style) {
            node.put("style", Objects.requireNonNull(style));
            return this;
        }

        /**
         * Set explode flag.
         *
         * @param explode explode flag
         * @return updated builder
         */
        public ParameterBuilder explode(boolean explode) {
            node.put("explode", explode);
            return this;
        }

        /**
         * Set allow reserved flag.
         *
         * @param allowReserved allow reserved flag
         * @return updated builder
         */
        public ParameterBuilder allowReserved(boolean allowReserved) {
            node.put("allowReserved", allowReserved);
            return this;
        }

        /**
         * Set schema.
         *
         * @param schema schema
         * @return updated builder
         */
        public ParameterBuilder schema(JsonValue schema) {
            node.put("schema", jsonValue(Objects.requireNonNull(schema)));
            return this;
        }

        /**
         * Set example.
         *
         * @param example example value
         * @return updated builder
         */
        public ParameterBuilder example(JsonValue example) {
            node.put("example", jsonValue(Objects.requireNonNull(example)));
            return this;
        }

        /**
         * Add example.
         *
         * @param name example name
         * @param example example
         * @return updated builder
         */
        public ParameterBuilder example(String name, Example example) {
            object(node, "examples").put(Objects.requireNonNull(name), Objects.requireNonNull(example).toNode());
            return this;
        }

        /**
         * Add content entry.
         *
         * @param mediaType media type
         * @param content media type object
         * @return updated builder
         */
        public ParameterBuilder content(String mediaType, MediaTypeObject content) {
            object(node, "content").put(Objects.requireNonNull(mediaType), Objects.requireNonNull(content).toNode());
            return this;
        }

        /**
         * Add content entry.
         *
         * @param mediaType media type
         * @param content consumer to update media type object builder
         * @return updated builder
         */
        public ParameterBuilder content(String mediaType, Consumer<MediaTypeObjectBuilder> content) {
            MediaTypeObjectBuilder builder = MediaTypeObject.builder();
            content.accept(builder);
            return content(mediaType, builder.build());
        }

        /**
         * Add an extension.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public ParameterBuilder extension(String name, JsonValue value) {
            OpenApiDocument.extension(node, name, value);
            return this;
        }

        private Map<String, Object> toNode() {
            return node;
        }

        @Override
        public Parameter build() {
            if (!isReference(node)) {
                requireString(node, "in", "OpenAPI Parameter");
                if (!"querystring".equals(stringValue(node.get("in")).orElse(""))) {
                    requireString(node, "name", "OpenAPI Parameter");
                }
            }
            return new Parameter(node);
        }
    }

    /**
     * OpenAPI Header Object.
     */
    @Api.Preview
    public static final class Header {
        private final Map<String, Object> node;

        private Header(Map<String, Object> node) {
            this.node = immutableMap(node);
        }

        /**
         * Create a new header builder.
         *
         * @return builder
         */
        public static HeaderBuilder builder() {
            return new HeaderBuilder();
        }

        /**
         * Create a reference header.
         *
         * @param ref reference
         * @return header
         */
        public static Header reference(String ref) {
            return new HeaderBuilder().ref(ref).build();
        }

        private Map<String, Object> toNode() {
            return node;
        }
    }

    /**
     * OpenAPI header builder.
     */
    @Api.Preview
    public static final class HeaderBuilder implements io.helidon.common.Builder<HeaderBuilder, Header> {
        private final ParameterBuilder delegate = Parameter.builder();

        private HeaderBuilder() {
        }

        /**
         * Set reference.
         *
         * @param ref reference
         * @return updated builder
         */
        public HeaderBuilder ref(String ref) {
            delegate.ref(ref);
            return this;
        }

        /**
         * Set reference summary.
         *
         * @param summary summary
         * @return updated builder
         */
        public HeaderBuilder summary(String summary) {
            delegate.summary(summary);
            return this;
        }

        /**
         * Set description.
         *
         * @param description description
         * @return updated builder
         */
        public HeaderBuilder description(String description) {
            delegate.description(description);
            return this;
        }

        /**
         * Set required flag.
         *
         * @param required required flag
         * @return updated builder
         */
        public HeaderBuilder required(boolean required) {
            delegate.required(required);
            return this;
        }

        /**
         * Set deprecated flag.
         *
         * @param deprecated deprecated flag
         * @return updated builder
         */
        public HeaderBuilder deprecated(boolean deprecated) {
            delegate.deprecated(deprecated);
            return this;
        }

        /**
         * Set allow empty value flag.
         *
         * @param allowEmptyValue allow empty value flag
         * @return updated builder
         */
        public HeaderBuilder allowEmptyValue(boolean allowEmptyValue) {
            delegate.allowEmptyValue(allowEmptyValue);
            return this;
        }

        /**
         * Set style.
         *
         * @param style style
         * @return updated builder
         */
        public HeaderBuilder style(String style) {
            delegate.style(style);
            return this;
        }

        /**
         * Set explode flag.
         *
         * @param explode explode flag
         * @return updated builder
         */
        public HeaderBuilder explode(boolean explode) {
            delegate.explode(explode);
            return this;
        }

        /**
         * Set allow reserved flag.
         *
         * @param allowReserved allow reserved flag
         * @return updated builder
         */
        public HeaderBuilder allowReserved(boolean allowReserved) {
            delegate.allowReserved(allowReserved);
            return this;
        }

        /**
         * Set schema.
         *
         * @param schema schema
         * @return updated builder
         */
        public HeaderBuilder schema(JsonValue schema) {
            delegate.schema(schema);
            return this;
        }

        /**
         * Set example.
         *
         * @param example example value
         * @return updated builder
         */
        public HeaderBuilder example(JsonValue example) {
            delegate.example(example);
            return this;
        }

        /**
         * Add example.
         *
         * @param name example name
         * @param example example
         * @return updated builder
         */
        public HeaderBuilder example(String name, Example example) {
            delegate.example(name, example);
            return this;
        }

        /**
         * Add content entry.
         *
         * @param mediaType media type
         * @param content media type object
         * @return updated builder
         */
        public HeaderBuilder content(String mediaType, MediaTypeObject content) {
            delegate.content(mediaType, content);
            return this;
        }

        /**
         * Add content entry.
         *
         * @param mediaType media type
         * @param content consumer to update media type object builder
         * @return updated builder
         */
        public HeaderBuilder content(String mediaType, Consumer<MediaTypeObjectBuilder> content) {
            delegate.content(mediaType, content);
            return this;
        }

        /**
         * Add an extension.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public HeaderBuilder extension(String name, JsonValue value) {
            OpenApiDocument.extension(delegate.toNode(), name, value);
            return this;
        }

        @Override
        public Header build() {
            Map<String, Object> node = new LinkedHashMap<>(delegate.toNode());
            node.remove("name");
            node.remove("in");
            return new Header(node);
        }
    }

    /**
     * OpenAPI Request Body Object.
     */
    @Api.Preview
    public static final class RequestBody {
        private final Map<String, Object> node;

        private RequestBody(Map<String, Object> node) {
            this.node = immutableMap(node);
        }

        /**
         * Create a new request body builder.
         *
         * @return builder
         */
        public static RequestBodyBuilder builder() {
            return new RequestBodyBuilder();
        }

        /**
         * Create a reference request body.
         *
         * @param ref reference
         * @return request body
         */
        public static RequestBody reference(String ref) {
            return new RequestBodyBuilder().ref(ref).build();
        }

        private Map<String, Object> toNode() {
            return node;
        }
    }

    /**
     * OpenAPI request body builder.
     */
    @Api.Preview
    public static final class RequestBodyBuilder implements io.helidon.common.Builder<RequestBodyBuilder, RequestBody> {
        private final Map<String, Object> node = new LinkedHashMap<>();

        private RequestBodyBuilder() {
        }

        /**
         * Set reference.
         *
         * @param ref reference
         * @return updated builder
         */
        public RequestBodyBuilder ref(String ref) {
            node.put("$ref", Objects.requireNonNull(ref));
            return this;
        }

        /**
         * Set reference summary.
         *
         * @param summary summary
         * @return updated builder
         */
        public RequestBodyBuilder summary(String summary) {
            node.put("summary", Objects.requireNonNull(summary));
            return this;
        }

        /**
         * Set description.
         *
         * @param description description
         * @return updated builder
         */
        public RequestBodyBuilder description(String description) {
            node.put("description", Objects.requireNonNull(description));
            return this;
        }

        /**
         * Add content entry.
         *
         * @param mediaType media type
         * @param content media type object
         * @return updated builder
         */
        public RequestBodyBuilder content(String mediaType, MediaTypeObject content) {
            object(node, "content").put(Objects.requireNonNull(mediaType), Objects.requireNonNull(content).toNode());
            return this;
        }

        /**
         * Add content entry.
         *
         * @param mediaType media type
         * @param content consumer to update media type object builder
         * @return updated builder
         */
        public RequestBodyBuilder content(String mediaType, Consumer<MediaTypeObjectBuilder> content) {
            MediaTypeObjectBuilder builder = MediaTypeObject.builder();
            content.accept(builder);
            return content(mediaType, builder.build());
        }

        /**
         * Set required flag.
         *
         * @param required required flag
         * @return updated builder
         */
        public RequestBodyBuilder required(boolean required) {
            node.put("required", required);
            return this;
        }

        /**
         * Add an extension.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public RequestBodyBuilder extension(String name, JsonValue value) {
            OpenApiDocument.extension(node, name, value);
            return this;
        }

        @Override
        public RequestBody build() {
            if (!isReference(node)) {
                requireValue(node, "content", "OpenAPI RequestBody");
            }
            return new RequestBody(node);
        }
    }

    /**
     * OpenAPI Response Object.
     */
    @Api.Preview
    public static final class Response {
        private final Map<String, Object> node;
        private final String description;

        private Response(Map<String, Object> node) {
            this.node = immutableMap(node);
            this.description = stringValue(this.node.get("description")).orElse("");
        }

        /**
         * Create an empty response builder.
         *
         * @return builder
         */
        public static ResponseBuilder builder() {
            return new ResponseBuilder();
        }

        /**
         * Create a reference response.
         *
         * @param ref reference
         * @return response
         */
        public static Response reference(String ref) {
            return builder().ref(ref).build();
        }

        /**
         * Response summary.
         *
         * @return summary
         */
        public Optional<String> summary() {
            return stringValue(node.get("summary"));
        }

        /**
         * Response description.
         *
         * @return description
         */
        public String description() {
            return description;
        }

        private Map<String, Object> toNode() {
            return node;
        }
    }

    /**
     * OpenAPI response builder.
     */
    @Api.Preview
    public static final class ResponseBuilder implements io.helidon.common.Builder<ResponseBuilder, Response> {
        private final Map<String, Object> node = new LinkedHashMap<>();

        private ResponseBuilder() {
        }

        /**
         * Set reference.
         *
         * @param ref reference
         * @return updated builder
         */
        public ResponseBuilder ref(String ref) {
            node.put("$ref", Objects.requireNonNull(ref));
            return this;
        }

        /**
         * Set summary.
         *
         * @param summary summary
         * @return updated builder
         */
        public ResponseBuilder summary(String summary) {
            node.put("summary", Objects.requireNonNull(summary));
            return this;
        }

        /**
         * Set description.
         *
         * @param description description
         * @return updated builder
         */
        public ResponseBuilder description(String description) {
            node.put("description", Objects.requireNonNull(description));
            return this;
        }

        /**
         * Add header.
         *
         * @param name header name
         * @param header header
         * @return updated builder
         */
        public ResponseBuilder header(String name, Header header) {
            object(node, "headers").put(Objects.requireNonNull(name), Objects.requireNonNull(header).toNode());
            return this;
        }

        /**
         * Add header.
         *
         * @param name header name
         * @param header consumer to update header builder
         * @return updated builder
         */
        public ResponseBuilder header(String name, Consumer<HeaderBuilder> header) {
            HeaderBuilder builder = Header.builder();
            header.accept(builder);
            return header(name, builder.build());
        }

        /**
         * Add content entry.
         *
         * @param mediaType media type
         * @param content media type object
         * @return updated builder
         */
        public ResponseBuilder content(String mediaType, MediaTypeObject content) {
            object(node, "content").put(Objects.requireNonNull(mediaType), Objects.requireNonNull(content).toNode());
            return this;
        }

        /**
         * Add content entry.
         *
         * @param mediaType media type
         * @param content consumer to update media type object builder
         * @return updated builder
         */
        public ResponseBuilder content(String mediaType, Consumer<MediaTypeObjectBuilder> content) {
            MediaTypeObjectBuilder builder = MediaTypeObject.builder();
            content.accept(builder);
            return content(mediaType, builder.build());
        }

        /**
         * Add link.
         *
         * @param name link name
         * @param link link
         * @return updated builder
         */
        public ResponseBuilder link(String name, Link link) {
            object(node, "links").put(Objects.requireNonNull(name), Objects.requireNonNull(link).toNode());
            return this;
        }

        /**
         * Add link.
         *
         * @param name link name
         * @param link consumer to update link builder
         * @return updated builder
         */
        public ResponseBuilder link(String name, Consumer<LinkBuilder> link) {
            LinkBuilder builder = Link.builder();
            link.accept(builder);
            return link(name, builder.build());
        }

        /**
         * Add an extension.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public ResponseBuilder extension(String name, JsonValue value) {
            OpenApiDocument.extension(node, name, value);
            return this;
        }

        @Override
        public Response build() {
            if (!isReference(node)) {
                requireString(node, "description", "OpenAPI Response");
            }
            return new Response(node);
        }
    }

    /**
     * OpenAPI Media Type Object.
     */
    @Api.Preview
    public static final class MediaTypeObject {
        private final Map<String, Object> node;

        private MediaTypeObject(Map<String, Object> node) {
            this.node = immutableMap(node);
        }

        /**
         * Create a new media type builder.
         *
         * @return builder
         */
        public static MediaTypeObjectBuilder builder() {
            return new MediaTypeObjectBuilder();
        }

        /**
         * Create a reference media type.
         *
         * @param ref reference
         * @return media type object
         */
        public static MediaTypeObject reference(String ref) {
            return builder().ref(ref).build();
        }

        private Map<String, Object> toNode() {
            return node;
        }
    }

    /**
     * OpenAPI media type builder.
     */
    @Api.Preview
    public static final class MediaTypeObjectBuilder implements io.helidon.common.Builder<MediaTypeObjectBuilder, MediaTypeObject> {
        private final Map<String, Object> node = new LinkedHashMap<>();

        private MediaTypeObjectBuilder() {
        }

        /**
         * Set reference.
         *
         * @param ref reference
         * @return updated builder
         */
        public MediaTypeObjectBuilder ref(String ref) {
            node.put("$ref", Objects.requireNonNull(ref));
            return this;
        }

        /**
         * Set reference summary.
         *
         * @param summary summary
         * @return updated builder
         */
        public MediaTypeObjectBuilder summary(String summary) {
            node.put("summary", Objects.requireNonNull(summary));
            return this;
        }

        /**
         * Set reference description.
         *
         * @param description description
         * @return updated builder
         */
        public MediaTypeObjectBuilder description(String description) {
            node.put("description", Objects.requireNonNull(description));
            return this;
        }

        /**
         * Set schema.
         *
         * @param schema schema
         * @return updated builder
         */
        public MediaTypeObjectBuilder schema(JsonValue schema) {
            node.put("schema", jsonValue(Objects.requireNonNull(schema)));
            return this;
        }

        /**
         * Set OpenAPI 3.2 item schema.
         *
         * @param schema item schema
         * @return updated builder
         */
        public MediaTypeObjectBuilder itemSchema(JsonValue schema) {
            node.put("itemSchema", jsonValue(Objects.requireNonNull(schema)));
            return this;
        }

        /**
         * Set example.
         *
         * @param example example value
         * @return updated builder
         */
        public MediaTypeObjectBuilder example(JsonValue example) {
            node.put("example", jsonValue(Objects.requireNonNull(example)));
            return this;
        }

        /**
         * Add example.
         *
         * @param name example name
         * @param example example
         * @return updated builder
         */
        public MediaTypeObjectBuilder example(String name, Example example) {
            object(node, "examples").put(Objects.requireNonNull(name), Objects.requireNonNull(example).toNode());
            return this;
        }

        /**
         * Add encoding.
         *
         * @param name encoding name
         * @param encoding encoding
         * @return updated builder
         */
        public MediaTypeObjectBuilder encoding(String name, Encoding encoding) {
            object(node, "encoding").put(Objects.requireNonNull(name), Objects.requireNonNull(encoding).toNode());
            return this;
        }

        /**
         * Set OpenAPI 3.2 prefix encodings.
         *
         * @param prefixEncoding prefix encodings
         * @return updated builder
         */
        public MediaTypeObjectBuilder prefixEncoding(JsonArray prefixEncoding) {
            node.put("prefixEncoding", jsonValue(Objects.requireNonNull(prefixEncoding)));
            return this;
        }

        /**
         * Set OpenAPI 3.2 item encoding.
         *
         * @param itemEncoding item encoding
         * @return updated builder
         */
        public MediaTypeObjectBuilder itemEncoding(Encoding itemEncoding) {
            node.put("itemEncoding", Objects.requireNonNull(itemEncoding).toNode());
            return this;
        }

        /**
         * Add an extension.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public MediaTypeObjectBuilder extension(String name, JsonValue value) {
            OpenApiDocument.extension(node, name, value);
            return this;
        }

        @Override
        public MediaTypeObject build() {
            return new MediaTypeObject(node);
        }
    }

    /**
     * OpenAPI Encoding Object.
     */
    @Api.Preview
    public static final class Encoding {
        private final Map<String, Object> node;

        private Encoding(Map<String, Object> node) {
            this.node = immutableMap(node);
        }

        /**
         * Create a new encoding builder.
         *
         * @return builder
         */
        public static EncodingBuilder builder() {
            return new EncodingBuilder();
        }

        private Map<String, Object> toNode() {
            return node;
        }
    }

    /**
     * OpenAPI encoding builder.
     */
    @Api.Preview
    public static final class EncodingBuilder implements io.helidon.common.Builder<EncodingBuilder, Encoding> {
        private final Map<String, Object> node = new LinkedHashMap<>();

        private EncodingBuilder() {
        }

        /**
         * Set content type.
         *
         * @param contentType content type
         * @return updated builder
         */
        public EncodingBuilder contentType(String contentType) {
            node.put("contentType", Objects.requireNonNull(contentType));
            return this;
        }

        /**
         * Add header.
         *
         * @param name header name
         * @param header header
         * @return updated builder
         */
        public EncodingBuilder header(String name, Header header) {
            object(node, "headers").put(Objects.requireNonNull(name), Objects.requireNonNull(header).toNode());
            return this;
        }

        /**
         * Add nested OpenAPI 3.2 encoding.
         *
         * @param name encoding name
         * @param encoding encoding
         * @return updated builder
         */
        public EncodingBuilder encoding(String name, Encoding encoding) {
            object(node, "encoding").put(Objects.requireNonNull(name), Objects.requireNonNull(encoding).toNode());
            return this;
        }

        /**
         * Set OpenAPI 3.2 prefix encodings.
         *
         * @param prefixEncoding prefix encodings
         * @return updated builder
         */
        public EncodingBuilder prefixEncoding(JsonArray prefixEncoding) {
            node.put("prefixEncoding", jsonValue(Objects.requireNonNull(prefixEncoding)));
            return this;
        }

        /**
         * Set OpenAPI 3.2 item encoding.
         *
         * @param itemEncoding item encoding
         * @return updated builder
         */
        public EncodingBuilder itemEncoding(Encoding itemEncoding) {
            node.put("itemEncoding", Objects.requireNonNull(itemEncoding).toNode());
            return this;
        }

        /**
         * Set style.
         *
         * @param style style
         * @return updated builder
         */
        public EncodingBuilder style(String style) {
            node.put("style", Objects.requireNonNull(style));
            return this;
        }

        /**
         * Set explode flag.
         *
         * @param explode explode flag
         * @return updated builder
         */
        public EncodingBuilder explode(boolean explode) {
            node.put("explode", explode);
            return this;
        }

        /**
         * Set allow reserved flag.
         *
         * @param allowReserved allow reserved flag
         * @return updated builder
         */
        public EncodingBuilder allowReserved(boolean allowReserved) {
            node.put("allowReserved", allowReserved);
            return this;
        }

        /**
         * Add an extension.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public EncodingBuilder extension(String name, JsonValue value) {
            OpenApiDocument.extension(node, name, value);
            return this;
        }

        @Override
        public Encoding build() {
            return new Encoding(node);
        }
    }

    /**
     * OpenAPI Example Object.
     */
    @Api.Preview
    public static final class Example {
        private final Map<String, Object> node;

        private Example(Map<String, Object> node) {
            this.node = immutableMap(node);
        }

        /**
         * Create a new example builder.
         *
         * @return builder
         */
        public static ExampleBuilder builder() {
            return new ExampleBuilder();
        }

        /**
         * Create a reference example.
         *
         * @param ref reference
         * @return example
         */
        public static Example reference(String ref) {
            return builder().ref(ref).build();
        }

        private Map<String, Object> toNode() {
            return node;
        }
    }

    /**
     * OpenAPI example builder.
     */
    @Api.Preview
    public static final class ExampleBuilder implements io.helidon.common.Builder<ExampleBuilder, Example> {
        private final Map<String, Object> node = new LinkedHashMap<>();

        private ExampleBuilder() {
        }

        /**
         * Set reference.
         *
         * @param ref reference
         * @return updated builder
         */
        public ExampleBuilder ref(String ref) {
            node.put("$ref", Objects.requireNonNull(ref));
            return this;
        }

        /**
         * Set summary.
         *
         * @param summary summary
         * @return updated builder
         */
        public ExampleBuilder summary(String summary) {
            node.put("summary", Objects.requireNonNull(summary));
            return this;
        }

        /**
         * Set description.
         *
         * @param description description
         * @return updated builder
         */
        public ExampleBuilder description(String description) {
            node.put("description", Objects.requireNonNull(description));
            return this;
        }

        /**
         * Set value.
         *
         * @param value value
         * @return updated builder
         */
        public ExampleBuilder value(JsonValue value) {
            node.put("value", jsonValue(Objects.requireNonNull(value)));
            return this;
        }

        /**
         * Set OpenAPI 3.2 data value.
         *
         * @param value data value
         * @return updated builder
         */
        public ExampleBuilder dataValue(JsonValue value) {
            node.put("dataValue", jsonValue(Objects.requireNonNull(value)));
            return this;
        }

        /**
         * Set OpenAPI 3.2 serialized value.
         *
         * @param value serialized value
         * @return updated builder
         */
        public ExampleBuilder serializedValue(String value) {
            node.put("serializedValue", Objects.requireNonNull(value));
            return this;
        }

        /**
         * Set external value.
         *
         * @param externalValue external value
         * @return updated builder
         */
        public ExampleBuilder externalValue(String externalValue) {
            node.put("externalValue", Objects.requireNonNull(externalValue));
            return this;
        }

        /**
         * Add an extension.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public ExampleBuilder extension(String name, JsonValue value) {
            OpenApiDocument.extension(node, name, value);
            return this;
        }

        @Override
        public Example build() {
            return new Example(node);
        }
    }

    /**
     * OpenAPI Link Object.
     */
    @Api.Preview
    public static final class Link {
        private final Map<String, Object> node;

        private Link(Map<String, Object> node) {
            this.node = immutableMap(node);
        }

        /**
         * Create a new link builder.
         *
         * @return builder
         */
        public static LinkBuilder builder() {
            return new LinkBuilder();
        }

        /**
         * Create a reference link.
         *
         * @param ref reference
         * @return link
         */
        public static Link reference(String ref) {
            return builder().ref(ref).build();
        }

        private Map<String, Object> toNode() {
            return node;
        }
    }

    /**
     * OpenAPI link builder.
     */
    @Api.Preview
    public static final class LinkBuilder implements io.helidon.common.Builder<LinkBuilder, Link> {
        private final Map<String, Object> node = new LinkedHashMap<>();

        private LinkBuilder() {
        }

        /**
         * Set reference.
         *
         * @param ref reference
         * @return updated builder
         */
        public LinkBuilder ref(String ref) {
            node.put("$ref", Objects.requireNonNull(ref));
            return this;
        }

        /**
         * Set reference summary.
         *
         * @param summary summary
         * @return updated builder
         */
        public LinkBuilder summary(String summary) {
            node.put("summary", Objects.requireNonNull(summary));
            return this;
        }

        /**
         * Set operation reference.
         *
         * @param operationRef operation reference
         * @return updated builder
         */
        public LinkBuilder operationRef(String operationRef) {
            node.put("operationRef", Objects.requireNonNull(operationRef));
            return this;
        }

        /**
         * Set operation id.
         *
         * @param operationId operation id
         * @return updated builder
         */
        public LinkBuilder operationId(String operationId) {
            node.put("operationId", Objects.requireNonNull(operationId));
            return this;
        }

        /**
         * Set parameters.
         *
         * @param parameters parameters
         * @return updated builder
         */
        public LinkBuilder parameters(JsonObject parameters) {
            node.put("parameters", jsonValue(Objects.requireNonNull(parameters)));
            return this;
        }

        /**
         * Set request body.
         *
         * @param requestBody request body
         * @return updated builder
         */
        public LinkBuilder requestBody(JsonValue requestBody) {
            node.put("requestBody", jsonValue(Objects.requireNonNull(requestBody)));
            return this;
        }

        /**
         * Set description.
         *
         * @param description description
         * @return updated builder
         */
        public LinkBuilder description(String description) {
            node.put("description", Objects.requireNonNull(description));
            return this;
        }

        /**
         * Set server.
         *
         * @param server server
         * @return updated builder
         */
        public LinkBuilder server(Server server) {
            node.put("server", Objects.requireNonNull(server).toNode());
            return this;
        }

        /**
         * Add an extension.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public LinkBuilder extension(String name, JsonValue value) {
            OpenApiDocument.extension(node, name, value);
            return this;
        }

        @Override
        public Link build() {
            return new Link(node);
        }
    }

    /**
     * OpenAPI Components Object.
     */
    @Api.Preview
    public static final class Components {
        private final Map<String, Object> node;

        private Components(Map<String, Object> node) {
            this.node = immutableMap(node);
        }

        /**
         * Create a new components builder.
         *
         * @return builder
         */
        public static ComponentsBuilder builder() {
            return new ComponentsBuilder();
        }

        private Map<String, Object> toNode() {
            return node;
        }
    }

    /**
     * OpenAPI components builder.
     */
    @Api.Preview
    public static final class ComponentsBuilder implements io.helidon.common.Builder<ComponentsBuilder, Components> {
        private final Map<String, Object> node = new LinkedHashMap<>();

        private ComponentsBuilder() {
        }

        /**
         * Set schema.
         *
         * @param name schema name
         * @param schema schema
         * @return updated builder
         */
        public ComponentsBuilder schema(String name, JsonValue schema) {
            object(node, "schemas").put(Objects.requireNonNull(name), jsonValue(Objects.requireNonNull(schema)));
            return this;
        }

        /**
         * Set response.
         *
         * @param name response name
         * @param response response
         * @return updated builder
         */
        public ComponentsBuilder response(String name, Response response) {
            object(node, "responses").put(Objects.requireNonNull(name), Objects.requireNonNull(response).toNode());
            return this;
        }

        /**
         * Set response.
         *
         * @param name response name
         * @param response consumer to update response builder
         * @return updated builder
         */
        public ComponentsBuilder response(String name, Consumer<ResponseBuilder> response) {
            ResponseBuilder builder = Response.builder();
            response.accept(builder);
            return response(name, builder.build());
        }

        /**
         * Set parameter.
         *
         * @param name parameter name
         * @param parameter parameter
         * @return updated builder
         */
        public ComponentsBuilder parameter(String name, Parameter parameter) {
            object(node, "parameters").put(Objects.requireNonNull(name), Objects.requireNonNull(parameter).toNode());
            return this;
        }

        /**
         * Set parameter.
         *
         * @param name parameter name
         * @param parameter consumer to update parameter builder
         * @return updated builder
         */
        public ComponentsBuilder parameter(String name, Consumer<ParameterBuilder> parameter) {
            ParameterBuilder builder = Parameter.builder();
            parameter.accept(builder);
            return parameter(name, builder.build());
        }

        /**
         * Set example.
         *
         * @param name example name
         * @param example example
         * @return updated builder
         */
        public ComponentsBuilder example(String name, Example example) {
            object(node, "examples").put(Objects.requireNonNull(name), Objects.requireNonNull(example).toNode());
            return this;
        }

        /**
         * Set example.
         *
         * @param name example name
         * @param example consumer to update example builder
         * @return updated builder
         */
        public ComponentsBuilder example(String name, Consumer<ExampleBuilder> example) {
            ExampleBuilder builder = Example.builder();
            example.accept(builder);
            return example(name, builder.build());
        }

        /**
         * Set request body.
         *
         * @param name request body name
         * @param requestBody request body
         * @return updated builder
         */
        public ComponentsBuilder requestBody(String name, RequestBody requestBody) {
            object(node, "requestBodies").put(Objects.requireNonNull(name), Objects.requireNonNull(requestBody).toNode());
            return this;
        }

        /**
         * Set request body.
         *
         * @param name request body name
         * @param requestBody consumer to update request body builder
         * @return updated builder
         */
        public ComponentsBuilder requestBody(String name, Consumer<RequestBodyBuilder> requestBody) {
            RequestBodyBuilder builder = RequestBody.builder();
            requestBody.accept(builder);
            return requestBody(name, builder.build());
        }

        /**
         * Set header.
         *
         * @param name header name
         * @param header header
         * @return updated builder
         */
        public ComponentsBuilder header(String name, Header header) {
            object(node, "headers").put(Objects.requireNonNull(name), Objects.requireNonNull(header).toNode());
            return this;
        }

        /**
         * Set header.
         *
         * @param name header name
         * @param header consumer to update header builder
         * @return updated builder
         */
        public ComponentsBuilder header(String name, Consumer<HeaderBuilder> header) {
            HeaderBuilder builder = Header.builder();
            header.accept(builder);
            return header(name, builder.build());
        }

        /**
         * Set security scheme.
         *
         * @param name security scheme name
         * @param securityScheme security scheme
         * @return updated builder
         */
        public ComponentsBuilder securityScheme(String name, SecurityScheme securityScheme) {
            object(node, "securitySchemes").put(Objects.requireNonNull(name), Objects.requireNonNull(securityScheme).toNode());
            return this;
        }

        /**
         * Set security scheme.
         *
         * @param name security scheme name
         * @param securityScheme consumer to update security scheme builder
         * @return updated builder
         */
        public ComponentsBuilder securityScheme(String name, Consumer<SecuritySchemeBuilder> securityScheme) {
            SecuritySchemeBuilder builder = SecurityScheme.builder();
            securityScheme.accept(builder);
            return securityScheme(name, builder.build());
        }

        /**
         * Set link.
         *
         * @param name link name
         * @param link link
         * @return updated builder
         */
        public ComponentsBuilder link(String name, Link link) {
            object(node, "links").put(Objects.requireNonNull(name), Objects.requireNonNull(link).toNode());
            return this;
        }

        /**
         * Set link.
         *
         * @param name link name
         * @param link consumer to update link builder
         * @return updated builder
         */
        public ComponentsBuilder link(String name, Consumer<LinkBuilder> link) {
            LinkBuilder builder = Link.builder();
            link.accept(builder);
            return link(name, builder.build());
        }

        /**
         * Set callback.
         *
         * @param name callback name
         * @param callback callback path item
         * @return updated builder
         */
        public ComponentsBuilder callback(String name, PathItem callback) {
            object(node, "callbacks").put(Objects.requireNonNull(name), Objects.requireNonNull(callback).toNode());
            return this;
        }

        /**
         * Set callback.
         *
         * @param name callback name
         * @param callback consumer to update callback path item builder
         * @return updated builder
         */
        public ComponentsBuilder callback(String name, Consumer<PathItemBuilder> callback) {
            PathItemBuilder builder = PathItem.builder();
            callback.accept(builder);
            return callback(name, builder.build());
        }

        /**
         * Set path item.
         *
         * @param name path item name
         * @param pathItem path item
         * @return updated builder
         */
        public ComponentsBuilder pathItem(String name, PathItem pathItem) {
            object(node, "pathItems").put(Objects.requireNonNull(name), Objects.requireNonNull(pathItem).toNode());
            return this;
        }

        /**
         * Set path item.
         *
         * @param name path item name
         * @param pathItem consumer to update path item builder
         * @return updated builder
         */
        public ComponentsBuilder pathItem(String name, Consumer<PathItemBuilder> pathItem) {
            PathItemBuilder builder = PathItem.builder();
            pathItem.accept(builder);
            return pathItem(name, builder.build());
        }

        /**
         * Set OpenAPI 3.2 media type.
         *
         * @param name media type name
         * @param mediaType media type object
         * @return updated builder
         */
        public ComponentsBuilder mediaType(String name, MediaTypeObject mediaType) {
            object(node, "mediaTypes").put(Objects.requireNonNull(name), Objects.requireNonNull(mediaType).toNode());
            return this;
        }

        /**
         * Set OpenAPI 3.2 media type.
         *
         * @param name media type name
         * @param mediaType consumer to update media type object builder
         * @return updated builder
         */
        public ComponentsBuilder mediaType(String name, Consumer<MediaTypeObjectBuilder> mediaType) {
            MediaTypeObjectBuilder builder = MediaTypeObject.builder();
            mediaType.accept(builder);
            return mediaType(name, builder.build());
        }

        /**
         * Add an extension.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public ComponentsBuilder extension(String name, JsonValue value) {
            OpenApiDocument.extension(node, name, value);
            return this;
        }

        @Override
        public Components build() {
            return new Components(node);
        }
    }

    /**
     * OpenAPI Security Scheme Object.
     */
    @Api.Preview
    public static final class SecurityScheme {
        private final Map<String, Object> node;

        private SecurityScheme(Map<String, Object> node) {
            this.node = immutableMap(node);
        }

        /**
         * Create a new security scheme builder.
         *
         * @return builder
         */
        public static SecuritySchemeBuilder builder() {
            return new SecuritySchemeBuilder();
        }

        /**
         * Create a reference security scheme.
         *
         * @param ref reference
         * @return security scheme
         */
        public static SecurityScheme reference(String ref) {
            return new SecuritySchemeBuilder().ref(ref).build();
        }

        private Map<String, Object> toNode() {
            return node;
        }
    }

    /**
     * OpenAPI security scheme builder.
     */
    @Api.Preview
    public static final class SecuritySchemeBuilder implements io.helidon.common.Builder<SecuritySchemeBuilder, SecurityScheme> {
        private final Map<String, Object> node = new LinkedHashMap<>();

        private SecuritySchemeBuilder() {
        }

        /**
         * Set reference.
         *
         * @param ref reference
         * @return updated builder
         */
        public SecuritySchemeBuilder ref(String ref) {
            node.put("$ref", Objects.requireNonNull(ref));
            return this;
        }

        /**
         * Set reference summary.
         *
         * @param summary summary
         * @return updated builder
         */
        public SecuritySchemeBuilder summary(String summary) {
            node.put("summary", Objects.requireNonNull(summary));
            return this;
        }

        /**
         * Set security scheme type.
         *
         * @param type security scheme type
         * @return updated builder
         */
        public SecuritySchemeBuilder type(String type) {
            node.put("type", Objects.requireNonNull(type));
            return this;
        }

        /**
         * Set description.
         *
         * @param description description
         * @return updated builder
         */
        public SecuritySchemeBuilder description(String description) {
            node.put("description", Objects.requireNonNull(description));
            return this;
        }

        /**
         * Set parameter name.
         *
         * @param name parameter name
         * @return updated builder
         */
        public SecuritySchemeBuilder name(String name) {
            node.put("name", Objects.requireNonNull(name));
            return this;
        }

        /**
         * Set parameter location.
         *
         * @param in parameter location
         * @return updated builder
         */
        public SecuritySchemeBuilder in(String in) {
            node.put("in", Objects.requireNonNull(in));
            return this;
        }

        /**
         * Set HTTP scheme.
         *
         * @param scheme HTTP scheme
         * @return updated builder
         */
        public SecuritySchemeBuilder scheme(String scheme) {
            node.put("scheme", Objects.requireNonNull(scheme));
            return this;
        }

        /**
         * Set bearer format.
         *
         * @param bearerFormat bearer format
         * @return updated builder
         */
        public SecuritySchemeBuilder bearerFormat(String bearerFormat) {
            node.put("bearerFormat", Objects.requireNonNull(bearerFormat));
            return this;
        }

        /**
         * Set OAuth flows object.
         *
         * @param flows OAuth flows object
         * @return updated builder
         */
        public SecuritySchemeBuilder flows(JsonObject flows) {
            node.put("flows", jsonValue(Objects.requireNonNull(flows)));
            return this;
        }

        /**
         * Set OpenID Connect URL.
         *
         * @param openIdConnectUrl OpenID Connect URL
         * @return updated builder
         */
        public SecuritySchemeBuilder openIdConnectUrl(String openIdConnectUrl) {
            node.put("openIdConnectUrl", Objects.requireNonNull(openIdConnectUrl));
            return this;
        }

        /**
         * Set OpenAPI 3.2 OAuth 2 metadata URL.
         *
         * @param oauth2MetadataUrl OAuth 2 metadata URL
         * @return updated builder
         */
        public SecuritySchemeBuilder oauth2MetadataUrl(String oauth2MetadataUrl) {
            node.put("oauth2MetadataUrl", Objects.requireNonNull(oauth2MetadataUrl));
            return this;
        }

        /**
         * Set whether the security scheme is deprecated.
         *
         * @param deprecated whether the security scheme is deprecated
         * @return updated builder
         */
        public SecuritySchemeBuilder deprecated(boolean deprecated) {
            node.put("deprecated", deprecated);
            return this;
        }

        /**
         * Add an extension.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public SecuritySchemeBuilder extension(String name, JsonValue value) {
            OpenApiDocument.extension(node, name, value);
            return this;
        }

        @Override
        public SecurityScheme build() {
            if (!isReference(node)) {
                requireString(node, "type", "OpenAPI SecurityScheme");
            }
            return new SecurityScheme(node);
        }
    }

    /**
     * OpenAPI Security Requirement Object.
     */
    @Api.Preview
    public static final class SecurityRequirement {
        private final Map<String, Object> node;
        private final List<SchemeRequirement> schemes;

        private SecurityRequirement(Map<String, Object> node) {
            this.node = immutableMap(node);
            this.schemes = schemes(this.node);
        }

        /**
         * Create a new security requirement builder.
         *
         * @return builder
         */
        public static SecurityRequirementBuilder builder() {
            return new SecurityRequirementBuilder();
        }

        /**
         * Required schemes. All returned schemes are required together.
         *
         * @return required schemes
         */
        public List<SchemeRequirement> schemes() {
            return schemes;
        }

        private Map<String, Object> toNode() {
            return node;
        }

        private static List<SchemeRequirement> schemes(Map<String, Object> node) {
            List<SchemeRequirement> result = new ArrayList<>();
            node.forEach((name, scopes) -> result.add(new SchemeRequirement(name, stringList(scopes))));
            return Collections.unmodifiableList(result);
        }
    }

    /**
     * OpenAPI security requirement builder.
     */
    @Api.Preview
    public static final class SecurityRequirementBuilder
            implements io.helidon.common.Builder<SecurityRequirementBuilder, SecurityRequirement> {
        private final Map<String, Object> node = new LinkedHashMap<>();

        private SecurityRequirementBuilder() {
        }

        /**
         * Add a required security scheme to this requirement.
         *
         * @param name security scheme name
         * @param scopes required scopes
         * @return updated builder
         */
        public SecurityRequirementBuilder scheme(String name, List<String> scopes) {
            node.put(Objects.requireNonNull(name), List.copyOf(scopes));
            return this;
        }

        @Override
        public SecurityRequirement build() {
            return new SecurityRequirement(node);
        }
    }

    /**
     * Scheme requirement within an OpenAPI Security Requirement Object.
     */
    @Api.Preview
    public static final class SchemeRequirement {
        private final String name;
        private final List<String> scopes;

        private SchemeRequirement(String name, List<String> scopes) {
            this.name = name;
            this.scopes = scopes;
        }

        /**
         * Security scheme name.
         *
         * @return security scheme name
         */
        public String name() {
            return name;
        }

        /**
         * Required scopes.
         *
         * @return required scopes
         */
        public List<String> scopes() {
            return scopes;
        }
    }

    /**
     * OpenAPI document builder.
     */
    @Api.Preview
    public static final class Builder implements io.helidon.common.Builder<Builder, OpenApiDocument> {
        private final Map<String, Object> node = new LinkedHashMap<>();
        private final Map<String, String> pathTemplates = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Set the OpenAPI version string.
         *
         * @param openapi OpenAPI version
         * @return updated builder
         */
        public Builder openapi(String openapi) {
            node.put("openapi", Objects.requireNonNull(openapi));
            return this;
        }

        /**
         * Set the OpenAPI 3.2 document identity URI.
         *
         * @param self document identity URI
         * @return updated builder
         */
        public Builder self(String self) {
            node.put("$self", Objects.requireNonNull(self));
            return this;
        }

        /**
         * Set the JSON Schema dialect URI.
         *
         * @param jsonSchemaDialect JSON Schema dialect URI
         * @return updated builder
         */
        public Builder jsonSchemaDialect(String jsonSchemaDialect) {
            node.put("jsonSchemaDialect", Objects.requireNonNull(jsonSchemaDialect));
            return this;
        }

        /**
         * Set the required Info object values.
         *
         * @param title API title
         * @param version API version
         * @return updated builder
         */
        public Builder info(String title, String version) {
            return info(Info.builder()
                                .title(title)
                                .version(version)
                                .build());
        }

        /**
         * Set the Info object.
         *
         * @param info info
         * @return updated builder
         */
        public Builder info(Info info) {
            node.put("info", mutableMap(Objects.requireNonNull(info).toNode()));
            return this;
        }

        /**
         * Set the Info object.
         *
         * @param info consumer to update info builder
         * @return updated builder
         */
        public Builder info(Consumer<InfoBuilder> info) {
            InfoBuilder builder = Info.builder();
            info.accept(builder);
            return info(builder.build());
        }

        /**
         * Add a server.
         *
         * @param server server model
         * @return updated builder
         */
        public Builder server(Server server) {
            array(node, "servers").add(mutableMap(Objects.requireNonNull(server).toNode()));
            return this;
        }

        /**
         * Add a server.
         *
         * @param server consumer to update server builder
         * @return updated builder
         */
        public Builder server(Consumer<ServerBuilder> server) {
            ServerBuilder builder = Server.builder();
            server.accept(builder);
            return server(builder.build());
        }

        /**
         * Add or merge a path item.
         *
         * @param path OpenAPI path
         * @param pathItem path item
         * @return updated builder
         */
        public Builder path(String path, PathItem pathItem) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put(Objects.requireNonNull(path), mutableMap(Objects.requireNonNull(pathItem).toNode()));
            mergePathItems(object(node, "paths"), source, "paths", pathTemplates);
            return this;
        }

        /**
         * Add or merge a path item.
         *
         * @param path OpenAPI path
         * @param pathItem consumer to update path item builder
         * @return updated builder
         */
        public Builder path(String path, Consumer<PathItemBuilder> pathItem) {
            PathItemBuilder builder = PathItem.builder();
            pathItem.accept(builder);
            return path(path, builder.build());
        }

        /**
         * Add an extension to the paths object.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public Builder pathExtension(String name, JsonValue value) {
            OpenApiDocument.extension(object(node, "paths"), name, value);
            return this;
        }

        /**
         * Add or merge a webhook path item.
         *
         * @param name webhook name
         * @param pathItem path item
         * @return updated builder
         */
        public Builder webhook(String name, PathItem pathItem) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put(Objects.requireNonNull(name), mutableMap(Objects.requireNonNull(pathItem).toNode()));
            mergePathItems(object(node, "webhooks"), source, "webhooks", null);
            return this;
        }

        /**
         * Add or merge a webhook path item.
         *
         * @param name webhook name
         * @param pathItem consumer to update path item builder
         * @return updated builder
         */
        public Builder webhook(String name, Consumer<PathItemBuilder> pathItem) {
            PathItemBuilder builder = PathItem.builder();
            pathItem.accept(builder);
            return webhook(name, builder.build());
        }

        /**
         * Add or merge components.
         *
         * @param components components
         * @return updated builder
         */
        public Builder components(Components components) {
            OpenApiDocument.merge(object(node, "components"),
                                  mutableMap(Objects.requireNonNull(components).toNode()),
                                  "components");
            return this;
        }

        /**
         * Add or merge components.
         *
         * @param components consumer to update components builder
         * @return updated builder
         */
        public Builder components(Consumer<ComponentsBuilder> components) {
            ComponentsBuilder builder = Components.builder();
            components.accept(builder);
            return components(builder.build());
        }

        /**
         * Add a security requirement.
         *
         * @param name security scheme name
         * @param scopes security scopes
         * @return updated builder
         */
        public Builder securityRequirement(String name, List<String> scopes) {
            return securityRequirement(SecurityRequirement.builder()
                                               .scheme(name, scopes)
                                               .build());
        }

        /**
         * Add a security requirement.
         *
         * @param requirement security requirement
         * @return updated builder
         */
        public Builder securityRequirement(SecurityRequirement requirement) {
            array(node, "security").add(mutableMap(Objects.requireNonNull(requirement).toNode()));
            return this;
        }

        /**
         * Add a security requirement.
         *
         * @param requirement consumer to update security requirement builder
         * @return updated builder
         */
        public Builder securityRequirement(Consumer<SecurityRequirementBuilder> requirement) {
            SecurityRequirementBuilder builder = SecurityRequirement.builder();
            requirement.accept(builder);
            return securityRequirement(builder.build());
        }

        /**
         * Add a document tag.
         *
         * @param name tag name
         * @param description tag description
         * @return updated builder
         */
        public Builder tag(String name, String description) {
            return tag(Tag.builder()
                       .name(name)
                       .description(description)
                       .build());
        }

        /**
         * Add a document tag.
         *
         * @param tag tag model
         * @return updated builder
         */
        public Builder tag(Tag tag) {
            array(node, "tags").add(mutableMap(Objects.requireNonNull(tag).toNode()));
            return this;
        }

        /**
         * Add a document tag.
         *
         * @param tag consumer to update tag builder
         * @return updated builder
         */
        public Builder tag(Consumer<TagBuilder> tag) {
            TagBuilder builder = Tag.builder();
            tag.accept(builder);
            return tag(builder.build());
        }

        /**
         * Set external documentation.
         *
         * @param externalDocs external documentation
         * @return updated builder
         */
        public Builder externalDocs(ExternalDocs externalDocs) {
            node.put("externalDocs", mutableMap(Objects.requireNonNull(externalDocs).toNode()));
            return this;
        }

        /**
         * Set external documentation.
         *
         * @param externalDocs consumer to update external documentation builder
         * @return updated builder
         */
        public Builder externalDocs(Consumer<ExternalDocsBuilder> externalDocs) {
            ExternalDocsBuilder builder = ExternalDocs.builder();
            externalDocs.accept(builder);
            return externalDocs(builder.build());
        }

        /**
         * Add an extension.
         * <p>
         * Extension names must start with {@code x-}.
         *
         * @param name extension name
         * @param value extension value
         * @return updated builder
         */
        public Builder extension(String name, JsonValue value) {
            OpenApiDocument.extension(node, name, value);
            return this;
        }

        /**
         * Merge another OpenAPI document into this builder.
         *
         * @param document document to merge
         * @return updated builder
         * @throws IllegalStateException if both documents define conflicting values or the same path operation
         */
        public Builder merge(OpenApiDocument document) {
            OpenApiDocument.merge(node, mutableMap(Objects.requireNonNull(document).toNode()), "",
                                  pathTemplates);
            return this;
        }

        Builder mergeNode(Map<String, Object> source) {
            OpenApiDocument.merge(node, Objects.requireNonNull(source), "", pathTemplates);
            return this;
        }

        Map<String, Object> node() {
            return node;
        }

        @Override
        public OpenApiDocument build() {
            return new OpenApiDocument(node);
        }
    }

    private static List<Parameter> parameterList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Parameter> result = new ArrayList<>();
        list.forEach(item -> objectValue(item).ifPresent(node -> result.add(new Parameter(node))));
        return Collections.unmodifiableList(result);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Map<String, Object> parent, String name) {
        return (Map<String, Object>) parent.computeIfAbsent(name, ignored -> new LinkedHashMap<String, Object>());
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Map<String, Object> parent, String name) {
        return (List<Object>) parent.computeIfAbsent(name, ignored -> new ArrayList<>());
    }

    private static JsonObject jsonObject(Map<String, Object> source) {
        Map<String, JsonValue> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, jsonValue(value)));
        return JsonObject.create(result);
    }

    private static JsonValue jsonValue(Object value) {
        switch (value) {
        case null -> {
            return JsonNull.instance();
        }
        case JsonValue jsonValue -> {
            return jsonValue;
        }
        case String string -> {
            return JsonString.create(string);
        }
        case Boolean bool -> {
            return JsonBoolean.create(bool);
        }
        case BigDecimal number -> {
            return JsonNumber.create(number);
        }
        case Number number -> {
            return jsonNumber(number);
        }
        case Map<?, ?> map -> {
            Map<String, Object> object = new LinkedHashMap<>();
            map.forEach((key, item) -> object.put(String.valueOf(key), item));
            return jsonObject(object);
        }
        case List<?> list -> {
            return JsonArray.create(list.stream()
                                            .map(OpenApiDocument::jsonValue)
                                            .toList());
        }
        default -> {
        }
        }
        throw new IllegalArgumentException("Unsupported OpenAPI document value type: " + value.getClass().getName());
    }

    private static JsonNumber jsonNumber(Number number) {
        if (number instanceof Byte
                || number instanceof Short
                || number instanceof Integer
                || number instanceof Long) {
            return JsonNumber.create(number.longValue());
        }
        if (number instanceof BigInteger bigInteger) {
            return JsonNumber.create(new BigDecimal(bigInteger));
        }
        return JsonNumber.create(new BigDecimal(number.toString()));
    }

    private static Map<String, Object> jsonObject(JsonObject object) {
        Map<String, Object> result = new LinkedHashMap<>();
        object.keysAsStrings()
                .forEach(key -> object.value(key)
                        .ifPresent(value -> result.put(key, jsonValue(value))));
        return result;
    }

    private static Object jsonValue(JsonValue value) {
        return switch (value.type()) {
        case OBJECT -> jsonObject(value.asObject());
        case ARRAY -> value.asArray()
                .values()
                .stream()
                .map(OpenApiDocument::jsonValue)
                .toList();
        case STRING -> value.asString().value();
        case NUMBER -> value.asNumber().bigDecimalValue();
        case BOOLEAN -> value.asBoolean().value();
        case NULL -> null;
        case UNKNOWN -> value.toString();
        };
    }
}
