package candi.demo.pages;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import candi.runtime.*;
import candi.demo.service.PostService;

/**
 * Hand-compiled page simulating v2 compiler output for:
 *
 * @Page("/post/{id}")
 * public class PostViewPage {
 *
 *     @Autowired
 *     private PostService posts;
 *
 *     @Autowired
 *     private RequestContext ctx;
 *
 *     private PostService.Post post;
 *
 *     public void init() {
 *         post = posts.getById(ctx.path("id"));
 *     }
 * }
 *
 * <template>
 * {{ if post }}
 *   <h1>{{ post.title }}</h1>
 *   {{ if post.published }}
 *     <article>{{ raw post.content }}</article>
 *   {{ end }}
 * {{ else }}
 *   <h1>Not Found</h1>
 * {{ end }}
 * </template>
 */
@Component
@Scope(WebApplicationContext.SCOPE_REQUEST)
@CandiRoute(path = "/post/{id}", methods = {"GET"})
public class PostView__Page implements CandiPage {

    @Autowired
    private PostService posts;

    @Autowired
    private RequestContext ctx;

    private PostService.Post post;

    @Override
    public void init() {
        this.post = posts.getById(ctx.path("id"));
    }

    @Override
    public void render(HtmlOutput out) {
        if (this.post != null) {
            out.append("<h1>");
            out.appendEscaped(String.valueOf(this.post.title()));
            out.append("</h1>\n");
            if (this.post.published()) {
                out.append("<article>");
                out.append(String.valueOf(this.post.content()));
                out.append("</article>");
            }
        } else {
            out.append("<h1>Not Found</h1>\n");
        }
    }
}
