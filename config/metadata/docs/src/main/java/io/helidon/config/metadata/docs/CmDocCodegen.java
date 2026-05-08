/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;

import io.helidon.config.metadata.model.CmModel;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.context.MethodValueResolver;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

/**
 * Config docs generator.
 *
 * @see #process()
 */
class CmDocCodegen {
    private static final System.Logger LOGGER = System.getLogger(CmDocCodegen.class.getName());
    private static final String PAGE_EXT = ".adoc";
    private static final String CONFIG_REFERENCE = "config_reference" + PAGE_EXT;
    private static final String MANIFEST = "manifest" + PAGE_EXT;

    private final Path outputDir;
    private final Template rootTemplate;
    private final Template manifestTemplate;
    private final Template configTemplate;
    private final Template enumTemplate;
    private final Template contractTemplate;
    private final CmPageResolver resolver;

    /**
     * Create a new instance.
     *
     * @param outputDir output directory
     * @param metadata  config metadata
     */
    CmDocCodegen(Path outputDir, CmModel metadata) {
        var loader = new ClassPathTemplateLoader("/io/helidon/config/metadata/docs");
        var handlebars = new Handlebars(loader);
        this.rootTemplate = template(handlebars, CONFIG_REFERENCE);
        this.manifestTemplate = template(handlebars, MANIFEST);
        this.configTemplate = template(handlebars, "config" + PAGE_EXT);
        this.enumTemplate = template(handlebars, "enum" + PAGE_EXT);
        this.contractTemplate = template(handlebars, "contract" + PAGE_EXT);
        this.resolver = new CmPageResolver(metadata, CONFIG_REFERENCE, PAGE_EXT, MANIFEST);
        this.outputDir = outputDir;
    }

    /**
     * Process the config metadata and generate the corresponding
     * documentation.
     */
    void process() {
        var fileNames = new LinkedHashSet<String>();

        fileNames.add(generateFile(CONFIG_REFERENCE, rootTemplate, resolver.rootPage()));
        fileNames.add(generateFile(MANIFEST, manifestTemplate, resolver));
        for (var page : resolver.pages()) {
            var fileName = switch (page.kind()) {
                case CONFIG -> generateFile(page.fileName(), configTemplate, page);
                case CONTRACT -> generateFile(page.fileName(), contractTemplate, page);
                case ENUM -> generateFile(page.fileName(), enumTemplate, page);
                case ROOT -> throw new IllegalStateException("Unexpected root page in generated pages");
            };
            fileNames.add(fileName);
        }

        try (var stream = Files.walk(outputDir)
                .filter(Files::isRegularFile)
                .filter(path -> !fileNames.contains(outputDir.relativize(path).toString()))) {
            var toRemove = stream.toList();
            for (var file : toRemove) {
                LOGGER.log(Level.INFO, "Removing obsolete file: {0}", file);
                Files.delete(file);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private String generateFile(String fileName, Template template, Object model) {
        try {
            LOGGER.log(Level.INFO, "Generating {0}", fileName);
            var outputFile = outputDir.resolve(fileName);
            var outputFileParent = outputFile.getParent();
            if (outputFileParent != null) {
                Files.createDirectories(outputFileParent);
            }
            try (var writer = Files.newBufferedWriter(outputFile, TRUNCATE_EXISTING, CREATE)) {
                var context = Context.newBuilder(model)
                        .push(ValueResolverImpl.INSTANCE)
                        .build();
                var rendered = template.apply(context).replaceAll("\\n+\\z", "\n");
                writer.write(rendered);
            }
            return fileName;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to generate: " + fileName, ex);
        }
    }

    private static Template template(Handlebars handlebars, String template) {
        try {
            return handlebars.compile(template);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load template: " + template, e);
        }
    }

    private static class ValueResolverImpl extends MethodValueResolver {

        static final ValueResolverImpl INSTANCE = new ValueResolverImpl();

        @Override
        protected boolean isPublic(Method member) {
            return true;
        }
    }
}
