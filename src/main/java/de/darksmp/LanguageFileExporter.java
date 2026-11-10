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

    public void exportIfMissing() throws IOException {
        Files.createDirectories(langFolder);
        for (String file : languageFiles) {
            Path targetFile = langFolder.resolve(file);
            if (!Files.exists(targetFile)) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("lang/" + file)) {
                    if (in == null) {
                        throw new IOException("Missing bundled language file: " + file);
                    }
                    Files.copy(in, targetFile);
                }
            }
        }
    }
}
