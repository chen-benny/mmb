package org.plurb.panorama.service;

import org.plurb.panorama.model.*;
import org.plurb.panorama.repository.PostRepository;
import org.plurb.panorama.repository.PostSeriesRepository;
import org.plurb.panorama.repository.TagRepository;
import org.plurb.panorama.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final PostSeriesRepository postSeriesRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final SeriesService seriesService;

    public PostService(PostRepository postRepository, PostSeriesRepository postSeriesRepository,
                       TagRepository tagRepository, UserRepository userRepository,
                       SeriesService seriesService) {
        this.postRepository = postRepository;
        this.postSeriesRepository = postSeriesRepository;
        this.tagRepository = tagRepository;
        this.userRepository = userRepository;
        this.seriesService = seriesService;
    }

    public List<Post> getPublishedPosts(User author) {
        return postRepository.findByAuthorAndStatusOrderByPublishedAtDesc(
                author, PostStatus.PUBLISHED);
    }

    public List<Post> getAllPosts(User author) {
        return postRepository.findByAuthorOrderByCreatedAtDesc(author);
    }

    public Optional<Post> getPublishedPost(User author, String slug) {
        return postRepository.findByAuthorAndSlug(author, slug)
                .filter(post -> post.getStatus().equals(PostStatus.PUBLISHED));
    }

    @Transactional
    public void incrementViewCount(Long postId) {
        postRepository.incrementViewCount(postId);
    }

    public List<Post> searchPublished(String query) {
        return postRepository.searchPublished(query);
    }

    public List<Post> getAllPublishedPosts() {
        return postRepository.findByStatusOrderByPublishedAtDesc(PostStatus.PUBLISHED);
    }

    public Optional<Post> getPostById(Long id) {
        return postRepository.findById(id);
    }

    public List<Post> getPublishedPostsByTag(User author, String tagSlug) {
        return postRepository.findByAuthorAndTagsSlugAndStatusOrderByCreatedAtDesc(
                author, tagSlug, PostStatus.PUBLISHED);
    }

    public Page<Post> getAllPublishedPostsPaged(int page, int size) {
        return postRepository.findByStatusOrderByPublishedAtDesc(
                PostStatus.PUBLISHED, PageRequest.of(page, size));
    }

    public Page<Post> getPublishedPostsByTagPaged(String tagSlug, int page, int size) {
        return postRepository.findByTagsSlugAndStatusOrderByPublishedAtDesc(
                tagSlug, PostStatus.PUBLISHED, PageRequest.of(page, size));
    }

    public Optional<Post> getPreviousPost(Post post) {
        var list = postRepository.findByAuthorAndStatusAndPublishedAtBeforeOrderByPublishedAtDesc(
                post.getAuthor(), PostStatus.PUBLISHED, post.getPublishedAt(), PageRequest.of(0, 1));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<Post> getNextPost(Post post) {
        var list = postRepository.findByAuthorAndStatusAndPublishedAtAfterOrderByPublishedAtAsc(
                post.getAuthor(), PostStatus.PUBLISHED, post.getPublishedAt(), PageRequest.of(0, 1));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<Tag> getAllUsedTags() {
        return tagRepository.findByPostsStatusOrderByNameAsc(PostStatus.PUBLISHED);
    }

    @Transactional
    public Post createPost(User author, String title, String slug, String description,
                           String coverImageUrl, String bodyMd, List<String> tagNames,
                           String seriesTitle, String seriesDescription) {
        String resolvedSlug = slug == null || slug.isBlank()
                ? generateUniqueSlug(author, title, null)
                : slug;
        if (postRepository.existsByAuthorAndSlug(author, resolvedSlug)) {
            throw new IllegalArgumentException("Slug '" + resolvedSlug + "' is already used by one of your posts.");
        }
        Post post = new Post();
        post.setAuthor(author);
        post.setTitle(title);
        post.setSlug(resolvedSlug);
        post.setDescription(description);
        post.setCoverImageUrl(coverImageUrl);
        post.setBodyMd(bodyMd);
        post.setTags(resolveTags(tagNames));
        post = postRepository.save(post);
        postRepository.flush();
        resolveSeriesMembership(author, post, seriesTitle, seriesDescription);
        return post;
    }

    @Transactional
    public Post updatePost(Post post, String title, String slug, String description,
                           String coverImageUrl, String bodyMd, List<String> tagNames,
                           String seriesTitle, String seriesDescription) {
        String resolvedSlug = slug == null || slug.isBlank()
                ? generateUniqueSlug(post.getAuthor(), title, post.getId())
                : slug;
        if (!post.getSlug().equals(resolvedSlug) && postRepository.existsByAuthorAndSlug(post.getAuthor(), resolvedSlug)) {
            throw new IllegalArgumentException("Slug '" + resolvedSlug + "' is already used by one of your posts.");
        }
        post.setTitle(title);
        post.setSlug(resolvedSlug);
        post.setDescription(description);
        post.setBodyMd(bodyMd);
        post.setCoverImageUrl(coverImageUrl);
        post.setTags(resolveTags(tagNames));
        post.setUpdatedAt(OffsetDateTime.now());
        post = postRepository.save(post);
        postRepository.flush();
        resolveSeriesMembership(post.getAuthor(), post, seriesTitle, seriesDescription);
        return post;
    }

    @Transactional
    public Post togglePublish(Post post) {
        if (post.getStatus().equals(PostStatus.DRAFT)) {
            post.setStatus(PostStatus.PUBLISHED);
            post.setPublishedAt(OffsetDateTime.now());
        } else {
            post.setStatus(PostStatus.DRAFT);
            post.setPublishedAt(null);
        }
        post.setUpdatedAt(OffsetDateTime.now());
        return postRepository.save(post);
    }

    @Transactional
    public void deletePost(Post post) {
        postRepository.delete(post);
    }

    private void resolveSeriesMembership(User author, Post post, String seriesTitle, String seriesDescription) {
        postSeriesRepository.deleteByPost(post);
        if (seriesTitle == null || seriesTitle.isBlank()) return;
        Series series = seriesService.findOrCreate(author, seriesTitle.trim(), seriesDescription);
        int position = postSeriesRepository.findMaxPositionBySeries(series) + 1;
        PostSeries ps = new PostSeries();
        ps.setPost(post);
        ps.setSeries(series);
        ps.setPosition(position);
        postSeriesRepository.save(ps);
    }

    private String generateUniqueSlug(User author, String title, Long excludePostId) {
        String base = title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("[\\s-]+", "-");
        if (base.isEmpty()) base = "post";
        String candidate = base;
        int suffix = 2;
        while (true) {
            String c = candidate;
            boolean taken = postRepository.findByAuthorAndSlug(author, c)
                    .filter(p -> excludePostId == null || !p.getId().equals(excludePostId))
                    .isPresent();
            if (!taken) return c;
            candidate = base + "-" + suffix++;
        }
    }

    private List<Tag> resolveTags(List<String> tagNames) {
        List<Tag> tags = new ArrayList<>();
        for (String tagName : tagNames) {
            String trimmed = tagName.trim().toLowerCase();
            if (trimmed.isEmpty()) { continue; }
            Tag tag = tagRepository.findByName(trimmed).orElseGet(() -> {
                Tag newTag = new Tag();
                newTag.setName(trimmed);
                newTag.setSlug(trimmed.replaceAll("[^a-z0-9]+", "-"));
                return tagRepository.save(newTag);
            });
            tags.add(tag);
        }
        return tags;
    }
}
