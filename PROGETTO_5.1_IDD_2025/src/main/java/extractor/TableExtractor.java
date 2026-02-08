package extractor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

public class TableExtractor {

    // ------------------------------------------------------------
    // STRUCT PER UNA TABELLA
    // ------------------------------------------------------------
    public static class ExtractedTable {
        public String paperId;
        public String tableId;
        public String caption;
        public String body;
        public List<String> mentions;
        public List<String> contextParagraphs;
    }

    // ------------------------------------------------------------
    // MAIN STANDALONE
    // ------------------------------------------------------------
    public static void main(String[] args) throws Exception {

        Path inputDir = Paths.get("corpus-html");
        Path outputDir = Paths.get("tables");

        if (!Files.exists(inputDir)) {
            System.err.println("ERRORE: directory 'corpus-html' non trovata!");
            return;
        }

        Files.createDirectories(outputDir);

        TableExtractor extractor = new TableExtractor();

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        int totalTables = 0;

        System.out.println("=== ESTRAZIONE TABELLE INIZIATA ===");

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputDir, "*.html")) {
            for (Path htmlPath : stream) {

                File htmlFile = htmlPath.toFile();
                String paperId = htmlFile.getName().replace(".html", "");

                System.out.println("\n>> Processing " + paperId);

                List<ExtractedTable> tables = extractor.extractTablesFromHtml(htmlFile, paperId);

                if (tables.isEmpty()) {
                    System.out.println("Nessuna tabella trovata.");
                    continue;
                }

                // salva UN FILE JSON PER PAPER
                Path outFile = outputDir.resolve(paperId + "_tables.json");
                mapper.writeValue(outFile.toFile(), tables);

                System.out.println("Tabelle trovate: " + tables.size());
                System.out.println("Salvato file: " + outFile);

                totalTables += tables.size();
            }
        }

        System.out.println("\n=== COMPLETATO ===");
        System.out.println("Totale tabelle estratte: " + totalTables);
    }

    // ------------------------------------------------------------
    // LOGICA DI ESTRAZIONE (tua versione migliorata)
    // ------------------------------------------------------------
    public List<ExtractedTable> extractTablesFromHtml(File htmlFile, String paperId) throws IOException {

        List<ExtractedTable> results = new ArrayList<>();

        Document doc = Jsoup.parse(htmlFile, "UTF-8");

        // ===== 1. PARAGRAFI DEL PAPER =====
        List<String> paragraphs = new ArrayList<>();
        for (Element p : doc.select("p")) {
            String text = cleanText(p.text());
            if (text.length() > 30)
                paragraphs.add(text);
        }

        // ===== 2. SELEZIONA SOLO LE VERE TABELLE =====
        // Supporta sia formato arXiv (figure.ltx_table) che PMC (section.tw)
        Elements tables = doc.select("figure.ltx_table");
        
        // Se non trova tabelle arXiv, cerca tabelle PMC
        if (tables.isEmpty()) {
            tables = doc.select("section.tw");
        }

        for (Element fig : tables) {

            ExtractedTable t = new ExtractedTable();
            t.paperId = paperId;

            // ID della tabella
            String id = fig.id();
            if (id == null || id.isEmpty())
                id = UUID.randomUUID().toString();
            t.tableId = id;

            // CAPTION - per arXiv è figcaption.ltx_caption, per PMC è h4.obj_head o simile
            Element captionEl = fig.selectFirst("figcaption.ltx_caption");
            if (captionEl == null) {
                // fallback PMC: h4.obj_head, h3.obj_head
                captionEl = fig.selectFirst("h4.obj_head, h3.obj_head");
            }
            t.caption = captionEl != null ? cleanText(captionEl.text()) : "";

            // BODY - per arXiv è dentro figure, per PMC è dentro div.tbl-box o direttamente
            Element tableEl = fig.selectFirst("table");
            if (tableEl == null) {
                // fallback: cerca tabella dentro tbl-box
                Element tblBox = fig.selectFirst("div.tbl-box");
                if (tblBox != null) {
                    tableEl = tblBox.selectFirst("table");
                }
            }
            t.body = tableEl != null ? cleanText(tableEl.text()) : "";

            // Salta se la tabella non ha né caption né body significativo
            if ((t.caption == null || t.caption.isEmpty()) && 
                (t.body == null || t.body.isEmpty())) {
                continue;
            }

            // KEYWORDS
            Set<String> tableKeywords = extractKeywords(t.caption + " " + t.body);

            // CITAZIONI
            t.mentions = findMentionParagraphs(paragraphs, id);

            // PARAGRAFI DI CONTESTO
            t.contextParagraphs = findContextParagraphs(paragraphs, tableKeywords);

            results.add(t);
        }

        return results;
    }

    // ------------------------------------------------------------
    // METODI DI SUPPORTO
    // ------------------------------------------------------------
    private String cleanText(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", " ")
                .replaceAll("[^\\x20-\\x7E]", "")
                .trim();
    }

    private Set<String> extractKeywords(String text) {
        String[] tokens = text.toLowerCase().split("[^a-z0-9]+");
        Set<String> out = new HashSet<>();

        Set<String> stop = Set.of(
                "the","a","an","and","or","to","of","in","for","on","by",
                "is","are","we","our","this","that","with","as","these",
                "those","it","be"
        );

        for (String tok : tokens) {
            if (tok.length() > 2 && !stop.contains(tok))
                out.add(tok);
        }
        return out;
    }
private List<String> findMentionParagraphs(List<String> paragraphs, String tableId) {
    List<String> out = new ArrayList<>();

    String code = extractNumericTableCode(tableId); // es: "2" da "S4.T2"
    if (code == null || code.isEmpty()) {
        return out;
    }

    List<String> tokens = new ArrayList<>();
    if (code.matches("\\d+")) {
        tokens.add(code);
        tokens.add(toRoman(Integer.parseInt(code)));
    } else if (code.matches("[IVXLC]+")) {
        tokens.add(code);
    } else {
        return out;
    }

    String patternStr = "(?i)\\b(?:table|tab)\\.?\\s*(?:" + String.join("|", tokens) + ")\\b";
    Pattern pattern = Pattern.compile(patternStr);

    for (String p : paragraphs) {
        if (pattern.matcher(p).find()) {
            out.add(p);
        }
    }
    return out;
}

// Metodo di supporto: converte un numero intero in romano (1-3999)
private String toRoman(int number) {
    String[] m = {"","M","MM","MMM"};
    String[] c = {"","C","CC","CCC","CD","D","DC","DCC","DCCC","CM"};
    String[] x = {"","X","XX","XXX","XL","L","LX","LXX","LXXX","XC"};
    String[] i = {"","I","II","III","IV","V","VI","VII","VIII","IX"};
    return m[number/1000] + c[(number%1000)/100] + x[(number%100)/10] + i[number%10];
}


    private String extractNumericTableCode(String raw) {
        if (!raw.contains("T"))
            return raw;
        String afterT = raw.substring(raw.indexOf("T") + 1);
        return afterT.replaceAll("[^0-9IVXLC]", "");
    }

    private List<String> findContextParagraphs(List<String> paragraphs, Set<String> keywords) {
        List<String> out = new ArrayList<>();
        for (String p : paragraphs) {
            String lower = p.toLowerCase();
            for (String k : keywords) {
                if (lower.contains(k)) {
                    out.add(p);
                    break;
                }
            }
        }
        return out;
    }
}
