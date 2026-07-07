package org.plurb.panorama.service;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.footnotes.FootnotesExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MarkdownService {

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownService() {
        List<Extension> extensions = List.of(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListItemsExtension.create(),
                AutolinkExtension.create(),
                FootnotesExtension.create()
        );
        this.parser = Parser.builder().extensions(extensions).build();
        this.renderer = HtmlRenderer.builder().extensions(extensions).build();
    }

    public String render(String markdown) {
        if (markdown == null) return "";
        Node document = parser.parse(markdown);
        document.accept(new SmartTypographyVisitor());
        return renderer.render(document);
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
            s = s.replace("'", "’");                                // apostrophe / closing ’
            return s;
        }
    }
}
