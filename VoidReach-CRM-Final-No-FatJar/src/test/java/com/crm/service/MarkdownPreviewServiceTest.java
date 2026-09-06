package com.crm.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarkdownPreviewServiceTest {
    private final MarkdownPreviewService renderer = new MarkdownPreviewService();

    @Test void paragraphsHeadingsAndNestedFormattingFollowCommonMark() {
        String html = renderer.render("""
                # Heading

                First line
                continues here.

                #### Small heading
                ##### Fifth
                ###### Sixth

                **Strong with *emphasis*** and ~~removed~~ and `a < b`.

                Underlined heading
                ------------------
                """).body();
        assertTrue(html.contains("<p>First line\ncontinues here.</p>"), html);
        for (int level : new int[]{1, 2, 4, 5, 6}) assertTrue(html.contains("<h" + level + " "), html);
        assertTrue(html.contains("<strong>Strong with <em>emphasis</em></strong>"), html);
        assertTrue(html.contains("<del>removed</del>"), html);
        assertTrue(html.contains("<code>a &lt; b</code>"), html);
    }

    @Test void supportsNumberedNestedListsTasksQuotesAndTables() {
        String html = renderer.render("""
                3. First
                   - Nested item
                   - Another item
                4. Second

                - [x] Done
                - [ ] Pending

                > A quoted paragraph.
                >
                > Another paragraph.

                | Item | Count |
                | :--- | ---: |
                | **Notes** | 12 |
                """).body();
        for (String fragment : new String[]{"<ol start=\"3\">", "<ul>", "<blockquote>", "<table>", "<thead>", "<tbody>",
                "type=\"checkbox\"", "disabled", "checked", "<strong>Notes</strong>"}) assertTrue(html.contains(fragment), fragment + "\n" + html);
    }

    @Test void fencesAndIndentedCodePreserveSourceWithoutInventedBacktickNormalization() {
        var result = renderer.render("```java\n  if (a < b) {\n    return \"<script>\";\n  }\n```\n\n    raw <tag>\n\n`one\n two`");
        assertTrue(result.body().contains("data-copy=\"code-1\""));
        assertTrue(result.body().contains("&lt;script&gt;"));
        assertTrue(result.body().contains("raw &lt;tag&gt;\n"));
        assertTrue(result.body().contains("<code>one two</code>"), result.body());
        assertTrue(result.plainText().contains("  if (a < b) {"));
        assertTrue(result.plainText().contains("    return \"<script>\";"));
    }

    @Test void wikiLinksWorkButNeverInsideCodeImagesOrOtherLinks() {
        String html = renderer.render("[[Meeting notes]] `[[not a link]]` [**[[label]]**](https://example.test) ![[[alt]]](https://example.test/image.png)").body();
        assertTrue(html.contains("href=\"voidreach-note:Meeting+notes\""), html);
        assertEquals(1, html.split("href=\"voidreach-note:", -1).length - 1, html);
        assertTrue(html.contains("<code>[[not a link]]</code>"));
    }

    @Test void rawHtmlAndUnsafeUrisCannotExecuteAndImagesRequireExplicitLoading() {
        String html = renderer.render("""
                <script>alert(1)</script>

                [unsafe](javascript:alert%281%29) [file](file:///C:/secret.txt)
                [safe](https://example.test/path) [mail](mailto:hello@example.test) [anchor](#heading)
                ![Remote](https://example.test/pixel.png)
                ![Local](./image.png)
                ![Bad](data:image/svg+xml,anything)
                """).body();
        assertFalse(html.contains("<script>"), html);
        assertFalse(html.contains("href=\"javascript:"));
        assertFalse(html.contains("href=\"file:"));
        assertFalse(html.contains("<img"));
        assertTrue(html.contains("data-image-url=\"https://example.test/pixel.png\""), html);
        assertEquals(1, html.split("data-load-image=", -1).length - 1);
        assertTrue(html.contains("image path unavailable"));
        assertTrue(html.contains("href=\"https://example.test/path\""));
        assertTrue(html.contains("href=\"#heading\""));
    }

    @Test void permitsOnlyDeliberateExternalWebAndMailLinks() {
        for (String safe : new String[]{"https://example.test", "HTTP://example.test/a", "mailto:me@example.test"})
            assertTrue(ExternalLinks.allowed(safe), safe);
        for (String unsafe : new String[]{"javascript:alert(1)", "data:text/html,x", "file:/tmp/a", "//example.test", "https:", "https://", "", "https://exa mple.test"})
            assertFalse(ExternalLinks.allowed(unsafe), unsafe);
    }

    @Test void emptyContentIsValidAndOversizedPreviewFailsExplicitly() {
        assertEquals("", renderer.render("").body());
        assertThrows(IllegalArgumentException.class, () -> renderer.render("a".repeat(2_000_001)));
    }
}
