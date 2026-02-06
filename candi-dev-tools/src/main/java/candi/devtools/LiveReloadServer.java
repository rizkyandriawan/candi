package candi.devtools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-Sent Events (SSE) based live reload server.
 * Browser clients subscribe to /_candi/reload for reload notifications.
 */
public class LiveReloadServer {

    private static final Logger log = LoggerFactory.getLogger(LiveReloadServer.class);
    private static final long SSE_TIMEOUT = 0L; // no timeout

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Create a new SSE subscription for a browser client.
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        log.debug("Live reload client connected ({} total)", emitters.size());

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("Candi dev tools connected"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    /**
     * Notify all connected browsers to reload.
     */
    public void notifyReload() {
        notifyReload(null);
    }

    /**
     * Notify all connected browsers to reload, with optional error info.
     */
    public void notifyReload(String error) {
        log.debug("Sending reload notification to {} client(s)", emitters.size());

        for (SseEmitter emitter : emitters) {
            try {
                if (error != null) {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(error, MediaType.TEXT_PLAIN));
                } else {
                    emitter.send(SseEmitter.event()
                            .name("reload")
                            .data("reload"));
                }
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    /**
     * Get the number of connected clients.
     */
    public int getClientCount() {
        return emitters.size();
    }
}
