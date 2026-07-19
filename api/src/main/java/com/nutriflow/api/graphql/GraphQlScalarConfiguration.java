package com.nutriflow.api.graphql;

import graphql.GraphQLContext;
import graphql.language.StringValue;
import graphql.scalars.ExtendedScalars;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

@Configuration
public class GraphQlScalarConfiguration {

    @Bean
    RuntimeWiringConfigurer scalarWiringConfigurer() {
        return wiringBuilder ->
                wiringBuilder
                        .scalar(ExtendedScalars.Json)
                        .scalar(ExtendedScalars.Date)
                        .scalar(dateTimeScalar());
    }

    private GraphQLScalarType dateTimeScalar() {
        return GraphQLScalarType.newScalar()
                .name("DateTime")
                .description("An ISO-8601 UTC timestamp")
                .coercing(
                        new Coercing<Object, String>() {
                            @Override
                            public String serialize(
                                    Object value,
                                    GraphQLContext context,
                                    Locale locale) {
                                if (value instanceof Instant instant) {
                                    return instant.toString();
                                }
                                if (value instanceof OffsetDateTime dateTime) {
                                    return dateTime.toInstant().toString();
                                }
                                throw new CoercingSerializeException(
                                        "DateTime must be an Instant or OffsetDateTime");
                            }

                            @Override
                            public Object parseValue(
                                    Object input,
                                    GraphQLContext context,
                                    Locale locale) {
                                return parse(input, false);
                            }

                            @Override
                            public Object parseLiteral(
                                    graphql.language.Value<?> input,
                                    graphql.execution.CoercedVariables variables,
                                    GraphQLContext context,
                                    Locale locale) {
                                if (!(input instanceof StringValue stringValue)) {
                                    throw new CoercingParseLiteralException(
                                            "DateTime literal must be a string");
                                }
                                return parse(stringValue.getValue(), true);
                            }

                            private Instant parse(Object input, boolean literal) {
                                if (!(input instanceof String value)) {
                                    if (literal) {
                                        throw new CoercingParseLiteralException(
                                                "DateTime must be an ISO-8601 string");
                                    }
                                    throw new CoercingParseValueException(
                                            "DateTime must be an ISO-8601 string");
                                }
                                try {
                                    return Instant.parse(value);
                                } catch (DateTimeParseException exception) {
                                    if (literal) {
                                        throw new CoercingParseLiteralException(
                                                "Invalid ISO-8601 DateTime", exception);
                                    }
                                    throw new CoercingParseValueException(
                                            "Invalid ISO-8601 DateTime", exception);
                                }
                            }
                        })
                .build();
    }
}
