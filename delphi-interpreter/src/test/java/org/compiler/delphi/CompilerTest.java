package org.compiler.delphi;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompilerTest {
    @Test
    void compilesSimpleProgramToLlvmIr() {
        String llvm = compileResource("test1.pas");

        assertTrue(llvm.contains("define i32 @main()"));
        assertTrue(llvm.contains("call i32 (ptr, ...) @printf"));
        assertTrue(llvm.contains("icmp sgt i32"));
    }

    @Test
    void compilesLoopsCaseAndFunctions() {
        String loops = compileResource("test2.pas");
        String functions = compileResource("test8.pas");
        String branching = compileResource("test10.pas");
        String booleans = compileResource("test15.pas");

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
        String llvm = compileResource("test17.pas");

        assertTrue(llvm.contains("@g_v = global i32 0"));
        assertTrue(llvm.contains("sdiv i32"));
        assertTrue(llvm.contains("srem i32"));
    }

    @Test
    void rejectsUnsupportedObjectFeatures() {
        DelphiCompiler.CompilerException error = assertThrows(
                DelphiCompiler.CompilerException.class,
                () -> compileResource("test3.pas")
        );

        assertTrue(error.getMessage().toLowerCase().contains("not supported"));
    }

    @Test
    void compilerMainWritesLlvmFile() {
        Path output = Paths.get("target/test-output/test1.ll");
        assertDoesNotThrow(() -> DelphiCompilerMain.main(new String[]{
                resourcePath("test1.pas").toString(),
                output.toString()
        }));
        assertTrue(output.toFile().isFile());
    }

    private String compileResource(String filename) {
        try {
            return new DelphiCompiler().compile(DelphiFrontend.parse(resourcePath(filename)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
}
