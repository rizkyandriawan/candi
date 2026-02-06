package candi.maven;

import candi.compiler.CandiCompiler;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles .jhtml files to Java source code.
 *
 * Scans {@code src/main/candi/} for .jhtml files, invokes the Candi compiler,
 * and writes generated Java sources to {@code target/generated-sources/candi}.
 */
@Mojo(name = "compile", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class CandiCompileMojo extends AbstractMojo {

    private static final Pattern PUBLIC_CLASS_PATTERN = Pattern.compile(
            "public\\s+class\\s+(\\w+)");

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.basedir}/src/main/candi", property = "candi.sourceDir")
    private String sourceDir;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/candi", property = "candi.outputDir")
    private String outputDir;

    @Parameter(defaultValue = "pages", property = "candi.packageName")
    private String packageName;

    @Override
    public void execute() throws MojoExecutionException {
        Path sourcePath = Paths.get(sourceDir);
        Path outputPath = Paths.get(outputDir);

        if (!Files.isDirectory(sourcePath)) {
            getLog().info("No Candi source directory found: " + sourcePath);
            return;
        }

        // Find all .jhtml files (and legacy .page.html)
        List<Path> candiFiles = findCandiFiles(sourcePath);
        if (candiFiles.isEmpty()) {
            getLog().info("No .jhtml files found in " + sourcePath);
            return;
        }

        getLog().info("Compiling " + candiFiles.size() + " Candi file(s)...");

        CandiCompiler compiler = new CandiCompiler();
        int compiled = 0;

        for (Path candiFile : candiFiles) {
            try {
                // Derive package from subdirectory structure
                Path relative = sourcePath.relativize(candiFile);
                String filePackage = derivePackage(relative);

                String javaSource = compiler.compileFile(candiFile, filePackage);

                // Extract actual class name from generated source
                String className = extractClassName(javaSource);
                if (className == null) {
                    className = CandiCompiler.deriveClassName(candiFile.getFileName().toString());
                }

                // Write to output directory
                Path javaFile = outputPath
                        .resolve(filePackage.replace('.', '/'))
                        .resolve(className + ".java");

                Files.createDirectories(javaFile.getParent());
                Files.writeString(javaFile, javaSource);

                getLog().debug("Compiled: " + relative + " -> " + javaFile);
                compiled++;
            } catch (Exception e) {
                throw new MojoExecutionException("Failed to compile: " + candiFile, e);
            }
        }

        // Add generated sources to the project
        project.addCompileSourceRoot(outputPath.toString());

        getLog().info("Candi: " + compiled + " file(s) compiled successfully");
    }

    private List<Path> findCandiFiles(Path sourceDir) throws MojoExecutionException {
        List<Path> result = new ArrayList<>();
        try {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.toString();
                    if (name.endsWith(".jhtml") || name.endsWith(".page.html") ||
                        name.endsWith(".layout.html") || name.endsWith(".component.html")) {
                        result.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to scan source directory", e);
        }
        return result;
    }

    /**
     * Extract the public class name from generated Java source.
     */
    private static String extractClassName(String javaSource) {
        Matcher m = PUBLIC_CLASS_PATTERN.matcher(javaSource);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * Derive Java package from file path relative to source root.
     * e.g. "admin/users.jhtml" → "pages.admin"
     * e.g. "index.jhtml" → "pages"
     */
    private String derivePackage(Path relativePath) {
        Path parent = relativePath.getParent();
        if (parent == null) {
            return packageName;
        }
        String subPackage = parent.toString()
                .replace('/', '.')
                .replace('\\', '.');
        return packageName + "." + subPackage;
    }
}
