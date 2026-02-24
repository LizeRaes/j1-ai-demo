package com.example.ticket.config;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;

@RequestScoped
public class ActorContext {
    @Context
    ContainerRequestContext requestContext;
    
    @Inject
    TeamUserMapper teamUserMapper;

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
        // Delegate to ApplicationScoped service that works in async contexts
        return teamUserMapper.getDefaultUserIdForTeam(team);
    }
}
