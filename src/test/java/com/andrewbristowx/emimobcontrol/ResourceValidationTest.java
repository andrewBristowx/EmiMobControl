package com.andrewbristowx.emimobcontrol;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceValidationTest {
    @Test
    void everyJsonResourceParses() throws Exception {
        Path resources = Path.of("src", "main", "resources");
        try (var paths = Files.walk(resources)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".json")).toList()) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    assertTrue(JsonParser.parseReader(reader).isJsonObject(), path.toString());
                }
            }
        }
    }
}
