package org.plurb.panorama.service;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.footnotes.FootnotesExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.Heading;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MarkdownService {

    private static final int WORDS_PER_MINUTE = 200;

    private final List<Extension> extensions;
    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownService() {
        this.extensions = List.of(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListItemsExtension.create(),
                AutolinkExtension.create(),
                FootnotesExtension.create()
        );
        this.parser = Parser.builder().extensions(extensions).build();
        this.renderer = HtmlRenderer.builder().extensions(extensions).build();
    }

    /** Renders markdown to HTML (used for About pages and anywhere a TOC isn't needed). */
    public String render(String markdown) {
        if (markdown == null) return "";
        Node document = parser.parse(markdown);
        document.accept(new SmartTypographyVisitor());
        return renderer.render(document);
    }

    /** A single heading in the table of contents. */
    public record TocEntry(int level, String text, String id) {}

    /** Rendered HTML plus derived reading aids. */
    public record RenderedContent(String html, List<TocEntry> toc, int readingMinutes, int wordCount) {}

    /**
     * Renders a post body, additionally assigning stable ids to h2/h3 headings and
     * returning a table of contents that links to them, plus an estimated reading time.
     */
    public RenderedContent renderPost(String markdown) {
        if (markdown == null) markdown = "";
        Node document = parser.parse(markdown);
        document.accept(new SmartTypographyVisitor());

        Map<Node, String> headingIds = new IdentityHashMap<>();
        List<TocEntry> toc = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Heading heading) {
                if (heading.getLevel() >= 2 && heading.getLevel() <= 3) {
                    String text = textOf(heading);
                    String id = uniqueSlug(text, usedIds);
                    headingIds.put(heading, id);
                    toc.add(new TocEntry(heading.getLevel(), text, id));
                }
                super.visit(heading);
            }
        });

        HtmlRenderer postRenderer = HtmlRenderer.builder()
                .extensions(extensions)
                .attributeProviderFactory(context -> (node, tagName, attributes) -> {
                    String id = headingIds.get(node);
                    if (id != null) attributes.put("id", id);
                })
                .build();

        String html = postRenderer.render(document);
        int words = countWords(markdown);
        int minutes = Math.max(1, (int) Math.round(words / (double) WORDS_PER_MINUTE));
        return new RenderedContent(html, toc, minutes, words);
    }

    private static String textOf(Node node) {
        StringBuilder sb = new StringBuilder();
        node.accept(new AbstractVisitor() {
            @Override
            public void visit(Text text) { sb.append(text.getLiteral()); }
            @Override
            public void visit(Code code) { sb.append(code.getLiteral()); }
        });
        return sb.toString().trim();
    }

    private static String uniqueSlug(String text, Set<String> used) {
        String base = text.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("[\\s-]+", "-");
        if (base.isEmpty()) base = "section";
        String candidate = base;
        int suffix = 2;
        while (!used.add(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static int countWords(String markdown) {
        String trimmed = markdown.trim();
        if (trimmed.isEmpty()) return 0;
        return trimmed.split("\\s+").length;
    }

    /**
     * Rewrites straight quotes, dashes and ellipses into their typographic
     * equivalents. Only visits Text nodes, so code spans, code blocks and URLs
     * are left untouched.
     */
    private static final class SmartTypographyVisitor extends AbstractVisitor {
        @Override
        public void visit(Text text) {
            // Leave link text/URLs alone (autolinked URLs are Text under a Link).
            if (!(text.getParent() instanceof Link)) {
                text.setLiteral(smarten(text.getLiteral()));
            }
            super.visit(text);
        }

        static String smarten(String s) {
            if (s == null || s.isEmpty()) return s;
            s = s.replace("---", "—"); // em dash —
            s = s.replace("--", "–");  // en dash –
            s = s.replace("...", "…"); // ellipsis …
            s = s.replaceAll("(^|[\\s(\\[{<‘“])\"", "$1“"); // opening double “
            s = s.replace("\"", "”");                                 // closing double ”
            s = s.replaceAll("(^|[\\s(\\[{<])'", "$1‘");             // opening single ‘
            s = s.replace("'", "’");                                  // apostrophe / closing ’
            return s;
        }
    }
}
