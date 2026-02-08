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

public class TableExtractor {

    // ===== STRUCT PER MEMORIZZARE I DATI DI UNA TABELLA ESTRATTA =====
    public static class ExtractedTable {
        public String paperId;           // ID del paper arXiv
        public String tableId;           // ID univoco della tabella
        public String caption;           // Caption della tabella
        public String body;              // Contenuto testuale della tabella
        public List<String> mentions;    // Paragrafi che citano la tabella
        public List<String> contextParagraphs; // Paragrafi di contesto
    }

    // ===== MAIN STANDALONE - PUNTO DI INGRESSO PER L'ESTRAZIONE =====
    public static void main(String[] args) throws Exception {
        // percorsi di input (HTML grezzi) e output (JSON con tabelle estratte)
        Path inputDir = Paths.get("corpus-html");
        Path outputDir = Paths.get("tables");

        // verifica existenza directory input
        if (!Files.exists(inputDir)) {
            System.err.println("ERRORE: directory 'corpus-html' non trovata!");
            return;
        }

        // crea directory output se non esiste
        Files.createDirectories(outputDir);

        // istanza dell'estrattore
        TableExtractor extractor = new TableExtractor();

        // configurazione di Jackson per serializzare in JSON formattato
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        // contatore per statistiche finali
        int totalTables = 0;

        System.out.println("=== ESTRAZIONE TABELLE INIZIATA ===");

        // legge tutti i file .html dalla directory corpus-html
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputDir, "*.html")) {
            for (Path htmlPath : stream) {
                // estrae ID del paper dal nome file (es: "0711.2087v1.html" -> "0711.2087v1")
                File htmlFile = htmlPath.toFile();
                String paperId = htmlFile.getName().replace(".html", "");

                System.out.println("\n>> Processing " + paperId);

                // chiama il metodo di estrazione per questo paper
                List<ExtractedTable> tables = extractor.extractTablesFromHtml(htmlFile, paperId);

                // se nessuna tabella trovata, passa al paper successivo
                if (tables.isEmpty()) {
                    System.out.println("Nessuna tabella trovata.");
                    continue;
                }

                // salva le tabelle estratte in un file JSON (UN FILE PER PAPER)
                Path outFile = outputDir.resolve(paperId + "_tables.json");
                mapper.writeValue(outFile.toFile(), tables);

                System.out.println("Tabelle trovate: " + tables.size());
                System.out.println("Salvato file: " + outFile);

                // accumula il conteggio totale
                totalTables += tables.size();
            }
        }

        // stampa statistiche finali
        System.out.println("\n=== COMPLETATO ===");
        System.out.println("Totale tabelle estratte: " + totalTables);
    }

    // ===== METODO PRINCIPALE DI ESTRAZIONE TABELLE DALL'HTML =====
    public List<ExtractedTable> extractTablesFromHtml(File htmlFile, String paperId) throws IOException {
        // lista dei risultati (tabelle estratte)
        List<ExtractedTable> results = new ArrayList<>();

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

        // ===== 2. SELEZIONA SOLO LE VERE TABELLE (usa il selettore arXiv) =====
        Elements tables = doc.select("figure.ltx_table");

        for (Element fig : tables) {
            // crea una nuova entry per la tabella
            ExtractedTable t = new ExtractedTable();
            t.paperId = paperId;

            // estrae ID della tabella (usa UUID se non disponibile)
            String id = fig.id();
            if (id == null || id.isEmpty())
                id = UUID.randomUUID().toString();
            t.tableId = id;

            // estrae la caption (titolo/descrizione della tabella)
            Element captionEl = fig.selectFirst("figcaption.ltx_caption");
            t.caption = captionEl != null ? cleanText(captionEl.text()) : "";

            // estrae il corpo della tabella (contenuto testuale)
            Element tableEl = fig.selectFirst("table");
            t.body = tableEl != null ? cleanText(tableEl.text()) : "";

            // estrae parole chiave dalla caption e body per trovare il contesto
            Set<String> tableKeywords = extractKeywords(t.caption + " " + t.body);

            // trova paragrafi che citano questa tabella (es: "Table 2")
            t.mentions = findMentionParagraphs(paragraphs, id);

            // trova paragrafi che condividono keywords con la tabella
            t.contextParagraphs = findContextParagraphs(paragraphs, tableKeywords);

            results.add(t);
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

    // ===== METODO DI SUPPORTO: TROVA PARAGRAFI CHE CITANO LA TABELLA =====
    private List<String> findMentionParagraphs(List<String> paragraphs, String tableId) {
        List<String> out = new ArrayList<>();

        // estrae il codice numerico dall'ID della tabella (es: "2" da "S4.T2")
        String code = extractNumericTableCode(tableId);
        if (code == null || code.isEmpty()) {
            return out;
        }

        // costruisce liste di token per la ricerca (numero arabo + numero romano)
        List<String> tokens = new ArrayList<>();
        if (code.matches("\\d+")) {
            tokens.add(code);
            tokens.add(toRoman(Integer.parseInt(code))); // converte in numero romano
        } else if (code.matches("[IVXLC]+")) {
            tokens.add(code);
        } else {
            return out;
        }

        // crea regex per trovare citazioni (es: "Table 2" o "tab.II")
        String patternStr = "(?i)\\b(?:table|tab)\\.?\\s*(?:" + String.join("|", tokens) + ")\\b";
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

    // ===== METODO DI SUPPORTO: ESTRAE CODICE NUMERICO DALLA TABELLA ID =====
    private String extractNumericTableCode(String raw) {
        // se non contiene "T", ritorna il valore grezzo
        if (!raw.contains("T"))
            return raw;
        // estrae tutto dopo la "T" e mantiene solo numeri e numeri romani
        String afterT = raw.substring(raw.indexOf("T") + 1);
        return afterT.replaceAll("[^0-9IVXLC]", "");
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
