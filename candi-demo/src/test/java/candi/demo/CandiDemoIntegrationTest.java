package candi.demo;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CandiDemoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ========== GET rendering ==========

    @Test
    @Order(1)
    void indexPageRendersPostList() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("<h1>All Posts</h1>")))
                .andExpect(content().string(containsString("Hello Candi")));
    }

    @Test
    @Order(2)
    void postViewPageRendersPublishedPost() throws Exception {
        mockMvc.perform(get("/post/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Hello Candi</h1>")))
                .andExpect(content().string(containsString("<article>")))
                .andExpect(content().string(containsString("Welcome to the Candi framework!")));
    }

    @Test
    @Order(3)
    void postViewPageHidesContentForUnpublished() throws Exception {
        mockMvc.perform(get("/post/2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Draft Post</h1>")))
                .andExpect(content().string(not(containsString("<article>"))));
    }

    @Test
    @Order(4)
    void postViewPageShowsNotFoundForMissingPost() throws Exception {
        mockMvc.perform(get("/post/999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Not Found</h1>")));
    }

    @Test
    @Order(5)
    void editPageRendersForm() throws Exception {
        mockMvc.perform(get("/post/1/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Edit Post</h1>")))
                .andExpect(content().string(containsString("name=\"title\"")))
                .andExpect(content().string(containsString("Hello Candi")));
    }

    // ========== 405 Method Not Allowed ==========

    @Test
    @Order(6)
    void postToReadOnlyPageReturns405() throws Exception {
        mockMvc.perform(post("/"))
                .andExpect(status().isMethodNotAllowed());
    }

    // ========== Fragment rendering ==========

    @Test
    @Order(7)
    void fragmentViaHeaderReturnsPartialHtml() throws Exception {
        mockMvc.perform(get("/post/1")
                        .header("HX-Fragment", "post-content"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<article>")))
                .andExpect(content().string(not(containsString("<h1>"))));
    }

    @Test
    @Order(8)
    void fragmentViaQueryParamReturnsPartialHtml() throws Exception {
        mockMvc.perform(get("/post/1")
                        .param("_fragment", "post-content"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<article>")))
                .andExpect(content().string(not(containsString("<h1>"))));
    }

    @Test
    @Order(9)
    void unknownFragmentReturns404() throws Exception {
        mockMvc.perform(get("/post/1")
                        .header("HX-Fragment", "nonexistent"))
                .andExpect(status().isNotFound());
    }

    // ========== Unknown route ==========

    @Test
    @Order(10)
    void unknownRouteReturns404() throws Exception {
        mockMvc.perform(get("/nonexistent"))
                .andExpect(status().isNotFound());
    }

    // ========== POST action (redirect) — mutates state, runs last ==========

    @Test
    @Order(20)
    void editPostRedirectsAfterUpdate() throws Exception {
        mockMvc.perform(post("/post/1/edit")
                        .param("title", "Updated Title"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/post/1"));

        // Verify the update took effect
        mockMvc.perform(get("/post/1"))
                .andExpect(content().string(containsString("Updated Title")));
    }

    // ========== DELETE action (redirect) ==========

    @Test
    @Order(21)
    void deletePostRedirectsToIndex() throws Exception {
        mockMvc.perform(delete("/post/2/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    // ========== Layout rendering (M4) ==========

    @Test
    @Order(30)
    void aboutPageRendersWithLayout() throws Exception {
        mockMvc.perform(get("/about"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<html>")))
                .andExpect(content().string(containsString("<title>About Us</title>")))
                .andExpect(content().string(containsString("<nav>Candi Demo</nav>")))
                .andExpect(content().string(containsString("<h1>About Candi</h1>")))
                .andExpect(content().string(containsString("<footer>Powered by Candi</footer>")));
    }

    // ========== Component rendering (M4) ==========

    @Test
    @Order(31)
    void dashboardPageRendersWithLayoutAndComponents() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Dashboard</title>")))
                .andExpect(content().string(containsString("<h1>Dashboard</h1>")))
                .andExpect(content().string(containsString("alert-success")))
                .andExpect(content().string(containsString("Welcome back!")))
                .andExpect(content().string(containsString("alert-warning")))
                .andExpect(content().string(containsString("3 items need review.")));
    }
}
