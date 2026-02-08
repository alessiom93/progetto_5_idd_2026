package searcher;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.*;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
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

public class WebTableSearcher extends HttpServlet {

    private IndexSearcher indexSearcher;
    private Analyzer analyzer;
    private MultiFieldQueryParser multiFieldQueryParser;
    private StoredFields storedFields;

    @Override
    public void init() {
        try {
            FSDirectory dir = FSDirectory.open(Paths.get("index_table"));
            DirectoryReader reader = DirectoryReader.open(dir);
            indexSearcher = new IndexSearcher(reader);
            storedFields = indexSearcher.storedFields();

            Analyzer bodyAnalyzer = CustomAnalyzer.builder()
                    .withTokenizer(WhitespaceTokenizerFactory.class)
                    .build();
            Analyzer textAnalyzer = new EnglishAnalyzer();
            Analyzer idAnalyzer = CustomAnalyzer.builder()
                    .withTokenizer(WhitespaceTokenizerFactory.class)
                    .build();

            Map<String, Analyzer> analyzerPerField = new HashMap<>();
            analyzerPerField.put("paper_id", idAnalyzer);
            analyzerPerField.put("table_id", idAnalyzer);
            analyzerPerField.put("caption", textAnalyzer);
            analyzerPerField.put("body", bodyAnalyzer);
            analyzerPerField.put("mentions", textAnalyzer);
            analyzerPerField.put("context_paragraphs", textAnalyzer);

            analyzer = new PerFieldAnalyzerWrapper(textAnalyzer, analyzerPerField);

            String[] defaultFields = {"paper_id", "table_id", "caption", "body", "mentions", "context_paragraphs"};
            multiFieldQueryParser = new MultiFieldQueryParser(defaultFields, analyzer);

            System.out.println("WebTableSearcher inizializzato.");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            int top_n = 10;

            resp.setContentType("text/html; charset=UTF-8");
            PrintWriter out = resp.getWriter();

            String queryStr = req.getParameter("q");
            out.println("<html><body style='font-family:Arial'>");
            out.println("<h2>Lucene Web Table Searcher</h2>");
            out.println("<h3>Campi disponibili per la ricerca: paper_id, table_id, caption, body, mentions, context_paragraphs</h3>");
            out.println("<h3>Scrivi la query seguendo questa sintassi:</h3>");
            out.println("<h3> - paper_id:termine  -> cerca il file che contiene quel termine nel paper_id</h3>");
            out.println("<h3> - table_id:termine1 AND caption:termine2   -> cerca il file che contiene il termine1 nel table_id e il termine2 nella caption</h3>");
            out.println("<h3> - paper_id:termine1 OR body:termine2   -> cerca il file che contiene il termine1 nel paper_id o il termine2 nel body</h3>");
            out.println("<h3>Puoi usare le virgolette per phrase query (es: caption:\"Query processing\")</h3>");
            out.println("<form method='GET' action='/search'>");                                              
            out.println("Query: <input type='text' name='q' size='60' value='" +
                (queryStr != null ? queryStr : "") + "'>");
            out.println("<input type='submit' value='Cerca'>");
            out.println("</form><hr>");

            if (queryStr != null && !queryStr.isEmpty()) {
                Query query = multiFieldQueryParser.parse(queryStr);
                TopDocs results = indexSearcher.search(query, top_n);
                out.println("<p>Trovati: " + results.totalHits.value + "</p>");

                for (ScoreDoc sd : results.scoreDocs) {
                    Document doc = storedFields.document(sd.doc);
                    String paperId = doc.get("paper_id");
                    String tableId = doc.get("table_id");
                    String caption = doc.get("caption");
                    String body = doc.get("body");
                    String mentions = doc.get("mentions");
                    String context = doc.get("context_paragraphs");
                    caption = (caption.length() > 10) ? caption.substring(0, 10) + "…" : caption;
                    body = (body.length() > 10) ? body.substring(0, 10) + "…" : body;
                    mentions = (mentions.length() > 10) ? mentions.substring(0, 10) + "…" : mentions;
                    context = (context.length() > 10) ? context.substring(0, 10) + "…" : context;
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

    public static void main(String[] args) throws Exception {
        Server server = new Server(3001);
        ServletContextHandler handler = new ServletContextHandler();
        handler.setContextPath("/");
        handler.addServlet(WebTableSearcher.class, "/search");
        server.setHandler(handler);
        server.start();
        System.out.println("Server web per tabelle avviato su http://localhost:3001/search");
        server.join();
    }
}
