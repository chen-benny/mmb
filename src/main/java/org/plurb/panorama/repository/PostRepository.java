package org.plurb.panorama.repository;

import org.plurb.panorama.model.Post;
import org.plurb.panorama.model.PostStatus;
import org.plurb.panorama.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    Optional<Post> findByAuthorAndSlug(User author, String slug);

    boolean existsByAuthorAndSlug(User author, String slug);

    List<Post> findByAuthorAndStatusOrderByPublishedAtDesc(User author, PostStatus status);

    List<Post> findByAuthorOrderByCreatedAtDesc(User author);

    List<Post> findByAuthorAndTagsSlugAndStatusOrderByCreatedAtDesc(User author, String slug, PostStatus status);

    Page<Post> findByStatusOrderByPublishedAtDesc(PostStatus status, Pageable pageable);

    Page<Post> findByTagsSlugAndStatusOrderByPublishedAtDesc(String slug, PostStatus status, Pageable pageable);

    List<Post> findByAuthorAndStatusAndPublishedAtBeforeOrderByPublishedAtDesc(
            User author, PostStatus status, OffsetDateTime publishedAt, Pageable pageable);

    List<Post> findByAuthorAndStatusAndPublishedAtAfterOrderByPublishedAtAsc(
        User author, PostStatus status, OffsetDateTime publishedAt, Pageable pageable
    );

    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);

    @Query(value = """
            SELECT * FROM posts
            WHERE status = 'PUBLISHED'
              AND search_vector @@ plainto_tsquery('english', :q)
            ORDER BY ts_rank(search_vector, plainto_tsquery('english', :q)) DESC
            """, nativeQuery = true)
    List<Post> searchPublished(@Param("q") String q);

    List<Post> findByStatusOrderByPublishedAtDesc(PostStatus status);
}
