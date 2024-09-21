package com.cloud.api.generator;

import com.cloud.api.configurations.Settings;
import com.cloud.api.constants.Constants;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.cloud.api.generator.ClassProcessor.basePackage;
import static org.junit.jupiter.api.Assertions.*;

public class DTOHandlerTest {
    private ClassProcessor classProcessor;
    private DTOHandler handler;
    private DTOHandler.TypeCollector typeCollector;

    private static String basePath;
    private static String controllers;
    private static String outputPath;

    @BeforeEach
    void loadConfigMapBeforeEach() throws IOException {
        Settings.loadConfigMap();
        basePath = Settings.getProperty(Constants.BASE_PATH).toString();
        controllers = Settings.getProperty(Constants.CONTROLLERS).toString();
        outputPath = Settings.getProperty(Constants.OUTPUT_PATH).toString();

        classProcessor = new ClassProcessor();
        handler = new DTOHandler();
        typeCollector = handler.new TypeCollector();
    }

    // -------- handleStaticImports -------- //
    @Test
    void handleStaticImportsAddsStaticImportsToDependencies() {
        NodeList<ImportDeclaration> imports = new NodeList<>();
        String util = String.format("%s.util", basePackage);
        String staticImport = String.format("%s.staticImport", util);
        imports.add(new ImportDeclaration(staticImport, true, false));

        handler.handleStaticImports(imports);

        assertEquals(1, handler.dependencies.size());
        for (String dependency : handler.dependencies) {
            assertTrue(dependency.startsWith(basePackage));
            assertEquals(util, dependency);
        }
    }

    @Test
    void handleStaticImportsAddsWildcardStaticImportsToDependencies() {
        NodeList<ImportDeclaration> imports = new NodeList<>();
        String wildcardStaticImport = String.format("%s.staticImport", basePackage);
        imports.add(new ImportDeclaration(wildcardStaticImport, true, true));

        handler.handleStaticImports(imports);

        assertEquals(1, handler.dependencies.size());
        for (String dependency : handler.dependencies) {
            assertTrue(dependency.startsWith(basePackage));
            assertEquals(wildcardStaticImport, dependency);
        }
    }

    @Test
    void handleStaticImportsIgnoresNonStaticImports() {
        NodeList<ImportDeclaration> imports = new NodeList<>();
        String nonStaticImport =  String.format("%s.NonStaticImport", basePackage);
        imports.add(new ImportDeclaration(nonStaticImport, false, false));

        handler.handleStaticImports(imports);

        assertEquals(0, handler.dependencies.size());
        assertFalse(handler.dependencies.contains(nonStaticImport));
    }

    @Test
    void handleStaticImportsIgnoresImportsNotStartingWithBasePackage() {
        NodeList<ImportDeclaration> imports = new NodeList<>();
        String otherPackageStaticImport = "com.otherpackage.StaticImport";
        imports.add(new ImportDeclaration(otherPackageStaticImport, true, false));

        handler.handleStaticImports(imports);

        assertEquals(0, handler.dependencies.size());
        assertFalse(classProcessor.dependencies.contains(otherPackageStaticImport));
    }

    @Test
    void handleStaticImportsAddAllImportsToDependencies() {
        NodeList<ImportDeclaration> imports = new NodeList<>();
        String util = String.format("%s.util", basePackage);
        String staticImport = String.format("%s.staticImport", util);
        imports.add(new ImportDeclaration(staticImport, true, false));

        String wildcardStaticImport = String.format("%s.staticImport", basePackage);
        imports.add(new ImportDeclaration(wildcardStaticImport, true, true));

        String nonStaticImport =  String.format("%s.NonStaticImport", basePackage);
        imports.add(new ImportDeclaration(nonStaticImport, false, false));

        String otherPackageStaticImport = "com.otherpackage.StaticImport";
        imports.add(new ImportDeclaration(otherPackageStaticImport, true, false));

        handler.handleStaticImports(imports);

        assertEquals(2, handler.dependencies.size());
        for (String dependency : handler.dependencies) {
            assertTrue(dependency.startsWith(basePackage));
            assertTrue(dependency.equals(util) || dependency.equals(wildcardStaticImport));
            assertFalse(dependency.equals(nonStaticImport) || dependency.equals(otherPackageStaticImport));
        }
    }

