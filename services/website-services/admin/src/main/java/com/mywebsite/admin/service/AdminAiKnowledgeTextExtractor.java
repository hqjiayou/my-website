package com.mywebsite.admin.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * 知识库上传文件的文本抽取：PDF（PDFBox 文字层）/ DOCX（POI 段落与表格）/ HTML（jsoup 正文）/
 * XLSX（POI 单元格，DataFormatter 渲染）/ PPTX（POI 文本框与表格）。
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
            case "xlsx" -> extractXlsx(bytes);
            case "pptx" -> extractPptx(bytes);
            default -> throw new ExtractionException("unsupported extension: " + extension, null);
        };
    }

    private static String extractXlsx(byte[] bytes) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            DataFormatter formatter = new DataFormatter();
            StringBuilder text = new StringBuilder();
            for (var sheet : workbook) {
                for (var row : sheet) {
                    StringBuilder line = new StringBuilder();
                    for (var cell : row) {
                        String value = formatter.formatCellValue(cell);
                        if (!value.isBlank()) {
                            if (line.length() > 0) {
                                line.append('\t');
                            }
                            line.append(value.trim());
                        }
                    }
                    if (line.length() > 0) {
                        text.append(line).append('\n');
                    }
                }
            }
            return text.toString();
        } catch (IOException | RuntimeException ex) {
            throw new ExtractionException("failed to extract XLSX text", ex);
        }
    }

    private static String extractPptx(byte[] bytes) {
        try (XMLSlideShow slideshow = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            StringBuilder text = new StringBuilder();
            for (var slide : slideshow.getSlides()) {
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String value = textShape.getText();
                        if (value != null && !value.isBlank()) {
                            text.append(value.trim()).append('\n');
                        }
                    } else if (shape instanceof XSLFTable table) {
                        for (var row : table) {
                            for (var cell : row) {
                                String value = cell.getText();
                                if (value != null && !value.isBlank()) {
                                    text.append(value.trim()).append('\t');
                                }
                            }
                            text.append('\n');
                        }
                    }
                }
            }
            return text.toString();
        } catch (IOException | RuntimeException ex) {
            throw new ExtractionException("failed to extract PPTX text", ex);
        }
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
