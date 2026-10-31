package com.mywebsite.infra.knowledge;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.jsoup.Jsoup;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

import com.mywebsite.infra.knowledge.KnowledgeTextExtractor.ExtractionException;



/**
 * 文档内容归一化（共享）：知识库上传（admin）与聊天文档附件（agent）共用同一份实现，
 * 避免两处格式覆盖与转换行为漂移。所有格式统一转成 Markdown 标准形（PDF 文字层无结构、存纯文本段落）。
 * 文本类（txt/md/标记/代码/数据）按 UTF-8→MS932→GB18030 回退解码，代码/数据类包代码围栏，CSV/TSV 转 Md 表格；
 * 二进制类（docx/docm/doc、xlsx/xlsm/xls、pptx/pptm/ppt、html/epub/odt/ods/odp/rtf）走各自转换器，
 * 其中旧版二进制 doc/ppt 只有纯文本可抽、电子表格经 WorkbookFactory 统一处理新旧格式。
 * 转换失败抛 {@link ExtractionException}。
 */
public final class KnowledgeContentConverter {

    /**
     * 索引入库段。表格的行区间只放在这里，不写入 {@link #toStoredContent} 的正文。
     * page 与 sheet 不同时使用。
     */
    public record IndexSegment(String text, Integer page, String sheet, Integer rowStart, Integer rowEnd) {}

    /** 一张表的结构化行。表头行不进入 rows；rowNumber 是工作表原始行号（1 起）。 */
    public record SheetRow(int rowNumber, List<String> cells) {}

    public record SheetTable(
            String sheet,
            int headerRow,
            List<String> headers,
            List<SheetRow> rows,
            boolean truncated) {}

    /** 一次电子表格读取：正文、位置段、结构化行、未求值公式格计数。 */
    public record SpreadsheetExtraction(
            String markdown,
            List<IndexSegment> segments,
            List<SheetTable> tables,
            int unevaluatedFormulaCount) {}

    private static final int MAX_TABLE_ROWS = 1000;
    private static final int MAX_TABLE_COLS = 50;
    private static final Pattern HEADING_STYLE =
            Pattern.compile("(?i)(heading|标题|見出し)\\s*([1-6])");
    /** 整格文本是日期样式才算。yyyy/M/d、yyyy-M-d、M/d、M-d。 */
    private static final Pattern DATE_TEXT = Pattern.compile(
            "^\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}$|^\\d{1,2}[/-]\\d{1,2}$");

    /** 结构化表定位用。正文和位置段仍只收非空行。 */
    private record SheetScanRow(
            int excelRow, List<String> cells, boolean blank, boolean dateFirst, int textCells) {}

    private KnowledgeContentConverter() {}

    public static String toStoredContent(String extension, byte[] bytes) {
        return switch (extension) {
            case "txt", "md", "markdown", "text", "rst", "adoc", "asciidoc", "org", "tex" ->
                    decodeText(bytes);
            case "csv" -> toMarkdownTable(decodeText(bytes), ',');
            case "tsv" -> toMarkdownTable(decodeText(bytes), '\t');
            case "json", "xml", "yaml", "yml", "toml", "ini", "log",
                    "jsonl", "ndjson", "properties", "conf", "cfg", "env", "diff", "patch",
                    "py", "java", "js", "ts", "tsx", "jsx", "css", "scss", "sql", "sh",
                    "bash", "zsh", "ps1", "mjs", "cjs",
                    "go", "rs", "c", "h", "cpp", "hpp", "cs", "rb", "php", "swift", "kt", "kts",
                    "scala", "pl", "r", "lua", "vue", "svelte", "dart", "groovy", "gradle",
                    "hs", "ex", "exs", "tf", "proto" ->
                    fence(extension, decodeText(bytes));
            case "pdf" -> KnowledgeTextExtractor.extractText("pdf", bytes);
            case "docx", "docm" -> docxToMarkdown(bytes);
            case "doc" -> docLegacyToText(bytes);
            case "xlsx", "xlsm", "xls" -> workbookToMarkdown(bytes);
            case "pptx", "pptm" -> pptxToMarkdown(bytes);
            case "ppt" -> pptLegacyToText(bytes);
            case "html", "htm" -> htmlToMarkdown(bytes);
            case "epub" -> epubToMarkdown(bytes);
            case "odt", "ods", "odp" -> odfToMarkdown(bytes);
            case "rtf" -> rtfToText(bytes);
            default -> throw new ExtractionException("unsupported extension: " + extension, null);
        };
    }

