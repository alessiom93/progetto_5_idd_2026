package scraper;
// ROME per il parsing dei feed RSS/Atom
import com.rometools.rome.feed.synd.SyndEntry; // Singolo articolo del feed
import com.rometools.rome.feed.synd.SyndFeed;  // Feed completo
import com.rometools.rome.io.SyndFeedInput;    // Parser del feed
import com.rometools.rome.io.XmlReader;        // Lettore XML
// OkHttp per le richieste HTTP
import okhttp3.OkHttpClient;  // Client HTTP
import okhttp3.Request;       // Richiesta HTTP
import okhttp3.Response;      // Risposta HTTP
// Java standard libraries
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class ArxivScraper {

    // ===== COSTANTI DI CONFIGURAZIONE PER LE RICHIESTE API =====
    // Endpoint principale dell'API query di arXiv per ricerche
    private static final String API_URL = "http://export.arxiv.org/api/query";
    // Endpoint di ar5iv.org per recuperare le versioni HTML dei paper (conversione da TeX a HTML)
    private static final String API_URL_HTML = "https://ar5iv.org/html/";
    // Indice di partenza per i risultati paginati
    private static final int START = 0;
    // Numero massimo di risultati per pagina (aumentare per più velocità, diminuire per meno carico di rete)
    private static final int MAX_RESULTS = 100;
    // Directory di output dove salvare i file HTML scaricati
    private static final String OUTPUT_BASE = "corpus-html";
    // Client HTTP condiviso per tutte le richieste (pool di connessioni, timeout gestiti automaticamente)
    private static final OkHttpClient client = new OkHttpClient();

    // ===== METODO PRINCIPALE: SCRAPING DI ARTICOLI DALL'API ARXIV =====
    // Scarica gli articoli da arXiv in base alla query di ricerca, pagina per pagina
    // Converti ogni articolo a HTML usando ar5iv.org e salva localmente
    public void scrapeGroup(String searchQuery) throws Exception {
        // ===== 1. ENCODING DELLA QUERY PER L'URL =====
        // Codifica la query di ricerca per essere sicura per l'uso in URL (spazi come %20, caratteri speciali, ecc.)
        String encoded = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8.name());
        // Inizializza l'indice di partenza per la paginazione
        int start = START;
        // Log della ricerca in corso
        System.out.println("Querying arXiv for: " + searchQuery);

        // ===== 2. LOOP DI PAGINAZIONE: SCARICA TUTTE LE PAGINE =====
        // Continua a scaricare finché ci sono risultati (il break avviene quando il feed è vuoto)
        while (true) {
            // Costruisce l'URL completo con parametri di ricerca, indice di partenza e dimensione di pagina
            String url = API_URL + "?search_query=" + encoded + "&start=" + start + "&max_results=" + MAX_RESULTS;
            System.out.println(url);

            // ===== 3. RICHIESTA API: FETCH DEL FEED ATOM =====
            // Crea una richiesta GET HTTP per il feed XML/Atom
            Request feedRequest = new Request.Builder().url(url).build();
            // Esegue la richiesta in try-with-resources per garantire la chiusura della connessione
            try (Response feedResponse = client.newCall(feedRequest).execute()) {
                // Verifica se la risposta HTTP è di successo e contiene dati
                if (!feedResponse.isSuccessful() || feedResponse.body() == null) {
                    System.out.println("Errore nella richiesta del feed: " + (feedResponse != null ? feedResponse.code() : "nessuna risposta"));
                    // Se l'API non risponde correttamente, esce dal loop
                    return;
                }

                // Estrae il corpo della risposta come stringa (feed XML/Atom)
                String feedContent = feedResponse.body().string();
                // Controlla se il feed è vuoto (nessun risultato trovato)
                if (feedContent.trim().isEmpty()) {
                    System.out.println("Feed vuoto, nessun articolo trovato.");
                    // Se il feed è vuoto, significa che non ci sono più articoli, esce
                    return;
                }

                // ===== 4. PARSING DEL FEED ATOM =====
                // Parsa il contenuto XML/Atom usando ROME (feed parser library)
                SyndFeed feed = new SyndFeedInput().build(
                    new XmlReader(new ByteArrayInputStream(feedContent.getBytes(StandardCharsets.UTF_8)))
                );

                // Verifica se il feed contiene articoli
                if (feed.getEntries() == null || feed.getEntries().isEmpty()) {
                    System.out.println("Nessun altro articolo trovato.");
                    // Se non ci sono più articoli in questa pagina, termina la paginazione
                    return;
                }

                // ===== 5. PREPARAZIONE DELLA DIRECTORY DI OUTPUT =====
                // Crea la directory di output se non esiste
                File outDir = new File(OUTPUT_BASE);
                if (!outDir.exists()) outDir.mkdirs();

                // ===== 6. ITERAZIONE SU OGNI ARTICOLO NEL FEED =====
                // Per ogni articolo nel feed della pagina corrente
                for (SyndEntry entry : feed.getEntries()) {
                    // Estrae l'ID univoco di arXiv dall'URI (es: "2301.12345v1")
                    String arxivId = entry.getUri().replace("http://arxiv.org/abs/", "");
                    // Costruisce l'URL di ar5iv per ottenere la versione HTML dell'articolo
                    String htmlUrl = API_URL_HTML + arxivId;
                    // Log del download in corso
                    System.out.println("Downloading: " + arxivId + " - " + entry.getTitle());

                    // ===== 7. DOWNLOAD DELLA VERSIONE HTML DELL'ARTICOLO =====
                    // Crea una richiesta GET HTTP per scaricare l'HTML da ar5iv.org
                    Request htmlRequest = new Request.Builder().url(htmlUrl).build();
                    // Esegue la richiesta in try-with-resources
                    try (Response htmlResponse = client.newCall(htmlRequest).execute()) {
                        // Verifica se la risposta è di successo e contiene dati
                        if (!htmlResponse.isSuccessful() || htmlResponse.body() == null) {
                            System.out.println("HTML non disponibile per " + arxivId);
                            // Se l'HTML non è disponibile, passa al prossimo articolo
                            continue;
                        }

                        // Estrae il contenuto HTML
                        String html = htmlResponse.body().string();
                        // Rende l'ID sicuro per il file system (sostituisce "/" con "_")
                        String safeId = arxivId.replace("/", "_");
                        // Crea il path del file di output
                        File output = new File(outDir, safeId + ".html");
                        
                        // ===== 8. SALVATAGGIO DEL FILE HTML =====
                        // Scrive il contenuto HTML nel file usando FileWriter in try-with-resources
                        try (FileWriter fw = new FileWriter(output)) {
                            fw.write(html);
                        }
                        // Log del completamento
                        System.out.println("Saved: " + output.getAbsolutePath());
                    }
                }
            }

            // ===== 9. PASSAGGIO ALLA PAGINA SUCCESSIVA =====
            // Incrementa l'indice di partenza per la paginazione
            start += MAX_RESULTS;
        }
    }
}   