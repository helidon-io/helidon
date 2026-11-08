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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.helidon.config.metadata.model.CmModel;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

class ReusableTypeOptionsTest {
    private static final String ALLOW_LIST = "com.acme.AllowList.md";
    private static final String ALPHA_CONSUMER = "com.acme.AlphaConsumer.md";
    private static final String ZULU_CONSUMER = "com.acme.ZuluConsumer.md";
    private static final Pattern LINKS = Pattern.compile("href=\"([^\"]+)\"|\\]\\(([^)]+)\\)");

    @Test
    void testGenericOptionsDoNotDependOnConsumers() throws Exception {
        var unused = resolver(model(false, false));
        var zulu = resolver(model(true, false));
        var both = resolver(model(true, true));

        var expected = genericContent(page(unused, ALLOW_LIST));
        assertThat("Generic content with one consumer", genericContent(page(zulu, ALLOW_LIST)), is(expected));
        assertThat("Generic content after adding an earlier consumer", genericContent(page(both, ALLOW_LIST)), is(expected));

        var allowList = page(both, ALLOW_LIST);
        assertThat(allowList.description(), is("Reusable allow and deny rules."));
        assertThat(allowList.tables().standard().rows(), contains(
                new CmPage.Row("allow.enabled", "Boolean", "true", "Enable allow rules.", "", ""),
                new CmPage.Row("allow.values.exact", "List<String>", "", "Exact values accepted by this list.", "", "")));
        assertThat(allowList.tables().standard().hasTypes(), is(true));
        assertThat(allowList.tables().standard().hasDefaults(), is(true));
        assertThat(allowList.tables().experimental().rows(), contains(
                new CmPage.Row("allow.values.patterns", "List<String>", "", "Patterns accepted by this list.", "", "")));
        assertThat(allowList.tables().deprecated().rows(), contains(
                new CmPage.Row("deny.exact", "List<String>", "", "Exact values rejected by this list.", "", "")));

        for (var resolver : List.of(unused, zulu, both)) {
            var content = genericContent(page(resolver, ALLOW_LIST)).toString();
            assertThat(content, not(containsString("Alpha")));
            assertThat(content, not(containsString("Zulu")));
            assertThat(content, not(containsString("trusted-proxies")));
            assertThat(content, not(containsString("approved-hosts")));
            assertThat("No invented nested types for AllowList",
                    resolver.pages().stream().map(CmPage::typeName).toList(),
                    not(hasItem(startsWith("com.acme.AllowList."))));
        }
    }

    @Test
    void testKnownTypesKeepTheirRootAndOptionTargets() throws Exception {
        var resolver = resolver(model(true, true));
        assertThat(row(resolver.rootPage(), "alpha").fileName(), is(ALPHA_CONSUMER));
        assertThat(row(resolver.rootPage(), "zulu").fileName(), is(ZULU_CONSUMER));

        var zulu = page(resolver, ZULU_CONSUMER);
        var trustedProxies = row(zulu, "trusted-proxies");
        assertThat(trustedProxies.fileName(), is(ALLOW_LIST));
        assertThat(trustedProxies.type(), is("AllowList"));
        assertThat(trustedProxies.description(), is("Zulu trusted proxies."));
        assertThat(row(zulu, "trusted-proxies.allow.values.exact").description(), is("Zulu-only exact values."));
        assertThat(row(zulu, "trusted-proxies.allow.extra").description(), is("Zulu-only option."));
        assertThat(rows(zulu).map(CmPage.Row::key).toList(),
                hasItems("trusted-proxies", "trusted-proxies.allow.values.exact", "trusted-proxies.allow.extra"));

        assertThat("Original options reachable through the real AllowList type link",
                reachableDescriptions(resolver, trustedProxies.fileName()), hasItems(
                        "Exact values accepted by this list.",
                        "Patterns accepted by this list.",
                        "Enable allow rules.",
                        "Exact values rejected by this list."));

        var alpha = page(resolver, ALPHA_CONSUMER);
        assertThat(row(alpha, "approved-hosts").fileName(), is(ALLOW_LIST));
        assertThat(row(alpha, "approved-hosts.allow.values.exact").description(), is("Alpha-only exact values."));
        assertThat(row(alpha, "approved-hosts.deny.extra").description(), is("Alpha-only option."));
    }

