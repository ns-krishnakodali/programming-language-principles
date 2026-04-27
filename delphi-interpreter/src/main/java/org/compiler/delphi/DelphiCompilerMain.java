package org.compiler.delphi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DelphiCompilerMain {
    private DelphiCompilerMain() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: DelphiCompilerMain <input.pas> [output.ll]");
            System.exit(1);
        }

        Path input = Path.of(args[0]);
        Path output = args.length == 2 ? Path.of(args[1]) : defaultOutput(input);

        DelphiParser.ProgramContext program = DelphiFrontend.parse(input);
        String llvmIr = new DelphiCompiler().compile(program);

        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.writeString(output, llvmIr);
        System.out.println("Wrote LLVM IR to " + output.toAbsolutePath());
    }

    private static Path defaultOutput(Path input) {
        String filename = input.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        String stem = dot >= 0 ? filename.substring(0, dot) : filename;
        return input.resolveSibling(stem + ".ll");
    }
}
