package com.mywebsite.infra.knowledge;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * PDF 文字层抽取（PDFBox）。其余格式的归一化（转 Markdown）在 {@link KnowledgeContentConverter}。
 * 扫描版 PDF 等没有文字层的文件会抽出空文本，由调用方按 FILE_EMPTY 诚实拒绝，不做 OCR。
 */
public final class KnowledgeTextExtractor {

    /** 抽取失败（文件损坏、加密或并非声明的格式）。 */
    public static class ExtractionException extends RuntimeException {
        public ExtractionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private KnowledgeTextExtractor() {}

    public static String extractText(String extension, byte[] bytes) {
        if (!"pdf".equals(extension)) {
            throw new ExtractionException("unsupported extension: " + extension, null);
        }
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException | RuntimeException ex) {
            throw new ExtractionException("failed to extract PDF text", ex);
        }
    }
}
