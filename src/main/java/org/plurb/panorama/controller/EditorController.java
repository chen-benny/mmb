package org.plurb.panorama.controller;

import org.plurb.panorama.model.Post;
import org.plurb.panorama.model.User;
import org.plurb.panorama.repository.TagRepository;
import org.plurb.panorama.repository.UserRepository;
import org.plurb.panorama.service.MarkdownService;
import org.plurb.panorama.service.PostService;
import org.plurb.panorama.service.SeriesService;
import org.plurb.panorama.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/panorama/editor")
public class EditorController {

    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final PostService postService;
    private final SeriesService seriesService;
    private final UserService userService;
    private final MarkdownService markdownService;

    public EditorController(UserRepository userRepository,
                            TagRepository tagRepository,
                            PostService postService,
                            SeriesService seriesService,
                            UserService userService,
                            MarkdownService markdownService) {
        this.userRepository = userRepository;
        this.tagRepository = tagRepository;
        this.postService = postService;
        this.seriesService = seriesService;
        this.userService = userService;
        this.markdownService = markdownService;
    }

    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found: " +
                        userDetails.getUsername()));
    }

    @GetMapping
    public String dashBoard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User author = resolveUser(userDetails);
        model.addAttribute("posts", postService.getAllPosts(author));
        model.addAttribute("seriesList", seriesService.getAllSeries(author));
        return "editor/dashboard";
    }

    @GetMapping("/new")
    public String newPostForm(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User author = resolveUser(userDetails);
        populateEditModel(model, author, null);
        return "editor/edit";
    }

    @PostMapping("/new")
    public String createPost(@AuthenticationPrincipal UserDetails userDetails,
                             @RequestParam String title,
                             @RequestParam(required = false, defaultValue = "") String slug,
                             @RequestParam(required = false, defaultValue = "") String description,
                             @RequestParam(required = false, defaultValue = "") String coverImageUrl,
                             @RequestParam String bodyMd,
                             @RequestParam(required = false, defaultValue = "") String tags,
                             @RequestParam(required = false, defaultValue = "") String series,
                             @RequestParam(required = false, defaultValue = "") String seriesDescription,
                             RedirectAttributes redirectAttributes) {
        User author = resolveUser(userDetails);
        List<String> tagList = Arrays.stream(tags.trim().split("\\s+"))
                .filter(s -> !s.isEmpty())
                .toList();
        try {
            postService.createPost(author, title, slug, description, coverImageUrl, bodyMd, tagList, series, seriesDescription);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/panorama/editor/new";
        }
        return "redirect:/panorama/editor";
    }

    @GetMapping("/{id}")
    public String editPostForm(@PathVariable Long id,
                               @AuthenticationPrincipal UserDetails userDetails,
                               Model model) {
        User author = resolveUser(userDetails);
        Post post = postService.getPostById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found: " + id));
        if (!post.getAuthor().getId().equals(author.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        populateEditModel(model, author, post);
        return "editor/edit";
    }

    @GetMapping("/{id}/preview")
    public String previewPost(@PathVariable Long id,
                              @AuthenticationPrincipal UserDetails userDetails,
                              Model model) {
        User author = resolveUser(userDetails);
        Post post = postService.getPostById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found: " + id));
        if (!post.getAuthor().getId().equals(author.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        MarkdownService.RenderedContent rendered = markdownService.renderPost(post.getBodyMd());
        model.addAttribute("author", author);
        model.addAttribute("post", post);
        model.addAttribute("renderedBody", rendered.html());
        model.addAttribute("toc", rendered.toc());
        model.addAttribute("readingMinutes", rendered.readingMinutes());
        model.addAttribute("preview", true);
        return "public/post";
    }

    @PostMapping("/{id}")
    public String updatePost(@PathVariable Long id,
                             @AuthenticationPrincipal UserDetails userDetails,
                             @RequestParam String title,
                             @RequestParam(required = false, defaultValue = "") String slug,
                             @RequestParam(required = false, defaultValue = "") String description,
                             @RequestParam(required = false, defaultValue = "") String coverImageUrl,
                             @RequestParam String bodyMd,
                             @RequestParam(required = false, defaultValue = "") String tags,
                             @RequestParam(required = false, defaultValue = "") String series,
                             @RequestParam(required = false, defaultValue = "") String seriesDescription,
                             RedirectAttributes redirectAttributes) {
        User author = resolveUser(userDetails);
        Post post = postService.getPostById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found: " + id));
        if (!post.getAuthor().getId().equals(author.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        List<String> tagList = Arrays.stream(tags.trim().split("\\s+"))
                .filter(s -> !s.isEmpty())
                .toList();
        try {
            postService.updatePost(post, title, slug, description, coverImageUrl, bodyMd, tagList, series, seriesDescription);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/panorama/editor/" + id;
        }
        return "redirect:/panorama/editor";
    }

    @PostMapping("/{id}/publish")
    public String togglePublish(@PathVariable Long id,
                                @AuthenticationPrincipal UserDetails userDetails) {
        User author = resolveUser(userDetails);
        Post post = postService.getPostById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found: " + id));
        if (!post.getAuthor().getId().equals(author.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        postService.togglePublish(post);
        return "redirect:/panorama/editor";
    }

    @PostMapping("/{id}/delete")
    public String deletePost(@PathVariable Long id,
                             @AuthenticationPrincipal UserDetails userDetails) {
        User author = resolveUser(userDetails);
        Post post = postService.getPostById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found: " + id));
        if (!post.getAuthor().getId().equals(author.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        postService.deletePost(post);
        return "redirect:/panorama/editor";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportPosts(@AuthenticationPrincipal UserDetails userDetails) {
        User author = resolveUser(userDetails);
        byte[] zip = postService.exportMarkdownZip(author);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"panorama-export.zip\"")
                .body(zip);
    }

    @GetMapping("/about")
    public String aboutForm(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User author = resolveUser(userDetails);
        model.addAttribute("aboutMd", author.getAboutMd() != null ? author.getAboutMd() : "");
        return "editor/about";
    }

    @PostMapping("/about")
    public String updateAbout(@AuthenticationPrincipal UserDetails userDetails,
                              @RequestParam String aboutMd) {
        User author = resolveUser(userDetails);
        userService.updateAbout(author, aboutMd);
        return "redirect:/panorama/" + author.getUsername() + "/about";
    }

    private void populateEditModel(Model model, User author, Post post) {
        model.addAttribute("post", post);
        model.addAttribute("userSeries", seriesService.getAllSeries(author));
        model.addAttribute("userTags", tagRepository.findByPostsAuthorOrderByNameAsc(author));
        if (post != null) {
            var ps = post.getPostSeriesList().stream().findFirst();
            model.addAttribute("postSeriesTitle", ps.map(e -> e.getSeries().getTitle()).orElse(""));
            model.addAttribute("postSeriesDescription", ps.map(e -> {
                String d = e.getSeries().getDescription();
                return d != null ? d : "";
            }).orElse(""));
        } else {
            model.addAttribute("postSeriesTitle", "");
            model.addAttribute("postSeriesDescription", "");
        }
    }

    @GetMapping("/password")
    public String passwordForm() {
        return "redirect:/panorama/editor/account";
    }

    @GetMapping("/account")
    public String accountForm(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User author = resolveUser(userDetails);
        model.addAttribute("currentUsername", author.getUsername());
        return "editor/account";
    }

    @PostMapping("/account/username")
    public String changeUsername(@AuthenticationPrincipal UserDetails userDetails,
                                 @RequestParam String newUsername,
                                 @RequestParam String password,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        User author = resolveUser(userDetails);
        String error = userService.changeUsername(author, newUsername, password);
        if (error != null) {
            redirectAttributes.addFlashAttribute("usernameError", error);
            return "redirect:/panorama/editor/account";
        }
        request.getSession().invalidate();
        return "redirect:/login";
    }

    @PostMapping("/account/password")
    public String changePassword(@AuthenticationPrincipal UserDetails userDetails,
                                 @RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 RedirectAttributes redirectAttributes) {
        User author = resolveUser(userDetails);
        boolean success = userService.changePassword(author, oldPassword, newPassword);
        if (!success) {
            redirectAttributes.addFlashAttribute("passwordError", "Current password is incorrect.");
            return "redirect:/panorama/editor/account";
        }
        redirectAttributes.addFlashAttribute("passwordSuccess", "Password changed successfully.");
        return "redirect:/panorama/editor/account";
    }
}
