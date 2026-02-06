package candi.devtools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Watches a directory for .page.html file changes using Java WatchService.
 * Debounces events at 50ms to avoid duplicate notifications.
 */
public class FileWatcher implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(FileWatcher.class);
    private static final long DEBOUNCE_MS = 50;

    private final Path watchDir;
    private final Consumer<Path> onChange;
    private final ConcurrentHashMap<Path, Long> lastModified = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    public FileWatcher(Path watchDir, Consumer<Path> onChange) {
        this.watchDir = watchDir;
        this.onChange = onChange;
    }

    @Override
    public void run() {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            registerAll(watchDir, watcher);
            log.info("Candi FileWatcher: watching {} for changes", watchDir);

            while (running) {
                WatchKey key;
                try {
                    key = watcher.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                Path dir = (Path) key.watchable();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path changed = dir.resolve(pathEvent.context());

                    // Register new subdirectories
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changed)) {
                        registerAll(changed, watcher);
                        continue;
                    }

                    // Only process .page.html, .layout.html, .component.html files
                    String fileName = changed.getFileName().toString();
                    if (!fileName.endsWith(".page.html") &&
                        !fileName.endsWith(".layout.html") &&
                        !fileName.endsWith(".component.html")) {
                        continue;
                    }

                    // Debounce: skip if we processed this file within DEBOUNCE_MS
                    long now = System.currentTimeMillis();
                    Long lastTime = lastModified.get(changed);
                    if (lastTime != null && (now - lastTime) < DEBOUNCE_MS) {
                        continue;
                    }
                    lastModified.put(changed, now);

                    log.debug("File changed: {}", changed);
                    try {
                        onChange.accept(changed);
                    } catch (Exception e) {
                        log.error("Error processing file change: {}", changed, e);
                    }
                }

                if (!key.reset()) {
                    log.warn("Watch key invalidated for directory");
                    break;
                }
            }
        } catch (IOException e) {
            log.error("FileWatcher error", e);
        }
    }

    public void stop() {
        running = false;
    }

    private void registerAll(Path start, WatchService watcher) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                dir.register(watcher,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
