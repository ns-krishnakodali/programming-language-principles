package org.compiler.delphi;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.fail;

public class InterpreterTest {

    @Test
    void test1() {
        runTestFile("test1.pas");
    }

    @Test
    void test2() {
        runTestFile("test2.pas");
    }

    @Test
    void test3() {
        runTestFile("test3.pas");
    }

    @Test
    void test4() {
        System.setIn(new ByteArrayInputStream("100\n".getBytes()));
        runTestFile("test4.pas");
    }

    @Test
    void test5() {
        runTestFile("test5.pas");
    }

    @Test
    void test6() {
        runTestFile("test6.pas");
    }

    @Test
    void test7() {
        runTestFile("test7.pas");
    }

    private void runTestFile(String filename) {
        try {
            System.out.println("\n\nRunning: " + filename);

            Path path = Paths.get(
                    Objects.requireNonNull(
                            getClass().getClassLoader().getResource(filename)
                    ).toURI()
            );

            CharStream input = CharStreams.fromPath(path);
            DelphiLexer lexer = new DelphiLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            DelphiParser parser = new DelphiParser(tokens);

            ParseTree tree = parser.program();

            DelphiInterpreter delphiInterpreter = new DelphiInterpreter();
            delphiInterpreter.visit(tree);

            System.out.println("Execution completed: " + filename);
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
            fail("Execution failed for: " + filename);
        }
    }
}
