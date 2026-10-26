package com.mywebsite.admin.service;

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

import com.mywebsite.admin.service.AdminAiKnowledgeTextExtractor.ExtractionException;

/** PDF 文字层抽取（其余格式归一化见 {@link AdminAiKnowledgeContentConverterTest}）。 */
class AdminAiKnowledgeTextExtractorTest {

    @Test
    void extractsPdfTextLayer() throws IOException {
        String text = AdminAiKnowledgeTextExtractor.extractText("pdf", pdfWithText("Hello KB PDF"));
        assertThat(text).contains("Hello KB PDF");
    }

    @Test
    void blankPdfExtractsToEmptyText() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            assertThat(AdminAiKnowledgeTextExtractor.extractText("pdf", toBytes(document))).isBlank();
        }
    }

    @Test
    void corruptPdfThrowsExtractionException() {
        assertThatThrownBy(() -> AdminAiKnowledgeTextExtractor.extractText(
                "pdf", "not a pdf".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ExtractionException.class);
    }

    @Test
    void unsupportedExtensionThrowsExtractionException() {
        assertThatThrownBy(() -> AdminAiKnowledgeTextExtractor.extractText("exe", new byte[] {1, 2}))
                .isInstanceOf(ExtractionException.class);
    }

    private static byte[] pdfWithText(String content) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText(content);
                stream.endText();
            }
            return toBytes(document);
        }
    }

    private static byte[] toBytes(PDDocument document) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }
}
