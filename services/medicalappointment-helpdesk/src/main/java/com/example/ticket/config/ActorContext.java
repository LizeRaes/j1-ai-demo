package com.example.ticket.config;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;

@RequestScoped
public class ActorContext {
    @Context
    ContainerRequestContext requestContext;

    public String getActorId() {
        if (requestContext == null) return "demo-user";
        String actorId = requestContext.getHeaderString("X-Actor-Id");
        return actorId != null ? actorId : "demo-user";
    }

    public String getActorRole() {
        if (requestContext == null) return "USER";
        String role = requestContext.getHeaderString("X-Actor-Role");
        return role != null ? role : "USER";
    }

    public String getActorTeam() {
        if (requestContext == null) return "DISPATCHER";
        String team = requestContext.getHeaderString("X-Actor-Team");
        return team != null ? team : "DISPATCHER";
    }

    public String getDefaultUserIdForTeam(String team) {
        if (team == null) return "demo-user";

        String teamLower = team.toLowerCase();
        return switch (teamLower) {
            case "dispatch" -> "dispatch-user1";
            case "billing" -> "billing-user1";
            case "reschedule" -> "reschedule-user1";
            case "engineering" -> "engineering-user1";
            case String _ -> teamLower + "-user1";
        };
    }
}
