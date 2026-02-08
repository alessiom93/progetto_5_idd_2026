package indexer;

// Jackson per la lettura di JSON
import com.fasterxml.jackson.core.type.TypeReference; // per deserializzare JSON generici
import com.fasterxml.jackson.databind.ObjectMapper; // per convertire JSON in oggetti Java
import extractor.FigureExtractor; // importa la classe che contiene la struttura ExtractedFigure

// Apache Lucene per l'indicizzazione e la ricerca testuale
import org.apache.lucene.analysis.Analyzer; // analizzatore base per il testo
import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory; // tokenizzatore che usa spazi
import org.apache.lucene.analysis.custom.CustomAnalyzer; // per creare analyzer personalizzati
import org.apache.lucene.analysis.en.EnglishAnalyzer; // analyzer specializzato per inglese
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper; // applica analyzer per-campo
import org.apache.lucene.document.*; // per creare documenti e campi dell'indice
import org.apache.lucene.index.*; // per gestire l'indice Lucene
import org.apache.lucene.store.FSDirectory; // per archiviare l'indice su disco

// Java standard libraries
import java.io.File; // per gestire i file del file system
import java.io.IOException; // per gestire le eccezioni di I/O
import java.nio.file.Path; // per rappresentare i percorsi dei file
import java.util.HashMap; // per mappe di analyzer per campo
import java.util.List; // per lavorare con liste generiche
import java.util.Map; // per mappe generiche

// Classe per indicizzare figure estratte da JSON usando Apache Lucene
public class FigureIndexer {

    // Scrittore dell'indice (gestisce i documenti nell'indice Lucene)
    private final IndexWriter writer;

    // Costruttore: inizializza l'IndexWriter con percorso e analyzer
    public FigureIndexer(String indexPath, Analyzer analyzer) throws IOException {
        // Apre la directory del file system per l'indice
        FSDirectory dir = FSDirectory.open(Path.of(indexPath));
        // Configura l'IndexWriter con l'analyzer scelto
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        // Istanzia l'IndexWriter che gestirà i documenti
        this.writer = new IndexWriter(dir, config);
    }

    // Metodo per indicizzare un singolo file JSON di figure
    public void indexFigureJson(File jsonFile) throws Exception {
        // Istanzia ObjectMapper per deserializzare il JSON
        ObjectMapper mapper = new ObjectMapper();

        // Legge una lista di figure dal file JSON
        List<FigureExtractor.ExtractedFigure> figures = mapper.readValue(
                jsonFile,
                // TypeReference specifica che vogliamo una lista di ExtractedFigure
                new TypeReference<List<FigureExtractor.ExtractedFigure>>() {}
        );

        // Itera su ogni figura nel JSON
        for (FigureExtractor.ExtractedFigure fig : figures) {
            // Crea un nuovo documento Lucene
            Document doc = new Document();

            // Aggiunge i campi della figura al documento (TextField: analizzato e ricercabile)
            doc.add(new TextField("paper_id", fig.paperId, Field.Store.YES));
            doc.add(new TextField("figure_id", fig.figureId, Field.Store.YES));
            doc.add(new TextField("url", fig.url, Field.Store.YES));
            doc.add(new TextField("caption", fig.caption, Field.Store.YES));
            // Unisce tutte le menzioni in una stringa separata da newline
            doc.add(new TextField("mentions", String.join("\n", fig.mentions), Field.Store.YES));

            // Aggiunge il documento all'indice
            writer.addDocument(doc);
        }
    }

    // Metodo per indicizzare tutti i file JSON di figure in una cartella
    public void indexFolder(File folder) throws Exception {
        // Ottiene la lista di tutti i file .json nella cartella
        File[] jsonFiles = folder.listFiles((dir, name) -> name.endsWith(".json"));
        // Se la cartella non ha file, esce
        if (jsonFiles == null) return;

        // Itera su ogni file JSON trovato
        for (File f : jsonFiles) {
            // Stampa il file che sta elaborando
            System.out.println("Indicizzazione figure: " + f.getName());
            // Chiama il metodo di indicizzazione per il singolo file
            indexFigureJson(f);
        }
    }

    // Metodo per chiudere l'IndexWriter e completare l'indicizzazione
    public void close() throws IOException {
        // Chiude l'IndexWriter e salva l'indice su disco
        writer.close();
    }

    // MAIN STANDALONE
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
