package com.nutriflow.api.graphql;

import com.nutriflow.api.common.DomainException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import java.util.Map;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class DomainExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(
            Throwable exception, DataFetchingEnvironment environment) {
        if (!(exception instanceof DomainException domainException)) {
            return null;
        }
        ErrorType errorType =
                switch (domainException.getCode()) {
                    case "NOT_FOUND" -> ErrorType.NOT_FOUND;
                    case "VALIDATION_ERROR" -> ErrorType.BAD_REQUEST;
                    default -> ErrorType.BAD_REQUEST;
                };
        return GraphqlErrorBuilder.newError(environment)
                .errorType(errorType)
                .message(domainException.getMessage())
                .extensions(
                        Map.of(
                                "code", domainException.getCode(),
                                "fieldPath", domainException.getFieldPath()))
                .build();
    }
}
