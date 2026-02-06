package candi.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Candi framework.
 *
 * <pre>
 * candi.dev=true                    # Enable dev mode (hot reload + live reload)
 * candi.source-dir=src/main/candi   # Directory containing .page.html files
 * candi.package=pages               # Default package for generated page classes
 * </pre>
 */
@ConfigurationProperties(prefix = "candi")
public class CandiProperties {

    /**
     * Enable development mode with hot reload and live reload.
     */
    private boolean dev = false;

    /**
     * Directory containing .page.html source files.
     * Used by file watcher in dev mode.
     */
    private String sourceDir = "src/main/candi";

    /**
     * Default Java package for generated page classes.
     */
    private String packageName = "pages";

    public boolean isDev() {
        return dev;
    }

    public void setDev(boolean dev) {
        this.dev = dev;
    }

    public String getSourceDir() {
        return sourceDir;
    }

    public void setSourceDir(String sourceDir) {
        this.sourceDir = sourceDir;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }
}
