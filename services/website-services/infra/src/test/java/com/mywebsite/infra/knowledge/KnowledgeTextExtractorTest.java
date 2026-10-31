package com.mywebsite.infra.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import com.mywebsite.infra.knowledge.KnowledgeTextExtractor.ExtractionException;


/** PDF 文字层抽取（其余格式归一化见 {@link KnowledgeContentConverterTest}）。 */
class KnowledgeTextExtractorTest {

    @Test
    void pagedMarkdownInsertsPageHeading() throws IOException {
        byte[] pdf;
        try (PDDocument document = new PDDocument()) {
            addTextPage(document, "Page one");
            document.addPage(new PDPage());
            addTextPage(document, "Page three");
            pdf = toBytes(document);
        }
        String markdown = KnowledgeTextExtractor.pagedMarkdown(pdf);
        assertThat(markdown).contains("## 第 1 页").contains("Page one");
        assertThat(markdown).contains("## 第 3 页").contains("Page three");
        assertThat(markdown).doesNotContain("## 第 2 页");
        assertThat(KnowledgeTextExtractor.extractText("pdf", pdf)).doesNotContain("## 第");
    }

    @Test
    void extractsPdfTextLayer() throws IOException {
        String text = KnowledgeTextExtractor.extractText("pdf", pdfWithText("Hello KB PDF"));
        assertThat(text).contains("Hello KB PDF");
    }

    @Test
    void blankPdfExtractsToEmptyText() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            assertThat(KnowledgeTextExtractor.extractText("pdf", toBytes(document))).isBlank();
        }
    }

    @Test
    void corruptPdfThrowsExtractionException() {
        assertThatThrownBy(() -> KnowledgeTextExtractor.extractText(
                "pdf", "not a pdf".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ExtractionException.class);
    }

    @Test
    void unsupportedExtensionThrowsExtractionException() {
        assertThatThrownBy(() -> KnowledgeTextExtractor.extractText("exe", new byte[] {1, 2}))
                .isInstanceOf(ExtractionException.class);
    }

    private static byte[] pdfWithText(String content) throws IOException {
        try (PDDocument document = new PDDocument()) {
            addTextPage(document, content);
            return toBytes(document);
        }
    }

    private static void addTextPage(PDDocument document, String content) throws IOException {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            stream.newLineAtOffset(72, 720);
            stream.showText(content);
            stream.endText();
        }
    }

    private static byte[] toBytes(PDDocument document) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }
}
