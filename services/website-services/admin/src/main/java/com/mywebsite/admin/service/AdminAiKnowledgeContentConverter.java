package com.mywebsite.admin.service;

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

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

import com.mywebsite.admin.service.AdminAiKnowledgeTextExtractor.ExtractionException;

/**
 * 知识库上传内容归一化：所有格式入库时统一转成 Markdown 标准形（PDF 文字层无结构、存纯文本段落）。
 * 文本类（txt/md/代码/数据）按 UTF-8→MS932→GB18030 回退解码，代码/数据类包代码围栏，CSV/TSV 转 Md 表格；
 * 二进制类（docx/xlsx/pptx/html/epub/odt/ods/odp/rtf）走各自转换器。转换失败抛 {@link ExtractionException}。
 */
public final class AdminAiKnowledgeContentConverter {

    private static final int MAX_TABLE_ROWS = 1000;
    private static final int MAX_TABLE_COLS = 50;
    private static final Pattern HEADING_STYLE =
            Pattern.compile("(?i)(heading|标题|見出し)\\s*([1-6])");

    private AdminAiKnowledgeContentConverter() {}

    public static String toStoredContent(String extension, byte[] bytes) {
        return switch (extension) {
            case "txt", "md", "markdown" -> decodeText(bytes);
            case "csv" -> toMarkdownTable(decodeText(bytes), ',');
            case "tsv" -> toMarkdownTable(decodeText(bytes), '\t');
            case "json", "xml", "yaml", "yml", "toml", "ini", "log",
                    "py", "java", "js", "ts", "tsx", "jsx", "css", "scss", "sql", "sh",
                    "go", "rs", "c", "h", "cpp", "hpp", "cs", "rb", "php", "swift", "kt",
                    "scala", "pl", "r", "lua", "vue" ->
                    fence(extension, decodeText(bytes));
            case "pdf" -> AdminAiKnowledgeTextExtractor.extractText("pdf", bytes);
            case "docx" -> docxToMarkdown(bytes);
            case "xlsx" -> xlsxToMarkdown(bytes);
            case "pptx" -> pptxToMarkdown(bytes);
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
            case "log", "ini" -> "";
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

    private static String xlsxToMarkdown(byte[] bytes) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            DataFormatter formatter = new DataFormatter();
            StringBuilder out = new StringBuilder();
            for (var sheet : workbook) {
                List<List<String>> rows = new ArrayList<>();
                for (var row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (var cell : row) {
                        cells.add(formatter.formatCellValue(cell));
                    }
                    if (cells.stream().anyMatch(value -> !value.isBlank())) {
                        rows.add(cells);
                    }
                    if (rows.size() > MAX_TABLE_ROWS) {
                        break;
                    }
                }
                if (!rows.isEmpty()) {
                    out.append("## ").append(sheet.getSheetName()).append("\n\n");
                    out.append(renderMarkdownTable(rows)).append('\n');
                }
            }
            return out.toString();
        } catch (IOException | RuntimeException ex) {
            throw new ExtractionException("failed to convert XLSX", ex);
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

    // ---------- HTML → Md ----------

    private static String htmlToMarkdown(byte[] bytes) {
        try {
            org.jsoup.nodes.Document document = Jsoup.parse(new ByteArrayInputStream(bytes), null, "");
            document.select("script, style, noscript").remove();
            String bodyHtml = document.body() == null ? "" : document.body().html();
            String markdown = FlexmarkHtmlConverter.builder().build().convert(bodyHtml);
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
