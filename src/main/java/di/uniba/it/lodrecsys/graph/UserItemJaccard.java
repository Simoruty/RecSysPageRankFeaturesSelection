package di.uniba.it.lodrecsys.graph;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.hp.hpl.jena.rdf.model.Statement;
import di.uniba.it.lodrecsys.entity.MovieMapping;
import di.uniba.it.lodrecsys.entity.Rating;
import di.uniba.it.lodrecsys.entity.RequestStruct;
import di.uniba.it.lodrecsys.eval.EvaluateRecommendation;
import di.uniba.it.lodrecsys.graph.scorer.ImprovedJaccardTransformer;
import di.uniba.it.lodrecsys.graph.scorer.JaccardVertexTransformer;
import di.uniba.it.lodrecsys.properties.JaccardSimilarityFunction;
import di.uniba.it.lodrecsys.properties.PropertiesCalculator;
import di.uniba.it.lodrecsys.properties.Similarity;
import di.uniba.it.lodrecsys.properties.SimilarityFunction;
import di.uniba.it.lodrecsys.utils.Utils;
import di.uniba.it.lodrecsys.utils.mapping.PropertiesManager;
import edu.uci.ics.jung.algorithms.scoring.PageRankWithPriors;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by asuglia on 7/30/14.
 */
public class UserItemJaccard extends RecGraph {
    private Map<String, String> idUriMap;
    private Map<String, String> uriIdMap;
    private ArrayListMultimap<String, Set<String>> trainingPosNeg;
    private Map<String, Set<String>> testSet;
    private Map<String, Multimap<String, String>> usersCentroid;
    private PropertiesManager propManager;
    private Map<String, Multimap<String, String>> itemsRepresentation;
    private int totalNumberItems;
    private Map<String, Map<String, Double>> simUserMap;


