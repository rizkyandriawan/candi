package candi.devtools;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Servlet filter that injects the live reload script into HTML responses.
 * Adds a small SSE-based script before the closing &lt;/body&gt; tag.
 */
public class LiveReloadFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(LiveReloadFilter.class);

    private static final String LIVE_RELOAD_SCRIPT = """
            <script>
            (function() {
              var es = new EventSource('/_candi/reload');
              es.addEventListener('reload', function() { location.reload(); });
              es.addEventListener('error', function(e) {
                var overlay = document.getElementById('_candi_error');
                if (!overlay) {
                  overlay = document.createElement('div');
                  overlay.id = '_candi_error';
                  overlay.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,0.9);color:#ff6b6b;' +
                    'font-family:monospace;font-size:14px;padding:40px;z-index:99999;overflow:auto;white-space:pre-wrap';
                  document.body.appendChild(overlay);
                }
                overlay.textContent = '\\u274C Candi Compilation Error\\n\\n' + e.data;
                overlay.style.display = 'block';
              });
              es.addEventListener('connected', function() {
                var overlay = document.getElementById('_candi_error');
                if (overlay) overlay.style.display = 'none';
              });
            })();
            </script>
            """;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletResponse httpResponse = (HttpServletResponse) response;
        BufferingResponseWrapper wrapper = new BufferingResponseWrapper(httpResponse);

        chain.doFilter(request, wrapper);

        String contentType = wrapper.getContentType();
        if (contentType != null && contentType.contains("text/html")) {
            String body = wrapper.getCapturedBody();
            // Inject script before </body> or at the end
            int idx = body.lastIndexOf("</body>");
            if (idx >= 0) {
                body = body.substring(0, idx) + LIVE_RELOAD_SCRIPT + body.substring(idx);
            } else {
                body = body + LIVE_RELOAD_SCRIPT;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            response.setContentLength(bytes.length);
            response.getOutputStream().write(bytes);
        } else {
            // Non-HTML: write through
            byte[] data = wrapper.getCapturedBytes();
            response.setContentLength(data.length);
            response.getOutputStream().write(data);
        }
    }

    /**
     * Response wrapper that captures the output in a buffer.
     */
    private static class BufferingResponseWrapper extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private ServletOutputStream outputStream;
        private PrintWriter writer;

        BufferingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() {
            if (outputStream == null) {
                outputStream = new ServletOutputStream() {
                    @Override
                    public void write(int b) { buffer.write(b); }

                    @Override
                    public void write(byte[] b, int off, int len) { buffer.write(b, off, len); }

                    @Override
                    public boolean isReady() { return true; }

                    @Override
                    public void setWriteListener(WriteListener listener) {}
                };
            }
            return outputStream;
        }

        @Override
        public PrintWriter getWriter() {
            if (writer == null) {
                writer = new PrintWriter(new OutputStreamWriter(buffer, StandardCharsets.UTF_8), true);
            }
            return writer;
        }

        @Override
        public void flushBuffer() {
            if (writer != null) writer.flush();
        }

        String getCapturedBody() {
            flushBuffer();
            return buffer.toString(StandardCharsets.UTF_8);
        }

        byte[] getCapturedBytes() {
            flushBuffer();
            return buffer.toByteArray();
        }
    }
}
