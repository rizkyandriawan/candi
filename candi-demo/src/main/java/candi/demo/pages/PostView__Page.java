package candi.demo.pages;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import candi.runtime.*;
import candi.demo.service.PostService;
import java.util.Objects;

/**
 * Hand-compiled page simulating compiler output for:
 *
 * @page "/post/{id}"
 * @inject PostService posts
 * @inject RequestContext ctx
 *
 * @init {
 *   post = posts.getById(ctx.path("id"));
 * }
 *
 * @fragment "post-content" {
 *   <article>{{ raw post.content }}</article>
 * }
 *
 * {{ if post }}
 *   <h1>{{ post.title }}</h1>
 *   {{ if post.published }}
 *     {{ fragment "post-content" }}
 *   {{ end }}
 * {{ else }}
 *   <h1>Not Found</h1>
 * {{ end }}
 */
@Component
@Scope(WebApplicationContext.SCOPE_REQUEST)
@CandiRoute(path = "/post/{id}", methods = {"GET"})
public class PostView__Page implements CandiPage {

    @Autowired
    private PostService posts;

    @Autowired
    private RequestContext ctx;

    private Object post;

    @Override
    public void init() {
        this.post = posts.getById(ctx.path("id"));
    }

    @Override
    public ActionResult handleAction(String method) {
        return ActionResult.methodNotAllowed();
    }

    @Override
    public void render(HtmlOutput out) {
        if (this.post != null && !Boolean.FALSE.equals(this.post)) {
            PostService.Post p = (PostService.Post) this.post;
            out.append("<h1>");
            out.appendEscaped(String.valueOf(p.title()));
            out.append("</h1>\n");
            if (p.published()) {
                renderFragment_postContent(out);
            }
        } else {
            out.append("<h1>Not Found</h1>\n");
        }
    }

    @Override
    public void renderFragment(String name, HtmlOutput out) {
        if ("post-content".equals(name)) {
            renderFragment_postContent(out);
            return;
        }
        throw new FragmentNotFoundException(name);
    }

    private void renderFragment_postContent(HtmlOutput out) {
        PostService.Post p = (PostService.Post) this.post;
        out.append("<article>");
        out.append(String.valueOf(p.content()));
        out.append("</article>");
    }
}
