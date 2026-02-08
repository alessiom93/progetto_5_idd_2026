package searcher;

import org.apache.lucene.analysis.Analyzer; // analisi del testo (tokenizzazione, stemming, ecc.)
import org.apache.lucene.analysis.custom.CustomAnalyzer; // analyzer custom per campi specifici
import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory; // tokenizzatore che usa gli spazi bianchi
import org.apache.lucene.analysis.en.EnglishAnalyzer; // analyzer per la lingua inglese
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper; // analyzer per campo specifico
import org.apache.lucene.document.Document; // per creare documenti e campi
import org.apache.lucene.index.DirectoryReader; // per la lettura dell'indice
import org.apache.lucene.search.*; // interfacce per la ricerca (Query, IndexSearcher, TopDocs, ecc.)
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser; // per il parsing delle query su più campi
import org.apache.lucene.store.Directory; // per gestire la directory dell'indice
import org.apache.lucene.store.FSDirectory; // per accedere all'indice nel filesystem

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class TableSearcher {

    // ===== MAIN - INTERFACCIA INTERATTIVA DI RICERCA TABELLE =====
    public static void main(String[] args) {
        // percorso dell'indice delle tabelle
        Path indexDir = Paths.get("index_table");

        try (Scanner input = new Scanner(System.in)) {
            int top_n = 10;

            Directory directory = FSDirectory.open(indexDir);
            DirectoryReader reader = DirectoryReader.open(directory);
            IndexSearcher searcher = new IndexSearcher(reader);

            // Analyzer per i campi testuali
            Analyzer bodyAnalyzer = CustomAnalyzer.builder()
                    .withTokenizer(WhitespaceTokenizerFactory.class)
                    .build();
            Analyzer textAnalyzer = new EnglishAnalyzer();

            // Analyzer “neutro” per paper_id e table_id
            Analyzer idAnalyzer = CustomAnalyzer.builder()
                    .withTokenizer(WhitespaceTokenizerFactory.class)
                    .build();

            Map<String, Analyzer> perFieldAnalyzer = new HashMap<>();
            perFieldAnalyzer.put("paper_id", idAnalyzer);
            perFieldAnalyzer.put("table_id", idAnalyzer);
            perFieldAnalyzer.put("caption", textAnalyzer);
            perFieldAnalyzer.put("body", bodyAnalyzer);
            perFieldAnalyzer.put("mentions", textAnalyzer);
            perFieldAnalyzer.put("context_paragraphs", textAnalyzer);

            Analyzer analyzer = new PerFieldAnalyzerWrapper(textAnalyzer, perFieldAnalyzer);

            // Tutti i campi disponibili per la ricerca
            String[] defaultFields = {
                "paper_id", "table_id",
                "caption", "body", "mentions", "context_paragraphs"
            };
            MultiFieldQueryParser parser = new MultiFieldQueryParser(defaultFields, analyzer);

            System.out.println("=== Sistema di ricerca tabelle ===");
            System.out.println("Campi testuali: caption, body, mentions, context_paragraphs");
            System.out.println("Campi esatti: paper_id, table_id");
            System.out.println("Esempio query: caption:\"Neural Network\" AND body:training");
            System.out.println("Puoi cercare paper_id o table_id direttamente: paper_id:Paper123");
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
                            String tableId = doc.get("table_id");
                            String caption = doc.get("caption");
                            String body = doc.get("body");
                            String mentions = doc.get("mentions");
                            String context = doc.get("context_paragraphs");
                            caption = (caption.length() > 10) ? caption.substring(0, 10) + "…" : caption;
                            body = (body.length() > 10) ? body.substring(0, 10) + "…" : body;
                            mentions = (mentions.length() > 10) ? mentions.substring(0, 10) + "…" : mentions;
                            context = (context.length() > 10) ? context.substring(0, 10) + "…" : context;
                            System.out.println("- paper_id: " + paperId +
                                            ", table_id: " + tableId +
                                            ", caption: " + caption +
                                            ", body: " + body +
                                            ", mentions: " + mentions +
                                            ", context_paragraphs: " + context +
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
