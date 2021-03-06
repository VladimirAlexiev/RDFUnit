package org.aksw.rdfunit.Utils;

import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.shared.uuid.JenaUUID;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.model.QueryExecutionFactoryModel;
import org.aksw.rdfunit.enums.RLOGLevel;
import org.aksw.rdfunit.enums.TestAppliesTo;
import org.aksw.rdfunit.enums.TestGenerationType;
import org.aksw.rdfunit.exceptions.BindingException;
import org.aksw.rdfunit.exceptions.TestCaseInstantiationException;
import org.aksw.rdfunit.io.writer.RDFWriter;
import org.aksw.rdfunit.io.writer.RDFWriterException;
import org.aksw.rdfunit.patterns.Pattern;
import org.aksw.rdfunit.patterns.PatternParameter;
import org.aksw.rdfunit.services.PatternService;
import org.aksw.rdfunit.services.PrefixNSService;
import org.aksw.rdfunit.sources.Source;
import org.aksw.rdfunit.tests.*;
import org.aksw.rdfunit.tests.results.ResultAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;


/**
 * <p>TestUtils class.</p>
 *
 * @author Dimitris Kontokostas
 *         Various utility test functions for tests
 * @since 9/24/13 10:59 AM
 * @version $Id: $Id
 */
public final class TestUtils {
    private static final Logger log = LoggerFactory.getLogger(TestUtils.class);

    private TestUtils() {
    }

    /**
     * <p>instantiateTestGeneratorsFromModel.</p>
     *
     * @param queryFactory a {@link org.aksw.jena_sparql_api.core.QueryExecutionFactory} object.
     * @return a {@link java.util.Collection} object.
     */
    public static Collection<TestAutoGenerator> instantiateTestGeneratorsFromModel(QueryExecutionFactory queryFactory) {
        Collection<TestAutoGenerator> autoGenerators = new ArrayList<>();

        String sparqlSelect = PrefixNSService.getSparqlPrefixDecl() +
                " SELECT ?generator ?desc ?query ?patternID WHERE { " +
                " ?generator " +
                "    a rut:TestGenerator ; " +
                "    dcterms:description ?desc ; " +
                "    rut:sparqlGenerator ?query ; " +
                "    rut:basedOnPattern ?pattern . " +
                " ?pattern dcterms:identifier ?patternID ." +
                "} ";

        QueryExecution qe = queryFactory.createQueryExecution(sparqlSelect);
        ResultSet results = qe.execSelect();

        while (results.hasNext()) {
            QuerySolution qs = results.next();

            String generator = qs.get("generator").toString();
            String description = qs.get("desc").toString();
            String query = qs.get("query").toString();
            String patternID = qs.get("patternID").toString();

            // Get annotations from TAG URI
            Collection<ResultAnnotation> annotations = SparqlUtils.getResultAnnotations(queryFactory, generator);

            TestAutoGenerator tag = new TestAutoGenerator(generator, description, query, PatternService.getPattern(patternID), annotations);
            if (tag.isValid()) {
                autoGenerators.add(tag);
            } else {
                log.error("AutoGenerator not valid: " + tag.getUri());
                System.exit(-1);
            }
        }
        qe.close();

        return autoGenerators;

    }

    /**
     * <p>instantiateTestsFromAG.</p>
     *
     * @param autoGenerators a {@link java.util.Collection} object.
     * @param source a {@link org.aksw.rdfunit.sources.Source} object.
     * @return a {@link java.util.Collection} object.
     */
    public static Collection<TestCase> instantiateTestsFromAG(Collection<TestAutoGenerator> autoGenerators, Source source) {
        Collection<TestCase> tests = new ArrayList<>();

        for (TestAutoGenerator tag : autoGenerators) {
            tests.addAll(tag.generate(source));
        }

        return tests;

    }

    /**
     * <p>instantiateTestsFromModel.</p>
     *
     * @param model a {@link com.hp.hpl.jena.rdf.model.Model} object.
     * @return a {@link java.util.Collection} object.
     */
    public static Collection<TestCase> instantiateTestsFromModel(Model model) {
        try {
            return instantiateTestsFromModel(model, false);
        } catch (TestCaseInstantiationException e) {
            // This should not occur since we pass strict-> false
        }
        throw new RuntimeException("Unexpected exception...");
    }

