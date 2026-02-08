package searcher;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilterFactory;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class WebSearcher extends HttpServlet {

    private IndexSearcher indexSearcher;
    private Analyzer analyzer;
    private MultiFieldQueryParser multiFieldQueryParser;
    private StoredFields storedFields;

    @Override
    public void init() {
        try {
            // apertura dell'indice
            FSDirectory dir = FSDirectory.open(Paths.get("index"));
            DirectoryReader reader = DirectoryReader.open(dir);
            indexSearcher = new IndexSearcher(reader);
            storedFields = indexSearcher.storedFields();

            // definizione degli analyzer per campo
            Analyzer analyzerEN = new EnglishAnalyzer();
            Analyzer analyzerTitle = CustomAnalyzer.builder()
                    .withTokenizer(WhitespaceTokenizerFactory.class)
                    .addTokenFilter(LowerCaseFilterFactory.class)
                    .addTokenFilter(WordDelimiterGraphFilterFactory.class)
                    .build();
            Analyzer analyzerAuthors = CustomAnalyzer.builder()
                    .withTokenizer(WhitespaceTokenizerFactory.class)
                    .addTokenFilter(LowerCaseFilterFactory.class)
                    .build();

            Map<String, Analyzer> analyzerPerField = new HashMap<>();
            analyzerPerField.put("title", analyzerTitle);
            analyzerPerField.put("authors", analyzerAuthors);
            analyzerPerField.put("abstract", analyzerEN);
            analyzerPerField.put("fullText", analyzerEN);

            analyzer = new PerFieldAnalyzerWrapper(analyzerEN, analyzerPerField);

            // parser multi-field
            String[] defaultFields = {"title", "authors", "abstract", "fullText"};
            multiFieldQueryParser = new MultiFieldQueryParser(defaultFields, analyzer);

            System.out.println("Lucene Web Searcher inizializzato.");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // numero massimo di risultati (documenti) da recuperare
        int top_n = 10;

        resp.setContentType("text/html; charset=UTF-8");
        PrintWriter out = resp.getWriter();

        String queryStr = req.getParameter("q");

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

        if (queryStr != null && !queryStr.isEmpty()) {
            try {
                // parsing della query
                Query query = multiFieldQueryParser.parse(queryStr);

                // ricerca top 20
                TopDocs results = indexSearcher.search(query, top_n);
                out.println("<p>Trovati: " + results.totalHits.value + "</p>");

                // stampa dei risultati
                for (ScoreDoc hit : results.scoreDocs) {
                    Document doc = storedFields.document(hit.doc);
                    out.println("<div style='margin-bottom:20px;'>");
                    out.println("<b>Titolo:</b> " + doc.get("title") + "<br>");
                    out.println("<b>Autori:</b> " + doc.get("authors") + "<br>");
                    out.println("<b>File:</b> " + doc.get("filename") + "<br>");
                    out.println("<b>Score:</b> " + hit.score + "<br>");
                    out.println("</div>");
                }
            } catch (Exception e) {
                out.println("<p style='color:red;'>Errore nella query: " + e.getMessage() + "</p>");
            }
        }

        out.println("</body></html>");
    }

    public static void main(String[] args) throws Exception {
        Server server = new Server(3000);

        ServletContextHandler handler = new ServletContextHandler();
        handler.setContextPath("/");
        handler.addServlet(WebSearcher.class, "/search");

        server.setHandler(handler);
        server.start();
        System.out.println("Server web avviato su http://localhost:3000/search");
        server.join();
    }
}
