package com.mywebsite.admin.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * 知识库上传文件的文本抽取：PDF（PDFBox 文字层）/ DOCX（POI 段落与表格）/ HTML（jsoup 正文）。
 * 扫描版 PDF 等没有文字层的文件会抽出空文本，由调用方按 FILE_EMPTY 诚实拒绝，不做 OCR。
 * 文件损坏、加密或格式伪造时抛 {@link ExtractionException}，调用方按无法读取处理。
 */
public final class AdminAiKnowledgeTextExtractor {

    /** 抽取失败（文件损坏、加密或并非声明的格式）。 */
    public static class ExtractionException extends RuntimeException {
        public ExtractionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private AdminAiKnowledgeTextExtractor() {
    }

    public static String extractText(String extension, byte[] bytes) {
        return switch (extension) {
            case "pdf" -> extractPdf(bytes);
            case "docx" -> extractDocx(bytes);
            case "html", "htm" -> extractHtml(bytes);
            default -> throw new ExtractionException("unsupported extension: " + extension, null);
        };
    }

    private static String extractPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException | RuntimeException ex) {
            throw new ExtractionException("failed to extract PDF text", ex);
        }
    }

    private static String extractDocx(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (IOException | RuntimeException ex) {
            throw new ExtractionException("failed to extract DOCX text", ex);
        }
    }

    private static String extractHtml(byte[] bytes) {
        try {
            Document document = Jsoup.parse(new ByteArrayInputStream(bytes), null, "");
            document.select("script, style, noscript").remove();
            return document.body() == null ? "" : document.body().wholeText();
        } catch (IOException | RuntimeException ex) {
            throw new ExtractionException("failed to extract HTML text", ex);
        }
    }
}
