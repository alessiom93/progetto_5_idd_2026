package scraper;

public class Main {
    public static void main(String[] args) throws Exception {

        String searchQuery = "cancer risk AND coffee consumption";
        PubMedScraper scraper = new PubMedScraper();
        scraper.scrapeOpenAccess(searchQuery, 500);
        System.out.println("DONE.");
    }
}
