package org.aksw.rdfunit.Utils;

import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.model.QueryExecutionFactoryModel;
import org.aksw.rdfunit.RDFUnit;
import org.aksw.rdfunit.io.reader.RDFReader;
import org.junit.Before;
import org.junit.Test;

//import static org.junit.Assert.*;

/**
 * We test if the defined patterns in resources throw any exceptions during generation
 */
public class PatternUtilsTest {

    private QueryExecutionFactory qef;

    @Before
    public void setUp() throws Exception {
        RDFReader reader = RDFUnit.getPatternsReader();
        qef = new QueryExecutionFactoryModel(reader.read());
    }

    @Test
    public void testInstantiatePatternsFromModel() throws Exception {
        PatternUtils.instantiatePatternsFromModel(qef);
    }

    @Test
    public void testGetPattern() throws Exception {


    }
}