    // ---------- 文本解码与围栏 ----------

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

    private static String fence(String extension, String text) {
        String lang = switch (extension) {
            case "yml" -> "yaml";
            case "jsonl", "ndjson" -> "json";
            case "log", "ini", "properties", "conf", "cfg", "env" -> "";
            default -> extension;
        };
        return "```" + lang + "\n" + text.strip() + "\n```";
    }

    // ---------- CSV/TSV → Md 表格 ----------

    static String toMarkdownTable(String text, char delimiter) {
        List<List<String>> rows = parseDelimited(text, delimiter);
        rows.removeIf(row -> row.stream().allMatch(String::isBlank));
        if (rows.isEmpty()) {
            return "";
        }
        return renderMarkdownTable(rows);
    }

    private static List<List<String>> parseDelimited(String text, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == delimiter) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                if (rows.size() > MAX_TABLE_ROWS) {
                    return rows;
                }
            } else {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    private static String renderMarkdownTable(List<List<String>> rows) {
        int cols = Math.min(
                MAX_TABLE_COLS, rows.stream().mapToInt(List::size).max().orElse(0));
        if (cols == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            out.append('|');
            for (int c = 0; c < cols; c++) {
                String value = c < row.size() ? row.get(c) : "";
                out.append(' ').append(value.trim().replace("|", "\\|").replaceAll("\\s+", " ")).append(" |");
            }
            out.append('\n');
            if (r == 0) {
                out.append('|');
                for (int c = 0; c < cols; c++) {
                    out.append(" --- |");
                }
                out.append('\n');
            }
        }
        return out.toString();
    }

    // ---------- DOCX → Md ----------

    private static String docxToMarkdown(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder out = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    appendDocxParagraph(document, paragraph, out);
                } else if (element instanceof XWPFTable table) {
                    out.append(renderMarkdownTable(tableRows(table))).append('\n');
                }
            }
            return out.toString();
        } catch (IOException | RuntimeException ex) {
            throw new ExtractionException("failed to convert DOCX", ex);
        }
    }

    private static void appendDocxParagraph(XWPFDocument document, XWPFParagraph paragraph, StringBuilder out) {
        String text = paragraph.getText() == null ? "" : paragraph.getText().trim();
        if (text.isBlank()) {
            return;
        }
        int headingLevel = headingLevel(document, paragraph);
        if (headingLevel > 0) {
            out.append("#".repeat(headingLevel)).append(' ').append(text).append("\n\n");
        } else if (paragraph.getNumID() != null) {
            out.append("- ").append(text).append('\n');
        } else {
            out.append(text).append("\n\n");
        }
    }

    private static int headingLevel(XWPFDocument document, XWPFParagraph paragraph) {
        String styleId = paragraph.getStyle();
        if (styleId == null || document.getStyles() == null) {
            return 0;
        }
        var style = document.getStyles().getStyle(styleId);
        if (style == null || style.getName() == null) {
            return 0;
        }
        if (style.getName().equalsIgnoreCase("Title")) {
            return 1;
        }
        Matcher matcher = HEADING_STYLE.matcher(style.getName());
        return matcher.find() ? Integer.parseInt(matcher.group(2)) : 0;
    }

    private static List<List<String>> tableRows(XWPFTable table) {
        List<List<String>> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                cells.add(cell.getText() == null ? "" : cell.getText());
            }
            rows.add(cells);
            if (rows.size() > MAX_TABLE_ROWS) {
                break;
            }
        }
        return rows;
    }

    // ---------- XLSX → Md ----------

    private static String workbookToMarkdown(byte[] bytes) {
        return readSpreadsheet(bytes).markdown();
    }

    /**
     * 表格位置段。正文仍是 {@link #workbookToMarkdown} 的 Markdown，不含行号标记。
     * rowStart/rowEnd 是工作表原始行号（1 起），空行不占正文但仍落在区间内。
     */
    public static List<IndexSegment> spreadsheetSegments(byte[] bytes) {
        return readSpreadsheet(bytes).segments();
    }

    /** 未求值公式格计数。非电子表格或读失败时由调用方处理。 */
    public static int unevaluatedFormulaCount(byte[] bytes) {
        return readSpreadsheet(bytes).unevaluatedFormulaCount();
    }

    /**
     * 一次读完正文、位置段和结构化行。公式格没有可用缓存结果时该格正文为空，公式原文不入库。
     * 有缓存结果时写入缓存的显示文本，不重新计算公式。
     */
    public static SpreadsheetExtraction readSpreadsheet(byte[] bytes) {
        // WorkbookFactory 按魔数自动区分 OOXML（xlsx/xlsm）与旧版二进制（xls/HSSF）
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            DataFormatter formatter = new DataFormatter();
            int[] unevaluated = {0};
            StringBuilder markdown = new StringBuilder();
            List<IndexSegment> segments = new ArrayList<>();
            List<SheetTable> tables = new ArrayList<>();
            for (var sheet : workbook) {
                List<List<String>> rows = new ArrayList<>();
                List<SheetScanRow> scans = new ArrayList<>();
                int rowStart = 0;
                int rowEnd = 0;
                boolean truncated = false;
                for (var row : sheet) {
                    List<String> cells = new ArrayList<>();
                    boolean dateFirst = false;
                    boolean seenValue = false;
                    int textCells = 0;
                    for (var cell : row) {
                        String text = cellText(cell, formatter, unevaluated);
                        cells.add(text);
                        if (!text.isBlank()) {
                            boolean date = isDateValue(cell, text);
                            if (!seenValue) {
                                dateFirst = date;
                                seenValue = true;
                            }
                            if (!date) {
                                textCells++;
                            }
                        }
                        if (cells.size() >= MAX_TABLE_COLS) {
                            break;
                        }
                    }
                    int excelRow = row.getRowNum() + 1;
                    scans.add(new SheetScanRow(excelRow, cells, !seenValue, dateFirst, textCells));
                    if (seenValue) {
                        if (rowStart == 0) {
                            rowStart = excelRow;
                        }
                        rowEnd = excelRow;
                        rows.add(cells);
                    }
                    if (rows.size() > MAX_TABLE_ROWS) {
                        truncated = true;
                        break;
                    }
                }
                if (rows.isEmpty()) {
                    continue;
                }
                String text = "## " + sheet.getSheetName() + "\n\n" + renderMarkdownTable(rows);
                markdown.append(text).append('\n');
                segments.add(new IndexSegment(text, null, sheet.getSheetName(), rowStart, rowEnd));
                SheetTable table = structuredTable(sheet.getSheetName(), scans, truncated);
                if (table != null) {
                    tables.add(table);
                }
            }
            return new SpreadsheetExtraction(
                    markdown.toString(), List.copyOf(segments), List.copyOf(tables), unevaluated[0]);
        } catch (IOException | RuntimeException ex) {
            throw new ExtractionException("failed to convert spreadsheet", ex);
        }
    }

    /**
     * 有日期型数据行时，表头是其上方最近的非空文本行，结构化行只取表头后连续的日期行块。
     * 没有日期型数据行时，仍用第一个非空行作表头、其后各非空行作数据。
     * 不改 Markdown 与位置段：那些仍包含标题行和合计行。
     */
    private static SheetTable structuredTable(String sheet, List<SheetScanRow> scans, boolean truncated) {
        int firstDate = -1;
        for (int index = 0; index < scans.size(); index++) {
            if (scans.get(index).dateFirst()) {
                firstDate = index;
                break;
            }
        }
        if (firstDate < 0) {
            return legacyTable(sheet, scans, truncated);
        }
        int header = -1;
        for (int index = firstDate - 1; index >= 0; index--) {
            SheetScanRow row = scans.get(index);
            if (!row.blank() && row.textCells() >= 2 && !row.dateFirst()) {
                header = index;
                break;
            }
        }
        if (header < 0) {
            return legacyTable(sheet, scans, truncated);
        }
        SheetScanRow headerRow = scans.get(header);
        List<SheetRow> data = new ArrayList<>();
        int previous = headerRow.excelRow();
        for (int index = header + 1; index < scans.size(); index++) {
            SheetScanRow row = scans.get(index);
            if (row.excelRow() > previous + 1 || row.blank() || !row.dateFirst()) {
                break;
            }
            data.add(new SheetRow(row.excelRow(), List.copyOf(row.cells())));
            previous = row.excelRow();
        }
        return new SheetTable(
                sheet, headerRow.excelRow(), headerTexts(headerRow.cells()), List.copyOf(data), truncated);
    }

    /** 第一个非空行是表头，其后非空行都是数据。空行不进结构化行，但原始行号保留。 */
    private static SheetTable legacyTable(String sheet, List<SheetScanRow> scans, boolean truncated) {
        List<SheetScanRow> filled = new ArrayList<>();
        for (SheetScanRow row : scans) {
            if (!row.blank()) {
                filled.add(row);
            }
        }
        if (filled.isEmpty()) {
            return null;
        }
        List<SheetRow> data = new ArrayList<>();
        for (int index = 1; index < filled.size(); index++) {
            SheetScanRow row = filled.get(index);
            data.add(new SheetRow(row.excelRow(), List.copyOf(row.cells())));
        }
        return new SheetTable(
                sheet, filled.getFirst().excelRow(), headerTexts(filled.getFirst().cells()), List.copyOf(data), truncated);
    }

    private static List<String> headerTexts(List<String> cells) {
        List<String> headers = new ArrayList<>();
        for (String header : cells) {
            headers.add(header == null ? "" : header.trim());
        }
        return List.copyOf(headers);
    }

    /** 首个非空格是 POI 日期格式的数值，或整格文本是日期样式。 */
    private static boolean isDateValue(Cell cell, String text) {
        if (DATE_TEXT.matcher(text.trim()).matches()) {
            return true;
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        if (type != CellType.NUMERIC) {
            return false;
        }
        try {
            return DateUtil.isCellDateFormatted(cell);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * 公式格且没有可用缓存（OOXML 无缓存值节点，或缓存类型不是数字/文本/布尔）时计一次并返回空串。
     * 不调用 FormulaEvaluator，不计算公式。
     */
    private static String cellText(Cell cell, DataFormatter formatter, int[] unevaluated) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() != CellType.FORMULA) {
            return formatter.formatCellValue(cell);
        }
        if (!formulaCacheUsable(cell)) {
            unevaluated[0]++;
            return "";
        }
        CellType cached = cell.getCachedFormulaResultType();
        if (cached == CellType.STRING) {
            return cell.getStringCellValue();
        }
        if (cached == CellType.BOOLEAN) {
            return cell.getBooleanCellValue() ? "TRUE" : "FALSE";
        }
        String format = cell.getCellStyle().getDataFormatString();
        return formatter.formatRawCellContents(
                cell.getNumericCellValue(),
                cell.getCellStyle().getDataFormat(),
                format == null || format.isBlank() ? "General" : format);
    }

    private static boolean formulaCacheUsable(Cell cell) {
        if (cell instanceof XSSFCell xssf) {
            var ct = xssf.getCTCell();
            if (!ct.isSetV() && !ct.isSetIs()) {
                return false;
            }
        }
        CellType cached = cell.getCachedFormulaResultType();
        return cached == CellType.NUMERIC || cached == CellType.STRING || cached == CellType.BOOLEAN;
    }

    // ---------- 旧版 Office 二进制（doc/ppt）：抽取器给纯文本，无结构可转 ----------

    private static String docLegacyToText(byte[] bytes) {
        try (WordExtractor extractor = new WordExtractor(new ByteArrayInputStream(bytes))) {
            String text = extractor.getText();
            return text == null ? "" : text;
        } catch (IOException | RuntimeException ex) {
            throw new ExtractionException("failed to convert DOC", ex);
        }
    }

    private static String pptLegacyToText(byte[] bytes) {
        try (HSLFSlideShow slideshow = new HSLFSlideShow(new ByteArrayInputStream(bytes))) {
            StringBuilder out = new StringBuilder();
            for (var slide : slideshow.getSlides()) {
                for (HSLFShape shape : slide.getShapes()) {
                    if (shape instanceof HSLFTextShape textShape) {
                        String value = textShape.getText();
                        if (value != null && !value.isBlank()) {
                            out.append(value.trim()).append("\n\n");
                        }
                    }
                }
            }
            return out.toString();
        } catch (IOException | RuntimeException ex) {
            throw new ExtractionException("failed to convert PPT", ex);
        }
    }

    // ---------- PPTX → Md ----------

    private static String pptxToMarkdown(byte[] bytes) {
        try (XMLSlideShow slideshow = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            StringBuilder out = new StringBuilder();
            int slideNumber = 0;
            for (var slide : slideshow.getSlides()) {
                slideNumber++;
                out.append("## Slide ").append(slideNumber).append("\n\n");
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String value = textShape.getText();
                        if (value != null && !value.isBlank()) {
                            out.append(value.trim()).append("\n\n");
                        }
                    } else if (shape instanceof XSLFTable table) {
                        List<List<String>> rows = new ArrayList<>();
                        for (var row : table) {
                            List<String> cells = new ArrayList<>();
                            for (var cell : row) {
                                cells.add(cell.getText() == null ? "" : cell.getText());
                            }
                            rows.add(cells);
                        }
                        out.append(renderMarkdownTable(rows)).append('\n');
                    }
                }
            }
            return out.toString();
        } catch (IOException | RuntimeException ex) {
            throw new ExtractionException("failed to convert PPTX", ex);
        }
    }

    // ---------- HTML → Md ----------

    private static String htmlToMarkdown(byte[] bytes) {
        try {
            org.jsoup.nodes.Document document = Jsoup.parse(new ByteArrayInputStream(bytes), null, "");
            document.select("script, style, noscript").remove();
            String bodyHtml = document.body() == null ? "" : document.body().html();
            // 标题统一 ATX 风格（#），不用 Setext 下划线式——入库标准形要与按标题分块的切分规则一致
            com.vladsch.flexmark.util.data.MutableDataSet options =
                    new com.vladsch.flexmark.util.data.MutableDataSet();
            options.set(FlexmarkHtmlConverter.SETEXT_HEADINGS, false);
            String markdown = FlexmarkHtmlConverter.builder(options).build().convert(bodyHtml);
            if (markdown != null && !markdown.isBlank()) {
                return markdown;
            }
            return document.body() == null ? "" : document.body().wholeText();
        } catch (IOException | RuntimeException ex) {
            throw new ExtractionException("failed to convert HTML", ex);
        }
    }

    // ---------- EPUB → Md ----------

    private static String epubToMarkdown(byte[] bytes) {
        try {
            Map<String, byte[]> entries = unzip(bytes);
            List<String> chapterPaths = epubChapterOrder(entries);
            StringBuilder out = new StringBuilder();
            for (String path : chapterPaths) {
                byte[] chapter = entries.get(path);
                if (chapter == null) {
                    continue;
                }
                String markdown = htmlToMarkdown(chapter);
                if (!markdown.isBlank()) {
                    out.append(markdown.strip()).append("\n\n");
                }
            }
            if (out.isEmpty()) {
                // 结构解析失败时兜底：按文件名顺序转换全部 XHTML 条目
                entries.entrySet().stream()
                        .filter(entry -> entry.getKey().endsWith(".xhtml") || entry.getKey().endsWith(".html"))
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> {
                            String markdown = htmlToMarkdown(entry.getValue());
                            if (!markdown.isBlank()) {
                                out.append(markdown.strip()).append("\n\n");
                            }
                        });
            }
            return out.toString();
        } catch (ExtractionException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ExtractionException("failed to convert EPUB", ex);
        }
    }

    private static List<String> epubChapterOrder(Map<String, byte[]> entries) {
        try {
            String containerPath = entries.keySet().stream()
                    .filter(key -> key.endsWith("META-INF/container.xml"))
                    .findFirst()
                    .orElse(null);
            if (containerPath == null) {
                return List.of();
            }
            Document container = parseXml(entries.get(containerPath));
            NodeList rootfiles = container.getElementsByTagName("rootfile");
            if (rootfiles.getLength() == 0) {
                return List.of();
            }
            String opfPath = ((Element) rootfiles.item(0)).getAttribute("full-path");
            byte[] opfBytes = entries.get(opfPath);
            if (opfBytes == null) {
                return List.of();
            }
            String baseDir = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";
            Document opf = parseXml(opfBytes);
            Map<String, String> manifest = new HashMap<>();
            NodeList items = opf.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                manifest.put(item.getAttribute("id"), item.getAttribute("href"));
            }
            List<String> chapters = new ArrayList<>();
            NodeList itemrefs = opf.getElementsByTagName("itemref");
            for (int i = 0; i < itemrefs.getLength(); i++) {
                String href = manifest.get(((Element) itemrefs.item(i)).getAttribute("idref"));
                if (href != null && (href.endsWith(".xhtml") || href.endsWith(".html"))) {
                    chapters.add(baseDir + href);
                }
            }
            return chapters;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    // ---------- ODF（odt/ods/odp）→ Md ----------

    private static String odfToMarkdown(byte[] bytes) {
        try {
            Map<String, byte[]> entries = unzip(bytes);
            byte[] contentXml = entries.get("content.xml");
            if (contentXml == null) {
                throw new ExtractionException("ODF content.xml missing", null);
            }
            Document document = parseXml(contentXml);
            StringBuilder out = new StringBuilder();
            walkOdf(document.getDocumentElement(), out);
            return out.toString();
        } catch (ExtractionException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ExtractionException("failed to convert ODF", ex);
        }
    }

    private static void walkOdf(Node node, StringBuilder out) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            String local = node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
            switch (local) {
                case "h" -> {
                    int level = 1;
                    Node levelAttr = node.getAttributes().getNamedItem("text:outline-level");
                    if (levelAttr != null) {
                        level = Math.max(1, Math.min(6, parseIntOrDefault(levelAttr.getNodeValue(), 1)));
                    }
                    String text = node.getTextContent().trim();
                    if (!text.isBlank()) {
                        out.append("#".repeat(level)).append(' ').append(text).append("\n\n");
                    }
                    return;
                }
                case "p" -> {
                    // 表格单元格内的 p 在 table 分支里取文本，这里跳过以免重复
                    if (hasAncestor(node, "table-cell")) {
                        return;
                    }
                    String text = node.getTextContent().trim();
                    if (!text.isBlank()) {
                        if (hasAncestor(node, "list-item")) {
                            out.append("- ").append(text).append('\n');
                        } else if (hasAncestor(node, "page")) {
                            out.append(text).append('\n');
                        } else {
                            out.append(text).append("\n\n");
                        }
                    }
                    return;
                }
                case "table" -> {
                    String name = attribute(node, "table:name");
                    if (name != null && !name.isBlank() && !"local-table".equals(name)) {
                        out.append("## ").append(name).append("\n\n");
                    }
                    List<List<String>> rows = new ArrayList<>();
                    collectOdfRows(node, rows);
                    if (!rows.isEmpty()) {
                        out.append(renderMarkdownTable(rows)).append('\n');
                    }
                    return;
                }
                case "page" -> {
                    String name = attribute(node, "draw:name");
                    out.append("## ").append(name == null || name.isBlank() ? "Slide" : name).append("\n\n");
                    break;
                }
                default -> {
                    // 继续下钻
                }
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            walkOdf(children.item(i), out);
        }
    }

    private static void collectOdfRows(Node tableNode, List<List<String>> rows) {
        NodeList children = tableNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String local = child.getLocalName() == null ? child.getNodeName() : child.getLocalName();
            if ("table-row".equals(local)) {
                List<String> cells = new ArrayList<>();
                NodeList cellNodes = child.getChildNodes();
                for (int j = 0; j < cellNodes.getLength(); j++) {
                    Node cell = cellNodes.item(j);
                    if (cell.getNodeType() == Node.ELEMENT_NODE
                            && "table-cell".equals(
                                    cell.getLocalName() == null ? cell.getNodeName() : cell.getLocalName())) {
                        cells.add(cell.getTextContent().trim().replaceAll("\\s+", " "));
                    }
                }
                if (cells.stream().anyMatch(value -> !value.isBlank())) {
                    rows.add(cells);
                }
                if (rows.size() > MAX_TABLE_ROWS) {
                    return;
                }
            } else {
                collectOdfRows(child, rows);
            }
        }
    }

    private static boolean hasAncestor(Node node, String localNameSuffix) {
        Node parent = node.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE) {
                String local = parent.getLocalName() == null ? parent.getNodeName() : parent.getLocalName();
                if (local.endsWith(localNameSuffix)) {
                    return true;
                }
            }
            parent = parent.getParentNode();
        }
        return false;
    }

    private static String attribute(Node node, String qualifiedName) {
        Node attr = node.getAttributes() == null ? null : node.getAttributes().getNamedItem(qualifiedName);
        return attr == null ? null : attr.getNodeValue();
    }

    private static int parseIntOrDefault(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    // ---------- RTF → 纯文本 ----------

    private static String rtfToText(byte[] bytes) {
        try {
            var kit = new javax.swing.text.rtf.RTFEditorKit();
            var document = kit.createDefaultDocument();
            kit.read(new ByteArrayInputStream(bytes), document, 0);
            return document.getText(0, document.getLength());
        } catch (Exception | LinkageError ex) {
            throw new ExtractionException("failed to convert RTF", ex instanceof Exception e ? e : null);
        }
    }

    // ---------- zip / XML 工具 ----------

    private static Map<String, byte[]> unzip(byte[] bytes) {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
            }
            return entries;
        } catch (IOException ex) {
            throw new ExtractionException("failed to unzip", ex);
        }
    }

    private static Document parseXml(byte[] bytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        } catch (Exception ex) {
            throw new ExtractionException("failed to parse XML", ex);
        }
    }
}
