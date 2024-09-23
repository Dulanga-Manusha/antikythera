package com.cloud.api.generator;

import com.cloud.api.configurations.Settings;
import com.cloud.api.constants.Constants;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static com.cloud.api.generator.ClassProcessor.*;
import static org.junit.jupiter.api.Assertions.*;

class RestControllerParserTest {

    private RestControllerParser parser;
    private Path path;
    private static String basePath;
    private static String outputPath;

    @BeforeEach
    void setUp() throws IOException {
        Settings.loadConfigMap();
        basePackage = Settings.getProperty(Constants.BASE_PACKAGE).toString();
        basePath = Settings.getProperty(Constants.BASE_PATH).toString();
        outputPath = Settings.getProperty(Constants.OUTPUT_PATH).toString();

        String controllers = Settings.getProperty(Constants.CONTROLLERS).toString();
        parser = new RestControllerParser(Paths.get(basePath,controllers.replaceAll("\\.","/")).toFile());
    }


    @Test
    void start_processesRestControllerSuccessfully() throws IOException {
        parser.start();

        File srcDirectory = new File(outputPath + "/src/main/java/");
        File testDirectory = new File(outputPath + "/src/test/java/");
        assertTrue(srcDirectory.exists() && srcDirectory.isDirectory());
        assertTrue(testDirectory.exists() && testDirectory.isDirectory());
    }

    @Test
    void getFields_returnsEmptyMapWhenNoFieldsPresent() {
        CompilationUnit cu = StaticJavaParser.parse("public class TempClass {}");
        Map<String, FieldDeclaration> fields = parser.getFields(cu, "TempClass");
        assertTrue(fields.isEmpty());
    }

    @Test
    void getFields_returnsMapWithFieldNamesAndTypes() throws IOException {
        Path testFilePath = Paths.get("src/test/resources/sources/com/csi/dto/SimpleDTO.java");
        CompilationUnit cu = StaticJavaParser.parse(testFilePath);

        Map<String, FieldDeclaration> fields = parser.getFields(cu, "SimpleDTO");
        assertFalse(fields.isEmpty());
        assertEquals(fields.size(), 3);
        assertTrue(fields.containsKey("id"));
        assertEquals(fields.get("id").toString(), "private Long id;");
        assertTrue(fields.containsKey("name"));
        assertEquals(fields.get("name").toString(), "private String name;");
        assertTrue(fields.containsKey("description"));
        assertEquals(fields.get("description").toString(), "private String description;");
    }
}
