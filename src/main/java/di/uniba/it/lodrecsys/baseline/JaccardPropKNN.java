package di.uniba.it.lodrecsys.baseline;

import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.hp.hpl.jena.rdf.model.Statement;
import di.uniba.it.lodrecsys.entity.MovieMapping;
import di.uniba.it.lodrecsys.eval.SparsityLevel;
import di.uniba.it.lodrecsys.properties.JaccardSimilarityFunction;
import di.uniba.it.lodrecsys.properties.SimilarityFunction;
import di.uniba.it.lodrecsys.utils.Utils;
import di.uniba.it.lodrecsys.utils.mapping.PropertiesManager;
import org.apache.commons.lang3.text.StrSubstitutor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Created by asuglia on 9/2/14.
 */
public class JaccardPropKNN {
    private static Logger currLogger = Logger.getLogger(JaccardPropKNN.class.getName());
    private PropertiesManager manager;
    private Map<String, Multimap<String, String>> itemsRepresentation;
    private Map<String, Set<SimScore>> itemSimScore;
    private SimilarityFunction simFunction;
    private Map<String, String> idUriMap;


    public static class SimScore implements Comparable<SimScore> {
        private String itemID;
        private Float similarity;

        public SimScore(String itemID, Float similarity) {
            this.itemID = itemID;
            this.similarity = similarity;
        }

        public String getItemID() {
            return itemID;
        }

        public void setItemID(String itemID) {
            this.itemID = itemID;
        }

        public Float getSimilarity() {
            return similarity;
        }

        public void setSimilarity(Float similarity) {
            this.similarity = similarity;
        }

        @Override
        public int compareTo(SimScore o) {
            if (this.similarity == null || o.similarity == null)
                return this.itemID.compareTo(o.itemID);

            return this.similarity != null ? this.similarity.compareTo(o.similarity) : -1;
        }

        @Override
        public String toString() {
            return itemID;
        }
    }

    public JaccardPropKNN(String trainFile, String propIndexDir, List<MovieMapping> mappedItems) throws IOException {
        manager = new PropertiesManager(propIndexDir);
        simFunction = new JaccardSimilarityFunction();
        itemSimScore = new HashMap<>();
        getMapForMappedItems(mappedItems, Utils.loadRatingForEachItem(trainFile).keySet());
        loadItemsRepresentation(manager);
    }

    private void getMapForMappedItems(List<MovieMapping> movieList, Set<String> allItems) {
        // key: item-id - value: dbpedia uri
        idUriMap = new HashMap<>();

        for (String itemID : allItems)
            idUriMap.put(itemID, null);

        for (MovieMapping movie : movieList) {
            idUriMap.put(movie.getItemID(), movie.getDbpediaURI());
        }
    }

    private void computeItemSimilarites() {
        float minSimilarity = simFunction.getMaxValue();
        Set<String> allItems = idUriMap.keySet();
        for (String currItemID : allItems) {
            currLogger.info("Computing similarities for item " + currItemID);
            Set<SimScore> currSimSet = new TreeSet<>();
            Multimap<String, String> currItemVector = itemsRepresentation.get(currItemID);


            for (String otherItemID : allItems) {
                Multimap<String, String> otherItemVector = itemsRepresentation.get(otherItemID);
                Float finalScore = null;

                if (currItemVector != null && otherItemVector != null) {
                    finalScore = simFunction.compute(currItemVector, otherItemVector);
                }

                currSimSet.add(new SimScore(otherItemID, finalScore));
                if (finalScore != null) {
                    // update minimum score similarity
                    if (finalScore < minSimilarity)
                        minSimilarity = finalScore;
                }

            }


            this.itemSimScore.put(currItemID, currSimSet);

            // set missed minimum similarity value for not mapped item
            if (minSimilarity == simFunction.getMaxValue()) // all one or all null for the current user
                minSimilarity = simFunction.getMinValue();

            replaceNullFields(currItemID, minSimilarity);
            minSimilarity = simFunction.getMaxValue();

        }


    }

    private void replaceNullFields(String currUser, Float minValue) {
        Set<SimScore> currItemSim = itemSimScore.get(currUser);
        for (SimScore score : currItemSim) {
            if (score.getSimilarity() == null) {
                score.setSimilarity(minValue);

            }
        }

    }

    private void loadItemsRepresentation(PropertiesManager manager) {
        itemsRepresentation = new HashMap<>();

        for (String itemID : idUriMap.keySet()) {
            String resourceID = idUriMap.get(itemID);
            if (resourceID != null) {
                List<Statement> propList = manager.getResourceProperties(resourceID);
                Multimap<String, String> itemProperties = ArrayListMultimap.create();

                for (Statement stat : propList) {
                    itemProperties.put(stat.getPredicate().toString(), stat.getObject().toString());
                }

                itemsRepresentation.put(itemID, itemProperties);

            }
        }

    }

    private long getMaxID() {
        long maxID = Long.MIN_VALUE;

        for (String itemID : itemSimScore.keySet()) {
            long other = Long.parseLong(itemID);
            if (maxID < other)
                maxID = other;

        }

        return maxID;
    }

    public void serializeSimMatrix(String simMatrixFile) throws IOException {
        StringBuilder builder = new StringBuilder();

        for (String itemID : itemSimScore.keySet()) {
            for (SimScore score : itemSimScore.get(itemID)) {
                builder.append(itemID).append(" ").append(score.getItemID()).append(" ").append(score.getSimilarity()).append("\n");

            }
        }

        BufferedWriter writer = null;

        try {
            writer = new BufferedWriter(new FileWriter(simMatrixFile));
            writer.write(String.valueOf(getMaxID() + 1)); // number of items
            writer.newLine();
            writer.write(builder.toString()); // similarities
        } finally {
            if (writer != null)
                writer.close();
        }

    }


