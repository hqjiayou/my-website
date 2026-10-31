package com.mywebsite.infra.knowledge;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    /** 一页文字层。page 是 PDF 页序（1 起），空文字层也保留页码。 */
    public record PageText(int page, String text) {}

    /**
     * 按页抽取。聊天附件仍走 {@link #extractText}，不插入页标题。
     */
    public static List<PageText> pages(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            int count = document.getNumberOfPages();
            List<PageText> pages = new ArrayList<>();
            for (int page = 1; page <= count; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                pages.add(new PageText(page, text == null ? "" : text));
            }
            return List.copyOf(pages);
        } catch (IOException | RuntimeException ex) {
            throw new ExtractionException("failed to extract PDF text", ex);
        }
    }

    /** 知识库正文：非空页前插入 {@code ## 第 N 页}。全空则返回空串。 */
    public static String pagedMarkdown(byte[] bytes) {
        return pagedMarkdown(pages(bytes));
    }

    public static String pagedMarkdown(List<PageText> pages) {
        StringBuilder out = new StringBuilder();
        for (PageText page : pages) {
            String body = page.text() == null ? "" : page.text().strip();
            if (body.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append("\n\n");
            }
            out.append("## 第 ").append(page.page()).append(" 页\n\n").append(body);
        }
        return out.toString();
    }
}
