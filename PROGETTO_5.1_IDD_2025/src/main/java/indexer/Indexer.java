package indexer;

import org.apache.lucene.analysis.Analyzer; // analisi del testo (tokenizzazione, stemming, ecc.)
// import org.apache.lucene.analysis.standard.StandardAnalyzer; // analyzer standard di Lucene per l'inglese
import org.apache.lucene.analysis.en.EnglishAnalyzer; // analyzer per la lingua inglese
import org.apache.lucene.analysis.custom.CustomAnalyzer; // analyzer custom per il filename
import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory; // tokenizzatore che usa gli spazi bianchi
import org.apache.lucene.analysis.core.LowerCaseFilterFactory; // filtro per convertire in minuscolo
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilterFactory; // filtro per dividere le parole
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper; // analyzer per campo specifico
import org.apache.lucene.document.*; // per creare documenti e campi
import org.apache.lucene.index.*; // per l'indicizzazione
import org.apache.lucene.store.*; // per gestire l'archiviazione dell'indice
import org.jsoup.Jsoup; // per il parsing HTML
import org.jsoup.nodes.Element; // per manipolare elementi HTML

import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Indexer {

    public static void main(String[] args) {
        // path dei documenti da indicizzare
        Path docsDir = Paths.get("corpus-html");
        // path dell'indice
        Path indexDir = Paths.get("index");

        try {
            // timer per calcolare il tempo di indicizzazione
            long startTime = System.currentTimeMillis();
            // analyzer per l'analisi del testo
            // si applica in automatico ai campi di tipo TextField, ignora i campi di tipo StringField
            // (tokenizzazione, rimozione stop words, stemming e normalizzazione)
            // Analyzer analyzerIT = new ItalianAnalyzer();
            Analyzer analyzerEN = new EnglishAnalyzer();
            // analyzer custom meno invadenti
            Analyzer analyzerTitle = CustomAnalyzer.builder()
                    .withTokenizer(WhitespaceTokenizerFactory.class) // tokenizza usando gli spazi bianchi
                    .addTokenFilter(LowerCaseFilterFactory.class) // converte tutto in minuscolo
                    .addTokenFilter(WordDelimiterGraphFilterFactory.class) // divide le parole basandosi su maiuscole, numeri, simboli, ecc.
                    .build();
            Analyzer analyzerAuthors = CustomAnalyzer.builder()
                    .withTokenizer(WhitespaceTokenizerFactory.class) // tokenizza usando gli spazi bianchi
                    .addTokenFilter(LowerCaseFilterFactory.class) // converte tutto in minuscolo
                    .build();
            // creazione di un analyzer in base al Field da indicizzare
            Map<String, Analyzer> analyzerPerField = new HashMap<>();
            // analyzerPerField.put("filename", analyzerFilename); // usa l'analyzer custom per il campo "filename"
            analyzerPerField.put("title", analyzerTitle);
            analyzerPerField.put("authors", analyzerAuthors);
            analyzerPerField.put("abstract", analyzerEN);
            analyzerPerField.put("fullText", analyzerEN);
            // per Field non specificati usa l'analyzer inglese di default
            Analyzer analyzer = new PerFieldAnalyzerWrapper(analyzerEN, analyzerPerField);
            // apertura della directory per l'indice
            Directory directory = FSDirectory.open(indexDir);
            // configurazione dell'IndexWriter con l'analyzer scelto
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            // per debug, legge l'indice in formato testuale leggibile (non funziona retroattivamente), di default è il formato binario
            // config.setCodec(new SimpleTextCodec());
            // creazione dell'IndexWriter che gestisce l'indicizzazione
            IndexWriter writer = new IndexWriter(directory, config);

            // Scansione dei file .html
            // genera uno stream per tutti i file con estensione .html nella directory docsDir
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(docsDir, "*.html")) {
                // per ogni file trovato
                for (Path file : stream) {
                    // legge il contenuto del file come stringa UTF-8
                    String htmlContent = Files.readString(file, StandardCharsets.ISO_8859_1);
                    // ricavo i campi dal contenuto HTML
                    org.jsoup.nodes.Document htmlDoc = Jsoup.parse(htmlContent);
                    // salta i file corrotti
                    String bodyText = htmlDoc.body().text();
                    if (bodyText.contains("Conversion to HTML had a Fatal error")) {
                        System.out.println("ATTENZIONE: file HTML troncato o danneggiato: " + file.getFileName());
                        // Passa oltre senza tentare il parsing
                        continue;
                    }

                    // rimuove gli script e gli stili
                    htmlDoc.select("script, style").remove();
                    // estraggo titolo
                    String title = extractTitle(htmlDoc);
                    System.out.println("TITOLO INDICIZZATO: " + title);
                    // estraggo autori
                    String authors = extractAuthors(htmlDoc);
                    System.out.println("AUTORI INDICIZZATI: " + authors);
                    // estraggo la data
                    String date = extractDate(htmlDoc);
                    System.out.println("DATA INDICIZZATA: " + date);
                    // estraggo abstract
                    String abstractText = cleanText(htmlDoc.select("div.ltx_abstract").text());
                    // fallback PMC - cerca anche section.abstract
                    if (abstractText.isEmpty()) {
                        abstractText = cleanText(htmlDoc.select("section.abstract").text());
                    }
                    System.out.println("ABSTRACT INDICIZZATO: " + abstractText.substring(0, Math.min(100, abstractText.length())) + "...");
                    // estraggo il corpo del testo
                    String fullText = cleanText(htmlDoc.text()); // testo completo
                    System.out.println("CORPO DEL TESTO INDICIZZATO: " + fullText.substring(0, Math.min(100, fullText.length())) + "...");
                    // crea un nuovo documento Lucene
                    Document doc = new Document();
                    // Field.Store.YES indica che il valore deve poter essere recuperato dalla ricerca
                    // aggiungo i field, se sono Stringfield non vengono analizzati, se sono TextField vengono analizzati
                    doc.add(new StringField("filename", file.getFileName().toString(), Field.Store.YES));
                    doc.add(new TextField("title", title, Field.Store.YES));
                    doc.add(new TextField("authors", authors, Field.Store.YES));
                    doc.add(new StringField("date", date, Field.Store.YES));
                    doc.add(new TextField("abstract", abstractText, Field.Store.YES));
                    doc.add(new TextField("fullText", fullText, Field.Store.YES));
                    // aggiorna (delete + add) il documento nell'indice (se esiste già, altrimenti lo aggiunge come nuovo documento)
                    // uso il nome del file come termine di identificazione univoco
                    writer.updateDocument(new Term("filename", file.getFileName().toString()), doc);
                    System.out.println("Indicizzato: " + file.getFileName());
                }
            }
            // chiude l'IndexWriter, fa implicitamente il commit delle modifiche
            writer.close();
            Long endTime = System.currentTimeMillis();
            Long elapsedTime = endTime - startTime;
            System.out.println("\nIndicizzazione completata con successo.");
            System.out.println("Tempo impiegato: " + elapsedTime + " ms");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
  
    // Estrae il titolo dal documento HTML
    private static String extractTitle(org.jsoup.nodes.Document htmlDoc) {
      String title = htmlDoc.title().trim();
      if (title.isEmpty()) {
          // fallback per pagine arXiv
          Element h1Title = htmlDoc.selectFirst("h1.title");
          if (h1Title != null) {
              title = h1Title.text().replaceFirst("Title:\\s*", "").trim();
          }
      }
      return title;
    }

    // Estrae gli autori dal documento HTML
    private static String extractAuthors(org.jsoup.nodes.Document htmlDoc) {
      List<String> authorsList = new ArrayList<>();
      for (Element a : htmlDoc.select("div.authors a")) {
          String name = normalizeAuthorName(a.text());
          if (!name.isEmpty()) authorsList.add(name);
      }

      // fallback LTX
      if (authorsList.isEmpty()) {
          for (Element authorElement : htmlDoc.select("span.ltx_creator.ltx_role_author")) {
              Element nameElement = authorElement.selectFirst("span.ltx_personname");
              if (nameElement != null) {
                  String name = normalizeAuthorName(nameElement.ownText());
                  if (!name.isEmpty()) authorsList.add(name);
              }
          }
      }
      
      // fallback PMC - estrae autori dai meta tag citation_author
      if (authorsList.isEmpty()) {
          for (Element metaTag : htmlDoc.select("meta[name=citation_author]")) {
              String name = normalizeAuthorName(metaTag.attr("content"));
              if (!name.isEmpty()) authorsList.add(name);
          }
      }
      
      // Rimuove eventuali "and"
      authorsList.replaceAll(n -> n.replaceAll("\\band\\b", "").trim());
      // **UNISCE CON SPAZIO, NON VIRGOLE**
      String authors = String.join(" ", authorsList);
      return authors;
    }

    // Normalizza nomi autori rimuovendo artefatti comuni
    private static String normalizeAuthorName(String s) {
        if (s == null) return "";
        // Unicode + rimuove artefatti LaTeX e HTML
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC);
        s = s.replaceAll("\\\\?[{}^_~]+", " "); // LaTeX
        s = s.replaceAll("\\?+", "");          // artefatti
        s = s.replaceAll("\\b\\d+\\b", "");    // numeri isolati
        s = s.replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", ""); // email
        // rimuove punti isolati che non fanno parte delle iniziali
        s = s.replaceAll("\\s+\\.\\s+", " "); 
        // rimuove virgole e spazi strani ma lascia punti nelle iniziali
        s = s.replaceAll(",", " ").replaceAll("\\s+", " ").trim();
        return s;
    }

    // Estrae la data dal documento HTML
    private static String extractDate(org.jsoup.nodes.Document htmlDoc) {
        Element dateElement = htmlDoc.selectFirst(
            "div.ltx_dates, div.dateline, div.submission-history, span.submission-date"
        );
        if (dateElement == null) return "";
        String text = dateElement.text().trim();
        // cerca pattern come "Submitted on 14 Feb 2016"
        if (text.matches("(?i).*submitted on \\d{1,2} \\w+ \\d{4}.*")) {
            text = text.replaceAll("(?i)submitted on ", "").trim();
        } 
        // cerca pattern con solo anno (es. 2018)
        else if (text.matches(".*\\d{4}.*")) {
            text = text.replaceAll("[^\\dA-Za-z ]", "").trim();
        } 
        // fallback
        else {
            text = "";
        }
        // rimuove eventuali simboli strani residui
        if (text == null) return "";
        // rimuove tutti i simboli non alfanumerici tranne spazi
        text = text.replaceAll("[^\\p{L}\\p{Nd} ]+", "");
        // comprime spazi multipli
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }

    private static String cleanText(String text) {
        if (text == null) return "";
        // rimuove tutti i simboli non alfanumerici tranne spazi
        text = text.replaceAll("[^\\p{L}\\p{Nd} ]+", "");
        // rimuove caratteri di controllo, parentesi, simboli LaTeX non standard
        text = text.replaceAll("[\\p{Cntrl}{}^~]", " ").replaceAll("\\s+", " ").trim();
        // comprime spazi multipli
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }
}