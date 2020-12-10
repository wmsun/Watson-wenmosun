package CSC583;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.similarities.TFIDFSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class Parser {

    IndexReader luceneIndex;
    TFIDFSimilarity similarity = new ClassicSimilarity();
    Similarity sim = similarity;

    public Parser() {

    }

    public void parse() {
        try {
            StandardAnalyzer analyzer = new StandardAnalyzer();
            Directory index = FSDirectory.open(Paths.get(Watson.luceneOutputDir));

            IndexWriterConfig config = new IndexWriterConfig(analyzer);

            IndexWriter w = new IndexWriter(index, config);
            System.out.println("test");

            //Locate wikipedia pages
            File folder = new File(Watson.wikipediaFilesPath);
            if (folder == null || folder.listFiles() == null) {
                //Then we don't have any files to parse
                return;
            }


            String currentTitle = "";
            String documentText = ""; // <- Lemmenized already
            for (File file : folder.listFiles()) {
                if (!file.getName().substring(0, 15).equals("enwiki-20140602"))
                    continue; //Then this file isn't named correctly, skip
                //Then we have a valid file name
                System.out.println(file.getName());
                Scanner fileReader = new Scanner(file);
                while (fileReader.hasNext()) {
                    String thisLine = fileReader.nextLine().trim();
                    if (isTitle(thisLine)) {
                        thisLine = thisLine.substring(2, thisLine.length() - 2); // Lop off brackets
                        //Wrap up previous document
                        if (!currentTitle.equals("")) {
                            Document thisDoc = new Document();
                            thisDoc.add(new StringField("docid", currentTitle, Field.Store.YES));
                            thisDoc.add(new TextField("text", documentText, Field.Store.YES));
                            w.addDocument(thisDoc);
                        }
                        //Now that we've stored the previous document, store this new title
                        //and reset our document text string
                        currentTitle = thisLine;
                        documentText = "";
                    } else {
                        //Then it's body text
                        //Lemmenize it first
                        thisLine = Lemmenizer.lemmenizeText(thisLine);
                        //now add it to our string
                        documentText = documentText + " " + thisLine;
                        documentText = documentText.trim();
                    }
                }
            }
            w.commit();

            luceneIndex = DirectoryReader.open(index);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public IndexReader getIndex() {
        if (this.luceneIndex == null) {
            System.err.println("Error");
        }
        return this.luceneIndex;
    }

    public boolean isTitle(String docLine) {
        if (docLine.length() < 4)
            return false;
        if (docLine.substring(0, 2).equals("[[") && docLine.substring(docLine.length() - 2).equals("]]")) {
            return (docLine.indexOf("|") == -1);
        }
        return false;
    }

    //This is used when we read in an index instead of parsing
    public void setLuceneIndex(IndexReader index) {
        this.luceneIndex = index;
    }

    public HashMap<String, ArrayList<ScoreDoc>> score(HashMap<String, String> lemmenizedQueries) {
        boolean TFIDF = false;

        HashMap<String, ArrayList<ScoreDoc>> scores = new HashMap<String, ArrayList<ScoreDoc>>();
        if (this.luceneIndex == null) {
            System.err.println("Error: parse() must be called before score(), or an index must be given");
            return scores;
        }
        for (String originalQuery : lemmenizedQueries.keySet()) {
            String lemmenizedQuery = lemmenizedQueries.get(originalQuery);

            //build query object
            Query q = null;

            try {
                StandardAnalyzer analyzer = new StandardAnalyzer();
                q = new QueryParser("text", analyzer).parse(lemmenizedQuery);
            } catch (ParseException e) {
                System.err.println(e.getMessage());
                return scores;
            }

            ArrayList<ScoreDoc> documents = new ArrayList<ScoreDoc>();

            int hitsPerPage = 10; //We only want the 10 best results
            IndexSearcher searcher = new IndexSearcher(this.luceneIndex);
            if (TFIDF) {
                searcher.setSimilarity(sim);
            }
            try {
                TopDocs docs = searcher.search(q, hitsPerPage);
                org.apache.lucene.search.ScoreDoc[] hits = docs.scoreDocs;

                for (int i = 0; i < hits.length; ++i) {
                    int docId = hits[i].doc;
                    Document d = searcher.doc(docId);
                    System.out.println(d.get("docid") + "\t" + hits[i].score);
                    ScoreDoc thisResult = new ScoreDoc(d.get("docid"), hits[i].score);
                    documents.add(thisResult);
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Error scoring the query '" + originalQuery + "', skipping.");
                continue;
            }
            //sort documents?
            scores.put(lemmenizedQuery, documents);
        }
        return scores;
    }

}
