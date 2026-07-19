package com.nutriflow.api.recipe;

import static com.nutriflow.api.common.DomainErrors.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecipeSearchService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final Map<String, String> ALLOWED_PATHS =
            Map.ofEntries(
                    Map.entry("macros.calories", "macros.calories"),
                    Map.entry("macros.proteinGrams", "macros.proteinGrams"),
                    Map.entry(
                            "macros.carbohydrateGrams",
                            "macros.carbohydrateGrams"),
                    Map.entry("macros.fatGrams", "macros.fatGrams"),
                    Map.entry("attributes.netCarbsG", "dietAttributes.netCarbsG"),
                    Map.entry("attributes.fatG", "dietAttributes.fatG"),
                    Map.entry("attributes.proteinG", "dietAttributes.proteinG"),
                    Map.entry("attributes.ketoRatio", "dietAttributes.ketoRatio"),
                    Map.entry(
                            "attributes.proteinSource",
                            "dietAttributes.proteinSource"),
                    Map.entry(
                            "attributes.b12Fortified",
                            "dietAttributes.b12Fortified"),
                    Map.entry(
                            "attributes.veganCertified",
                            "dietAttributes.veganCertified"),
                    Map.entry(
                            "attributes.glycemicIndex",
                            "dietAttributes.glycemicIndex"),
                    Map.entry("attributes.sugarG", "dietAttributes.sugarG"),
                    Map.entry(
                            "attributes.carbExchangeUnits",
                            "dietAttributes.carbExchangeUnits"));

    private final MongoTemplate mongoTemplate;

    public List<RecipeDocument> search(RecipeSearchCriteria search) {
        if (search == null) {
            throw validation("recipeSearch", "Search input is required");
        }
        int page = search.page() == null ? 0 : search.page();
        int size = search.size() == null ? DEFAULT_PAGE_SIZE : search.size();
        if (page < 0) {
            throw validation("recipeSearch.page", "Page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw validation(
                    "recipeSearch.size", "Page size must be between 1 and 100");
        }

        List<Criteria> criteria = new ArrayList<>();
        criteria.add(Criteria.where("active").is(true));
        if (search.dietType() != null) {
            criteria.add(Criteria.where("dietType").is(search.dietType()));
        }
        List<RecipeFilter> filters =
                search.filters() == null ? List.of() : search.filters();
        for (int index = 0; index < filters.size(); index++) {
            criteria.add(translateFilter(filters.get(index), index));
        }

        Query query =
                new Query(new Criteria().andOperator(criteria))
                        .with(PageRequest.of(page, size))
                        .with(Sort.by(Sort.Direction.ASC, "name", "_id"));
        return mongoTemplate.find(query, RecipeDocument.class);
    }

    private Criteria translateFilter(RecipeFilter filter, int index) {
        String fieldPath = "recipeSearch.filters[" + index + "]";
        if (filter == null || filter.path() == null) {
            throw validation(fieldPath + ".path", "Filter path is required");
        }
        String storedPath = ALLOWED_PATHS.get(filter.path());
        if (storedPath == null) {
            throw validation(fieldPath + ".path", "Filter path is not allowed");
        }
        if (filter.operator() == null) {
            throw validation(fieldPath + ".operator", "Filter operator is required");
        }
        if (filter.value() == null) {
            throw validation(fieldPath + ".value", "Filter value is required");
        }
        if (filter.operator() != FilterOperator.EQ
                && !(filter.value() instanceof Number)) {
            throw validation(
                    fieldPath + ".value", "Range operators require a numeric value");
        }

        Criteria criterion = Criteria.where(storedPath);
        return switch (filter.operator()) {
            case EQ -> criterion.is(filter.value());
            case LT -> criterion.lt(filter.value());
            case LTE -> criterion.lte(filter.value());
            case GT -> criterion.gt(filter.value());
            case GTE -> criterion.gte(filter.value());
        };
    }
}