    /**
     * <p>instantiateTestsFromModel.</p>
     *
     * @param model a {@link com.hp.hpl.jena.rdf.model.Model} object.
     * @param strict a boolean.
     * @return a {@link java.util.Collection} object.
     * @throws org.aksw.rdfunit.exceptions.TestCaseInstantiationException if any.
     */
    public static Collection<TestCase> instantiateTestsFromModel(Model model, boolean strict) throws TestCaseInstantiationException {
        Collection<TestCase> tests = new ArrayList<>();
        QueryExecutionFactory qef = new QueryExecutionFactoryModel(model);

        // Get all manual tests

        String manualTestsSelectSparql = PrefixNSService.getSparqlPrefixDecl() +
                " SELECT DISTINCT ?testURI WHERE {" +
                " ?testURI a rut:ManualTestCase }";

        QueryExecution qe = qef.createQueryExecution(manualTestsSelectSparql);
        ResultSet results = qe.execSelect();

        while (results.hasNext()) {
            QuerySolution qs = results.next();
            String testURI = qs.get("testURI").toString();
            try {
                ManualTestCase tc = instantiateSingleManualTestFromModel(qef, testURI);
                TestCaseValidator validator = new TestCaseValidator(tc);
                validator.validate();
                tests.add(tc);
            } catch (TestCaseInstantiationException e) {
                log.error(e.getMessage(), e);
                if (strict) {
                    throw new TestCaseInstantiationException(e.getMessage(), e);
                }
            }

        }

        // Get all pattern based tests

        String patternTestsSelectSparql = PrefixNSService.getSparqlPrefixDecl() +
                " SELECT DISTINCT ?testURI WHERE {" +
                " ?testURI a rut:PatternBasedTestCase } ";

        qe = qef.createQueryExecution(patternTestsSelectSparql);
        results = qe.execSelect();

        while (results.hasNext()) {
            QuerySolution qs = results.next();
            String testURI = qs.get("testURI").toString();
            try {
                PatternBasedTestCase tc = instantiateSinglePatternTestFromModel(qef, testURI);
                tests.add(tc);
            } catch (TestCaseInstantiationException e) {
                log.error(e.getMessage(), e);
                if (strict) {
                    throw new TestCaseInstantiationException(e.getMessage(), e);
                }
            }
        }

        return tests;
    }

    /**
     * <p>instantiateSingleManualTestFromModel.</p>
     *
     * @param qef a {@link org.aksw.jena_sparql_api.core.QueryExecutionFactory} object.
     * @param testURI a {@link java.lang.String} object.
     * @return a {@link org.aksw.rdfunit.tests.ManualTestCase} object.
     * @throws org.aksw.rdfunit.exceptions.TestCaseInstantiationException if any.
     */
    public static ManualTestCase instantiateSingleManualTestFromModel(QueryExecutionFactory qef, String testURI) throws TestCaseInstantiationException {

        String sparqlSelect = PrefixNSService.getSparqlPrefixDecl() +
                " SELECT DISTINCT ?description ?appliesTo ?generated ?source ?sparqlWhere ?sparqlPrevalence ?testGenerator ?testCaseLogLevel WHERE { " +
                " <" + testURI + "> " +
                "    dcterms:description  ?description ;" +
                "    rut:appliesTo        ?appliesTo ;" +
                "    rut:generated        ?generated ;" +
                "    rut:source           ?source ;" +
                "    rut:testCaseLogLevel ?testCaseLogLevel ;" +
                "    rut:sparqlWhere      ?sparqlWhere ;" +
                "    rut:sparqlPrevalence ?sparqlPrevalence ." +
                " OPTIONAL {<" + testURI + ">  rut:testGenerator ?testGenerator .}" +
                "} ";
        QueryExecution qe = null;
        try {
            qe = qef.createQueryExecution(sparqlSelect);
            ResultSet results = qe.execSelect();

            if (results.hasNext()) {
                QuerySolution qs = results.next();

                String description = qs.get("description").toString();
                String appliesTo = qs.get("appliesTo").toString();
                String generated = qs.get("generated").toString();
                String source = qs.get("source").toString();
                RLOGLevel testCaseLogLevel = RLOGLevel.resolve(qs.get("testCaseLogLevel").toString());
                String sparqlWhere = qs.get("sparqlWhere").toString();
                String sparqlPrevalence = qs.get("sparqlPrevalence").toString();
                Collection<String> referencesLst = getReferencesFromTestCase(qef, testURI);
                String testGenerator = "";
                if (qs.contains("testGenerator")) {
                    testGenerator = qs.get("testGenerator").toString();
                }

                // Get annotations from Test URI
                Collection<ResultAnnotation> resultAnnotations = SparqlUtils.getResultAnnotations(qef, testURI);

                TestCaseAnnotation annotation =
                        new TestCaseAnnotation(
                                TestGenerationType.resolve(generated),
                                testGenerator,
                                TestAppliesTo.resolve(appliesTo),
                                source,
                                referencesLst,
                                description,
                                testCaseLogLevel,
                                resultAnnotations);

                if (!results.hasNext()) {
                    ManualTestCase tc = new ManualTestCase(
                            testURI,
                            annotation,
                            sparqlWhere,
                            sparqlPrevalence);
                    new TestCaseValidator(tc).validate();
                    return tc;
                }
            }

        } finally {
            if (qe != null) {
                qe.close();
            }
        }

        throw new TestCaseInstantiationException("No results for TC (probably incomplete): " + testURI);
    }

