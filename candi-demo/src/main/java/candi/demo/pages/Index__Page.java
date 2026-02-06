package candi.demo.pages;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import candi.runtime.CandiPage;
import candi.runtime.ActionResult;
import candi.runtime.HtmlOutput;
import candi.runtime.CandiRoute;
import candi.demo.service.PostService;
import java.util.Objects;

/**
 * Hand-compiled page simulating compiler output for:
 *
 * @page "/"
 * @inject PostService posts
 *
 * @init {
 *   allPosts = posts.findAll();
 * }
 *
 * <h1>All Posts</h1>
 * <ul>
 * {{ for post in allPosts }}
 *   <li>{{ post.title }}</li>
 * {{ end }}
 * </ul>
 */
@Component
@Scope(WebApplicationContext.SCOPE_REQUEST)
@CandiRoute(path = "/", methods = {"GET"})
public class Index__Page implements CandiPage {

    @Autowired
    private PostService posts;

    private Object allPosts;

    @Override
    public void init() {
        this.allPosts = posts.findAll();
    }

    @Override
    public ActionResult handleAction(String method) {
        return ActionResult.methodNotAllowed();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void render(HtmlOutput out) {
        out.append("<h1>All Posts</h1>\n<ul>\n");
        for (var post : (java.util.List<PostService.Post>) this.allPosts) {
            out.append("<li>");
            out.appendEscaped(String.valueOf(post.title()));
            out.append("</li>\n");
        }
        out.append("</ul>\n");
    }
}
