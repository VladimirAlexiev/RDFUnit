package org.aksw.databugger.tests.executors;

import com.hp.hpl.jena.query.*;
import org.aksw.databugger.sources.Source;
import org.aksw.databugger.tests.TestCase;
import org.aksw.databugger.tests.TestSuite;
import org.aksw.databugger.tests.results.AggregatedTestCaseResult;
import org.aksw.databugger.tests.results.TestCaseResult;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Takes a dataset source and executes the test queries against the endpoint
 * Description
 * Created: 9/30/13 11:11 AM
 */
public class TestExecutor {
    private static Logger log = LoggerFactory.getLogger(TestExecutor.class);
    private boolean isCanceled = false;

    private final List<TestExecutorMonitor> progressMonitors = new ArrayList<TestExecutorMonitor>();

    public TestExecutor() {

    }

    public void cancel() {
        isCanceled = true;
    }

    public void execute(Source source, TestSuite testSuite, int resultType, int delay) {
        isCanceled = false;

        /*notify start of testing */
        for (TestExecutorMonitor monitor : progressMonitors) {
            monitor.testingStarted(source, testSuite.size());
        }

        for (TestCase testCase : testSuite.getTestCases()) {
            if (isCanceled) {
                break;
            }

            /*notify start of single test */
            for (TestExecutorMonitor monitor : progressMonitors) {
                monitor.singleTestStarted(testCase);
            }

            List<TestCaseResult> results = new ArrayList<TestCaseResult>();
            switch (resultType) {
                case 1:
                    results = getAggregatedResult(source, testCase);
                    break;
                case 2:
                    //results = getRLOGTestResultResult(source, testCase);
                    break;
                case 3:
                    //results = getExtendedResult(source, testCase);
                    break;
                default:
                    results = getAggregatedResult(source, testCase); //getStatusResult(source, testCase);
            }

            /*notify end of single test */
            for (TestExecutorMonitor monitor : progressMonitors) {
                monitor.singleTestExecuted(testCase, results);
            }

            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
        }

        /*notify end of testing */
        for (TestExecutorMonitor monitor : progressMonitors) {
            monitor.testingFinished();
        }
    }

    private List<TestCaseResult> getAggregatedResult(Source source, TestCase testCase) {

        int total = -1, prevalence = -1;

        try {
            prevalence = getCountNumber(source.getExecutionFactory(), testCase.getSparqlPrevalenceQuery(), "total");
        } catch (QueryParseException e) {
            if (!testCase.getSparqlPrevalence().trim().isEmpty())
                total = -2;
        } catch (Exception e) {
            //query time out total remains -1
            total = -1;
        }

        if (prevalence != 0) {
            // if prevalence !=0 calculate total
            try {
                total = getCountNumber(source.getExecutionFactory(), testCase.getSparqlAsCountQuery(), "total");
            } catch (QueryParseException e) {
                total = -2;
            } catch (Exception e) {
                //query time out total remains -1
                total = -1;
            }
        } else
            // else total will be 0 anyway
            total = 0;

        return Arrays.<TestCaseResult>asList(new AggregatedTestCaseResult(testCase, total, prevalence));

    }

    private int getCountNumber(QueryExecutionFactory model, String query, String var) {
        return getCountNumber(model, QueryFactory.create(query), var);
    }

    private int getCountNumber(QueryExecutionFactory model, Query query, String var) {

        int result = 0;
        QueryExecution qe = null;
        try {
            qe = model.createQueryExecution(query);
            ResultSet results = qe.execSelect();

            if (results != null && results.hasNext()) {
                QuerySolution qs = results.next();
                result = qs.get(var).asLiteral().getInt();
            }
        } finally {
            if (qe != null)
                qe.close();
        }

        return result;

    }

    /*
        private boolean testExists(QueryExecutionFactory qef, String testURI) {

            boolean result = false;
            QueryExecution qe = null;
            try {
                qe = qef.createQueryExecution("select * where { ?s ?p <" + testURI + "> }");
                ResultSet results = qe.execSelect();

                if (results != null && results.hasNext()) {
                    result = true;
                }
            } catch (Exception e) {
                //DO nothing (returns false)
            }   finally {
                if (qe != null)
                    qe.close();
            }
            return result;
        }
    */
    public void addTestExecutorMonitor(TestExecutorMonitor monitor) {
        progressMonitors.add(monitor);
    }

    public void removeTestExecutorMonitor(TestExecutorMonitor monitor) {
        progressMonitors.remove(monitor);
    }

}
