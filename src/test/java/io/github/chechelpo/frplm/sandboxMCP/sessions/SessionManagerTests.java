package io.github.chechelpo.frplm.sandboxMCP.sessions;

import io.github.chechelpo.frplm.sandboxMCP.workspace.StableWorkspaceManager;
import io.github.chechelpo.frplm.sandboxMCP.workspace.Workspace;
import io.github.chechelpo.frplm.sandboxMCP.workspace.WorkspaceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionManagerTests {

    private final AuthenticationFacade authentication =
            mock(AuthenticationFacade.class);
    private final WorkspaceFactory workspaceFactory = mock(WorkspaceFactory.class);
    private final StableWorkspaceManager stableWorkspaces =
            mock(StableWorkspaceManager.class);
    private final Workspace transientWorkspace = mock(Workspace.class);
    private final Workspace stableWorkspace = mock(Workspace.class);
    private SessionManager sessions;

    @BeforeEach
    void setUp() {
        var authenticated = UsernamePasswordAuthenticationToken.authenticated(
                "agent-one",
                null,
                List.of()
        );
        when(authentication.getAuthentication()).thenReturn(authenticated);
        when(workspaceFactory.getForIdentity(new ClientIdentity(
                "agent-one",
                "api-key"
        ))).thenReturn(transientWorkspace);
        when(stableWorkspaces.getByName("shared-notes"))
                .thenReturn(stableWorkspace);
        sessions = new SessionManager(
                authentication,
                workspaceFactory,
                stableWorkspaces
        );
    }

    @Test
    void omittedWorkspaceNameUsesTransientWorkspace() {
        assertSame(transientWorkspace, sessions.getForClient(null).workspace());
        assertSame(transientWorkspace, sessions.getForClient(" ").workspace());
        verify(stableWorkspaces, never()).getByName("shared-notes");
    }

    @Test
    void workspaceNameSelectsStableWorkspace() {
        assertSame(
                stableWorkspace,
                sessions.getForClient("shared-notes").workspace()
        );
        verify(stableWorkspaces).getByName("shared-notes");
    }
}
