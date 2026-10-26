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
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import com.mywebsite.admin.service.AdminAiKnowledgeTextExtractor.ExtractionException;

/**
 * 知识库上传文本抽取：PDF / DOCX / HTML 正常抽取、空白 PDF 诚实为空、损坏文件抛 ExtractionException。
 */
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
    void extractsDocxParagraphsAndTables() throws IOException {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("Docx body text");
            XWPFTable table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("cell-a");
            table.getRow(0).getCell(1).setText("cell-b");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            bytes = out.toByteArray();
        }
        String text = AdminAiKnowledgeTextExtractor.extractText("docx", bytes);
        assertThat(text).contains("Docx body text").contains("cell-a").contains("cell-b");
    }

    @Test
    void extractsHtmlBodyWithoutScripts() {
        String html = "<html><head><title>t</title><script>var secret = 1;</script></head>"
                + "<body><h1>Heading One</h1><p>First paragraph.</p></body></html>";
        String text = AdminAiKnowledgeTextExtractor.extractText("html", html.getBytes(StandardCharsets.UTF_8));
        assertThat(text).contains("Heading One").contains("First paragraph.").doesNotContain("secret");
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

    @Test
    void extractsXlsxCells() throws IOException {
        byte[] bytes;
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook =
                new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            var row = workbook.createSheet("s").createRow(0);
            row.createCell(0).setCellValue("Hours");
            row.createCell(1).setCellValue("9:00-18:00");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            bytes = out.toByteArray();
        }
        String text = AdminAiKnowledgeTextExtractor.extractText("xlsx", bytes);
        assertThat(text).contains("Hours").contains("9:00-18:00");
    }

    @Test
    void extractsPptxTextBoxes() throws IOException {
        byte[] bytes;
        try (org.apache.poi.xslf.usermodel.XMLSlideShow slideshow =
                new org.apache.poi.xslf.usermodel.XMLSlideShow()) {
            var slide = slideshow.createSlide();
            var textBox = slide.createTextBox();
            textBox.setText("Slide title text");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            slideshow.write(out);
            bytes = out.toByteArray();
        }
        String text = AdminAiKnowledgeTextExtractor.extractText("pptx", bytes);
        assertThat(text).contains("Slide title text");
    }
}
