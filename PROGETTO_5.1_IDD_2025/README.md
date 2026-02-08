### PROGETTO ###
Questo è il progetto 5.1 per l'esame di Ingegneria dei dati anno 2025/2026.

### OBIETTIVO ###
L'obiettivo del progetto è sviluppare un sistema avanzato di search su articoli scientifici di PubMed, in cui le tabelle siano trattate come oggetti di prima classe e completamente indicizzabili.

### STRUTTURA PROGETTO ###
La cartella corpus-html contiene gli html degli articoli scaricati da PubMed Central.<br>
La cartella index contiene l'indice dopo il processo di indicizzazione.<br>
La cartella src/main/java/scraper contiene 2 file:
- PubMedScraper.java: contiene lo scraper per recuperare tutti gli html degli articoli open access da PubMed Central che fanno match con la query e li salva in corpus-html.
- Main.java: è il main che esegue lo scraper.<br>
<br>
La cartella src/main/java/indexer contiene 3 file:
- Indexer.java: genera l'indice degli html contenuti in corpus-html, e lo salva in index.
- TableIndexer.java: genera l'indice delle tabelle.
- FigureIndexer.java: genera l'indice delle figure.<br>
<br>
La cartella src/main/java/extractor contiene 2 file:
- TableExtractor.java: estrae le tabelle dagli html.
- FigureExtractor.java: estrae le figure dagli html.<br>
<br>
La cartella src/main/java/searcher contiene 6 file:
- Searcher.java: permette la ricerca da cli degli html utilizzando l'indice.
- WebSearcher.java: permette la ricerca da pagina web (http://localhost:3000/search) degli html utilizzando l'indice.
- TableSearcher.java: ricerca tabelle da cli.
- WebTableSearcher.java: ricerca tabelle da web (http://localhost:3001/search).
- FigureSearcher.java: ricerca figure da cli.
- WebFigureSearcher.java: ricerca figure da web (http://localhost:3002/search).<br>
<br>
Il file pom.xml contiene le dipendenze.<br>
Il file readme.md è questo che stai leggendo.

### COMANDI ###
# Compile #
mvn compile<br>
mvn clean compile -U

# DOCUMENTI #
# Scraper #
mvn exec:java -Dexec.mainClass="scraper.Main"
# Indexer - html #
mvn exec:java -Dexec.mainClass="indexer.Indexer"
# Searcher - html #
mvn exec:java -Dexec.mainClass="searcher.Searcher"
# Searcher web - html #
mvn exec:java -Dexec.mainClass="searcher.WebSearcher"

# TABELLE #
# Extractor - table #
mvn exec:java -Dexec.mainClass="extractor.TableExtractor"
# Indexer - table #
mvn exec:java -Dexec.mainClass="indexer.TableIndexer"
# Searcher - table #
mvn exec:java -Dexec.mainClass="searcher.TableSearcher"
# Searcher web - table #
mvn exec:java -Dexec.mainClass="searcher.WebTableSearcher"

# FIGURE #
# Extractor - figure #
mvn exec:java -Dexec.mainClass="extractor.FigureExtractor"
# Indexer - figure #
mvn exec:java -Dexec.mainClass="indexer.FigureIndexer"
# Searcher - figure #
mvn exec:java -Dexec.mainClass="searcher.FigureSearcher"
# Searcher web - figure #
mvn exec:java -Dexec.mainClass="searcher.WebFigureSearcher"
