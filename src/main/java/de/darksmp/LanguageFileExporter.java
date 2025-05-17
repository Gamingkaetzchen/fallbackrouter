package de.darksmp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class LanguageFileExporter {

    private final Path langFolder;
    private final List<String> languageFiles = List.of("en.yml", "de.yml");

    public LanguageFileExporter(Path dataDirectory) {
        this.langFolder = dataDirectory.resolve("lang");
    }

    public void exportIfMissing() {
        try {
            Files.createDirectories(langFolder);
            for (String file : languageFiles) {
                Path targetFile = langFolder.resolve(file);
                if (!Files.exists(targetFile)) {
                    try (InputStream in = getClass().getClassLoader().getResourceAsStream("lang/" + file)) {
                        if (in != null) {
                            Files.copy(in, targetFile);
                            System.out.println("[FallbackRouter] Exported " + file + " to lang folder.");
                        } else {
                            System.out.println("[FallbackRouter] WARNING: Could not find " + file + " in JAR resources.");
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[FallbackRouter] Failed to export language files:");
            e.printStackTrace();
        }
    }
}
