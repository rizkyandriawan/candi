package candi.demo.pages;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import candi.runtime.CandiPage;
import candi.runtime.HtmlOutput;
import candi.runtime.CandiRoute;
import candi.demo.service.PostService;

import java.util.List;

/**
 * Hand-compiled page simulating v2 compiler output for:
 *
 * @Page("/")
 * public class IndexPage {
 *
 *     @Autowired
 *     private PostService posts;
 *
 *     private List<PostService.Post> allPosts;
 *
 *     public void init() {
 *         allPosts = posts.findAll();
 *     }
 * }
 *
 * <template>
 * <h1>All Posts</h1>
 * <ul>
 * {{ for post in allPosts }}
 *   <li>{{ post.title }}</li>
 * {{ end }}
 * </ul>
 * </template>
 */
@Component
@Scope(WebApplicationContext.SCOPE_REQUEST)
@CandiRoute(path = "/", methods = {"GET"})
public class Index__Page implements CandiPage {

    @Autowired
    private PostService posts;

    private List<PostService.Post> allPosts;

    @Override
    public void init() {
        this.allPosts = posts.findAll();
    }

    @Override
    public void render(HtmlOutput out) {
        out.append("<h1>All Posts</h1>\n<ul>\n");
        for (var post : this.allPosts) {
            out.append("<li>");
            out.appendEscaped(String.valueOf(post.title()));
            out.append("</li>\n");
        }
        out.append("</ul>\n");
    }
}
