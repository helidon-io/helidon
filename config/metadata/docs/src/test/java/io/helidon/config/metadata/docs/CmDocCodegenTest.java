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
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import io.helidon.config.metadata.model.CmModel;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link CmDocCodegenTest}.
 */
class CmDocCodegenTest {

    @Test
    void testBaseline() throws Exception {
        var testDir = testDir("baseline");
        var outputDir = generate(testDir);
        assertThat(outputDir, isDirectory(testDir.resolve("expected")));
    }

    @Test
    void testRootChildTypePages() throws Exception {
        var testDir = testDir("root-child-type-pages");
        var outputDir = generate(testDir);
        assertThat(outputDir, isDirectory(testDir.resolve("expected")));
    }

    @Test
    void testSortRootEntries() throws Exception {
        var testDir = testDir("sort-root-entries");
        var outputDir = generate(testDir);
        assertThat(outputDir, isDirectory(testDir.resolve("expected")));
    }

    @Test
    void testTypePageNestedOptions() throws Exception {
        var testDir = testDir("type-page-nested-options");
        var outputDir = generate(testDir);
        assertThat(outputDir, isDirectory(testDir.resolve("expected")));
    }

    @Test
    void testTypePageDottedOptions() throws Exception {
        var testDir = testDir("type-page-dotted-options");
        var outputDir = generate(testDir);
        assertThat(outputDir, isDirectory(testDir.resolve("expected")));
    }

    @Test
    void testJavaTypeNotation() throws Exception {
        var testDir = testDir("java-type-notation");
        var outputDir = generate(testDir);
        assertThat(outputDir, isDirectory(testDir.resolve("expected")));
    }

    @Test
    void testFileNameCollisions() throws Exception {
        var testDir = testDir("file-name-collisions");
        var outputDir = generate(testDir);
        assertThat(outputDir, isDirectory(testDir.resolve("expected")));
    }

    @Test
    void testUnderscoreFileNames() throws Exception {
        var testDir = testDir("underscore-file-names");
        var outputDir = generate(testDir);
        assertThat(outputDir, isDirectory(testDir.resolve("expected")));
    }

    @Test
    void testMergedServerRoot() throws Exception {
        var testDir = testDir("merged-server-root");
        var outputDir = generate(testDir);
        assertThat(outputDir, isDirectory(testDir.resolve("expected")));
    }

    @Test
    void testMergedTypeSections() throws Exception {
        var testDir = testDir("merged-type-sections");
        var outputDir = generate(testDir);
        assertThat(outputDir, isDirectory(testDir.resolve("expected")));
    }

    @Test
    void testProviderSort() throws Exception {
        var testDir = testDir("provider-sort");
        var outputDir = generate(testDir);
        assertThat(outputDir, isDirectory(testDir.resolve("expected")));
    }

    @Test
    void testProviderNoImpls() throws Exception {
        var testDir = testDir("provider-no-impls");
        var outputDir = generate(testDir);
        assertThat(outputDir, isDirectory(testDir.resolve("expected")));
    }

    @Test
    void testProviderNonContractType() throws Exception {
        var testDir = testDir("provider-non-contract-type");
        var outputDir = generate(testDir);
        assertThat(outputDir, isDirectory(testDir.resolve("expected")));
    }

    @Test
    void testUnreachableProviderContract() throws Exception {
        var testDir = testDir("unreachable-provider-contract");
        var outputDir = generate(testDir);
        assertThat(outputDir, isDirectory(testDir.resolve("expected")));
    }

    @Test
    void testUnreachableHelperType() throws Exception {
        var testDir = testDir("unreachable-helper-type");
        var outputDir = generate(testDir);
        assertThat(outputDir, isDirectory(testDir.resolve("expected")));
    }

    @Test
    void testDetachedProviderUsage() throws Exception {
        var testDir = testDir("detached-provider-usage");
        var outputDir = generate(testDir);
        assertThat(outputDir, isDirectory(testDir.resolve("expected")));
    }

    @Test
    void testProviderDuplicateKeys() throws Exception {
        var testDir = testDir("provider-duplicate-keys");
        var outputDir = generate(testDir);
        assertThat(outputDir, isDirectory(testDir.resolve("expected")));
    }

    @Test
    void testProviderDuplicateContracts() throws Exception {
        var testDir = testDir("provider-duplicate-contracts");
        var outputDir = generate(testDir);
        assertThat(outputDir, isDirectory(testDir.resolve("expected")));
    }

    static Path testDir(String testName) throws Exception {
        return classesDir().resolve(testName);
    }

    static Path generate(Path testDir) throws Exception {
        var outputDir = outputDir(testDir.getFileName().toString());
        try (var is = Files.newInputStream(testDir.resolve("config-metadata.json"))) {
            var metadata = CmModel.fromJson(is);
            new CmDocCodegen(outputDir, metadata).process();
        }
        return outputDir;
    }

    static Matcher<Path> isDirectory(Path expectedDir) {
        return new DirectoryMatcher(expectedDir);
    }

    static List<Path> files(Path dir) throws Exception {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile)
                    .map(dir::relativize)
                    .sorted()
                    .toList();
        }
    }

    static Path outputDir(String testName) throws Exception {
        var targetDir = classesDir().getParent();
        var outputDir = targetDir.resolve("config-docs-ut").resolve(testName).resolve("output");
        for (int i = 1; Files.exists(outputDir); i++) {
            outputDir = targetDir.resolve("config-docs-ut-" + i).resolve(testName).resolve("output");
        }
        return outputDir;
    }

    static Path classesDir() throws Exception {
        return Paths.get(CmDocCodegenTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    static final class DirectoryMatcher extends TypeSafeDiagnosingMatcher<Path> {
        private final Path expectedDir;

        private DirectoryMatcher(Path expectedDir) {
            this.expectedDir = expectedDir;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("directory matching ").appendValue(expectedDir);
        }

        @Override
        protected boolean matchesSafely(Path actualDir, Description mismatchDescription) {
            try {
                var expectedFiles = files(expectedDir);
                var actualFiles = files(actualDir);
                if (!actualFiles.equals(expectedFiles)) {
                    mismatchDescription.appendText("had files ").appendValue(actualFiles);
                    return false;
                }
                for (var expectedFile : expectedFiles) {
                    var actual = Files.readString(actualDir.resolve(expectedFile));
                    var expected = Files.readString(expectedDir.resolve(expectedFile));
                    if (!actual.equals(expected)) {
                        mismatchDescription.appendText("had different content in ").appendValue(expectedFile);
                        return false;
                    }
                }
                return true;
            } catch (Exception ex) {
                mismatchDescription.appendText("failed to compare directories: ").appendValue(ex.getMessage());
                return false;
            }
        }
    }
}
