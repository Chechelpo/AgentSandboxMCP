package io.github.chechelpo.frplm.sandboxMCP.sessions;

import io.github.chechelpo.frplm.sandboxMCP.workspace.StableWorkspaceManager;
import io.github.chechelpo.frplm.sandboxMCP.workspace.WorkspaceFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class SessionManager {
    private final ConcurrentMap<ClientIdentity, Session> runningSessions =
            new ConcurrentHashMap<>();

    private final AuthenticationFacade authentication;
    private final WorkspaceFactory workspaceFactory;
    private final StableWorkspaceManager stableWorkspaces;

    public SessionManager(
            AuthenticationFacade facade,
            WorkspaceFactory workspaceFactory,
            StableWorkspaceManager stableWorkspaces
    ) {
        this.authentication = facade;
        this.workspaceFactory = workspaceFactory;
        this.stableWorkspaces = stableWorkspaces;
    }

    public Session createNewSession(){
        ClientIdentity identity = authenticatedIdentity();
        Session session = createSession(identity);
        runningSessions.put(identity, session);
        return session;
    }

    public Session getForClient() {
        return getForClient(null);
    }

    public Session getForClient(String workspaceName) {
        ClientIdentity identity = authenticatedIdentity();
        if (workspaceName != null && !workspaceName.isBlank()) {
            return new Session(
                    identity,
                    stableWorkspaces.getByName(workspaceName)
            );
        }
        return runningSessions.computeIfAbsent(identity, this::createSession);
    }

    public ClientIdentity getCurrentIdentity() {
        return authenticatedIdentity();
    }

    private ClientIdentity authenticatedIdentity() {
        Authentication auth = authentication.getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("No authenticated MCP client");
        }

        try {
            return ClientIdentity.from(auth);
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("Unsupported MCP authentication", e);
        }
    }

    private Session createSession(ClientIdentity identity) {
        return new Session(
                identity,
                workspaceFactory.getForIdentity(identity)
        );
    }
}
