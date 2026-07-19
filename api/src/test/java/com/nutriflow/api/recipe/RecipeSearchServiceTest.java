package com.nutriflow.api.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nutriflow.api.common.DomainException;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class RecipeSearchServiceTest {

    @Mock private MongoTemplate mongoTemplate;

    @Test
    void translatesWhitelistedFiltersAndPagination() {
        when(mongoTemplate.find(
                        org.mockito.ArgumentMatchers.any(Query.class),
                        eq(RecipeDocument.class)))
                .thenReturn(List.of());
        RecipeSearchService service = new RecipeSearchService(mongoTemplate);

        service.search(
                new RecipeSearchCriteria(
                        DietType.DIABETIC_FRIENDLY,
                        List.of(
                                new RecipeFilter(
                                        "attributes.glycemicIndex",
                                        FilterOperator.LT,
                                        50)),
                        2,
                        25));

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(RecipeDocument.class));
        Query query = queryCaptor.getValue();
        Document queryDocument = query.getQueryObject();
        assertThat(query.getSkip()).isEqualTo(50);
        assertThat(query.getLimit()).isEqualTo(25);
        @SuppressWarnings("unchecked")
        List<Document> clauses = (List<Document>) queryDocument.get("$and");
        assertThat(clauses)
                .contains(
                        new Document("active", true),
                        new Document("dietType", DietType.DIABETIC_FRIENDLY),
                        new Document(
                                "dietAttributes.glycemicIndex",
                                new Document("$lt", 50)));
    }

    @Test
    void rejectsUnknownPathsAndNonNumericRangeValues() {
        RecipeSearchService service = new RecipeSearchService(mongoTemplate);

        assertThatThrownBy(
                        () ->
                                service.search(
                                        new RecipeSearchCriteria(
                                                null,
                                                List.of(
                                                        new RecipeFilter(
                                                                "attributes.secret",
                                                                FilterOperator.EQ,
                                                                true)),
                                                0,
                                                20)))
                .isInstanceOf(DomainException.class)
                .extracting("fieldPath")
                .isEqualTo("recipeSearch.filters[0].path");

        assertThatThrownBy(
                        () ->
                                service.search(
                                        new RecipeSearchCriteria(
                                                null,
                                                List.of(
                                                        new RecipeFilter(
                                                                "attributes.glycemicIndex",
                                                                FilterOperator.LT,
                                                                "50")),
                                                0,
                                                20)))
                .isInstanceOf(DomainException.class)
                .extracting("fieldPath")
                .isEqualTo("recipeSearch.filters[0].value");
    }
}
