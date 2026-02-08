package searcher;

import org.apache.lucene.analysis.Analyzer; // analisi del testo (tokenizzazione, stemming, ecc.)
import org.apache.lucene.analysis.custom.CustomAnalyzer; // analyzer custom per il filename
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory; // tokenizzatore che usa gli spazi bianchi
import org.apache.lucene.analysis.core.LowerCaseFilterFactory; // filtro per convertire in minuscolo
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilterFactory; // filtro per dividere le parole
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper; // analyzer per campo specifico
import org.apache.lucene.document.Document; // per creare documenti e campi
import org.apache.lucene.index.DirectoryReader; // per la lettura dell'indice
import org.apache.lucene.index.StoredFields; // per accedere ai campi memorizzati
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser; // per il parsing delle query su più campi
import org.apache.lucene.search.*;
import org.apache.lucene.store.*;

import java.nio.file.*;
import java.util.Scanner;
import java.util.HashMap;
import java.util.Map;

public class Searcher {
    public static void main(String[] args) {
      // path dell'indice
        Path indexDir = Paths.get("index");

        try {
            // numero massimo di risultati (documenti) da recuperare
            int top_n = 10;
            // apertura della directory per l'indice
            Directory directory = FSDirectory.open(indexDir);
            // ottiene accesso in lettura all'indice
            DirectoryReader directoryReader = DirectoryReader.open(directory);
            // configurazione dell'IndexSearcher con il DirectoryReader
            IndexSearcher indexSearcher = new IndexSearcher(directoryReader);
            // imposta il criterio di similarità per l'IndexSearcher, di default Lucene usa BM25Similarity
            // 
            // analyzer per l'analisi del testo
            // usa gli stessi analyzer definiti in fase di indicizzazione
            Analyzer analyzerEN = new EnglishAnalyzer();
            Analyzer analyzerTitle = CustomAnalyzer.builder()
                    .withTokenizer(WhitespaceTokenizerFactory.class) // tokenizza usando gli spazi bianchi
                    .addTokenFilter(LowerCaseFilterFactory.class) // converte tutto in minuscolo
                    .addTokenFilter(WordDelimiterGraphFilterFactory.class) // divide le parole basandosi su maiuscole, numeri, simboli, ecc.
                    .build();
            Analyzer analyzerAuthors = CustomAnalyzer.builder()
                    .withTokenizer(WhitespaceTokenizerFactory.class) // tokenizza usando gli spazi bianchi
                    .addTokenFilter(LowerCaseFilterFactory.class) // converte tutto in minuscolo
                    .build();
            Map<String, Analyzer> analyzerPerField = new HashMap<>();
            analyzerPerField.put("title", analyzerTitle);
            analyzerPerField.put("authors", analyzerAuthors);
            analyzerPerField.put("abstract", analyzerEN);
            analyzerPerField.put("fullText", analyzerEN);
            Analyzer analyzer = new PerFieldAnalyzerWrapper(analyzerEN, analyzerPerField);
            // input da console per la query di ricerca
            Scanner input = new Scanner(System.in);
            System.out.println("=== Sistema di Ricerca ===");
            System.out.println("Campi disponibili per la ricerca: title, authors, abstract, fullText");
            System.out.println("Scrivi la query seguendo questa sintassi:");
            System.out.println(" - title:<termine>  -> cerca il file che contiene quel termine nel titolo");
            System.out.println(" - title:<termine1> AND fullText:<termine2>   -> cerca il file che contiene il termine1 nel titolo e il termine2 nel corpo del testo");
            System.out.println(" - title:<termine1> OR fullText:<termine2>   -> cerca il file che contiene il termine1 nel titolo o il termine2 nel corpo del testo");
            System.out.println("Puoi usare le virgolette per phrase query (es: title:\"Query processing\")");
            System.out.println("Scrivi quit per uscire");
            // crea il parser per la query su più campi
            String[] defaultFields = {"title", "authors", "abstract", "fullText"};
            MultiFieldQueryParser multiFieldQueryParser = new MultiFieldQueryParser(defaultFields, analyzer);
            // ciclo per permettere più ricerche, esce quando l'utente scrive "quit"
            while (true) {
                System.out.print("\nInserisci la query di ricerca: ");
                String queryString = input.nextLine().trim();
                if (queryString.equalsIgnoreCase("quit")) {
                    input.close();
                    break;
                }
                try {
                  // definisce la query di ricerca
                  Query query = multiFieldQueryParser.parse(queryString);
                  // cerca i migliori top_n documenti che soddisfano la query
                  TopDocs results = indexSearcher.search(query, top_n);
                  System.out.println("\nRisultati trovati: " + results.totalHits.value + "\n");
                  // prende i campi memorizzati dei documenti nell'indice
                  StoredFields storedFields = indexSearcher.storedFields();
                  // itera sui risultati (docunmenti trovati)
                  for (ScoreDoc hit : results.scoreDocs) {
                    // recupera il documento risultante dall'indice
                    Document doc = storedFields.document(hit.doc);
                    // ottiene il nome del file dal campo "filename"
                    String filename = doc.get("filename");
                    // ottiene il punteggio di rilevanza del documento (ranking)
                    float score = hit.score;
                    System.out.println("- " + filename + " (score: " + score + ")");
                  }
                } catch (Exception e) {
                  System.out.println("Errore nel parsing della query: " + e.getMessage());
                }
            }        
            directoryReader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
