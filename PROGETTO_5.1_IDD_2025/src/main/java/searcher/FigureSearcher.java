package searcher;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.*;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class FigureSearcher {

    public static void main(String[] args) {
        Path indexDir = Paths.get("index_figure");
        try (Scanner input = new Scanner(System.in)) {
            int top_n = 10;

            Directory directory = FSDirectory.open(indexDir);
            DirectoryReader reader = DirectoryReader.open(directory);
            IndexSearcher searcher = new IndexSearcher(reader);

            // Analyzer “esatto” per url, paper_id, figure_id
            Analyzer idAnalyzer = CustomAnalyzer.builder()
                    .withTokenizer(WhitespaceTokenizerFactory.class)
                    .build();

            Analyzer textAnalyzer = new EnglishAnalyzer();

            Map<String, Analyzer> perFieldAnalyzer = new HashMap<>();
            perFieldAnalyzer.put("paper_id", idAnalyzer);
            perFieldAnalyzer.put("figure_id", idAnalyzer);
            perFieldAnalyzer.put("url", idAnalyzer);
            perFieldAnalyzer.put("caption", textAnalyzer);
            perFieldAnalyzer.put("mentions", textAnalyzer);

            Analyzer analyzer = new PerFieldAnalyzerWrapper(textAnalyzer, perFieldAnalyzer);

            // Campi ricercabili
            String[] defaultFields = {
                    "paper_id", "figure_id","url",
                    "caption", "mentions"
            };

            MultiFieldQueryParser parser = new MultiFieldQueryParser(defaultFields, analyzer);

            System.out.println("=== Sistema di ricerca figure ===");
            System.out.println("Campi testuali: caption, mentions");
            System.out.println("Campi esatti: paper_id, figure_id, url");
            System.out.println("Esempio: caption:\"convolutional network\"");
            System.out.println("Esempio: paper_id:1311.1626 AND caption:training");
            System.out.println("Per la ricerca sul campo url, metti sempre le virgolette");
            System.out.println("Scrivi 'quit' per uscire");

            while (true) {
                System.out.print("\nInserisci la query: ");
                String line = input.nextLine().trim();
                if (line.equalsIgnoreCase("quit")) break;

                try {
                    Query query = parser.parse(line);

                    TopDocs results = searcher.search(query, top_n);
                    System.out.println("\nRisultati trovati: " + results.totalHits.value);

                    for (ScoreDoc sd : results.scoreDocs) {
                        Document doc = searcher.doc(sd.doc);

                        String paperId = doc.get("paper_id");
                        String figureId = doc.get("figure_id");
                        String url = doc.get("url");
                        String caption = doc.get("caption");
                        String mentions = doc.get("mentions");

                        caption = (caption.length() > 10) ? caption.substring(0, 10) + "…" : caption;
                        mentions = (mentions.length() > 10) ? mentions.substring(0, 10) + "…" : mentions;

                        System.out.println("- paper_id: " + paperId +
                                ", figure_id: " + figureId +
                                ", url: " + url +
                                ", caption: " + caption +
                                ", mentions: " + mentions +
                                " (score: " + sd.score + ")");
                    }

                } catch (Exception e) {
                    System.out.println("Errore nella query: " + e.getMessage());
                }
            }

            reader.close();
            directory.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
