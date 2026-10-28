package com.mywebsite.agent.service;

import com.mywebsite.infra.knowledge.KnowledgeContentConverter;
import com.mywebsite.infra.knowledge.KnowledgeTextExtractor;

/**
 * 聊天文档附件的文本抽取（站点层适配）：转换实现与知识库共用 infra 的
 * {@link KnowledgeContentConverter}（同一份格式覆盖与归一化，避免两处漂移），
 * 本类只保留聊天侧的异常类型与调用契约。产出为 Markdown 标准形，模型可直接阅读。
 *
 * <p>没有文字层的文件（扫描版 PDF 等）会抽出空文本，由调用方诚实拒绝
 * （聊天路径不做 OCR，扫描件请走知识库上传）。
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
        try {
            return KnowledgeContentConverter.toStoredContent(extension, bytes);
        } catch (KnowledgeTextExtractor.ExtractionException ex) {
            throw new ExtractionException(ex.getMessage(), ex);
        }
    }
}
