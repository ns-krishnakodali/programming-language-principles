package org.compiler.delphi;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.fail;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class InterpreterTest {
    @Order(1)
    @Test
    void test1() {
        runTestFile("test1.pas");
    }

    @Order(2)
    @Test
    void test2() {
        runTestFile("test2.pas");
    }

    @Order(3)
    @Test
    void test3() {
        runTestFile("test3.pas");
    }

    @Order(4)
    @Test
    void test4() {
        System.setIn(new ByteArrayInputStream("100\n".getBytes()));
        runTestFile("test4.pas");
    }

    @Order(5)
    @Test
    void test5() {
        runTestFile("test5.pas");
    }

    @Order(6)
    @Test
    void test6() {
        runTestFile("test6.pas");
    }

    @Order(7)
    @Test
    void test7() {
        runTestFile("test7.pas");
    }

    @Order(8)
    @Test
    void test8() {
        runTestFile("test8.pas");
    }

    @Order(9)
    @Test
    void test9() {
        runTestFile("test9.pas");
    }

    @Order(10)
    @Test
    void test10() {
        runTestFile("test10.pas");
    }

    @Order(11)
    @Test
    void test11() {
        runTestFile("test11.pas");
    }

    @Order(12)
    @Test
    void test12() {
        runTestFile("test12.pas");
    }

    @Order(13)
    @Test
    void test13() {
        runTestFile("test13.pas");
    }

    @Order(14)
    @Test
    void test14() {
        runTestFile("test14.pas");
    }

    @Order(15)
    @Test
    void test15() {
        runTestFile("test15.pas");
    }

    @Order(16)
    @Test
    void test16() {
        runTestFile("test16.pas");
    }

    @Order(17)
    @Test
    void test17() {
        runTestFile("test17.pas");
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