    // -------- addLombok -------- //

    @Test
    void addLombokAddsAllAnnotationsForSmallClass() throws IOException {
        NodeList<AnnotationExpr> annotations = new NodeList<>();
        // Create a temporary Java file with no fields
        Path tempFilePath = Files.createTempFile("TempClass", ".java");
        Files.write(tempFilePath, """
            public class TempClass {
            }
        """.getBytes());

        // Parse the temporary file
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(tempFilePath.getParent()));

        JavaSymbolSolver symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        JavaParser javaParser = new JavaParser(parserConfiguration);

        FileInputStream in = new FileInputStream(tempFilePath.toFile());
        CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
        handler.setCompilationUnit(cu);
        ClassOrInterfaceDeclaration classDecl = cu.getClassByName("TempClass").orElseThrow();

        for (int i = 0; i < 255; i++) {
            classDecl.addField("String", "field" + i);
        }

        handler.addLombok(classDecl, annotations);

        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("Getter")));
        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("NoArgsConstructor")));
        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("AllArgsConstructor")));
        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("Setter")));
    }

    @Test
    void addLombokAddsLimitedAnnotationsForLargeClass() throws IOException {
        NodeList<AnnotationExpr> annotations = new NodeList<>();
        // Create a temporary Java file with no fields
        Path tempFilePath = Files.createTempFile("TempClass", ".java");
        Files.write(tempFilePath, """
            public class TempClass {
            }
        """.getBytes());

        // Parse the temporary file
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(tempFilePath.getParent()));

        JavaSymbolSolver symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        JavaParser javaParser = new JavaParser(parserConfiguration);

        FileInputStream in = new FileInputStream(tempFilePath.toFile());
        CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
        handler.setCompilationUnit(cu);
        ClassOrInterfaceDeclaration classDecl = cu.getClassByName("TempClass").orElseThrow();

        for (int i = 0; i < 256; i++) {
            classDecl.addField("String", "field" + i);
        }

        handler.addLombok(classDecl, annotations);

        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("Getter")));
        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("NoArgsConstructor")));
        assertFalse(annotations.stream().anyMatch(a -> a.getNameAsString().equals("AllArgsConstructor")));
        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("Setter")));
    }

    @Test
    void addLombokDoesNotAddAnnotationsForStaticFinalFieldsOnly() throws IOException {
        NodeList<AnnotationExpr> annotations = new NodeList<>();
        // Create a temporary Java file with no fields
        Path tempFilePath = Files.createTempFile("TempClass", ".java");
        Files.write(tempFilePath, """
            public class TempClass {
            }
        """.getBytes());

        // Parse the temporary file
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(tempFilePath.getParent()));

        JavaSymbolSolver symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        JavaParser javaParser = new JavaParser(parserConfiguration);

        FileInputStream in = new FileInputStream(tempFilePath.toFile());
        CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
        handler.setCompilationUnit(cu);
        ClassOrInterfaceDeclaration classDecl = cu.getClassByName("TempClass").orElseThrow();
        classDecl.addField("String", "field", Modifier.Keyword.STATIC, Modifier.Keyword.FINAL);

        handler.addLombok(classDecl, annotations);

        assertTrue(annotations.isEmpty());
    }

    @Test
    void addLombokAddsAnnotationsForNonStaticNonFinalFields() throws IOException {
        NodeList<AnnotationExpr> annotations = new NodeList<>();
        // Create a temporary Java file with no fields
        Path tempFilePath = Files.createTempFile("TempClass", ".java");
        Files.write(tempFilePath, """
            public class TempClass {
            }
        """.getBytes());

        // Parse the temporary file
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(tempFilePath.getParent()));

        JavaSymbolSolver symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        JavaParser javaParser = new JavaParser(parserConfiguration);

        FileInputStream in = new FileInputStream(tempFilePath.toFile());
        CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
        handler.setCompilationUnit(cu);
        ClassOrInterfaceDeclaration classDecl = cu.getClassByName("TempClass").orElseThrow();
        classDecl.addField("String", "field");

        handler.addLombok(classDecl, annotations);

        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("Getter")));
        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("NoArgsConstructor")));
        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("AllArgsConstructor")));
        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("Setter")));
    }

    // ======== TypeCollector ======== //

    // -------- capitalize -------- //
    @Test
    void capitalizeConvertsFirstCharacterToUpperCase() {
        assertEquals("Hello", typeCollector.capitalize("hello"));
    }

    @Test
    void capitalizeDoesNotChangeAlreadyCapitalizedString() {
        assertEquals("Hello", typeCollector.capitalize("Hello"));
    }

    @Test
    void capitalizeHandlesEmptyString() {
        assertThrows(StringIndexOutOfBoundsException.class, () -> typeCollector.capitalize(""));
    }


    // -------- generateRandomValue -------- //
    @Test
    void generateRandomValueAddSetterForBooleanField() throws IOException {
        Path tempFilePath = Files.createTempFile("TempClass", ".java");
        Files.write(tempFilePath, """
            public class TempClass {
            }
        """.getBytes());

        // Parse the temporary file
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(tempFilePath.getParent()));

        JavaSymbolSolver symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        JavaParser javaParser = new JavaParser(parserConfiguration);

        FileInputStream in = new FileInputStream(tempFilePath.toFile());
        CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
        handler.setCompilationUnit(cu);

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "Boolean"), "isActive"));

        MethodDeclaration method = new MethodDeclaration();
        handler.method = method;

        typeCollector.generateRandomValue(field);

        assertTrue(method.getBody().get().getStatements().stream()
                .anyMatch(stmt -> stmt.toString().contains("setIsActive(true)")));
    }

    @Test
    void generateRandomValueAddSetterForCharacterField() throws IOException {
        Path tempFilePath = Files.createTempFile("TempClass", ".java");
        Files.write(tempFilePath, """
            public class TempClass {
            }
        """.getBytes());

        // Parse the temporary file
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(tempFilePath.getParent()));

        JavaSymbolSolver symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        JavaParser javaParser = new JavaParser(parserConfiguration);

        FileInputStream in = new FileInputStream(tempFilePath.toFile());
        CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
        handler.setCompilationUnit(cu);

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "Character"), "initial"));

        MethodDeclaration method = new MethodDeclaration();
        handler.method = method;

        typeCollector.generateRandomValue(field);

        assertTrue(method.getBody().get().getStatements().stream()
                .anyMatch(stmt -> stmt.toString().contains("setInitial('A')")));
    }

    @Test
    void generateRandomValueAddSetterForDateField() throws IOException {
        Path tempFilePath = Files.createTempFile("TempClass", ".java");
        Files.write(tempFilePath, """
            public class TempClass {
            }
        """.getBytes());

        // Parse the temporary file
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(tempFilePath.getParent()));

        JavaSymbolSolver symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        JavaParser javaParser = new JavaParser(parserConfiguration);

        FileInputStream in = new FileInputStream(tempFilePath.toFile());
        CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
        handler.setCompilationUnit(cu);

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "Date"), "createdDate"));

        MethodDeclaration method = new MethodDeclaration();
        handler.method = method;

        typeCollector.generateRandomValue(field);

        assertTrue(method.getBody().get().getStatements().stream()
                .anyMatch(stmt -> stmt.toString().contains("setCreatedDate(new Date())")));
    }

    @Test
    void generateRandomValueAddSetterForIntegerField() throws IOException {
        Path tempFilePath = Files.createTempFile("TempClass", ".java");
        Files.write(tempFilePath, """
            public class TempClass {
            }
        """.getBytes());

        // Parse the temporary file
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(tempFilePath.getParent()));

        JavaSymbolSolver symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        JavaParser javaParser = new JavaParser(parserConfiguration);

        FileInputStream in = new FileInputStream(tempFilePath.toFile());
        CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
        handler.setCompilationUnit(cu);

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "Integer"), "count"));

        MethodDeclaration method = new MethodDeclaration();
        handler.method = method;

        typeCollector.generateRandomValue(field);

        assertTrue(method.getBody().get().getStatements().stream()
                .anyMatch(stmt -> stmt.toString().contains("setCount(0)")));
    }

    @Test
    void generateRandomValueAddSetterForStringField() throws IOException {
        Path tempFilePath = Files.createTempFile("TempClass", ".java");
        Files.write(tempFilePath, """
            public class TempClass {
            }
        """.getBytes());

        // Parse the temporary file
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(tempFilePath.getParent()));

        JavaSymbolSolver symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        JavaParser javaParser = new JavaParser(parserConfiguration);

        FileInputStream in = new FileInputStream(tempFilePath.toFile());
        CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
        handler.setCompilationUnit(cu);

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "String"), "message"));

        MethodDeclaration method = new MethodDeclaration();
        handler.method = method;

        typeCollector.generateRandomValue(field);

        assertTrue(method.getBody().get().getStatements().stream()
                .anyMatch(stmt -> stmt.toString().contains("setMessage(\"Hello world\")")));
    }

    @Test
    void generateRandomValueAddSetterForMapField() throws IOException {
        Path tempFilePath = Files.createTempFile("TempClass", ".java");
        Files.write(tempFilePath, """
            public class TempClass {
            }
        """.getBytes());

        // Parse the temporary file
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(tempFilePath.getParent()));

        JavaSymbolSolver symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        JavaParser javaParser = new JavaParser(parserConfiguration);

        FileInputStream in = new FileInputStream(tempFilePath.toFile());
        CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
        handler.setCompilationUnit(cu);

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "Map"), "testMap"));

        MethodDeclaration method = new MethodDeclaration();
        handler.method = method;

        typeCollector.generateRandomValue(field);

        assertTrue(method.getBody().get().getStatements().stream()
                .anyMatch(stmt -> stmt.toString().contains("setTestMap(Map.of())")));
    }

    @Test
    void generateRandomValueAddSetterForSetField() throws IOException {
        Path tempFilePath = Files.createTempFile("TempClass", ".java");
        Files.write(tempFilePath, """
            public class TempClass {
            }
        """.getBytes());

        // Parse the temporary file
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(tempFilePath.getParent()));

        JavaSymbolSolver symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        JavaParser javaParser = new JavaParser(parserConfiguration);

        FileInputStream in = new FileInputStream(tempFilePath.toFile());
        CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
        handler.setCompilationUnit(cu);

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "Set"), "testSet"));

        MethodDeclaration method = new MethodDeclaration();
        handler.method = method;

        typeCollector.generateRandomValue(field);

        assertTrue(method.getBody().get().getStatements().stream()
                .anyMatch(stmt -> stmt.toString().contains("setTestSet(Set.of())")));
    }

    @Test
    void generateRandomValueAddSetterForUUIDField() throws IOException {
        Path tempFilePath = Files.createTempFile("TempClass", ".java");
        Files.write(tempFilePath, """
            public class TempClass {
            }
        """.getBytes());

        // Parse the temporary file
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(tempFilePath.getParent()));

        JavaSymbolSolver symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        JavaParser javaParser = new JavaParser(parserConfiguration);

        FileInputStream in = new FileInputStream(tempFilePath.toFile());
        CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
        handler.setCompilationUnit(cu);

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "UUID"), "testUUID"));

        MethodDeclaration method = new MethodDeclaration();
        handler.method = method;

        typeCollector.generateRandomValue(field);

        assertTrue(method.getBody().get().getStatements().stream()
                .anyMatch(stmt -> stmt.toString().contains("setTestUUID(UUID.randomUUID())")));
    }

    @Test
    void generateRandomValueAddSetterForEnumType() throws IOException {
        Path tempFilePath = Files.createTempFile("TempClass", ".java");
        Files.write(tempFilePath, """
        public class TempClass {
            private enum MyEnum { VALUE1, VALUE2 }
        }
    """.getBytes());

        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(tempFilePath.getParent()));

        JavaSymbolSolver symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        JavaParser javaParser = new JavaParser(parserConfiguration);

        FileInputStream in = new FileInputStream(tempFilePath.toFile());
        CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
        handler.setCompilationUnit(cu);

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "MyEnum"), "enumField"));
        ClassOrInterfaceDeclaration classDecl = cu.getClassByName("TempClass").orElseThrow();
        classDecl.addMember(field);

        MethodDeclaration method = new MethodDeclaration();
        handler.method = method;

         typeCollector.generateRandomValue(field);

        assertFalse(method.getBody().get().getStatements().stream()
                .anyMatch(stmt -> stmt.toString().contains("setEnumField")));
    }
}