    public void serializeModelFormat(String modelFileName) throws IOException {
        Map<String, String> valuesMap = new HashMap<>();
        valuesMap.put("version", "3.03");
        valuesMap.put("class_name", "MyMediaLite.ItemRecommendation.ItemKNN");
        valuesMap.put("correlation", "Cosine");
        String maxVal = String.valueOf(itemSimScore.keySet().size() + 1);
        valuesMap.put("k", maxVal);

        StringBuilder builder = new StringBuilder();

        for (String itemID : itemSimScore.keySet()) {
            builder.append(Joiner.on(" ").join(Iterables.limit(itemSimScore.get(itemID), 80))).append("\n");
        }

        valuesMap.put("neighbours", builder.toString());

        builder = new StringBuilder();

        for (String itemID : itemSimScore.keySet()) {
            for (SimScore score : itemSimScore.get(itemID)) {
                builder.append(itemID).append(" ").append(score.getItemID()).append(" ").append(score.getSimilarity()).append("\n");

            }
        }

        valuesMap.put("similarities", builder.toString());

        String templateString = "${class_name}\n${version}\n${correlation}\n${k}\n${neighbours}${k}\n${similarities}";
        StrSubstitutor sub = new StrSubstitutor(valuesMap);
        String resolvedString = sub.replace(templateString);

        BufferedWriter writer = null;

        try {
            writer = new BufferedWriter(new FileWriter(modelFileName));
            writer.write(resolvedString);
        } finally {
            if (writer != null)
                writer.close();
        }

    }

    public static void main(String[] args) throws IOException {
        String trainPath = "/home/asuglia/thesis/dataset/ml-100k/definitive",
                simPath = "/home/asuglia/thesis/dataset/ml-100k/results/ItemKNNLod/similarities",
                propertyIndexDir = "/home/asuglia/thesis/content_lodrecsys/movielens/stored_prop",
                mappedItemFile = "mapping/item.mapping";
        List<MovieMapping> mappingList = Utils.loadDBpediaMappedItems(mappedItemFile);

        for (SparsityLevel level : SparsityLevel.values()) {
            for (int i = 1; i <= 5; i++) {
                JaccardPropKNN jaccard = new JaccardPropKNN(trainPath + File.separator + level
                        + File.separator + "u" + i + ".base", propertyIndexDir, mappingList);

                jaccard.computeItemSimilarites();

                jaccard.serializeSimMatrix(simPath + File.separator + level + File.separator + "u" + i + ".sim");

            }

        }


        /*
        * for (int numRec : list_rec_size) {

                if (method.equals(IIRecSys.algorithmName) || method.equals(UURecSys.algorithmName)) {
                    for (int num_neigh : IIRecSys.num_neighbors) {
                        String neigh_options = "\"k=" + num_neigh + "\"";
                        // for each sparsity level
                        for (SparsityLevel level : SparsityLevel.values()) {
                            //for each split (from 1 to 5)
                            String completeResFile = resPath + File.separator + method + File.separator + "neigh_" + num_neigh + File.separator + "given_" + level.toString() + File.separator +
                                    "top_" + numRec + File.separator + "metrics.complete";
                            for (int i = 1; i <= 5; i++) {
                                String trainFile = trainPath + File.separator + "given_" + level.toString() + File.separator +
                                        "u" + i + ".base",
                                        testFile = testPath + File.separator + "u" + i + ".test",
                                        trecTestFile = testTrecPath + File.separator + "u" + i + ".test",
                                        tempResFile = resPath + File.separator + method + File.separator + "neigh_" + num_neigh + File.separator + "given_" + level.toString() + File.separator +
                                                "top_" + numRec + File.separator + "u" + i + ".temp_res",
                                        resFile = resPath + File.separator + method + File.separator + "neigh_" + num_neigh + File.separator + "given_" + level.toString() + File.separator +
                                                "top_" + numRec + File.separator + "u" + i + ".mml_res",
                                        trecResFile = resPath + File.separator + method + File.separator + "neigh_" + num_neigh + File.separator + "given_" + level.toString() + File.separator +
                                                "top_" + numRec + File.separator + "u" + i + ".results";


                                // Executes MyMediaLite tool
                                String mmlString = "item_recommendation --training-file=" + trainFile + " --test-file=" +
                                        testFile + " --prediction-file=" + tempResFile +
                                        " --recommender=" + method + " --recommender-options=" + neigh_options;
                                currLogger.info(mmlString);
                                CmdExecutor.executeCommand(mmlString, false);
                                // Now transform the results file in the TrecEval format for evaluation
                                PredictionFileConverter.fixPredictionFile(testFile, tempResFile, resFile, numRec);
                                EvaluateRecommendation.generateTrecEvalFile(resFile, trecResFile);
                                String trecResultFinal = trecResFile.substring(0, trecResFile.lastIndexOf(File.separator))
                                        + File.separator + "u" + i + ".final";
                                EvaluateRecommendation.saveTrecEvalResult(trecTestFile, trecResFile, trecResultFinal);
                                metricsForSplit.add(EvaluateRecommendation.getTrecEvalResults(trecResultFinal));
                                currLogger.info(metricsForSplit.get(metricsForSplit.size() - 1).toString());
                            }

                            currLogger.info(("Metrics results for sparsity level " + level + "\n"));
                            EvaluateRecommendation.generateMetricsFile(EvaluateRecommendation.averageMetricsResult(metricsForSplit, numberOfSplit), completeResFile);
                            metricsForSplit.clear(); // evaluate for the next sparsity level
                        }
                    }
        *
        * */

    }


}
