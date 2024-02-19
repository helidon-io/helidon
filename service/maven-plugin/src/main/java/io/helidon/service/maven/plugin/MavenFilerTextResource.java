package io.helidon.service.maven.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.FilerTextResource;

class MavenFilerTextResource implements FilerTextResource {
    private final Path resourcePath;
    private final ArrayList<String> currentLines;

    boolean modified;

    MavenFilerTextResource(Path resourcePath) {
        this.resourcePath = resourcePath;
        this.currentLines = new ArrayList<>();
    }

    MavenFilerTextResource(Path resourcePath, List<String> lines) {
        this.resourcePath = resourcePath;
        this.currentLines = new ArrayList<>(lines);
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
            try {
                Files.write(resourcePath, currentLines, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            } catch (IOException e) {
                throw new CodegenException("Failed to write resource " + resourcePath.toAbsolutePath(), e);
            }
        }
    }
}
