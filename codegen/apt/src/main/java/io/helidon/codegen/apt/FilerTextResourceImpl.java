package io.helidon.codegen.apt;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.FilerTextResource;

import static java.nio.charset.StandardCharsets.UTF_8;

class FilerTextResourceImpl implements FilerTextResource {
    private final Filer filer;
    private final String location;
    private final Element[] originatingElements;
    private final FileObject originalResource; // may be null
    private final List<String> currentLines;

    boolean modified;

    FilerTextResourceImpl(Filer filer, String location, Element[] originatingElements) {
        this.filer = filer;
        this.location = location;
        this.originatingElements = originatingElements;
        this.originalResource = null;
        this.currentLines = new ArrayList<>();
    }

    FilerTextResourceImpl(Filer filer,
                          String location,
                          Element[] originatingElements,
                          FileObject originalResource,
                          List<String> existingLines) {
        this.filer = filer;
        this.location = location;
        this.originatingElements = originatingElements;
        this.originalResource = originalResource;
        this.currentLines = new ArrayList<>(existingLines);
    }

    @Override
    public List<String> lines() {
        return List.copyOf(currentLines);
    }

    @Override
    public void lines(List<String> newLines) {
        currentLines.clear();
        currentLines.addAll(newLines);
        modified = true;
    }

    @Override
    public void write() {
        if (modified) {
            if (originalResource != null) {
                originalResource.delete();
            }
            try {
                FileObject newResource = filer.createResource(StandardLocation.CLASS_OUTPUT,
                                                              "",
                                                              location,
                                                              originatingElements);
                try (var pw = new PrintWriter(new OutputStreamWriter(newResource.openOutputStream(), UTF_8))) {
                    for (String line : currentLines) {
                        pw.println(line);
                    }
                }
            } catch (Exception e) {
                throw new CodegenException("Failed to create resource: " + location, e);
            }
        }
    }
}
