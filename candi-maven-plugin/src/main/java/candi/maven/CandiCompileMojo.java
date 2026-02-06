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

/**
 * Compiles .page.html files to Java source code.
 *
 * Scans {@code src/main/candi/} for .page.html files, invokes the Candi compiler,
 * and writes generated Java sources to {@code target/generated-sources/candi}.
 */
@Mojo(name = "compile", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class CandiCompileMojo extends AbstractMojo {

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

        // Find all .page.html files
        List<Path> pageFiles = findPageFiles(sourcePath);
        if (pageFiles.isEmpty()) {
            getLog().info("No .page.html files found in " + sourcePath);
            return;
        }

        getLog().info("Compiling " + pageFiles.size() + " Candi page(s)...");

        CandiCompiler compiler = new CandiCompiler();
        int compiled = 0;

        for (Path pageFile : pageFiles) {
            try {
                // Derive package from subdirectory structure
                Path relative = sourcePath.relativize(pageFile);
                String filePackage = derivePackage(relative);

                String javaSource = compiler.compileFile(pageFile, filePackage);
                String className = CandiCompiler.deriveClassName(pageFile.getFileName().toString());

                // Write to output directory
                Path javaFile = outputPath
                        .resolve(filePackage.replace('.', '/'))
                        .resolve(className + ".java");

                Files.createDirectories(javaFile.getParent());
                Files.writeString(javaFile, javaSource);

                getLog().debug("Compiled: " + relative + " -> " + javaFile);
                compiled++;
            } catch (Exception e) {
                throw new MojoExecutionException("Failed to compile: " + pageFile, e);
            }
        }

        // Add generated sources to the project
        project.addCompileSourceRoot(outputPath.toString());

        getLog().info("Candi: " + compiled + " page(s) compiled successfully");
    }

    private List<Path> findPageFiles(Path sourceDir) throws MojoExecutionException {
        List<Path> result = new ArrayList<>();
        try {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".page.html")) {
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
     * Derive Java package from file path relative to source root.
     * e.g. "admin/users.page.html" → "pages.admin"
     * e.g. "index.page.html" → "pages"
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
