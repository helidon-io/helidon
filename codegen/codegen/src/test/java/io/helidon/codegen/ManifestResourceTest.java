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

package io.helidon.codegen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.TypeName;
import io.helidon.metadata.MetadataConstants;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class ManifestResourceTest {
    private static final String LOCATION = MetadataConstants.LOCATION + "/test.module/service.loader";

    @Test
    void combinesDeferredResourceUpdatesIntoOneWrite() {
        var filer = new TestFiler();
        ManifestResource manifest = ManifestResource.create(filer);
        FilerTextResource first = manifest.deferredTextResource(LOCATION);
        FilerTextResource second = manifest.deferredTextResource(LOCATION);

        assertThat(second, sameInstance(first));

        first.lines(List.of("# header", "example.First"));
        first.write();
        List<String> combined = new ArrayList<>(second.lines());
        combined.add("example.Second");
        second.lines(combined);
        second.write();
        manifest.add(LOCATION);

        assertThat(filer.resource(LOCATION).writeCount(), is(0));

        manifest.write();

        assertThat(filer.resource(LOCATION).lines(), is(List.of("# header", "example.First", "example.Second")));
        assertThat(filer.resource(LOCATION).writeCount(), is(1));
        assertThat(filer.resource(MetadataConstants.LOCATION + "/" + MetadataConstants.MANIFEST_FILE).lines(),
                   is(List.of(MetadataConstants.MANIFEST_ID_LINE, LOCATION)));
    }

    private static final class TestFiler implements CodegenFiler {
        private final Map<String, TestTextResource> resources = new HashMap<>();

        @Override
        public Path writeSourceFile(ClassModel classModel, Object... originatingElements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path writeSourceFile(TypeName type, String content, Object... originatingElements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path writeResource(byte[] resource, String location, Object... originatingElements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FilerTextResource textResource(String location, Object... originatingElements) {
            return resource(location);
        }

        private TestTextResource resource(String location) {
            return resources.computeIfAbsent(location, _ -> new TestTextResource());
        }
    }

    private static final class TestTextResource implements FilerTextResource {
        private List<String> lines = List.of();
        private int writeCount;

        @Override
        public List<String> lines() {
            return lines;
        }

        @Override
        public void lines(List<String> newLines) {
            lines = List.copyOf(newLines);
        }

        @Override
        public void write() {
            writeCount++;
        }

        private int writeCount() {
            return writeCount;
        }
    }
}
