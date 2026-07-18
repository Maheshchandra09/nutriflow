package com.nutriflow.api.graphql;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class SystemController {

    @QueryMapping
    public String status() {
        return "NutriFlow API is running";
    }
}

