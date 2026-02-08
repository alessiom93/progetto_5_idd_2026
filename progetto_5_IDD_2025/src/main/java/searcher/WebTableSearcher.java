package searcher;

import org.apache.lucene.analysis.Analyzer; // analisi del testo (tokenizzazione, stemming, ecc.)
import org.apache.lucene.analysis.en.EnglishAnalyzer; // analyzer per la lingua inglese
import org.apache.lucene.analysis.custom.CustomAnalyzer; // analyzer custom per campi specifici
import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory; // tokenizzatore che usa gli spazi bianchi
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper; // analyzer per campo specifico
import org.apache.lucene.document.Document; // per creare documenti e campi
import org.apache.lucene.index.DirectoryReader; // per la lettura dell'indice
import org.apache.lucene.index.StoredFields; // per accedere ai campi memorizzati
import org.apache.lucene.search.*; // interfacce per la ricerca (Query, IndexSearcher, TopDocs, ecc.)
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser; // per il parsing delle query su più campi
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

public class WebTableSearcher extends HttpServlet {

    private IndexSearcher indexSearcher;      // oggetto per la ricerca sull'indice
    private Analyzer analyzer;                // analyzer per l'analisi del testo
    private MultiFieldQueryParser multiFieldQueryParser; // parser per query complesse su più campi
    private StoredFields storedFields;        // accesso ai campi memorizzati del documento

    // ===== INIT - INIZIALIZZA IL SERVLET CARICANDO L'INDICE DELLE TABELLE =====
    @Override
    public void init() {
        try {
            // apertura dell'indice delle tabelle
            FSDirectory dir = FSDirectory.open(Paths.get("index_table"));
            DirectoryReader reader = DirectoryReader.open(dir);
            indexSearcher = new IndexSearcher(reader);
            storedFields = indexSearcher.storedFields();

            // ===== CONFIGURAZIONE ANALYZER PER CAMPI SPECIFICI =====
            // analyzer per il corpo della tabella (testo preciso)
            Analyzer bodyAnalyzer = CustomAnalyzer.builder()
                    .withTokenizer(WhitespaceTokenizerFactory.class)
                    .build();
            // analyzer con stemming e stop words per campi testuali
            Analyzer textAnalyzer = new EnglishAnalyzer();
            // analyzer "neutro" per ID
            Analyzer idAnalyzer = CustomAnalyzer.builder()
                    .withTokenizer(WhitespaceTokenizerFactory.class)
                    .build();

            // mappa analyzer per campo
            Map<String, Analyzer> analyzerPerField = new HashMap<>();
            analyzerPerField.put("paper_id", idAnalyzer);           // ID esatto
            analyzerPerField.put("table_id", idAnalyzer);           // ID esatto
            analyzerPerField.put("caption", textAnalyzer);          // testo descrittivo
            analyzerPerField.put("body", bodyAnalyzer);             // testo preciso della tabella
            analyzerPerField.put("mentions", textAnalyzer);         // paragrafi che citano
            analyzerPerField.put("context_paragraphs", textAnalyzer); // paragrafi di contesto

            // combina gli analyzer per campo
            analyzer = new PerFieldAnalyzerWrapper(textAnalyzer, analyzerPerField);

            // parser multi-field per query complesse
            String[] defaultFields = {"paper_id", "table_id", "caption", "body", "mentions", "context_paragraphs"};
            multiFieldQueryParser = new MultiFieldQueryParser(defaultFields, analyzer);

            System.out.println("WebTableSearcher inizializzato.");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ===== DOGET - GESTISCE LE RICHIESTE HTTP GET =====
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            // numero massimo di risultati da recuperare
            int top_n = 10;

            // imposta il tipo di contenuto della risposta
            resp.setContentType("text/html; charset=UTF-8");
            PrintWriter out = resp.getWriter();

            // estrae il parametro 'q' dalla query string
            String queryStr = req.getParameter("q");
            
            // inizia l'HTML di risposta
            out.println("<html><body style='font-family:Arial'>");
            out.println("<h2>Lucene Web Table Searcher</h2>");
            out.println("<h3>Campi disponibili per la ricerca: paper_id, table_id, caption, body, mentions, context_paragraphs</h3>");
            out.println("<h3>Scrivi la query seguendo questa sintassi:</h3>");
            out.println("<h3> - paper_id:termine  -> cerca il file che contiene quel termine nel paper_id</h3>");
            out.println("<h3> - table_id:termine1 AND caption:termine2   -> cerca il file che contiene il termine1 nel table_id e il termine2 nella caption</h3>");
            out.println("<h3> - paper_id:termine1 OR body:termine2   -> cerca il file che contiene il termine1 nel paper_id o il termine2 nel body</h3>");
            out.println("<h3>Puoi usare le virgolette per phrase query (es: caption:\"Query processing\")</h3>");
            
            // form di ricerca
            out.println("<form method='GET' action='/search'>");
            out.println("Query: <input type='text' name='q' size='60' value='" +
                (queryStr != null ? queryStr : "") + "'>");
            out.println("<input type='submit' value='Cerca'>");
            out.println("</form><hr>");

            // esegue la ricerca se è stata inserita una query
            if (queryStr != null && !queryStr.isEmpty()) {
                // parsing della query
                Query query = multiFieldQueryParser.parse(queryStr);
                // ricerca dei top_n risultati
                TopDocs results = indexSearcher.search(query, top_n);
                out.println("<p>Trovati: " + results.totalHits.value + "</p>");

                // stampa dei risultati
                for (ScoreDoc sd : results.scoreDocs) {
                    // recupera il documento dall'indice
                    Document doc = storedFields.document(sd.doc);
                    // estrae i campi
                    String paperId = doc.get("paper_id");
                    String tableId = doc.get("table_id");
                    String caption = doc.get("caption");
                    String body = doc.get("body");
                    String mentions = doc.get("mentions");
                    String context = doc.get("context_paragraphs");
                    
                    // tronca i campi lunghi
                    caption = (caption.length() > 10) ? caption.substring(0, 10) + "…" : caption;
                    body = (body.length() > 10) ? body.substring(0, 10) + "…" : body;
                    mentions = (mentions.length() > 10) ? mentions.substring(0, 10) + "…" : mentions;
                    context = (context.length() > 10) ? context.substring(0, 10) + "…" : context;
                    
                    // stampa il documento con i campi
                    out.println("<div style='margin-bottom:20px;'>");
                    out.println("<b>Paper ID:</b> " + paperId + "<br>");
                    out.println("<b>Table ID:</b> " + tableId + "<br>");
                    out.println("<b>Caption:</b> " + caption + "<br>");
                    out.println("<b>Body:</b> " + body + "<br>");
                    out.println("<b>Mentions:</b> " + mentions + "<br>");
                    out.println("<b>Context:</b> " + context + "<br>");
                    out.println("<b>Score:</b> " + sd.score + "<br>");
                    out.println("</div>");
                }
            }

            out.println("</body></html>");
          } catch (Exception e) {
            e.printStackTrace();
          }
        }

    // ===== MAIN - AVVIA IL SERVER WEB =====
    public static void main(String[] args) throws Exception {
        // crea il server Jetty sulla porta 3001
        Server server = new Server(3001);
        // configura il context handler e monta il servlet
        ServletContextHandler handler = new ServletContextHandler();
        handler.setContextPath("/");
        handler.addServlet(WebTableSearcher.class, "/search");
        // associa il handler al server e avvia
        server.setHandler(handler);
        server.start();
        System.out.println("Server web per tabelle avviato su http://localhost:3001/search");
        server.join();
    }
}
