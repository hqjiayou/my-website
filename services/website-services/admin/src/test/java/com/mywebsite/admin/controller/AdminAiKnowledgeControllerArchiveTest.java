package com.mywebsite.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.mywebsite.admin.dto.AdminAiKnowledgeDtos;
import com.mywebsite.admin.service.AdminAiKnowledgeService;
import com.mywebsite.common.api.HttpMessageBody;

/** zip 批量导入：逐条入库、部分失败不回滚、系统垃圾跳过、非 zip 拒绝。 */
class AdminAiKnowledgeControllerArchiveTest {

    private static byte[] zipOf(String[]... entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String[] entry : entries) {
                zip.putNextEntry(new ZipEntry(entry[0]));
                if (entry.length > 1) {
                    zip.write(entry[1].getBytes(StandardCharsets.UTF_8));
                }
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    @Test
    void archiveImportsSupportedEntriesAndReportsFailures() throws Exception {
        AdminAiKnowledgeService service = mock(AdminAiKnowledgeService.class);
        AdminAiKnowledgeDtos.AdminAiKnowledgeDetailDto detail =
                mock(AdminAiKnowledgeDtos.AdminAiKnowledgeDetailDto.class);
        when(service.createUploadedDocument(eq(7L), any(), any())).thenReturn(detail);
        AdminAiKnowledgeController controller = new AdminAiKnowledgeController(service);

        byte[] zip = zipOf(
                new String[] {"docs/readme.md", "# 标题\n正文"},
                new String[] {"evil.exe", "MZ"},
                new String[] {"__MACOSX/._readme.md", "junk"},
                new String[] {"docs/", ""});
        MockMultipartFile file = new MockMultipartFile("file", "bundle.zip", "application/zip", zip);

        ResponseEntity<HttpMessageBody<AdminAiKnowledgeDtos.AdminAiKnowledgeArchiveResultDto>> response =
                controller.uploadArchive(7L, file, null);

        AdminAiKnowledgeDtos.AdminAiKnowledgeArchiveResultDto result = response.getBody().data();
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.documents()).containsExactly(detail);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).fileName()).isEqualTo("evil.exe");
        assertThat(result.failures().get(0).reason()).isEqualTo("admin.ai.kb.file.type");
    }

    @Test
    void archiveRejectsNonZipUpload() {
        AdminAiKnowledgeController controller =
                new AdminAiKnowledgeController(mock(AdminAiKnowledgeService.class));
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.md", "text/markdown", "# hi".getBytes(StandardCharsets.UTF_8));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.uploadArchive(7L, file, null))
                .isInstanceOf(com.mywebsite.common.exception.BusinessException.class);
    }
}
