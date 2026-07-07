package org.plurb.panorama.controller;

import org.plurb.panorama.model.Post;
import org.plurb.panorama.model.Series;
import org.plurb.panorama.model.User;
import org.plurb.panorama.repository.UserRepository;
import org.plurb.panorama.service.MarkdownService;
import org.plurb.panorama.service.PostService;
import org.plurb.panorama.service.SeriesService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
public class PublicController {

    private final UserRepository userRepository;
    private final PostService postService;
    private final SeriesService seriesService;
    private final MarkdownService markdownService;

    @Value("${app.base-url}")
    private String baseUrl;

    public PublicController(UserRepository userRepository,
                            PostService postService,
                            SeriesService seriesService,
                            MarkdownService markdownService) {
        this.userRepository = userRepository;
        this.postService = postService;
        this.seriesService = seriesService;
        this.markdownService = markdownService;
    }

    @GetMapping("/")
    public String landing() {
        return "landing";
    }

    @GetMapping("/panorama")
    public String index(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) String tag,
                        Model model) {
        int size = 10;
        Page<Post> postsPage;
        if (tag != null && !tag.isBlank()) {
            postsPage = postService.getPublishedPostsByTagPaged(tag, page, size);
            model.addAttribute("activeTag", tag);
        } else {
            postsPage = postService.getAllPublishedPostsPaged(page, size);
        }
        model.addAttribute("postsPage", postsPage);
        model.addAttribute("allTags", postService.getAllUsedTags());
        model.addAttribute("allSeries", seriesService.getAllSeriesAcrossUsers());
        return "public/index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/panorama/{username}")
    public String profile(@PathVariable String username, Model model) {
        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
        model.addAttribute("author", author);
        model.addAttribute("posts", postService.getPublishedPosts(author));
        model.addAttribute("seriesList", seriesService.getAllSeries(author));
        model.addAttribute("renderedAbout", author.getAboutMd() != null
                ? markdownService.render(author.getAboutMd()) : "");
        model.addAttribute("rssUrl", baseUrl + "/panorama/" + username + "/feed.xml");
        return "public/profile";
    }

    @GetMapping("/panorama/{username}/about")
    public String about(@PathVariable String username, Model model) {
        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
        model.addAttribute("author", author);
        model.addAttribute("renderedBody", author.getAboutMd() != null
                ? markdownService.render(author.getAboutMd()) : "");
        return "public/about";
    }

    @GetMapping("/panorama/{username}/tag/{tagSlug}")
    public String tag(@PathVariable String username, @PathVariable String tagSlug, Model model) {
        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
        model.addAttribute("author", author);
        model.addAttribute("tagSlug", tagSlug);
        model.addAttribute("posts", postService.getPublishedPostsByTag(author, tagSlug));
        return "public/tag";
    }

    @GetMapping("/panorama/{username}/series/{seriesSlug}")
    public String series(@PathVariable String username, @PathVariable String seriesSlug, Model model) {
        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
        Series series = seriesService.getSeries(author, seriesSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Series not found: " + seriesSlug));
        model.addAttribute("author", author);
        model.addAttribute("series", series);
        return "public/series";
    }

    @GetMapping("/panorama/{username}/{slug}")
    public String post(@PathVariable String username, @PathVariable String slug, Model model) {
        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
        Post post = postService.getPublishedPost(author, slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found: " + slug));
        postService.incrementViewCount(post.getId());
        MarkdownService.RenderedContent rendered = markdownService.renderPost(post.getBodyMd());
        model.addAttribute("author", author);
        model.addAttribute("post", post);
        model.addAttribute("renderedBody", rendered.html());
        model.addAttribute("toc", rendered.toc());
        model.addAttribute("readingMinutes", rendered.readingMinutes());
        model.addAttribute("prevPost", postService.getPreviousPost(post).orElse(null));
        model.addAttribute("nextPost", postService.getNextPost(post).orElse(null));
        model.addAttribute("relatedPosts", postService.getRelatedPosts(post, 4));
        model.addAttribute("canonicalUrl", baseUrl + "/panorama/" + username + "/" + slug);
        model.addAttribute("rssUrl", baseUrl + "/panorama/" + username + "/feed.xml");
        return "public/post";
    }

    @GetMapping("/panorama/search")
    public String search(@RequestParam(required = false, defaultValue = "") String q, Model model) {
        model.addAttribute("q", q);
        if (!q.isBlank()) {
            model.addAttribute("results", postService.searchPublished(q));
        }
        return "public/search";
    }

    @GetMapping("/sitemap.xml")
    @ResponseBody
    public ResponseEntity<String> sitemap() {
        List<Post> posts = postService.getAllPublishedPosts();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        sb.append("<url><loc>").append(xmlEscape(baseUrl)).append("/panorama</loc></url>\n");
        for (Post post : posts) {
            String loc = baseUrl + "/panorama/" + post.getAuthor().getUsername() + "/" + post.getSlug();
            sb.append("<url>\n");
            sb.append("  <loc>").append(xmlEscape(loc)).append("</loc>\n");
            if (post.getPublishedAt() != null) {
                sb.append("  <lastmod>").append(post.getPublishedAt().toLocalDate()).append("</lastmod>\n");
            }
            sb.append("</url>\n");
        }
        sb.append("</urlset>\n");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/xml;charset=UTF-8"))
                .body(sb.toString());
    }

    @GetMapping("/panorama/{username}/feed.xml")
    @ResponseBody
    public ResponseEntity<String> rssFeed(@PathVariable String username) {
        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
        List<Post> posts = postService.getPublishedPosts(author);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/rss+xml;charset=UTF-8"))
                .body(buildRss(author, posts));
    }

    private String buildRss(User author, List<Post> posts) {
        String channelUrl = baseUrl + "/panorama/" + author.getUsername();
        String feedUrl = channelUrl + "/feed.xml";
        String displayName = author.getDisplayName() != null ? author.getDisplayName() : author.getUsername();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<rss version=\"2.0\" xmlns:atom=\"http://www.w3.org/2005/Atom\">\n");
        sb.append("<channel>\n");
        sb.append("<title>").append(xmlEscape(displayName)).append(" — Panorama</title>\n");
        sb.append("<link>").append(xmlEscape(channelUrl)).append("</link>\n");
        sb.append("<atom:link href=\"").append(xmlEscape(feedUrl))
          .append("\" rel=\"self\" type=\"application/rss+xml\"/>\n");
        sb.append("<description>Posts by ").append(xmlEscape(author.getUsername()))
          .append(" on Panorama</description>\n");
        sb.append("<language>en-us</language>\n");
        for (Post post : posts) {
            String postUrl = baseUrl + "/panorama/" + author.getUsername() + "/" + post.getSlug();
            sb.append("<item>\n");
            sb.append("<title>").append(xmlEscape(post.getTitle())).append("</title>\n");
            sb.append("<link>").append(xmlEscape(postUrl)).append("</link>\n");
            sb.append("<guid isPermaLink=\"true\">").append(xmlEscape(postUrl)).append("</guid>\n");
            if (post.getDescription() != null && !post.getDescription().isBlank()) {
                sb.append("<description>").append(xmlEscape(post.getDescription())).append("</description>\n");
            }
            if (post.getPublishedAt() != null) {
                sb.append("<pubDate>")
                  .append(DateTimeFormatter.RFC_1123_DATE_TIME.format(
                          post.getPublishedAt().atZoneSameInstant(ZoneOffset.UTC)))
                  .append("</pubDate>\n");
            }
            sb.append("</item>\n");
        }
        sb.append("</channel>\n");
        sb.append("</rss>\n");
        return sb.toString();
    }

    private static String xmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
