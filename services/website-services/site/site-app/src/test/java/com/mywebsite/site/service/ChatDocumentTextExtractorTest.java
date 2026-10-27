package com.mywebsite.site.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mywebsite.site.service.ChatDocumentTextExtractor.ExtractionException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

/** 聊天文档纯文本抽取：各格式抽得到、文本类编码回退、坏文件诚实失败。 */
class ChatDocumentTextExtractorTest {

    @Test
    void extractsUtf8Text() {
        byte[] bytes = "你好，文档".getBytes(StandardCharsets.UTF_8);
        assertThat(ChatDocumentTextExtractor.extractText("txt", bytes)).isEqualTo("你好，文档");
    }

    @Test
    void decodesGb18030Fallback() {
        byte[] bytes = "中文内容".getBytes(Charset.forName("GB18030"));
        assertThat(ChatDocumentTextExtractor.extractText("md", bytes)).isEqualTo("中文内容");
    }

    @Test
    void extractsPdfTextLayer() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText("Hello PDF");
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            pdf = out.toByteArray();
        }
        assertThat(ChatDocumentTextExtractor.extractText("pdf", pdf)).contains("Hello PDF");
    }

    @Test
    void extractsDocxParagraphsAndTables() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("第一段");
            var table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("甲");
            table.getRow(0).getCell(1).setText("乙");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            docx = out.toByteArray();
        }
        String text = ChatDocumentTextExtractor.extractText("docx", docx);
        assertThat(text).contains("第一段").contains("甲 | 乙");
    }

    @Test
    void extractsXlsxCells() throws Exception {
        byte[] xlsx;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("库存");
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("苹果");
            row.createCell(1).setCellValue(12);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            xlsx = out.toByteArray();
        }
        String text = ChatDocumentTextExtractor.extractText("xlsx", xlsx);
        assertThat(text).contains("库存").contains("苹果 | 12");
    }

    @Test
    void extractsPptxSlideText() throws Exception {
        byte[] pptx;
        try (XMLSlideShow slideShow = new XMLSlideShow()) {
            var slide = slideShow.createSlide();
            var box = slide.createTextBox();
            box.setText("标题文字");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            slideShow.write(out);
            pptx = out.toByteArray();
        }
        assertThat(ChatDocumentTextExtractor.extractText("pptx", pptx)).contains("标题文字");
    }

    @Test
    void rejectsUnsupportedExtension() {
        assertThatThrownBy(() -> ChatDocumentTextExtractor.extractText("exe", new byte[] {1}))
                .isInstanceOf(ExtractionException.class);
    }

    @Test
    void rejectsCorruptDocx() {
        assertThatThrownBy(() -> ChatDocumentTextExtractor.extractText("docx", "not a zip".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ExtractionException.class);
    }
}
