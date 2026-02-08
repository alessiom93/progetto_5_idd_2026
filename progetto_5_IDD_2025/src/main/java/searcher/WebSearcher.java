package searcher;

import org.apache.lucene.analysis.Analyzer; // analisi del testo (tokenizzazione, stemming, ecc.)
import org.apache.lucene.analysis.en.EnglishAnalyzer; // analyzer per la lingua inglese
import org.apache.lucene.analysis.custom.CustomAnalyzer; // analyzer custom per campi specifici
import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory; // tokenizzatore che usa gli spazi bianchi
import org.apache.lucene.analysis.core.LowerCaseFilterFactory; // filtro per convertire in minuscolo
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilterFactory; // filtro per dividere le parole
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper; // analyzer per campo specifico
import org.apache.lucene.document.Document; // per creare documenti e campi
import org.apache.lucene.index.DirectoryReader; // per la lettura dell'indice
import org.apache.lucene.index.StoredFields; // per accedere ai campi memorizzati
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser; // per il parsing delle query su più campi
import org.apache.lucene.search.*; // interfacce per la ricerca (Query, IndexSearcher, TopDocs, ecc.)
import org.apache.lucene.store.FSDirectory; // per accedere all'indice nel filesystem

import org.eclipse.jetty.ee10.servlet.ServletContextHandler; // handler per i servlet Jetty
import org.eclipse.jetty.server.Server; // server HTTP Jetty

import jakarta.servlet.http.HttpServlet; // servlet base
import jakarta.servlet.http.HttpServletRequest; // richiesta HTTP
import jakarta.servlet.http.HttpServletResponse; // risposta HTTP

import java.io.IOException; // per gestire eccezioni di I/O
import java.io.PrintWriter; // per scrivere la risposta HTML
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class WebSearcher extends HttpServlet {

    private IndexSearcher indexSearcher;      // oggetto per la ricerca sull'indice
    private Analyzer analyzer;                // analyzer per l'analisi del testo
    private MultiFieldQueryParser multiFieldQueryParser; // parser per query complesse su pi campi
    private StoredFields storedFields;        // accesso ai campi memorizzati del documento

    // ===== INIT - INIZIALIZZA IL SERVLET CARICANDO L'INDICE =====
    @Override
    public void init() {
        try {
            // apertura dell'indice
            FSDirectory dir = FSDirectory.open(Paths.get("index"));
            DirectoryReader reader = DirectoryReader.open(dir);
            indexSearcher = new IndexSearcher(reader);
            storedFields = indexSearcher.storedFields();

            // ===== CONFIGURAZIONE ANALYZER PER CAMPI SPECIFICI =====
            // analyzer per la lingua inglese
            Analyzer analyzerEN = new EnglishAnalyzer();
            // analyzer custom per il titolo (whitespace + lowercase + word delimiter)
            Analyzer analyzerTitle = CustomAnalyzer.builder()
                    .withTokenizer(WhitespaceTokenizerFactory.class)
                    .addTokenFilter(LowerCaseFilterFactory.class)
                    .addTokenFilter(WordDelimiterGraphFilterFactory.class)
                    .build();
            // analyzer custom per gli autori (whitespace + lowercase)
            Analyzer analyzerAuthors = CustomAnalyzer.builder()
                    .withTokenizer(WhitespaceTokenizerFactory.class)
                    .addTokenFilter(LowerCaseFilterFactory.class)
                    .build();

            // mappa analyzer per campo
            Map<String, Analyzer> analyzerPerField = new HashMap<>();
            analyzerPerField.put("title", analyzerTitle);      // usa analyzer custom per titolo
            analyzerPerField.put("authors", analyzerAuthors);  // usa analyzer custom per autori
            analyzerPerField.put("abstract", analyzerEN);      // usa analyzer inglese per abstract
            analyzerPerField.put("fullText", analyzerEN);      // usa analyzer inglese per corpo

            // combina gli analyzer per campo
            analyzer = new PerFieldAnalyzerWrapper(analyzerEN, analyzerPerField);

            // parser multi-field per query complesse
            String[] defaultFields = {"title", "authors", "abstract", "fullText"};
            multiFieldQueryParser = new MultiFieldQueryParser(defaultFields, analyzer);

            System.out.println("Lucene Web Searcher inizializzato.");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ===== DOGET - GESTISCE LE RICHIESTE HTTP GET =====
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // numero massimo di risultati da recuperare
        int top_n = 10;

        // imposta il tipo di contenuto della risposta
        resp.setContentType("text/html; charset=UTF-8");
        PrintWriter out = resp.getWriter();

        // estrae il parametro 'q' dalla query string
        String queryStr = req.getParameter("q");

        // inizia l'HTML di risposta
        out.println("<html><body style='font-family: Arial;'>");
        out.println("<h2>Lucene Web Search</h2>");
        out.println("<h3>Campi disponibili per la ricerca: title, authors, abstract, fullText</h3>");
        out.println("<h3>Scrivi la query seguendo questa sintassi:</h3>");
        out.println("<h3> - title:termine  -> cerca il file che contiene quel termine nel titolo</h3>");
        out.println("<h3> - title:termine1 AND fullText:termine2   -> cerca il file che contiene il termine1 nel titolo e il termine2 nel corpo del testo</h3>");
        out.println("<h3> - title:termine1 OR fullText:termine2   -> cerca il file che contiene il termine1 nel titolo o il termine2 nel corpo del testo</h3>");
        out.println("<h3>Puoi usare le virgolette per phrase query (es: title:\"Query processing\")</h3>");
        
        // form di ricerca
        out.println("<form method='GET' action='/search'>");
        out.println("Query: <input type='text' name='q' size='60' value='" +
                (queryStr != null ? queryStr : "") + "'>");
        out.println("<input type='submit' value='Cerca'>");
        out.println("</form><hr>");

        // esegue la ricerca se è stata inserita una query
        if (queryStr != null && !queryStr.isEmpty()) {
            try {
                // parsing della query
                Query query = multiFieldQueryParser.parse(queryStr);

                // ricerca dei top_n risultati
                TopDocs results = indexSearcher.search(query, top_n);
                out.println("<p>Trovati: " + results.totalHits.value + "</p>");

                // stampa dei risultati
                for (ScoreDoc hit : results.scoreDocs) {
                    // recupera il documento dall'indice
                    Document doc = storedFields.document(hit.doc);
                    // stampa info documento
                    out.println("<div style='margin-bottom:20px;'>");
                    out.println("<b>Titolo:</b> " + doc.get("title") + "<br>");
                    out.println("<b>Autori:</b> " + doc.get("authors") + "<br>");
                    out.println("<b>File:</b> " + doc.get("filename") + "<br>");
                    out.println("<b>Score:</b> " + hit.score + "<br>");
                    out.println("</div>");
                }
            } catch (Exception e) {
                // stampa messaggio di errore se la query è invalida
                out.println("<p style='color:red;'>Errore nella query: " + e.getMessage() + "</p>");
            }
        }

        out.println("</body></html>");
    }

    // ===== MAIN - AVVIA IL SERVER WEB =====
    public static void main(String[] args) throws Exception {
        // crea il server Jetty sulla porta 3000
        Server server = new Server(3000);

        // configura il context handler e monta il servlet
        ServletContextHandler handler = new ServletContextHandler();
        handler.setContextPath("/");
        handler.addServlet(WebSearcher.class, "/search");

        // associa il handler al server e avvia
        server.setHandler(handler);
        server.start();
        System.out.println("Server web avviato su http://localhost:3000/search");
        server.join();
    }
  }
