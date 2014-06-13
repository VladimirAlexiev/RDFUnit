package org.aksw.rdfunit;

import org.aksw.rdfunit.Utils.CacheUtils;
import org.aksw.rdfunit.enums.TestCaseExecutionType;
import org.aksw.rdfunit.services.SchemaService;
import org.aksw.rdfunit.sources.*;

import java.util.ArrayList;
import java.util.Collection;

/**
 * User: Dimitris Kontokostas
 * Holds a configuration for a complete test
 * TODO: Got too big, maybe break it down a bit
 * Created: 11/15/13 11:50 AM
 */
public class RDFUnitConfiguration {

    /* Main Dataset URI (also used for loading dataset tests) */
    private final String datasetURI;
    private String prefix = null;

    /* folder where we store tests / configuration.  */
    private final String dataFolder;
    private final String testFolder;

    /* SPARQL endpoint configuration */
    private String endpointURI = null;
    private Collection<String> endpointGraphs = null;
    private long endpointDelayinMS = 7000; // delay between queries
    private long endpointCacheTTL = 7l * 24l * 60l * 60l * 1000l; // cache time to live
    private long endpointPagination = 900; // default pagination behind the schenes

    /* Dereference testing (if different from datasetURI) */
    private String customDereferenceURI = null;

    /* list of schemas for testing a dataset */
    private Collection<SchemaSource> schemas = null;

    private EnrichedSchemaSource enrichedSchema = null;

    /* use the cache for loading tests (do not regenerate if already exists) */
    private boolean testCacheEnabled = true;

    /* if set to false it will generate only schema based test cases */
    private boolean manualTestsEnabled = true;

    /* Execution type */
    private TestCaseExecutionType resultLevelReporting = TestCaseExecutionType.aggregatedTestCaseResult;

    /*  */
    private boolean calculateCoverageEnabled = false;

    public RDFUnitConfiguration(String datasetURI, String dataFolder){
        this(datasetURI, dataFolder, dataFolder + "tests/");
    }

    public RDFUnitConfiguration(String datasetURI, String dataFolder, String testFolder){
        this.datasetURI = datasetURI;
        this.dataFolder = dataFolder;
        this.testFolder = testFolder;

        prefix = CacheUtils.getAutoPrefixForURI(datasetURI); // default prefix
    }

    public void setEndpointConfiguration(String endpointURI, Collection<String> endpointGraphs) {
        setEndpointConfiguration(endpointURI, endpointGraphs, this.endpointDelayinMS, this.endpointCacheTTL, this.endpointPagination);
    }

    public void setEndpointConfiguration(String endpointURI, Collection<String> endpointGraphs, long endpointDelayinMS, long endpointCacheTTL, long endpointPagination) {
        this.endpointURI = endpointURI;
        this.endpointGraphs = new ArrayList<String>();
        this.endpointGraphs.addAll(endpointGraphs);
        this.endpointDelayinMS = endpointDelayinMS;
        this.endpointCacheTTL = endpointCacheTTL;
        this.endpointPagination = endpointPagination;
    }

    public void setCustomDereferenceURI(String customDereferenceURI) {
        this.customDereferenceURI = customDereferenceURI;
    }

    public void setSchemataFromPrefixes(Collection<String> schemaPrefixes) {
        this.schemas = SchemaService.getSourceList(testFolder, schemaPrefixes);
    }
    public void setSchemata(Collection<SchemaSource> schemata) {
        this.schemas = new ArrayList<>();
        this.schemas.addAll(schemata);
    }

    public void setEnrichedSchema(String enrichedSchemaPrefix) {
        if (enrichedSchemaPrefix != null && !enrichedSchemaPrefix.isEmpty()) {
            enrichedSchema = SourceFactory.createEnrichedSchemaSourceFromCache(testFolder, enrichedSchemaPrefix, datasetURI);
        }
    }

    public Collection<SchemaSource> getAllSchemata(){
        Collection<SchemaSource> allSchemas = new ArrayList<>();
        if (this.schemas != null) {
            allSchemas.addAll(this.schemas);
        }
        if (this.enrichedSchema != null) {
            allSchemas.add(this.enrichedSchema);
        }

        return allSchemas;
    }

    public Source getTestSource() {

        if (customDereferenceURI != null && !customDereferenceURI.isEmpty()) {
            // return a DumpSource
            return new DumpSource(
                    CacheUtils.getAutoPrefixForURI(customDereferenceURI),
                    datasetURI,
                    getAllSchemata());
        } else {
            // return a SPARQL Endpoint source
            return new DatasetSource(
                    CacheUtils.getAutoPrefixForURI(datasetURI),
                    datasetURI,
                    endpointURI,
                    endpointGraphs,
                    getAllSchemata());

        }
    }

    public boolean isTestCacheEnabled() {
        return testCacheEnabled;
    }

    public void setTestCacheEnabled(boolean testCacheEnabled) {
        this.testCacheEnabled = testCacheEnabled;
    }

    public boolean isManualTestsEnabled() {
        return manualTestsEnabled;
    }

    public void setManualTestsEnabled(boolean manualTestsEnabled) {
        this.manualTestsEnabled = manualTestsEnabled;
    }

    public TestCaseExecutionType getResultLevelReporting() {
        return resultLevelReporting;
    }

    public void setResultLevelReporting(TestCaseExecutionType resultLevelReporting) {
        this.resultLevelReporting = resultLevelReporting;
    }

    public boolean isCalculateCoverageEnabled() {
        return calculateCoverageEnabled;
    }

    public void setCalculateCoverageEnabled(boolean calculateCoverageEnabled) {
        this.calculateCoverageEnabled = calculateCoverageEnabled;
    }

    public String getDataFolder() {
        return dataFolder;
    }

    public String getTestFolder() {
        return testFolder;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getDatasetURI() {
        return datasetURI;
    }

    public String getEndpointURI() {
        return endpointURI;
    }

    public Collection<String> getEndpointGraphs() {
        return endpointGraphs;
    }

    public String getCustomDereferenceURI() {
        return customDereferenceURI;
    }

    public EnrichedSchemaSource getEnrichedSchema() {
        return enrichedSchema;
    }
}
