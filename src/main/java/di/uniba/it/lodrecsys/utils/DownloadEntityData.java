package di.uniba.it.lodrecsys.utils;

import de.l3s.boilerpipe.BoilerpipeProcessingException;
import de.l3s.boilerpipe.extractors.ArticleExtractor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import di.uniba.it.lodrecsys.query.SimpleQueryInRelation;
import di.uniba.it.lodrecsys.query.SimpleQueryOutRelation;
import di.uniba.it.lodrecsys.query.SimpleResult;

import di.uniba.it.lodrecsys.utils.mapping.SPARQLClient;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Version;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * @author pierpaolo
 */
public class DownloadEntityData {

    private static final Logger logger = Logger.getLogger(DownloadEntityData.class.getName());

    private static final String PREDICATE_WIKIPAGE = "http://xmlns.com/foaf/0.1/isPrimaryTopicOf";

    private static final String PREDICATE_ABSTRACT = "http://dbpedia.org/ontology/abstract";

    private static final int MAX_ATTEMPT = 3;


    private void download(String startFile, String outputDir) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(startFile));

        int numPages = 1;

        SPARQLClient client = new SPARQLClient();
        while (reader.ready()) {
            if (numPages % 50 == 0) {
                //dirty solution to limit request to wikipedia
                logger.info("Waiting for 30 seconds...");
                myWait(30);
            }

            String line = reader.readLine();
            String[] lineValues = line.split("\t");
            logger.log(Level.INFO, "Processing {0}", lineValues[0]);
            logger.log(Level.INFO, "Make dir");
            String bookDirname = outputDir + "/" + lineValues[0];
            new File(bookDirname).mkdir();
            String wikiURI = client.getWikipediaURI(lineValues[2]);
            String text = null;

            if (wikiURI != null) {
                numPages++;
                int t = 0;
                while (text == null && t < MAX_ATTEMPT) {

                    try {
                        text = ArticleExtractor.getInstance().getText(new URL(wikiURI));
                        if (text != null) {
                            text = text.replace("[ edit ]", "");
                        }
                    } catch (BoilerpipeProcessingException ex) {
                        Logger.getLogger(DownloadEntityData.class.getName()).log(Level.WARNING, "Error to extract text", ex);
                        logger.warning("Re-try downloading...");
                        myWait(15);
                        t++;
                    }
                }
                if (text == null) {
                    logger.log(Level.WARNING, "No text for {0}", lineValues[0]);
                    text = "";
                }
            } else {
                text = client.getResourceAbstract(lineValues[2]);
            }

            if (text == null) {
                text = "";
            }


            FileWriter writer = new FileWriter(bookDirname + "/" + lineValues[0] + ".text");
            writer.write(text);
            writer.close();

        }
        reader.close();
    }

    public static void main(String[] args) {
        if (args.length == 2) {
            try {
                DownloadEntityData dd = new DownloadEntityData();
                dd.download(args[0], args[1]);
            } catch (IOException ex) {
                Logger.getLogger(DownloadEntityData.class.getName()).log(Level.SEVERE, "Error in downloading data", ex);
            }
        } else {
            logger.log(Level.WARNING, "Number of arguments not valid {0}", args.length);
            System.exit(1);
        }
    }

    private void myWait(int sec) {
        //wait
        logger.info("Ronf...");
        long cm = System.currentTimeMillis();
        while ((System.currentTimeMillis() - cm) < sec * 1000) {

        }
        logger.info("Wake-up!");
    }
}
