package com.mywebsite.site.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;

/**
 * 聊天文档附件的纯文本抽取（站点层）。与知识库的 Markdown 归一化（admin 模块）分工不同：
 * 这里只产出给模型阅读的纯文本，不做索引格式转换。
 *
 * <p>支持 pdf / docx / xlsx / pptx / txt / md。扫描版 PDF 等没有文字层的文件会抽出空文本，
 * 由调用方诚实拒绝（聊天路径不做 OCR，扫描件请走知识库上传）。
 */
public final class ChatDocumentTextExtractor {

    /** 抽取失败（文件损坏、加密或并非声明的格式）。 */
    public static class ExtractionException extends RuntimeException {
        public ExtractionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private ChatDocumentTextExtractor() {}

    public static String extractText(String extension, byte[] bytes) {
        return switch (extension) {
            case "pdf" -> extractPdf(bytes);
            case "docx" -> extractDocx(bytes);
            case "xlsx" -> extractXlsx(bytes);
            case "pptx" -> extractPptx(bytes);
            case "txt", "md", "markdown" -> decodeText(bytes);
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
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder out = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText();
                    if (text != null && !text.isBlank()) {
                        out.append(text.trim()).append('\n');
                    }
                } else if (element instanceof XWPFTable table) {
                    for (var row : table.getRows()) {
                        StringBuilder line = new StringBuilder();
                        for (XWPFTableCell cell : row.getTableCells()) {
                            if (line.length() > 0) {
                                line.append(" | ");
                            }
                            line.append(cell.getText() == null ? "" : cell.getText().trim());
                        }
                        if (line.length() > 0) {
                            out.append(line).append('\n');
                        }
                    }
                }
            }
            return out.toString();
        } catch (IOException | RuntimeException ex) {
            throw new ExtractionException("failed to extract DOCX text", ex);
        }
    }

    private static String extractXlsx(byte[] bytes) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            DataFormatter formatter = new DataFormatter();
            StringBuilder out = new StringBuilder();
            for (var sheet : workbook) {
                out.append("# ").append(sheet.getSheetName()).append('\n');
                for (var row : sheet) {
                    StringBuilder line = new StringBuilder();
                    for (var cell : row) {
                        if (line.length() > 0) {
                            line.append(" | ");
                        }
                        line.append(formatter.formatCellValue(cell));
                    }
                    if (line.length() > 0) {
                        out.append(line).append('\n');
                    }
                }
            }
            return out.toString();
        } catch (IOException | RuntimeException ex) {
            throw new ExtractionException("failed to extract XLSX text", ex);
        }
    }

    private static String extractPptx(byte[] bytes) {
        try (XMLSlideShow slideShow = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            StringBuilder out = new StringBuilder();
            int index = 0;
            for (XSLFSlide slide : slideShow.getSlides()) {
                index++;
                out.append("# Slide ").append(index).append('\n');
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            out.append(text.trim()).append('\n');
                        }
                    }
                }
            }
            return out.toString();
        } catch (IOException | RuntimeException ex) {
            throw new ExtractionException("failed to extract PPTX text", ex);
        }
    }

    /** 与知识库文本类一致的回退解码：UTF-8 → MS932 → GB18030。 */
    static String decodeText(byte[] bytes) {
        try {
            return decodeStrict(bytes, StandardCharsets.UTF_8);
        } catch (CharacterCodingException utf8Failure) {
            for (String fallbackName : new String[] {"MS932", "GB18030"}) {
                try {
                    return decodeStrict(bytes, Charset.forName(fallbackName));
                } catch (CharacterCodingException | RuntimeException fallbackFailure) {
                    // 继续下一个候选字符集
                }
            }
            throw new ExtractionException("failed to decode text (not UTF-8/MS932/GB18030)", utf8Failure);
        }
    }

    private static String decodeStrict(byte[] bytes, Charset charset) throws CharacterCodingException {
        String text = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }
}
