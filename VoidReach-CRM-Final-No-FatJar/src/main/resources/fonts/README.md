# Bundled typefaces

These are unmodified upstream assets; no system font installation is required.

- **Inter 4.1**: static TTF faces (Regular, Medium, SemiBold, Bold and corresponding italics) and WOFF2 reading faces. Source: [official Inter 4.1 release](https://github.com/rsms/inter/releases/tag/v4.1), Inter-4.1.zip. License: [Inter-LICENSE.txt](Inter-LICENSE.txt).
- **JetBrains Mono 2.304**: static TTF faces (Regular, Medium, SemiBold, Bold and corresponding italics) and Regular WOFF2. Source: [official tagged repository](https://github.com/JetBrains/JetBrainsMono/tree/v2.304/fonts). License: [JetBrainsMono-OFL.txt](JetBrainsMono-OFL.txt).

Both families use SIL OFL 1.1; their complete original license notices accompany them. The application's proprietary license does not replace these licenses.

JavaFX loads TTF resources through streams, which also works in JARs and paths containing spaces. Medium and SemiBold have distinct legacy family names; Typography.editorFamily and the application stylesheet select those actual families. WebView receives local, embedded WOFF2 resources; it does not request fonts from a CDN.
