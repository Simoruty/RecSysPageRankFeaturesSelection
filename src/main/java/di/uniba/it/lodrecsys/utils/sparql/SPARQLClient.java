package di.uniba.it.lodrecsys.utils.sparql;

import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.RDFNode;
import di.uniba.it.lodrecsys.entity.MovieMapping;
import di.uniba.it.lodrecsys.utils.Utils;
import jena.query;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by asuglia on 5/27/14.
 */
public class SPARQLClient {
    private String resource;
    String property;
    private String endpoint = "http://dbpedia.org/sparql";
    private String newEndpoint = "http://live.dbpedia.org/sparql";
    private String graphURI = "http://dbpedia.org";
    private static Logger currLogger = Logger.getLogger(SPARQLClient.class.getName());

    public void exec(String resource, String prop) {
        this.resource = resource;
        this.property = prop;
        Query query;
        String q;

        String resourceQuery = "<" + resource + ">";
        String propQuery = "<" + prop + ">";
        // creation of a sparql query for getting all the resources connected to resource
        //the FILTER isIRI is used to get only resources, so this query descards any literal or data-type

        q = " SELECT * WHERE {{" + " ?s " + propQuery + " " + resourceQuery
                + ". " + "FILTER isIRI(?s). " + " } UNION {" + resourceQuery + " "
                + propQuery + " ?o " + "FILTER isIRI(?o). " + "}}";
        try {
            query = QueryFactory.create(q);

            execQuery(query);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    public void exec(String resource) {
        this.resource = resource;
        Query query;
        String q;

        String resourceQuery = "<" + resource + ">";
        // creation of a sparql query for getting all the resources connected to resource
        //the FILTER isIRI is used to get only resources, so this query descards any literal or data-type

        q = " SELECT * WHERE {{" + " ?s ?p " + resourceQuery
                + ". " + "FILTER isIRI(?s). " + " } UNION {" + resourceQuery +
                " ?p ?o " + "FILTER isIRI(?o). " + "}}";
        try {
            query = QueryFactory.create(q);

            execQuery(query);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }


    public void movieQuery(String dbpediaFilms) throws IOException {
        String includeNamespaces = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
                "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                "PREFIX dcterms: <http://purl.org/dc/terms/>\n" +
                "PREFIX dbpedia-owl: <http://dbpedia.org/ontology/>\n";

        String currQuery = includeNamespaces + "SELECT DISTINCT ?movie (str(?movie_title) as ?title) (str(?movie_year) as ?year) WHERE {\n" +
                " ?movie rdf:type dbpedia-owl:Film.\n" +
                " ?movie rdfs:label ?movie_title.\n" +
                " ?movie dcterms:subject ?cat .\n" +
                " ?cat rdfs:label ?movie_year .\n" +
                " FILTER langMatches(lang(?movie_title), \"EN\") .\n" +
                " FILTER regex(?movie_year, \"^[0-9]{4} \", \"i\")\n" +
                " } limit 2000 offset ";
        int totalNumberOfFilms = 77794;
        int totNumQuery = 38;
        int offset = 2000;

        for(int i = 1; i <= totNumQuery+1; i++) {
            try {
                Query query = QueryFactory.create(currQuery + offset);
                Utils.serializeMappingList(getMovieMappingList(query), dbpediaFilms);

            } catch (Exception ex) {
                ex.printStackTrace();
                throw ex;
            }

            if(i == totNumQuery)
                offset = totalNumberOfFilms - offset;
            else
                offset += 2000;

            myWait(30);

        }


    }

    private void myWait(int sec) {
        //wait

        long cm = System.currentTimeMillis();
        while ((System.currentTimeMillis() - cm) < sec * 1000);

    }


    private List<MovieMapping> getMovieMappingList(Query query) {
        String dbpediaResVar = "?movie", movieTitleVar = "?title", movieDateVar = "?year";

        System.out.println("executing query : " + query.toString());

        QueryExecution qexec = null;
        try {
            if (graphURI == null)
                qexec = QueryExecutionFactory.sparqlService(endpoint, query);
                //qexec = QueryExecutionFactory.sparqlService(newEndpoint, query);
            else
                qexec = QueryExecutionFactory.sparqlService(endpoint, query,
                        graphURI);

            ResultSet resultSet = qexec.execSelect();
            List<MovieMapping> moviesList = new ArrayList<>();

            QuerySolution currSolution;

            while(resultSet.hasNext()) {
                currSolution = resultSet.nextSolution();
//                MovieMapping mapped = new MovieMapping(null, currSolution.getResource(dbpediaResVar).getURI(),
//                        currSolution.getLiteral(movieTitleVar).toString(),
//                        currSolution.getLiteral(movieDateVar).toString());
//
//                currLogger.info(mapped.toString());
                moviesList.add(new MovieMapping(null, currSolution.getResource(dbpediaResVar).getURI(),
                     currSolution.getLiteral(movieTitleVar).toString(),
                       currSolution.getLiteral(movieDateVar).toString()));

            }

            return moviesList;


        } finally {
            if (qexec != null)
                qexec.close();
        }


    }
    private void execQuery(Query query) {

        System.out.println("executing query : " + query.toString());

        QueryExecution qexec = null;
        try {
            if (graphURI == null)
                qexec = QueryExecutionFactory.sparqlService(endpoint, query);
            else
                qexec = QueryExecutionFactory.sparqlService(endpoint, query,
                        graphURI);

            ResultSet results = qexec.execSelect();

            QuerySolution qs;
            RDFNode node, prop;

            String n = "", p = this.property;

            System.out.println("Results:");
            //iteration over the resultset
            while (results.hasNext()) {

                qs = results.next();

                if (qs.contains("p")) {
                    prop = qs.get("p"); //get the predicate of the triple
                    p = prop.toString();
                    p = p.replace("<", "");
                    p = p.replace(">", "");

                }
                if (qs.get("o") == null) {
                    node = qs.get("s"); //get the subject of the triple
                    n = node.toString();
                    n = n.replace("<", "");
                    n = n.replace(">", "");

                    System.out.println(n + '\t' + p + '\t' + resource);
                } else {

                    node = qs.get("o"); //get the object of the triple
                    n = node.toString();
                    n = n.replace("<", "");
                    n = n.replace(">", "");

                    System.out.println(resource + '\t' + p + '\t' + n);

                }

            }

        } finally {
            if (qexec != null)
                qexec.close();
        }

    }

    public static void main(String[] args) {

        SPARQLClient exec = new SPARQLClient();
        //get all the triples related to the predicate http://dbpedia.org/ontology/starring
        //wherein the Godfather appears as subject or object
        exec.exec("http://dbpedia.org/resource/The_Godfather", "http://dbpedia.org/ontology/starring");

        //get all the triples that involve the Godfather
        //exec.exec("http://dbpedia.org/resource/The_Godfather");


    }
}
