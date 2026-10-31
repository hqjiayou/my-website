package com.mywebsite.infra.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

/** 上传内容归一化：各格式转 Markdown 标准形（标题/表格/围栏）、编码回退、CSV 表格化。 */
class KnowledgeContentConverterTest {

    @Test
    void docxBecomesMarkdownWithHeadingsAndTables() throws IOException {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("Guide Title");
            XWPFParagraph body = document.createParagraph();
            body.createRun().setText("Docx body text");
            XWPFTable table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("cell-a");
            table.getRow(0).getCell(1).setText("cell-b");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            bytes = out.toByteArray();
        }
        String markdown = KnowledgeContentConverter.toStoredContent("docx", bytes);
        assertThat(markdown).contains("Docx body text").contains("| cell-a | cell-b |");
    }

    @Test
    void spreadsheetSegmentsKeepOriginalRowNumbersOutsideStoredContent() throws IOException {
        byte[] xlsx;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Hours");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Day");
            header.createCell(1).setCellValue("Hours");
            sheet.createRow(3);
            var data = sheet.createRow(4);
            data.createCell(0).setCellValue("Mon");
            data.createCell(1).setCellValue(8);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            xlsx = out.toByteArray();
        }
        String stored = KnowledgeContentConverter.toStoredContent("xlsx", xlsx);
        var segments = KnowledgeContentConverter.spreadsheetSegments(xlsx);
        assertThat(segments).hasSize(1);
        assertThat(segments.getFirst().sheet()).isEqualTo("Hours");
        assertThat(segments.getFirst().rowStart()).isEqualTo(1);
        assertThat(segments.getFirst().rowEnd()).isEqualTo(5);
        assertThat(stored).doesNotContain("rowStart").doesNotContain("第 5 行");
        assertThat(stored).contains("| Day | Hours |").contains("| Mon | 8 |");
    }

    @Test
    void xlsxBecomesMarkdownTablePerSheet() throws IOException {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var row = workbook.createSheet("Hours").createRow(0);
            row.createCell(0).setCellValue("Day");
            row.createCell(1).setCellValue("9:00-18:00");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            bytes = out.toByteArray();
        }
        String markdown = KnowledgeContentConverter.toStoredContent("xlsx", bytes);
        assertThat(markdown).contains("## Hours").contains("| Day | 9:00-18:00 |").contains("| --- | --- |");
    }

    @Test
    void xlsLegacyBecomesMarkdownTablePerSheet() throws IOException {
        byte[] bytes;
        try (org.apache.poi.hssf.usermodel.HSSFWorkbook workbook =
                new org.apache.poi.hssf.usermodel.HSSFWorkbook()) {
            var row = workbook.createSheet("Prices").createRow(0);
            row.createCell(0).setCellValue("Item");
            row.createCell(1).setCellValue("1200");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            bytes = out.toByteArray();
        }
        String markdown = KnowledgeContentConverter.toStoredContent("xls", bytes);
        assertThat(markdown).contains("## Prices").contains("| Item | 1200 |");
    }

    @Test
    void docmRoutesToDocxConverter() throws IOException {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("Macro doc body");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            bytes = out.toByteArray();
        }
        assertThat(KnowledgeContentConverter.toStoredContent("docm", bytes))
                .contains("Macro doc body");
    }

    @Test
    void everySupportedNonImageExtensionIsRoutable() {
        // 与 AdminAiKnowledgeController.SUPPORTED_EXTENSIONS 去掉图片后的集合一致：
        // 任一扩展名都不许在转换器里落到 "unsupported extension"（上传被接受却转换
        // 不支持是最坏的漂移）。二进制格式喂垃圾字节只断言路由存在，不求转换成功。
        String[] routable = {
            "txt", "md", "markdown", "text", "rst", "adoc", "asciidoc", "org", "tex",
            "csv", "tsv", "json", "xml", "yaml", "yml", "toml", "ini", "log",
            "jsonl", "ndjson", "properties", "conf", "cfg", "env", "diff", "patch",
            "py", "java", "js", "ts", "tsx", "jsx", "css", "scss", "sql", "sh",
            "bash", "zsh", "ps1", "mjs", "cjs",
            "go", "rs", "c", "h", "cpp", "hpp", "cs", "rb", "php", "swift", "kt", "kts",
            "scala", "pl", "r", "lua", "vue", "svelte", "dart", "groovy", "gradle",
            "hs", "ex", "exs", "tf", "proto",
            "pdf", "docx", "docm", "doc", "html", "htm", "xlsx", "xlsm", "xls",
            "pptx", "pptm", "ppt", "epub", "odt", "ods", "odp", "rtf",
        };
        byte[] sample = "sample content".getBytes(StandardCharsets.UTF_8);
        for (String extension : routable) {
            try {
                KnowledgeContentConverter.toStoredContent(extension, sample);
            } catch (KnowledgeTextExtractor.ExtractionException ex) {
                assertThat(ex.getMessage())
                        .as("extension %s must be routable", extension)
                        .doesNotStartWith("unsupported extension");
            }
        }
    }

    @Test
    void pptxBecomesMarkdownSections() throws IOException {
        byte[] bytes;
        try (XMLSlideShow slideshow = new XMLSlideShow()) {
            var slide = slideshow.createSlide();
            slide.createTextBox().setText("Slide title text");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            slideshow.write(out);
            bytes = out.toByteArray();
        }
        String markdown = KnowledgeContentConverter.toStoredContent("pptx", bytes);
        assertThat(markdown).contains("## Slide 1").contains("Slide title text");
    }

    @Test
    void htmlBecomesMarkdownWithoutScripts() {
        String html = "<html><head><title>t</title><script>var secret = 1;</script></head>"
                + "<body><h1>Heading One</h1><p>First paragraph.</p></body></html>";
        String markdown =
                KnowledgeContentConverter.toStoredContent("html", html.getBytes(StandardCharsets.UTF_8));
        assertThat(markdown).contains("# Heading One").contains("First paragraph.").doesNotContain("secret");
    }

    @Test
    void csvBecomesMarkdownTable() {
        String csv = "name,hours\nMon,9-18\nTue,10-19\n";
        String markdown =
                KnowledgeContentConverter.toStoredContent("csv", csv.getBytes(StandardCharsets.UTF_8));
        assertThat(markdown).contains("| name | hours |").contains("| --- | --- |").contains("| Mon | 9-18 |");
    }

    @Test
    void codeFileIsFencedWithLanguage() {
        String code = "print('hi')\n";
        String stored =
                KnowledgeContentConverter.toStoredContent("py", code.getBytes(StandardCharsets.UTF_8));
        assertThat(stored).startsWith("```py").contains("print('hi')").endsWith("```");
    }

    @Test
    void shiftJisTextFallsBackFromUtf8() {
        byte[] sjis = "営業時間は9時から18時です".getBytes(Charset.forName("MS932"));
        String stored = KnowledgeContentConverter.toStoredContent("txt", sjis);
        assertThat(stored).contains("営業時間");
    }

    @Test
    void odtParagraphsAndHeadingsBecomeMarkdown() throws IOException {
        String contentXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                    xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                  <office:body><office:text>
                    <text:h text:outline-level="1">Odt Heading</text:h>
                    <text:p>Odt paragraph text.</text:p>
                  </office:text></office:body>
                </office:document-content>
                """;
        byte[] bytes = zipOf("content.xml", contentXml);
        String markdown = KnowledgeContentConverter.toStoredContent("odt", bytes);
        assertThat(markdown).contains("# Odt Heading").contains("Odt paragraph text.");
    }

    @Test
    void epubChaptersBecomeMarkdown() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            putZip(zip, "META-INF/container.xml", """
                    <?xml version="1.0"?>
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                      <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                    </container>
                    """);
            putZip(zip, "OEBPS/content.opf", """
                    <?xml version="1.0"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                      <manifest><item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/></manifest>
                      <spine><itemref idref="c1"/></spine>
                    </package>
                    """);
            putZip(zip, "OEBPS/ch1.xhtml",
                    "<html><body><h1>Chapter One</h1><p>Hello EPUB.</p></body></html>");
        }
        String markdown = KnowledgeContentConverter.toStoredContent("epub", buffer.toByteArray());
        assertThat(markdown).contains("# Chapter One").contains("Hello EPUB.");
    }

    @Test
    void unevaluatedFormulaCellIsBlankAndCounted() throws IOException {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var row = workbook.createSheet("Hours").createRow(0);
            row.createCell(0).setCellValue("Label");
            row.createCell(1).setCellFormula("1+1");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            bytes = out.toByteArray();
        }
        var extraction = KnowledgeContentConverter.readSpreadsheet(bytes);
        assertThat(extraction.unevaluatedFormulaCount()).isGreaterThanOrEqualTo(1);
        assertThat(extraction.markdown()).contains("Label").doesNotContain("1+1").doesNotContain("SUM");
    }

    @Test
    void cachedFormulaValueIsKeptWithoutWarning() throws IOException {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Hours");
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("Label");
            var formula = row.createCell(1);
            formula.setCellFormula("1+1");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(formula);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            bytes = out.toByteArray();
        }
        var extraction = KnowledgeContentConverter.readSpreadsheet(bytes);
        assertThat(extraction.unevaluatedFormulaCount()).isZero();
        assertThat(extraction.markdown()).contains("Label").contains("2").doesNotContain("1+1");
    }

    @Test
    void spreadsheetTablesKeepOriginalRowNumbersAcrossBlankRows() throws IOException {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Hours");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Day");
            header.createCell(1).setCellValue("Hours");
            sheet.createRow(1);
            var data = sheet.createRow(4);
            data.createCell(0).setCellValue("Mon");
            data.createCell(1).setCellValue("8:00");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            bytes = out.toByteArray();
        }
        var table = KnowledgeContentConverter.readSpreadsheet(bytes).tables().getFirst();
        assertThat(table.headerRow()).isEqualTo(1);
        assertThat(table.headers()).containsExactly("Day", "Hours");
        assertThat(table.rows()).hasSize(1);
        assertThat(table.rows().getFirst().rowNumber()).isEqualTo(5);
        assertThat(table.rows().getFirst().cells()).contains("8:00");
    }

    @Test
    void attendanceShapedSheetTakesHeaderAboveTheDateBlock() throws IOException {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("10月");
            sheet.createRow(0).createCell(0).setCellValue("勤務管理表2026");
            var person = sheet.createRow(1);
            person.createCell(0).setCellValue("氏名");
            person.createCell(1).setCellValue("山田");
            var team = sheet.createRow(2);
            team.createCell(0).setCellValue("所属");
            team.createCell(1).setCellValue("開発");
            var header = sheet.createRow(18);
            header.createCell(0).setCellValue("日付");
            header.createCell(1).setCellValue("ｺｰﾄﾞ");
            header.createCell(2).setCellValue("出勤");
            header.createCell(3).setCellValue("退勤");
            header.createCell(4).setCellValue("休憩時間");
            header.createCell(5).setCellValue("勤務時間");
            fillAttendance(sheet.createRow(19), "2026/10/1", "40", "9:00", "18:00", "60", "8:00");
            fillAttendance(sheet.createRow(20), "10/2", "40", "9:00", "18:00", "60", "8:00");
            var dated = sheet.createRow(21);
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy/m/d"));
            var dateCell = dated.createCell(0);
            dateCell.setCellValue(LocalDate.of(2026, 10, 3));
            dateCell.setCellStyle(dateStyle);
            dated.createCell(1).setCellValue("40");
            dated.createCell(2).setCellValue("9:00");
            dated.createCell(3).setCellValue("18:00");
            dated.createCell(4).setCellValue("60");
            dated.createCell(5).setCellValue("8:00");
            var total = sheet.createRow(22);
            total.createCell(0).setCellValue("合計");
            total.createCell(5).setCellValue("24:00");
            var project = sheet.createRow(23);
            project.createCell(0).setCellValue("プロジェクトコード");
            project.createCell(1).setCellValue("P-100");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            bytes = out.toByteArray();
        }
        var extraction = KnowledgeContentConverter.readSpreadsheet(bytes);
        assertThat(extraction.markdown()).contains("勤務管理表2026").contains("合計").contains("プロジェクトコード");
        assertThat(extraction.segments()).hasSize(1);
        assertThat(extraction.segments().getFirst().rowStart()).isEqualTo(1);
        assertThat(extraction.segments().getFirst().rowEnd()).isEqualTo(24);
        assertThat(extraction.unevaluatedFormulaCount()).isZero();
        var table = extraction.tables().getFirst();
        assertThat(table.headerRow()).isEqualTo(19);
        assertThat(table.headers()).containsExactly("日付", "ｺｰﾄﾞ", "出勤", "退勤", "休憩時間", "勤務時間");
        assertThat(table.rows()).hasSize(3);
        assertThat(table.rows().get(0).rowNumber()).isEqualTo(20);
        assertThat(table.rows().get(0).cells().getFirst()).isEqualTo("2026/10/1");
        assertThat(table.rows().get(1).rowNumber()).isEqualTo(21);
        assertThat(table.rows().get(1).cells().getFirst()).isEqualTo("10/2");
        assertThat(table.rows().get(2).rowNumber()).isEqualTo(22);
        assertThat(table.rows().get(2).cells().getFirst()).matches("\\d{4}/\\d{1,2}/\\d{1,2}");
        assertThat(table.rows())
                .extracting(row -> row.cells().getFirst())
                .noneMatch(cell -> cell.contains("合計") || cell.contains("プロジェクトコード") || cell.contains("氏名"));
    }

    private static void fillAttendance(
            org.apache.poi.ss.usermodel.Row row,
            String date,
            String code,
            String start,
            String end,
            String breakMinutes,
            String hours) {
        row.createCell(0).setCellValue(date);
        row.createCell(1).setCellValue(code);
        row.createCell(2).setCellValue(start);
        row.createCell(3).setCellValue(end);
        row.createCell(4).setCellValue(breakMinutes);
        row.createCell(5).setCellValue(hours);
    }

    private static byte[] zipOf(String name, String content) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            putZip(zip, name, content);
        }
        return buffer.toByteArray();
    }

    private static void putZip(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
