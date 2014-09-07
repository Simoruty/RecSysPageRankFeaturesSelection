package di.uniba.it.lodrecsys.graph.scorer;

import org.apache.commons.collections15.Transformer;
import java.util.Map;
import java.util.Set;

/**
 * Created by asuglia on 7/30/14.
 */
public class SimilarityVertexTransformer implements Transformer<String, Double> {
    private String currUserID;
    private Map<String, Map<String, Double>> simUserMap;

    public SimilarityVertexTransformer(String currUserID, Map<String, Map<String, Double>> simUserMap) {
        this.currUserID = currUserID;
        this.simUserMap = simUserMap;
    }

    @Override
    public Double transform(String entityID) {
        return simUserMap.get(currUserID).get(entityID);
    }
}