package extractor;

import org.jsoup.Jsoup; // parsing HTML e DOM
import org.jsoup.nodes.Document; // rappresenta il documento HTML parsato
import org.jsoup.nodes.Element; // rappresenta un elemento HTML
import org.jsoup.select.Elements; // lista di elementi HTML
import com.fasterxml.jackson.databind.ObjectMapper; // per serializzare/deserializzare JSON
import com.fasterxml.jackson.databind.SerializationFeature; // configurazione della serializzazione JSON

import java.io.File; // per gestire i file
import java.io.IOException; // per gestire le eccezioni di I/O
import java.nio.file.*; // per operazioni su file e directory
import java.util.*; // collezioni Java (ArrayList, Set, Map, ecc.)
import java.util.regex.Pattern; // per regex pattern matching

public class FigureExtractor {

    // ===== STRUCT PER MEMORIZZARE I DATI DI UNA FIGURA ESTRATTA =====
    public static class ExtractedFigure {
        public String paperId;
        public String figureId;
        public String url;
        public String caption;
        public List<String> mentions;
        public List<String> contextParagraphs;  
    }

    // ===== MAIN STANDALONE - PUNTO DI INGRESSO PER L'ESTRAZIONE =====
    public static void main(String[] args) throws Exception {
        // percorsi di input (HTML grezzi) e output (JSON con figure estratte)
        Path inputDir = Paths.get("corpus-html");
        Path outputDir = Paths.get("figures");

        // verifica existenza directory input
        if (!Files.exists(inputDir)) {
            System.err.println("ERRORE: directory 'corpus-html' non trovata!");
            return;
        }

        // crea directory output se non esiste
        Files.createDirectories(outputDir);

        // istanza dell'estrattore
        FigureExtractor extractor = new FigureExtractor();

        // configurazione di Jackson per serializzare in JSON formattato
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        // contatore per statistiche finali
        int totalFigures = 0;

        System.out.println("=== ESTRAZIONE FIGURE INIZIATA ===");

        // legge tutti i file .html dalla directory corpus-html
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputDir, "*.html")) {
            for (Path htmlPath : stream) {
                // estrae ID del paper dal nome file (es: "0711.2087v1.html" -> "0711.2087v1")
                File htmlFile = htmlPath.toFile();
                String paperId = htmlFile.getName().replace(".html", "");

                System.out.println("\n>> Processing " + paperId);

                // chiama il metodo di estrazione per questo paper
                List<ExtractedFigure> figures = extractor.extractFiguresFromHtml(htmlFile, paperId);

                // se nessuna figura trovata, passa al paper successivo
                if (figures.isEmpty()) {
                    System.out.println("   Nessuna figura trovata.");
                    continue;
                }

                // salva le figure estratte in un file JSON (UN FILE PER PAPER)
                Path outFile = outputDir.resolve(paperId + "_figures.json");
                mapper.writeValue(outFile.toFile(), figures);

                System.out.println("   Figure trovate: " + figures.size());
                System.out.println("   Salvato file: " + outFile);

                // accumula il conteggio totale
                totalFigures += figures.size();
            }
        }

        // stampa statistiche finali
        System.out.println("\n=== COMPLETATO ===");
        System.out.println("Totale figure estratte: " + totalFigures);
    }

    // ===== METODO PRINCIPALE DI ESTRAZIONE FIGURE DALL'HTML =====
    public List<ExtractedFigure> extractFiguresFromHtml(File htmlFile, String paperId) throws IOException {
        // lista dei risultati (figure estratte)
        List<ExtractedFigure> results = new ArrayList<>();

        // parsa il file HTML
        Document doc = Jsoup.parse(htmlFile, "UTF-8");

        // ===== 1. RACCOGLIE TUTTI I PARAGRAFI DEL PAPER (per trovare citazioni) =====
        List<String> paragraphs = new ArrayList<>();
        for (Element p : doc.select("p")) {
            String text = cleanText(p.text());
            // mantiene solo i paragrafi sostanziali (almeno 30 caratteri)
            if (text.length() > 30)
                paragraphs.add(text);
        }

        // ===== 2. SELEZIONA SOLO LE VERE FIGURE (usa il selettore arXiv) =====
        // arXiv HTML classico usa figure.ltx_figure
        Elements figs = doc.select("figure.ltx_figure");

        for (Element fig : figs) {
            // crea una nuova entry per la figura
            ExtractedFigure f = new ExtractedFigure();
            f.paperId = paperId;

            // estrae ID della figura (usa UUID se non disponibile)
            String id = fig.id();
            if (id == null || id.isEmpty())
                id = UUID.randomUUID().toString();
            f.figureId = id;

            // estrae l'URL dell'immagine
            // arXiv spesso usa <img src="...">
            // da appendere al dominio https://ar5iv.labs.arxiv.org
            Element imgEl = fig.selectFirst("img");
            f.url = imgEl != null ? "ar5iv.labs.arxiv.org" + imgEl.attr("src") : "";

            // estrae la caption (titolo/descrizione della figura)
            Element captionEl = fig.selectFirst("figcaption.ltx_caption");
            f.caption = captionEl != null ? cleanText(captionEl.text()) : "";

            // estrae parole chiave dalla caption per trovare il contesto
            Set<String> figureKeywords = extractKeywords(f.caption);

            // trova paragrafi che citano questa figura (es: "Figure 2")
            f.mentions = findMentionParagraphs(paragraphs, id, f.caption);

            // trova paragrafi che condividono keywords con la figura
            f.contextParagraphs = findContextParagraphs(paragraphs, figureKeywords);

            results.add(f);
        }

        return results;
    }

    // ===== METODO DI SUPPORTO: PULIZIA DEL TESTO =====
    private String cleanText(String s) {
        if (s == null) return "";
        // rimuove spazi multipli e caratteri non ASCII
        return s.replaceAll("\\s+", " ")
                .replaceAll("[^\\x20-\\x7E]", "")
                .trim();
    }

    // ===== METODO DI SUPPORTO: ESTRAZIONE KEYWORDS =====
    private Set<String> extractKeywords(String text) {
        // tokenizza il testo in parole
        String[] tokens = text.toLowerCase().split("[^a-z0-9]+");
        Set<String> out = new HashSet<>();

        // lista di parole comuni da ignorare (stop words)
        Set<String> stop = Set.of(
                "the","a","an","and","or","to","of","in","for","on","by",
                "is","are","we","our","this","that","with","as","these",
                "those","it","be"
        );

        // mantiene solo i token significativi (> 2 caratteri, non stop words)
        for (String tok : tokens) {
            if (tok.length() > 2 && !stop.contains(tok))
                out.add(tok);
        }
        return out;
    }

    // ===== METODO DI SUPPORTO: TROVA PARAGRAFI CHE CITANO LA FIGURA =====
    private List<String> findMentionParagraphs(List<String> paragraphs, String figureId, String caption) {
        List<String> out = new ArrayList<>();

        // estrae il numero dalla caption: "Figure 3", "Fig. 5", "FIG 12"
        Pattern numPattern = Pattern.compile("(?i)\\b(?:figure|fig)\\.?\\s*([0-9]+)\\b");
        java.util.regex.Matcher m = numPattern.matcher(caption);

        String numeric = null;
        String roman = null;

        if (m.find()) {
            numeric = m.group(1);   // es. "3"
            roman = toRoman(Integer.parseInt(numeric)); // converte in numero romano (es. "III")
        }

        // se non è stato possibile estrarre un numero, ritorna lista vuota
        if (numeric == null) {
            return out;
        }

        // costruisce regex per trovare citazioni (es: "Figure 3" o "fig.III")
        String patternStr =
                "(?i)\\b(?:figure|fig)\\.?\\s*(?:" + numeric + "|" + roman + ")\\b";
        Pattern pattern = Pattern.compile(patternStr);

        // cerca nei paragrafi
        for (String p : paragraphs) {
            if (pattern.matcher(p).find()) {
                out.add(p);
            }
        }

        return out;
    }

    // ===== METODO DI SUPPORTO: CONVERTE NUMERO IN NUMERO ROMANO =====
    private String toRoman(int number) {
        // array per rappresentare migliaia, centinaia, decine, unità in numeri romani
        String[] m = {"","M","MM","MMM"};
        String[] c = {"","C","CC","CCC","CD","D","DC","DCC","DCCC","CM"};
        String[] x = {"","X","XX","XXX","XL","L","LX","LXX","LXXX","XC"};
        String[] i = {"","I","II","III","IV","V","VI","VII","VIII","IX"};
        return m[number/1000] + c[(number%1000)/100] + x[(number%100)/10] + i[number%10];
    }

    // ===== METODO DI SUPPORTO: TROVA PARAGRAFI DI CONTESTO TRA LE KEYWORDS =====
    private List<String> findContextParagraphs(List<String> paragraphs, Set<String> keywords) {
        List<String> out = new ArrayList<>();
        for (String p : paragraphs) {
            String lower = p.toLowerCase();
            // se il paragrafo contiene almeno una keyword, lo aggiunge al contesto
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
