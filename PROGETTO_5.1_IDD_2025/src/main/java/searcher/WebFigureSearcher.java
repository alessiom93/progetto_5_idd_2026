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

public class WebFigureSearcher extends HttpServlet {

    private IndexSearcher indexSearcher;
    private Analyzer analyzer;
    private MultiFieldQueryParser multiFieldQueryParser;
    private StoredFields storedFields;

    @Override
    public void init() {
        try {
            FSDirectory dir = FSDirectory.open(Paths.get("index_figure"));
            DirectoryReader reader = DirectoryReader.open(dir);
            indexSearcher = new IndexSearcher(reader);
            storedFields = indexSearcher.storedFields();

            Analyzer textAnalyzer = new EnglishAnalyzer();
            Analyzer idAnalyzer = CustomAnalyzer.builder()
                    .withTokenizer(WhitespaceTokenizerFactory.class)
                    .build();

            Map<String, Analyzer> analyzerPerField = new HashMap<>();
            analyzerPerField.put("paper_id", idAnalyzer);
            analyzerPerField.put("figure_id", idAnalyzer);
            analyzerPerField.put("url", idAnalyzer);
            analyzerPerField.put("caption", textAnalyzer);
            analyzerPerField.put("mentions", textAnalyzer);

            analyzer = new PerFieldAnalyzerWrapper(textAnalyzer, analyzerPerField);

            String[] defaultFields = {"paper_id", "figure_id", "url", "caption", "mentions"};
            multiFieldQueryParser = new MultiFieldQueryParser(defaultFields, analyzer);

            System.out.println("WebFigureSearcher inizializzato.");
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
            out.println("<h2>Lucene Web Figure Searcher</h2>");
            out.println("<h3>Campi disponibili per la ricerca: paper_id, figure_id, url, caption, mentions</h3>");
            out.println("<h3>Scrivi la query seguendo questa sintassi:</h3>");
            out.println("<h3> - paper_id:termine  -> cerca il file che contiene quel termine nel paper_id</h3>");
            out.println("<h3> - figure_id:termine1 AND caption:termine2   -> cerca il file che contiene il termine1 nel figure_id e il termine2 nella caption</h3>");
            out.println("<h3> - paper_id:termine1 OR url:termine2   -> cerca il file che contiene il termine1 nel paper_id o il termine2 nell'url</h3>");
            out.println("<h3> - Per la ricerca sul campo url, metti sempre le virgolette</h3>");
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
                    String figureId = doc.get("figure_id");
                    String url = doc.get("url");
                    String caption = doc.get("caption");
                    String mentions = doc.get("mentions");
                    caption = (caption.length() > 10) ? caption.substring(0, 10) + "…" : caption;
                    mentions = (mentions.length() > 10) ? mentions.substring(0, 10) + "…" : mentions;
                    out.println("<div style='margin-bottom:20px;'>");
                    out.println("<b>Paper ID:</b> " + paperId + "<br>");
                    out.println("<b>Figure ID:</b> " + figureId + "<br>");
                    out.println("<b>URL:</b> " + url + "<br>");
                    out.println("<b>Caption:</b> " + caption + "<br>");
                    out.println("<b>Mentions:</b> " + doc.get("mentions") + "<br>");
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
        Server server = new Server(3002);
        ServletContextHandler handler = new ServletContextHandler();
        handler.setContextPath("/");
        handler.addServlet(WebFigureSearcher.class, "/search");
        server.setHandler(handler);
        server.start();
        System.out.println("Server web per figure avviato su http://localhost:3002/search");
        server.join();
    }
}
