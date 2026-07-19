package com.nutriflow.api.graphql;

import com.nutriflow.api.dashboard.ClientDashboard;
import com.nutriflow.api.dashboard.ClientDashboardService;
import static com.nutriflow.api.common.DomainErrors.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class DashboardGraphQlController {

    private final ClientDashboardService dashboardService;

    @QueryMapping
    public ClientDashboard clientDashboard(@Argument String clientId) {
        try {
            return dashboardService.get(java.util.UUID.fromString(clientId));
        } catch (IllegalArgumentException exception) {
            throw validation("clientDashboard.clientId", "ID must be a valid UUID");
        }
    }
}