    @Test
    void testConsumerLocalGroupUsageLinksToOwningOption() throws Exception {
        var resolver = resolver(model(true, true));
        var expectedUsages = List.of(
                new CmPage.Usage("alpha.approved-hosts.extra", ALPHA_CONSUMER, "approved-hosts"),
                new CmPage.Usage("zulu.trusted-proxies.extra", ZULU_CONSUMER, "trusted-proxies"));
        for (var usage : expectedUsages) {
            var pages = resolver.pages().stream()
                    .filter(page -> page.key().equals("config/" + usage.path()))
                    .toList();
            assertThat("Contextual group for " + usage.path(), pages.size(), is(1));
            assertThat("Owning option for " + usage.path(), pages.getFirst().usages(), contains(usage));
        }
    }

    @Test
    void testUsagesKeepConsumerNavigationAndGeneratedLinksResolve() throws Exception {
        var metadata = model(true, true);
        var resolver = resolver(metadata);
        var allowList = page(resolver, ALLOW_LIST);
        assertThat(allowList.usages().stream().map(CmPage.Usage::path).toList(),
                hasItems("alpha.approved-hosts", "zulu.trusted-proxies"));

        for (var usage : allowList.usages()) {
            assertThat("Parent type for " + usage.path(), usage.fileName(),
                    is(usage.path().startsWith("alpha.") ? ALPHA_CONSUMER : ZULU_CONSUMER));
            var descriptions = reachableDescriptions(resolver, usage.fileName());
            var expected = usage.path().startsWith("alpha.")
                    ? "Alpha-only exact values."
                    : "Zulu-only exact values.";
            assertThat("Context reachable from " + usage.path(), descriptions, hasItem(expected));
        }

        var outputDir = CmDocCodegenTest.outputDir("reusable-type-options");
        new CmDocCodegen(outputDir, metadata).process();
        for (var file : CmDocCodegenTest.files(outputDir)) {
            var content = Files.readString(outputDir.resolve(file));
            var links = LINKS.matcher(content);
            while (links.find()) {
                var link = links.group(1) == null ? links.group(2) : links.group(1);
                assertLink(outputDir, file, link);
            }
        }
    }

    private static CmModel model(boolean zulu, boolean alpha) throws Exception {
        var testDir = CmDocCodegenTest.testDir("reusable-type-options");
        try (var input = Files.newInputStream(testDir.resolve("config-metadata.json"))) {
            var metadata = CmModel.fromJson(input);
            return CmModel.of(metadata.modules().stream()
                    .filter(module -> !module.module().equals("zulu-consumer") || zulu)
                    .filter(module -> !module.module().equals("alpha-consumer") || alpha)
                    .toList());
        }
    }

    private static CmPageResolver resolver(CmModel metadata) {
        return new CmPageResolver(metadata, "config_reference.md", ".md", "manifest.md");
    }

    private static CmPage page(CmPageResolver resolver, String fileName) {
        var pages = resolver.pages().stream().filter(page -> page.fileName().equals(fileName)).toList();
        assertThat("Page " + fileName, pages.size(), is(1));
        return pages.getFirst();
    }

    private static CmPage.Row row(CmPage page, String key) {
        var rows = rows(page).filter(row -> row.key().equals(key)).toList();
        assertThat("Row " + page.fileName() + "#" + key, rows.size(), is(1));
        return rows.getFirst();
    }

    private static Stream<CmPage.Row> rows(CmPage page) {
        return Stream.of(page.tables().standard(), page.tables().experimental(), page.tables().deprecated())
                .flatMap(table -> table.rows().stream());
    }

    private static CmPage genericContent(CmPage page) {
        return new CmPage(page.kind(), page.key(), page.typeName(), page.fileName(), page.description(), page.tables(),
                page.mergedTypes(), page.dependentTypes(), List.of());
    }

    private static List<String> reachableDescriptions(CmPageResolver resolver, String fileName) {
        var pending = new ArrayDeque<String>();
        var visited = new HashSet<String>();
        var descriptions = new ArrayList<String>();
        pending.add(fileName);
        while (!pending.isEmpty()) {
            var current = pending.removeFirst();
            if (visited.add(current)) {
                var page = page(resolver, current);
                rows(page).forEach(row -> {
                    descriptions.add(row.description());
                    if (!row.fileName().isEmpty()) {
                        pending.add(row.fileName());
                    }
                });
            }
        }
        return descriptions;
    }

    private static void assertLink(Path outputDir, Path source, String link) throws Exception {
        var parts = link.split("#", 2);
        var target = outputDir.resolve(parts[0]);
        assertThat("Link from " + source + " to " + link, Files.isRegularFile(target), is(true));
        if (parts.length == 2 && !parts[1].isEmpty()) {
            assertThat("Anchor from " + source + " to " + link,
                    Files.readString(target), containsString("<a id=\"" + parts[1] + "\"></a>"));
        }
    }
}
