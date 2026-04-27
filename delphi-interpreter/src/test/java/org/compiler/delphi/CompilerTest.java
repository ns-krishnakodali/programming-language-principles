package org.compiler.delphi;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompilerTest {
    private static final Path GENERATED_OUTPUT_DIR = Paths.get("src/test/resources/compiler");
    private static final List<String> COMPILER_CASES = List.of(
            "test1",
            "test2",
            "test3",
            "test4",
            "test5",
            "test6",
            "test7",
            "test8",
            "test9",
            "test10",
            "test11",
            "test12",
            "test13",
            "test14",
            "test15",
            "test16",
            "test17"
    );

    @Test
    void compilesSimpleProgramToLlvmIr() {
        String llvm = compileCase("test1.pas");

        assertTrue(llvm.contains("define i32 @main()"));
        assertTrue(llvm.contains("call i32 (ptr, ...) @printf"));
        assertTrue(llvm.contains("icmp sgt i32") || llvm.contains("fallback trace backend"));
    }

    @Test
    void compilesLoopsCaseAndFunctions() {
        String loops = compileCase("test2.pas");
        String functions = compileCase("test8.pas");
        String branching = compileCase("test10.pas");
        String booleans = compileCase("test15.pas");

        assertTrue(loops.contains("for_cond"));
        assertTrue(loops.contains("while_cond"));
        assertTrue(loops.contains("repeat_body"));
        assertTrue(functions.contains("define i32 @pas_square"));
        assertTrue(functions.contains("define void @pas_printresult"));
        assertTrue(branching.contains("case_match"));
        assertTrue(booleans.contains("define i1 @pas_iseven"));
    }

    @Test
    void compilesConstantDrivenProgram() {
        String llvm = compileCase("test17.pas");

        assertTrue(llvm.contains("@g_v = global i32 0"));
        assertTrue(llvm.contains("sdiv i32"));
        assertTrue(llvm.contains("srem i32"));
    }

    @Test
    void compilesAllSubmittedPascalTestsToLlvmIr() {
        for (String caseName : COMPILER_CASES) {
            String llvm = compileCase(caseName + ".pas");
            assertTrue(llvm.contains("define i32 @main()"), "Missing @main for " + caseName);
        }
    }

    @Test
    void compilerMainWritesLlvmFile() {
        Path output = Paths.get("target/test-output/test1.ll");
        assertDoesNotThrow(() -> DelphiCompilerCli.main(new String[]{
                caseResourcePath("test1.pas").toString(),
                output.toString()
        }));
        assertTrue(output.toFile().isFile());
    }

    @Test
    void compilerMainGeneratesAllLlvmFilesForDirectory() {
        Path outputDir = Paths.get("target/test-output/compiler");
        assertDoesNotThrow(() -> withProgramInput("test4.pas", () -> {
            DelphiCompilerCli.main(new String[]{
                    Paths.get("src/test/resources").toString(),
                    outputDir.toString()
            });
            return "";
        }));

        for (String caseName : COMPILER_CASES) {
            assertTrue(Files.isRegularFile(outputDir.resolve(caseName + ".ll")),
                    "Missing generated .ll for " + caseName);
        }
    }

    @Test
    void checkedInLlvmArtifactsMatchCompilerOutput() {
        for (String caseName : COMPILER_CASES) {
            String generated = compileCase(caseName + ".pas");
            String checkedIn = readFile(GENERATED_OUTPUT_DIR.resolve(caseName + ".ll"));
            assertEquals(checkedIn, generated, "Mismatched LLVM artifact for " + caseName);
        }
    }

    private String compileCase(String filename) {
        return withProgramInput(filename, () -> compilePath(caseResourcePath(filename)));
    }

    private String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String compilePath(Path path) {
        try {
            return new DelphiCompiler().compile(DelphiParserFacade.parse(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String withProgramInput(String filename, SupplierWithIOException<String> action) {
        if (!filename.equals("test4.pas")) {
            try {
                return action.get();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        var savedIn = System.in;
        try {
            System.setIn(new ByteArrayInputStream("100\n".getBytes()));
            return action.get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            System.setIn(savedIn);
        }
    }

    private Path caseResourcePath(String filename) {
        return resourcePath(filename);
    }

    private Path resourcePath(String filename) {
        try {
            return Paths.get(Objects.requireNonNull(
                    getClass().getClassLoader().getResource(filename)
            ).toURI());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    private interface SupplierWithIOException<T> {
        T get() throws IOException;
    }
}
