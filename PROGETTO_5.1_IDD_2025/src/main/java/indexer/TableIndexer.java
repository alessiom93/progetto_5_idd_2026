package indexer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import extractor.TableExtractor;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TableIndexer {

    private final IndexWriter writer;

    public TableIndexer(String indexPath, Analyzer analyzer) throws IOException {
        FSDirectory dir = FSDirectory.open(Path.of(indexPath));
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        this.writer = new IndexWriter(dir, config);
    }

    public void indexTableJson(File jsonFile) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // Legge una lista di tabelle dal JSON
        List<TableExtractor.ExtractedTable> tables = mapper.readValue(
                jsonFile,
                new TypeReference<List<TableExtractor.ExtractedTable>>() {}
        );

        for (TableExtractor.ExtractedTable table : tables) {
            Document doc = new Document();
            doc.add(new TextField("paper_id", table.paperId, Field.Store.YES));
            doc.add(new TextField("table_id", table.tableId, Field.Store.YES));
            doc.add(new TextField("caption", table.caption, Field.Store.YES));
            doc.add(new TextField("body", table.body, Field.Store.YES));
            doc.add(new TextField("mentions", String.join("\n", table.mentions), Field.Store.YES));
            doc.add(new TextField("context_paragraphs",String.join("\n", table.contextParagraphs), Field.Store.YES));
            writer.addDocument(doc);
        }
    }

    public void indexFolder(File folder) throws Exception {
        File[] jsonFiles = folder.listFiles((dir, name) -> name.endsWith(".json"));
        if (jsonFiles == null) return;

        for (File f : jsonFiles) {
            System.out.println("➜ Indicizzazione tabella: " + f.getName());
            indexTableJson(f);
        }
    }

    public void close() throws IOException {
        writer.close();
    }

    // ---------------------------------------------------------------------
    // MAIN STANDALONE SENZA ARGOMENTI
    // ---------------------------------------------------------------------
    public static void main(String[] args) throws Exception {
        File jsonFolder = new File("tables");
        String indexPath = "index_table";

        if (!jsonFolder.exists()) {
            System.err.println("ERRORE: Cartella 'tables' non trovata.");
            return;
        }

        // Analyzer “neutro” per i campi che contengono valori precisi (paper_id, table_id, body)
        Analyzer idAnalyzer = CustomAnalyzer.builder()
                .withTokenizer(WhitespaceTokenizerFactory.class)
                .build();

        Analyzer bodyAnalyzer = CustomAnalyzer.builder()
                .withTokenizer(WhitespaceTokenizerFactory.class)
                .build();

        Analyzer textAnalyzer = new EnglishAnalyzer();

        Map<String, Analyzer> analyzerPerField = new HashMap<>();
        analyzerPerField.put("paper_id", idAnalyzer);                     // valori esatti
        analyzerPerField.put("table_id", idAnalyzer);                     // valori esatti
        analyzerPerField.put("body", bodyAnalyzer);                       // valori precisi
        analyzerPerField.put("caption", textAnalyzer);                    // testo descrittivo
        analyzerPerField.put("mentions", textAnalyzer);                   // paragrafi che citano la tabella
        analyzerPerField.put("context_paragraphs", textAnalyzer);         // paragrafi di contesto

        Analyzer analyzer = new PerFieldAnalyzerWrapper(textAnalyzer, analyzerPerField);

        TableIndexer indexer = new TableIndexer(indexPath, analyzer);
        indexer.indexFolder(jsonFolder);
        indexer.close();

        System.out.println("Indicizzazione completata nella cartella 'index_table'!");
    }
}
