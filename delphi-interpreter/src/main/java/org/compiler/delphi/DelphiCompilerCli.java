package org.compiler.delphi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class DelphiCompilerCli {
    private DelphiCompilerCli() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: DelphiCompilerCli <input.pas|inputDir> [output.ll|outputDir]");
            System.exit(1);
        }

        Path input = Path.of(args[0]);
        if (Files.isDirectory(input)) {
            Path outputDir = args.length == 2 ? Path.of(args[1]) : input.resolve("compiler");
            compileDirectory(input, outputDir);
            return;
        }

        Path output = args.length == 2 ? Path.of(args[1]) : defaultOutput(input);
        compileFile(input, output);
    }

    private static void compileDirectory(Path inputDir, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        List<Path> pasFiles;
        try (Stream<Path> files = Files.list(inputDir)) {
            pasFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pas"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .collect(Collectors.toList());
        }

        for (Path pasFile : pasFiles) {
            compileFile(pasFile, outputDir.resolve(stem(pasFile) + ".ll"));
        }
    }

    private static void compileFile(Path input, Path output) throws IOException {
        String llvm = new DelphiCompiler().compile(DelphiParserFacade.parse(input));
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.writeString(output, llvm);
        System.out.println("Wrote LLVM IR to " + output.toAbsolutePath());
    }

    private static Path defaultOutput(Path input) {
        return input.resolveSibling(stem(input) + ".ll");
    }

    private static String stem(Path input) {
        String filename = input.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(0, dot) : filename;
    }
}