    public UserItemJaccard(String trainingFileName, String testFile, String proprIndexDir, List<MovieMapping> mappedItems) {
        try {
            getMapForMappedItems(mappedItems);
            generateGraph(new RequestStruct(trainingFileName, testFile, proprIndexDir, mappedItems));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void getMapForMappedItems(List<MovieMapping> movieList) {
        // key: item-id - value: dbpedia uri
        idUriMap = new HashMap<>();
        uriIdMap = new HashMap<>();

        for (MovieMapping movie : movieList) {
            idUriMap.put(movie.getItemID(), movie.getDbpediaURI());
            uriIdMap.put(movie.getDbpediaURI(), movie.getItemID());
        }
    }

    @Override
    public void generateGraph(RequestStruct requestStruct) throws IOException {
        String trainingFileName = (String) requestStruct.params.get(0),
                testFile = (String) requestStruct.params.get(1);

        propManager = new PropertiesManager((String) requestStruct.params.get(2));
        List<MovieMapping> mappedItemsList = (List<MovieMapping>) requestStruct.params.get(3);
        getMapForMappedItems(mappedItemsList);
        usersCentroid = new HashMap<>();

        trainingPosNeg = Utils.loadPosNegRatingForEachUser(trainingFileName);
        testSet = Utils.loadRatedItems(new File(testFile), false);

        Set<String> allItemsID = new TreeSet<>();

        for (Set<String> items : testSet.values()) {
            allItemsID.addAll(items);
        }

        for (String userID : trainingPosNeg.keySet()) {
            allItemsID.addAll(trainingPosNeg.get(userID).get(0));

        }

        totalNumberItems = allItemsID.size();

        itemsRepresentation = loadItemsRepresentation(allItemsID, propManager);
        PropertiesCalculator calculator = PropertiesCalculator.create(Similarity.JACCARD);
        for (String userID : testSet.keySet()) {
            usersCentroid.put(userID, calculator.computeCentroid(getRatedItemRepresentation(trainingPosNeg.get(userID).get(0))));
        }

        computeSimilarityMap();

        for (String itemID : allItemsID) {
            recGraph.addVertex(itemID);
        }

        Set<String> userSet = trainingPosNeg.keySet();
        for (String userID : userSet) {
            int edgeCounter = 0;

            for (String posItemID : trainingPosNeg.get(userID).get(0)) {
                recGraph.addEdge(userID + "-" + edgeCounter, "U:" + userID, posItemID);
                edgeCounter++;

            }

            Map<String, Double> currUserSimMap = simUserMap.get(userID);
            if (currUserSimMap != null) {

                for (String otherUser : userSet) {
                    if (currUserSimMap.getOrDefault(otherUser, 0d) > 0.5) {
                        recGraph.addEdge("U:" + userID + "-U:" + otherUser, "U:" + userID, "U:" + otherUser);
                    }

                }
            }
        }


        /*for (String userID : trainingPosNeg.keySet()) {
            int edgeCounter = 0;

            for (String posItemID : trainingPosNeg.get(userID).get(0)) {
                recGraph.addEdge(userID + "-" + edgeCounter, "U:" + userID, posItemID);
                edgeCounter++;

            }


        }*/

        currLogger.info(String.format("Total number of vertex %s - Total number of edges %s", recGraph.getVertexCount(), recGraph.getEdgeCount()));

    }

    private void computeSimilarityMap() {
        this.simUserMap = new HashMap<>();

        Set<String> userSet = trainingPosNeg.keySet();
        SimilarityFunction function = new JaccardSimilarityFunction();

        for (String currUser : userSet) {
            Multimap<String, String> currUserVector = usersCentroid.get(currUser);

            for (String otherUser : userSet) {

                Double simScore;
                // compute covoted items first
                simScore = computeCovotedItems(currUser, otherUser);
                // compute content-based sim score
                Multimap<String, String> otherUserVector = usersCentroid.get(otherUser);
                if (currUserVector != null && otherUserVector != null)
                    simScore *= function.compute(currUserVector, otherUserVector);


                Map<String, Double> currUserMap = simUserMap.get(currUser);
                if (currUserMap == null) {
                    currUserMap = new HashMap<>();
                }

                currUserMap.put(otherUser, simScore);

            }

        }

    }

    private double computeCovotedItems(String firstUser, String secUser) {
        Set<String> firstPostems = trainingPosNeg.get(firstUser).get(0),
                secPosItems = trainingPosNeg.get(secUser).get(0);

        return Sets.intersection(firstPostems, secPosItems).size() / totalNumberItems;

    }

    @Override
    public Map<String, Set<Rating>> runPageRank(RequestStruct requestParam) throws IOException {
        Map<String, Set<Rating>> usersRecommendation = new HashMap<>();

        double massProb = (double) requestParam.params.get(0); // max proportion of positive items for user

        // compute recommendation for all users

        for (String userID : testSet.keySet()) {
            currLogger.info("Page rank for user: " + userID);
            List<Set<String>> posNegativeRatings = trainingPosNeg.get(userID);
            Set<String> testItems = testSet.get(userID);
            usersRecommendation.put(userID, profileUser(userID, posNegativeRatings.get(0), posNegativeRatings.get(1), testItems));
        }

        return usersRecommendation;
    }

    private Map<String, Multimap<String, String>> loadItemsRepresentation(Collection<String> allItems, PropertiesManager manager) {
        Map<String, Multimap<String, String>> itemsRepresentation = new HashMap<>();

        for (String itemID : allItems) {
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

        return itemsRepresentation;

    }

    private Map<String, Multimap<String, String>> getRatedItemRepresentation(Collection<String> ratedItems) {
        Map<String, Multimap<String, String>> ratedItemsRepresentation = new HashMap<>();
        for (String itemID : ratedItems) {
            Multimap<String, String> representation = this.itemsRepresentation.get(itemID);
            if (representation != null)
                ratedItemsRepresentation.put(itemID, representation);
        }

        return ratedItemsRepresentation;
    }

    private Set<Rating> profileUser(String userID, Set<String> trainingPos, Set<String> trainingNeg, Set<String> testItems) {
        Set<Rating> allRecommendation = new TreeSet<>();

        //ImprovedJaccardTransformer transformer = new ImprovedJaccardTransformer(userID, trainingPos, trainingNeg, totalNumberItems, recGraph.getVertexCount(), usersCentroid, itemsRepresentation);
        JaccardVertexTransformer transformer = new JaccardVertexTransformer(trainingPos, trainingNeg, recGraph.getVertexCount(), usersCentroid.get(userID), itemsRepresentation, usersCentroid);
        PageRankWithPriors<String, String> priors = new PageRankWithPriors<>(this.recGraph, transformer, 0.15);

        priors.setMaxIterations(25);
        priors.evaluate();

        for (String currItemID : testItems) {
            allRecommendation.add(new Rating(currItemID, String.valueOf(priors.getVertexScore(currItemID))));

        }

        return allRecommendation;
    }

    public static void main(String[] args) throws IOException {
        String trainPath = "/home/asuglia/thesis/dataset/ml-100k/definitive",
                testPath = "/home/asuglia/thesis/dataset/ml-100k/binarized",
                testTrecPath = "/home/asuglia/thesis/dataset/ml-100k/trec",
                resPath = "/home/asuglia/thesis/dataset/ml-100k/results",
                propertyIndexDir = "/home/asuglia/thesis/content_lodrecsys/movielens/stored_prop",
                tagmeDir = "/home/asuglia/thesis/content_lodrecsys/movielens/tagme",
                mappedItemFile = "mapping/item.mapping";

        List<MovieMapping> mappingList = Utils.loadDBpediaMappedItems(mappedItemFile);

        UserItemJaccard jaccard = new UserItemJaccard(testPath + File.separator + "u1.base", testPath + File.separator + "u1.test",
                propertyIndexDir, mappingList);
        Map<String, Set<Rating>> ratings = jaccard.runPageRank(new RequestStruct(0.85));

        String resFile = resPath + File.separator + "UserItemJaccard" + File.separator + "u1.result";

        EvaluateRecommendation.serializeRatings(ratings, resFile, 10);

        String trecTestFile = testTrecPath + File.separator + "u1.test";
        String trecResultFinal = resFile.substring(0, resFile.lastIndexOf(File.separator))
                + File.separator + "u1.final";
        EvaluateRecommendation.saveTrecEvalResult(trecTestFile, resFile, trecResultFinal);
        currLogger.info(EvaluateRecommendation.getTrecEvalResults(trecResultFinal).toString());
    }

}