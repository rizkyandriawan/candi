package candi.demo.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple in-memory post service for demo purposes.
 */
@Service
public class PostService {

    private final AtomicLong idGen = new AtomicLong(1);
    private final Map<String, Post> posts = new ConcurrentHashMap<>();

    public PostService() {
        // Seed some data
        create("Hello Candi", "<p>Welcome to the Candi framework!</p>", true);
        create("Draft Post", "<p>This is a draft.</p>", false);
    }

    public Post create(String title, String content, boolean published) {
        String id = String.valueOf(idGen.getAndIncrement());
        Post post = new Post(id, title, content, published);
        posts.put(id, post);
        return post;
    }

    public Post getById(String id) {
        return posts.get(id);
    }

    public List<Post> findAll() {
        return List.copyOf(posts.values());
    }

    public void update(String id, String title) {
        Post existing = posts.get(id);
        if (existing != null) {
            posts.put(id, new Post(id, title, existing.content(), existing.published()));
        }
    }

    public void delete(String id) {
        posts.remove(id);
    }

    public record Post(String id, String title, String content, boolean published) {}
}
