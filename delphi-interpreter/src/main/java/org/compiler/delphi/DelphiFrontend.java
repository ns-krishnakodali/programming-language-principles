package org.compiler.delphi;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.IOException;
import java.nio.file.Path;

public final class DelphiFrontend {
    private DelphiFrontend() {
    }

    public static DelphiParser.ProgramContext parse(Path path) throws IOException {
        return parse(CharStreams.fromPath(path));
    }

    public static DelphiParser.ProgramContext parse(String source) {
        return parse(CharStreams.fromString(source));
    }

    public static DelphiParser.ProgramContext parse(CharStream input) {
        DelphiLexer lexer = new DelphiLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DelphiParser parser = new DelphiParser(tokens);
        return parser.program();
    }
}
