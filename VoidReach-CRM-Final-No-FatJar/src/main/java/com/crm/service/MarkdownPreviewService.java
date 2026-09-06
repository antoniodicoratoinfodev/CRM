package com.crm.service;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.*;
import org.commonmark.renderer.text.TextContentRenderer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** CommonMark/GFM parsing is separate from JavaFX and never rewrites the note source. */
public final class MarkdownPreviewService {
    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create(), StrikethroughExtension.create(),
            TaskListItemsExtension.create(), HeadingAnchorExtension.create());
    private static final Pattern WIKI = Pattern.compile("\\[\\[([^]\\n]+)]]");
    public record Rendered(String body, String plainText) { }

    public Rendered render(String source) {
        if (source.length() > 2_000_000) throw new IllegalArgumentException("This note is too large for Preview. Its full content remains available in the editor.");
        Node document = Parser.builder().extensions(EXTENSIONS).build().parse(source);
        List<Text> texts = new ArrayList<>();
        document.accept(new AbstractVisitor() {
            @Override public void visit(Link link) { /* Never nest links inside link labels. */ }
            @Override public void visit(Image image) { /* Alt text is not an interactive note reference. */ }
            @Override public void visit(Text text) { texts.add(text); }
        });
        for (Text text : texts) {
            var matches = WIKI.matcher(text.getLiteral()); int cursor = 0;
            while (matches.find()) {
                if (matches.start() > cursor) text.insertBefore(new Text(text.getLiteral().substring(cursor, matches.start())));
                String target = matches.group(1).trim();
                Link link = new Link("voidreach-note:" + URLEncoder.encode(target, StandardCharsets.UTF_8), null);
                link.appendChild(new Text(target)); text.insertBefore(link); cursor = matches.end();
            }
            if (cursor > 0) { if (cursor < text.getLiteral().length()) text.insertBefore(new Text(text.getLiteral().substring(cursor))); text.unlink(); }
        }
        String html = HtmlRenderer.builder().extensions(EXTENSIONS).escapeHtml(true).sanitizeUrls(true)
                .urlSanitizer(new UrlSanitizer() {
                    @Override public String sanitizeLinkUrl(String url) {
                        return ExternalLinks.allowed(url) || url.startsWith("#") || url.startsWith("voidreach-note:") ? url : "";
                    }
                    @Override public String sanitizeImageUrl(String url) { return ""; }
                })
                .nodeRendererFactory(Blocks::new).build().render(document);
        return new Rendered(html, TextContentRenderer.builder().extensions(EXTENSIONS).build().render(document));
    }

    private static final class Blocks implements org.commonmark.renderer.NodeRenderer {
        private final HtmlWriter html;
        private int codeId, imageId;
        Blocks(HtmlNodeRendererContext context) { html = context.getWriter(); }
        @Override public Set<Class<? extends Node>> getNodeTypes() { return Set.of(FencedCodeBlock.class, IndentedCodeBlock.class, Image.class); }
        @Override public void render(Node node) {
            if (node instanceof Image image) { image(image); return; }
            String value = node instanceof FencedCodeBlock block ? block.getLiteral() : ((IndentedCodeBlock) node).getLiteral();
            String language = node instanceof FencedCodeBlock block ? block.getInfo().strip().split("\\s+", 2)[0] : "";
            String id = "code-" + ++codeId;
            html.raw("<div class=\"code-frame\"><div class=\"code-bar\"><span>");
            html.text(language.isBlank() ? "Code" : language);
            html.raw("</span><button type=\"button\" data-copy=\"" + id + "\" aria-label=\"Copy code\">Copy</button></div><pre id=\"" + id + "\"><code>");
            if (language.isBlank()) html.text(value);
            else for (var token : CodeSyntaxHighlighter.highlight(value)) {
                html.raw("<span class=\"syntax-" + token.kind().name().toLowerCase(Locale.ROOT) + "\">");
                html.text(token.text()); html.raw("</span>");
            }
            html.raw("</code></pre></div>\n");
        }
        private void image(Image image) {
            String id = "image-" + ++imageId;
            StringBuilder alt = new StringBuilder();
            image.accept(new AbstractVisitor() {
                @Override public void visit(Text text) { alt.append(text.getLiteral()); }
                @Override public void visit(Code code) { alt.append(code.getLiteral()); }
                @Override public void visit(SoftLineBreak line) { alt.append(' '); }
            });
            String caption = alt.toString().strip();
            if (caption.isBlank()) caption = "Image";
            String address = image.getDestination();
            if (ExternalLinks.allowed(address) && !address.toLowerCase(Locale.ROOT).startsWith("mailto:")) {
                html.tag("span", Map.of("class", "image-placeholder", "id", id, "data-image-url", address, "data-alt", caption));
                html.text(caption + " ");
                html.tag("button", Map.of("type", "button", "data-load-image", id, "title", "Fetch image from " + java.net.URI.create(address).getHost()));
                html.text("Load remote image"); html.tag("/button"); html.tag("/span");
            } else { html.tag("span", Map.of("class", "image-placeholder")); html.text(caption + " · image path unavailable"); html.tag("/span"); }
        }
    }
    public static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
