package com.mediflow.ticketing.config;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Application-scoped service for team-to-user mapping.
 * Can be used in async contexts where RequestScoped beans are not available.
 */
@ApplicationScoped
public class TeamUserMapper {
    public String getDefaultUserIdForTeam(String team) {
        if (team == null) return "demo-user";
        
        // Map team to default user (teams are now lowercase)
        String teamLower = team.toLowerCase();
        switch (teamLower) {
            case "dispatch":
                return "dispatch-user1";
            case "billing":
                return "billing-user1";
            case "reschedule":
                return "reschedule-user1";
            case "engineering":
                return "engineering-user1";
            default:
                // Fallback: use team name as-is with -user1 suffix
                return teamLower + "-user1";
        }
    }
}
