package scraper;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PubMedScraper {

    private static final String ESEARCH_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi";
    private static final String PMC_HTML_URL = "https://www.ncbi.nlm.nih.gov/pmc/articles/";
    private static final String OUTPUT_BASE = "corpus-html";
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public void scrapeOpenAccess(String searchQuery, int maxArticles) throws Exception {
        System.out.println("=== PUBMED SCRAPER ===");
        System.out.println("Query: " + searchQuery);
        System.out.println("Max articoli: " + maxArticles);
        
        File outDir = new File(OUTPUT_BASE);
        if (!outDir.exists()) outDir.mkdirs();

        List<String> pmcIds = searchPMC(searchQuery, maxArticles);
        System.out.println("\nTrovati " + pmcIds.size() + " articoli open access.");
        
        int downloaded = 0;
        for (String pmcId : pmcIds) {
            if (downloadArticle(pmcId, outDir)) {
                downloaded++;
            }
            // Pausa tra richieste per rispettare i limiti dell'API NCBI (max 3 req/sec)
            Thread.sleep(350);
        }
        
        System.out.println("\n=== COMPLETATO ===");
        System.out.println("Articoli scaricati: " + downloaded + "/" + pmcIds.size());
    }

    private List<String> searchPMC(String query, int maxResults) throws Exception {
        List<String> pmcIds = new ArrayList<>();
        int retMax = 500; // Massimo per richiesta
        int retStart = 0;

        while (pmcIds.size() < maxResults) {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = ESEARCH_URL + 
                "?db=pmc" +
                "&term=" + encoded + 
                "+AND+open+access[filter]" +
                "&retmax=" + retMax +
                "&retstart=" + retStart +
                "&retmode=xml";

            System.out.println("\nCerca PMC: " + searchUrl);
            
            Request request = new Request.Builder().url(searchUrl).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    System.out.println("Errore nella ricerca: " + response.code());
                    break;
                }

                String xml = response.body().string();
                Document doc = Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser());
                
                Elements ids = doc.select("Id");
                if (ids.isEmpty()) {
                    System.out.println("Nessun altro risultato.");
                    break;
                }

                for (Element idEl : ids) {
                    String pmcId = "PMC" + idEl.text();
                    pmcIds.add(pmcId);
                    if (pmcIds.size() >= maxResults) break;
                }
                
                System.out.println("IDs trovati finora: " + pmcIds.size());
                retStart += retMax;
            }
            
            Thread.sleep(350); // Rispetta rate limit API
        }

        return pmcIds;
    }

    private boolean downloadArticle(String pmcId, File outDir) {
        try {
            String htmlUrl = PMC_HTML_URL + pmcId + "/";
            System.out.println("Download: " + pmcId);

            Request request = new Request.Builder().url(htmlUrl).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    System.out.println("  HTML non disponibile per " + pmcId);
                    return false;
                }

                String html = response.body().string();
                File output = new File(outDir, pmcId + ".html");
                
                try (FileWriter fw = new FileWriter(output)) {
                    fw.write(html);
                }
                
                System.out.println("  Salvato: " + output.getName());
                return true;
            }
        } catch (Exception e) {
            System.out.println("  Errore su " + pmcId + ": " + e.getMessage());
            return false;
        }
    }
}
