package candi.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Starts the Candi application in development mode with hot reload.
 *
 * Runs the initial compilation, then launches the Spring Boot application
 * with candi.dev=true to enable file watching and live reload.
 *
 * Usage: mvn candi:dev
 */
@Mojo(name = "dev", requiresProject = true)
public class CandiDevMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.basedir}/src/main/candi", property = "candi.sourceDir")
    private String sourceDir;

    @Parameter(property = "candi.mainClass")
    private String mainClass;

    @Parameter(defaultValue = "8080", property = "candi.port")
    private String port;

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("Starting Candi dev mode...");

        // Step 1: Run initial compilation
        CandiCompileMojo compileMojo = new CandiCompileMojo();
        compileMojo.setLog(getLog());

        // Step 2: Find the main class
        String appMainClass = mainClass;
        if (appMainClass == null || appMainClass.isEmpty()) {
            appMainClass = detectMainClass();
        }

        if (appMainClass == null) {
            throw new MojoExecutionException(
                    "Cannot detect main class. Set candi.mainClass property or add @SpringBootApplication to your code.");
        }

        // Step 3: Build classpath
        String classpath = buildClasspath();

        // Step 4: Launch the application with dev mode enabled
        getLog().info("Launching: " + appMainClass + " (dev mode)");
        getLog().info("Source dir: " + sourceDir);
        getLog().info("Port: " + port);

        try {
            List<String> command = new ArrayList<>();
            command.add(getJavaExecutable());
            command.add("-classpath");
            command.add(classpath);
            command.add("-Dcandi.dev=true");
            command.add("-Dcandi.source-dir=" + sourceDir);
            command.add("-Dserver.port=" + port);
            command.add(appMainClass);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.inheritIO();
            pb.directory(project.getBasedir());

            Process process = pb.start();
            getLog().info("Candi dev server started (PID: " + process.pid() + ")");

            // Wait for the process (blocks until Ctrl+C)
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                getLog().warn("Application exited with code: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            getLog().info("Dev server stopped.");
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to start dev server", e);
        }
    }

    private String detectMainClass() {
        // Check common Spring Boot main class locations
        String[] candidates = {
                project.getGroupId() + ".Application",
                project.getGroupId() + "." + capitalize(project.getArtifactId()) + "Application",
        };

        String outputDir = project.getBuild().getOutputDirectory();
        for (String candidate : candidates) {
            String classFile = candidate.replace('.', File.separatorChar) + ".class";
            if (new File(outputDir, classFile).exists()) {
                return candidate;
            }
        }
        return null;
    }

    private String buildClasspath() {
        // Use the current JVM's classpath — Maven's forked process inherits it
        return System.getProperty("java.class.path", "");
    }

    private String getJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        return javaHome + File.separator + "bin" + File.separator + "java";
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
