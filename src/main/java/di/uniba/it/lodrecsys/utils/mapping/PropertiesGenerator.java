package di.uniba.it.lodrecsys.utils.mapping;

import di.uniba.it.lodrecsys.entity.MovieMapping;
import di.uniba.it.lodrecsys.utils.Utils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by asuglia on 6/26/14.
 */
public class PropertiesGenerator {
    public static void main(String[] args) throws Exception {
        String choosenProp = "mapping/choosen_prop.txt",
                propertiesDir = "/home/asuglia/thesis/content_lodrecsys/movielens/stored_prop",
                dbpediaMapping = "mapping/item.mapping",
                firstLevelExpProp = "mapping/exp_prop.txt";

        PropertiesManager manager = new PropertiesManager(propertiesDir);

        Collection<String> expPropList = loadPropertiesURI(firstLevelExpProp);

        SPARQLClient sparql = new SPARQLClient();

        List<MovieMapping> mappedItems = Utils.loadDBpediaMappedItems(dbpediaMapping);


        int i = 0;

        for (MovieMapping mappedItem : mappedItems) {
            try {
                manager.start(true);
                //sparql.saveResourceProperties(mappedItem.getDbpediaURI(), choosenPropList, manager);
                sparql.downloadFirstLevelRelation(mappedItem.getDbpediaURI(), expPropList, manager);

                i++;

                manager.commitChanges();
                if (i % 50 == 0) {
                    myWait(30);
                }
            } finally {

                manager.closeManager();
            }


        }


    }


    private static void myWait(int sec) {
        //wait

        long cm = System.currentTimeMillis();
        while ((System.currentTimeMillis() - cm) < sec * 1000) ;

    }


    private static Collection<String> loadPropertiesURI(String fileName) {
        BufferedReader reader = null;
        Collection<String> listProp = new ArrayList<>();

        try {
            reader = new BufferedReader(new FileReader(fileName));


            while (reader.ready()) {
                listProp.add(reader.readLine());

            }


        } catch (IOException e) {
            e.printStackTrace();
        }

        return listProp;

    }

}