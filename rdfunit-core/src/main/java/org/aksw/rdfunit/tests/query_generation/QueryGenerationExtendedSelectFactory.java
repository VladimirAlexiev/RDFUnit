package org.aksw.rdfunit.tests.query_generation;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import org.aksw.rdfunit.services.PrefixNSService;
import org.aksw.rdfunit.tests.TestCase;
import org.aksw.rdfunit.tests.results.ResultAnnotation;

import java.util.HashSet;
import java.util.Set;

/**
 * Factory that returns select queries and besides
 * SELECT ?resource it adds all variable annotations
 *
 * @author Dimitris Kontokostas
 * @since 7/25/14 10:02 PM
 * @version $Id: $Id
 */
public class QueryGenerationExtendedSelectFactory implements QueryGenerationFactory {

    private static final String selectDistinctResource = " SELECT DISTINCT ?resource ";

    private static final String resourceVar = "?resource";

    private static final String whereClause = " WHERE ";

    private static final String orderByResourceAsc = "  ORDER BY ASC(?resource) ";

    /** {@inheritDoc} */
    @Override
    public String getSparqlQueryAsString(TestCase testCase) {

        StringBuilder sb = new StringBuilder();

        sb.append(PrefixNSService.getSparqlPrefixDecl());
        sb.append(selectDistinctResource);

        Set<String> existingVariables = new HashSet<>();
        existingVariables.add(resourceVar);

        // Add all defined variables in the query
        for (ResultAnnotation annotation : testCase.getVariableAnnotations()) {
            String value = annotation.getAnnotationValue().toString().trim();

            // if variable is not redefined don't add it again
            // This is needed if the same variable takes part in different annotations
            if (!existingVariables.contains(value)) {
                sb.append(" ");
                sb.append(value);
                sb.append(" ");

                existingVariables.add(value);
            }
        }
        sb.append(whereClause);
        sb.append(testCase.getSparqlWhere());
        sb.append(orderByResourceAsc);
        return sb.toString();
    }

    /** {@inheritDoc} */
    @Override
    public Query getSparqlQuery(TestCase testCase) {
        return QueryFactory.create(this.getSparqlQueryAsString(testCase));
    }
}
