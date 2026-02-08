package indexer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import extractor.FigureExtractor;

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

public class FigureIndexer {

    private final IndexWriter writer;

    public FigureIndexer(String indexPath, Analyzer analyzer) throws IOException {
        FSDirectory dir = FSDirectory.open(Path.of(indexPath));
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        this.writer = new IndexWriter(dir, config);
    }

    public void indexFigureJson(File jsonFile) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // Legge una lista di figure dal JSON
        List<FigureExtractor.ExtractedFigure> figures = mapper.readValue(
                jsonFile,
                new TypeReference<List<FigureExtractor.ExtractedFigure>>() {}
        );

        for (FigureExtractor.ExtractedFigure fig : figures) {
            Document doc = new Document();

            doc.add(new TextField("paper_id", fig.paperId, Field.Store.YES));
            doc.add(new TextField("figure_id", fig.figureId, Field.Store.YES));
            doc.add(new TextField("url", fig.url, Field.Store.YES));
            doc.add(new TextField("caption", fig.caption, Field.Store.YES));
            doc.add(new TextField("mentions", String.join("\n", fig.mentions), Field.Store.YES));

            writer.addDocument(doc);
        }
    }

    public void indexFolder(File folder) throws Exception {
        File[] jsonFiles = folder.listFiles((dir, name) -> name.endsWith(".json"));
        if (jsonFiles == null) return;

        for (File f : jsonFiles) {
            System.out.println("➜ Indicizzazione figure: " + f.getName());
            indexFigureJson(f);
        }
    }

    public void close() throws IOException {
        writer.close();
    }

    // ---------------------------------------------------------------------
    // MAIN STANDALONE
    // ---------------------------------------------------------------------
    public static void main(String[] args) throws Exception {
        File jsonFolder = new File("figures");
        String indexPath = "index_figure";

        if (!jsonFolder.exists()) {
            System.err.println("ERRORE: Cartella 'figures' non trovata.");
            return;
        }

        // Analyzer neutro per campi ID
        Analyzer idAnalyzer = CustomAnalyzer.builder()
                .withTokenizer(WhitespaceTokenizerFactory.class)
                .build();

        // Analyzer descrittivi
        Analyzer textAnalyzer = new EnglishAnalyzer();

        Map<String, Analyzer> analyzerPerField = new HashMap<>();
        analyzerPerField.put("paper_id", idAnalyzer);
        analyzerPerField.put("figure_id", idAnalyzer);
        analyzerPerField.put("url", idAnalyzer);       // URL non deve essere tokenizzato
        analyzerPerField.put("caption", textAnalyzer);
        analyzerPerField.put("mentions", textAnalyzer);

        Analyzer analyzer = new PerFieldAnalyzerWrapper(textAnalyzer, analyzerPerField);

        FigureIndexer indexer = new FigureIndexer(indexPath, analyzer);
        indexer.indexFolder(jsonFolder);
        indexer.close();

        System.out.println("Indicizzazione completata nella cartella 'index_figure'!");
    }
}