    /**
     * <p>instantiateSinglePatternTestFromModel.</p>
     *
     * @param qef a {@link org.aksw.jena_sparql_api.core.QueryExecutionFactory} object.
     * @param testURI a {@link java.lang.String} object.
     * @return a {@link org.aksw.rdfunit.tests.PatternBasedTestCase} object.
     * @throws org.aksw.rdfunit.exceptions.TestCaseInstantiationException if any.
     */
    public static PatternBasedTestCase instantiateSinglePatternTestFromModel(QueryExecutionFactory qef, String testURI) throws TestCaseInstantiationException {

        String sparqlSelect = PrefixNSService.getSparqlPrefixDecl() +
                " SELECT DISTINCT ?description ?appliesTo ?generated ?source ?basedOnPattern ?testGenerator ?testCaseLogLevel WHERE { " +
                " <" + testURI + "> " +
                "    dcterms:description ?description ;" +
                "    rut:appliesTo      ?appliesTo ;" +
                "    rut:generated      ?generated ;" +
                "    rut:source         ?source ;" +
                "    rut:testCaseLogLevel ?testCaseLogLevel ;" +
                "    rut:basedOnPattern ?basedOnPattern ;" +
                " OPTIONAL {<" + testURI + ">  rut:testGenerator ?testGenerator .}" +
                "} ";

        QueryExecution qe = null;
        try {
            qe = qef.createQueryExecution(sparqlSelect);
            ResultSet results = qe.execSelect();

            if (results.hasNext()) {
                QuerySolution qs = results.next();

                String description = qs.get("description").toString();
                String appliesTo = qs.get("appliesTo").toString();
                String generated = qs.get("generated").toString();
                String source = qs.get("source").toString();
                RLOGLevel testCaseLogLevel = RLOGLevel.resolve(qs.get("testCaseLogLevel").toString());
                String patternURI = qs.get("basedOnPattern").toString();
                Pattern pattern = PatternService.getPattern(PrefixNSService.getLocalName(patternURI, "rutp"));
                if (pattern == null) {
                    throw new TestCaseInstantiationException("Pattern does not exists for TC: " + testURI);
                }

                Collection<String> referencesLst = getReferencesFromTestCase(qef, testURI);
                Collection<Binding> bindings = getBindingsFromTestCase(qef, testURI, pattern);
                String testGenerator = "";
                if (qs.contains("testGenerator")) {
                    testGenerator = qs.get("testGenerator").toString();
                }

                // Get annotations from Test URI
                Collection<ResultAnnotation> resultAnnotations = SparqlUtils.getResultAnnotations(qef, testURI);

                TestCaseAnnotation annotation =
                        new TestCaseAnnotation(
                                TestGenerationType.resolve(generated),
                                testGenerator,
                                TestAppliesTo.resolve(appliesTo),
                                source,
                                referencesLst,
                                description,
                                testCaseLogLevel,
                                resultAnnotations);

                if (!results.hasNext()) {
                    PatternBasedTestCase tc = new PatternBasedTestCase(
                            testURI,
                            annotation,
                            pattern,
                            bindings);
                    new TestCaseValidator(tc).validate();
                    return tc;
                }
            }
        } finally {
            if (qe != null) {
                qe.close();
            }
        }

        throw new TestCaseInstantiationException("No results for TC (probably incomplete): " + testURI);
    }

    /**
     * <p>writeTestsToFile.</p>
     *
     * @param tests a {@link java.util.Collection} object.
     * @param testCache a {@link org.aksw.rdfunit.io.writer.RDFWriter} object.
     */
    public static void writeTestsToFile(Collection<TestCase> tests, RDFWriter testCache) {
        Model model = ModelFactory.createDefaultModel();
        for (TestCase t : tests) {
            t.serialize(model);
        }
        try {
            PrefixNSService.setNSPrefixesInModel(model);
            testCache.write(model);
        } catch (RDFWriterException e) {
            log.error("Cannot cache tests: " + e.getMessage());
        }
    }

