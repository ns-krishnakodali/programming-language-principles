package org.compiler.delphi;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

public class DelphiInterpreter {
    public void interpret() {
        String source = """
            PROGRAM helloworld(output);
            BEGIN
                writeln('Hello, world!');
            END.
            """;

        CharStream input = CharStreams.fromString(source);
        org.compiler.delphi.DelphiLexer delphiLexer = new org.compiler.delphi.DelphiLexer(input);

        CommonTokenStream tokens = new CommonTokenStream(delphiLexer);
        org.compiler.delphi.DelphiParser parser = new org.compiler.delphi.DelphiParser(tokens);

        // Start rule
        ParseTree tree = parser.program();

        // Print parse tree
        System.out.println(tree.toStringTree(parser));
    }
}
