package scraper;

// Classe principale per avviare lo scraper di arXiv
public class Main {
    // Metodo di ingresso principale che esegue lo scaricamento degli articoli
    public static void main(String[] args) throws Exception {
        // Definisce la query di ricerca per arXiv
        // Cerca articoli con "Query processing" o "Query optimization" nel titolo (ti) o abstract (abs)
        String searchQuery = "ti:\"Query processing\" OR abs:\"Query processing\" OR ti:\"Query optimization\" OR abs:\"Query optimization\"";
        
        // Istanzia lo scraper di arXiv
        ArxivScraper scraper = new ArxivScraper();
        
        // Avvia il download degli articoli che soddisfano la query
        // I file HTML scaricati verranno salvati nella directory 'corpus-html'
        scraper.scrapeGroup(searchQuery);
        
        // Stampa messaggio di completamento
        System.out.println("DONE.");
    }
}