    /**
     * <p>getReferencesFromTestCase.</p>
     *
     * @param qef a {@link org.aksw.jena_sparql_api.core.QueryExecutionFactory} object.
     * @param testURI a {@link java.lang.String} object.
     * @return a {@link java.util.Collection} object.
     */
    public static Collection<String> getReferencesFromTestCase(QueryExecutionFactory qef, String testURI) {

        Collection<String> references = new ArrayList<>();

        String sparqlReferencesSelect = PrefixNSService.getSparqlPrefixDecl() +
                " SELECT DISTINCT ?references WHERE { " +
                " <" + testURI + "> rut:references ?references . }";

        QueryExecution qe = null;
        try {
            qe = qef.createQueryExecution(sparqlReferencesSelect);
            ResultSet results = qe.execSelect();

            while (results.hasNext()) {
                QuerySolution qs = results.next();
                references.add(qs.get("references").toString());
            }
        } finally {
            if (qe != null) {
                qe.close();
            }
        }
        return references;
    }

    /**
     * <p>getBindingsFromTestCase.</p>
     *
     * @param qef a {@link org.aksw.jena_sparql_api.core.QueryExecutionFactory} object.
     * @param testURI a {@link java.lang.String} object.
     * @param pattern a {@link org.aksw.rdfunit.patterns.Pattern} object.
     * @return a {@link java.util.Collection} object.
     */
    public static Collection<Binding> getBindingsFromTestCase(QueryExecutionFactory qef, String testURI, Pattern pattern) {

        Collection<Binding> bindings = new ArrayList<>();

        String sparqlReferencesSelect = PrefixNSService.getSparqlPrefixDecl() +
                " SELECT DISTINCT ?parameter ?value WHERE { " +
                " <" + testURI + "> rut:binding ?binding ." +
                " ?binding rut:bindingValue ?value ;" +
                "          rut:parameter ?parameter }";

        QueryExecution qe = null;
        try {
            qe = qef.createQueryExecution(sparqlReferencesSelect);
            ResultSet results = qe.execSelect();

            while (results.hasNext()) {
                QuerySolution qs = results.next();

                String parameterURI = qs.get("parameter").toString();
                PatternParameter parameter = pattern.getParameter(parameterURI);
                if (parameter == null) {
                    log.error("Test instantiation error: Pattern " + pattern.getId() + " does not contain parameter " + parameterURI + " in TestCase: " + testURI);
                    continue;
                }

                RDFNode value = qs.get("value");

                try {
                    bindings.add(new Binding(parameter, value));
                } catch (BindingException e) {
                    log.error("Non valid binding for parameter " + parameter.getId() + " in Test: " + testURI);
                }
            }
        } finally {
            if (qe != null) {
                qe.close();
            }
        }
        return bindings;
    }

    /**
     * <p>generateTestURI.</p>
     *
     * @param sourcePrefix a {@link java.lang.String} object.
     * @param pattern a {@link org.aksw.rdfunit.patterns.Pattern} object.
     * @param bindings a {@link java.util.Collection} object.
     * @param generatorURI a {@link java.lang.String} object.
     * @return a {@link java.lang.String} object.
     */
    public static String generateTestURI(String sourcePrefix, Pattern pattern, Collection<Binding> bindings, String generatorURI) {
        String testURI = PrefixNSService.getNSFromPrefix("rutt") + sourcePrefix + "-" + pattern.getId() + "-";
        StringBuilder string2hash = new StringBuilder(generatorURI);
        for (Binding binding : bindings) {
            string2hash.append(binding.getValueAsString());
        }
        String md5Hash = TestUtils.getHashFromString(string2hash.toString());
        if (md5Hash.isEmpty()) {
            return testURI + JenaUUID.generate().asString();
        } else {
            return testURI + md5Hash;
        }
    }

    // Taken from http://stackoverflow.com/questions/415953/generate-md5-hash-in-java
    /**
     * <p>getHashFromString.</p>
     *
     * @param str a {@link java.lang.String} object.
     * @return a {@link java.lang.String} object.
     * @since 0.7.2
     */
    public static String getHashFromString(String str) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] array = md.digest(str.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte anArray : array) {
                sb.append(Integer.toHexString((anArray & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new RuntimeException("Cannot calculate MD5 hash for :" + str, e);
        }
    }

}
