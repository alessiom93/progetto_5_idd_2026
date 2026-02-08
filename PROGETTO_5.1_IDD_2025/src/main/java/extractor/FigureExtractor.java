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

public class FigureExtractor {

    // ------------------------------------------------------------
    // STRUCT PER UNA FIGURA
    // ------------------------------------------------------------
    public static class ExtractedFigure {
        public String paperId;
        public String figureId;
        public String url;
        public String caption;
        public List<String> mentions;
        public List<String> contextParagraphs;   // facoltativo ma utile come per le tabelle
    }

    // ------------------------------------------------------------
    // MAIN STANDALONE
    // ------------------------------------------------------------
    public static void main(String[] args) throws Exception {

        Path inputDir = Paths.get("corpus-html");
        Path outputDir = Paths.get("figures");

        if (!Files.exists(inputDir)) {
            System.err.println("ERRORE: directory 'corpus-html' non trovata!");
            return;
        }

        Files.createDirectories(outputDir);

        FigureExtractor extractor = new FigureExtractor();

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        int totalFigures = 0;

        System.out.println("=== ESTRAZIONE FIGURE INIZIATA ===");

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputDir, "*.html")) {
            for (Path htmlPath : stream) {

                File htmlFile = htmlPath.toFile();
                String paperId = htmlFile.getName().replace(".html", "");

                System.out.println("\n>> Processing " + paperId);

                List<ExtractedFigure> figures = extractor.extractFiguresFromHtml(htmlFile, paperId);

                if (figures.isEmpty()) {
                    System.out.println("   Nessuna figura trovata.");
                    continue;
                }

                // salva UN FILE JSON PER PAPER
                Path outFile = outputDir.resolve(paperId + "_figures.json");
                mapper.writeValue(outFile.toFile(), figures);

                System.out.println("   Figure trovate: " + figures.size());
                System.out.println("   Salvato file: " + outFile);

                totalFigures += figures.size();
            }
        }

        System.out.println("\n=== COMPLETATO ===");
        System.out.println("Totale figure estratte: " + totalFigures);
    }

    // ------------------------------------------------------------
    // LOGICA DI ESTRAZIONE FIGURE
    // ------------------------------------------------------------
    public List<ExtractedFigure> extractFiguresFromHtml(File htmlFile, String paperId) throws IOException {

        List<ExtractedFigure> results = new ArrayList<>();

        Document doc = Jsoup.parse(htmlFile, "UTF-8");

        // ===== 1. PARAGRAFI DEL PAPER =====
        List<String> paragraphs = new ArrayList<>();
        for (Element p : doc.select("p")) {
            String text = cleanText(p.text());
            if (text.length() > 30)
                paragraphs.add(text);
        }

        // ===== 2. SELEZIONA FIGURE REALI =====
        // arXiv HTML classico usa figure.ltx_figure
        // PMC usa figure senza classe specifica
        Elements figs = doc.select("figure.ltx_figure");
        
        // Se non trova figure arXiv, cerca figure PMC
        if (figs.isEmpty()) {
            // Per PMC, escludiamo figure che sono tables (sezione .tw)
            figs = doc.select("figure:not(.tw)");
        }

        for (Element fig : figs) {

            ExtractedFigure f = new ExtractedFigure();
            f.paperId = paperId;

            // ID figura
            String id = fig.id();
            if (id == null || id.isEmpty())
                id = UUID.randomUUID().toString();
            f.figureId = id;

            // URL IMMAGINE
            // arXiv spesso usa <img src="...">
            // da appendere al dominio https://ar5iv.labs.arxiv.org
            Element imgEl = fig.selectFirst("img");
            if (imgEl != null) {
                String src = imgEl.attr("src");
                // Se è arXiv (relativo), aggiunge il dominio; altrimenti usa l'URL assoluto
                f.url = src.startsWith("http") ? src : "ar5iv.labs.arxiv.org" + src;
            } else {
                f.url = "";
            }

            // CAPTION
            // arXiv: figcaption.ltx_caption
            // PMC: figcaption oppure p dentro figure
            Element captionEl = fig.selectFirst("figcaption.ltx_caption");
            if (captionEl == null) {
                captionEl = fig.selectFirst("figcaption");
            }
            if (captionEl == null) {
                // fallback: cerca <p> dentro figure
                captionEl = fig.selectFirst("p");
            }
            f.caption = captionEl != null ? cleanText(captionEl.text()) : "";

            // Salta se la figura non ha né caption né URL
            if ((f.caption == null || f.caption.isEmpty()) && 
                (f.url == null || f.url.isEmpty())) {
                continue;
            }

            // KEYWORDS PER CONTESTO
            Set<String> figureKeywords = extractKeywords(f.caption);

            // CITAZIONI NEI PARAGRAFI
            f.mentions = findMentionParagraphs(paragraphs, id, f.caption);

            // PARAGRAFI DI CONTESTO
            f.contextParagraphs = findContextParagraphs(paragraphs, figureKeywords);

            results.add(f);
        }

        return results;
    }

    // ------------------------------------------------------------
    // METODI DI SUPPORTO (copiati dal tuo TableExtractor)
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

private List<String> findMentionParagraphs(List<String> paragraphs, String figureId, String caption) {
    List<String> out = new ArrayList<>();

    // 1. Prova a estrarre un numero dalla caption: "Figure 3", "Fig. 5", "FIG 12"
    Pattern numPattern = Pattern.compile("(?i)\\b(?:figure|fig)\\.?\\s*([0-9]+)\\b");
    java.util.regex.Matcher m = numPattern.matcher(caption);

    String numeric = null;
    String roman = null;

    if (m.find()) {
        numeric = m.group(1);   // es. "3"
        roman = toRoman(Integer.parseInt(numeric)); // es. "III"
    }

    // 2. Se non è stato possibile estrarre un numero, non cerchiamo citazioni
    if (numeric == null) {
        return out;
    }

    // 3. Costruisci regex di citazione
    String patternStr =
            "(?i)\\b(?:figure|fig)\\.?\\s*(?:" + numeric + "|" + roman + ")\\b";
    Pattern pattern = Pattern.compile(patternStr);

    // 4. Cerca nei paragrafi
    for (String p : paragraphs) {
        if (pattern.matcher(p).find()) {
            out.add(p);
        }
    }

    return out;
}


    private String extractNumericFigureCode(String raw) {
        if (!raw.contains("F"))
            return raw;
        String afterF = raw.substring(raw.indexOf("F") + 1);
        return afterF.replaceAll("[^0-9IVXLC]", "");
    }

    private String toRoman(int number) {
        String[] m = {"","M","MM","MMM"};
        String[] c = {"","C","CC","CCC","CD","D","DC","DCC","DCCC","CM"};
        String[] x = {"","X","XX","XXX","XL","L","LX","LXX","LXXX","XC"};
        String[] i = {"","I","II","III","IV","V","VI","VII","VIII","IX"};
        return m[number/1000] + c[(number%1000)/100] + x[(number%100)/10] + i[number%10];
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
