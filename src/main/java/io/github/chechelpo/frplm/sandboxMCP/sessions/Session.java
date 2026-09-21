package io.github.chechelpo.frplm.sandboxMCP.sessions;

import io.github.chechelpo.frplm.sandboxMCP.workspace.Workspace;

import java.util.Objects;

public record Session(
        ClientIdentity owner,
        Workspace workspace
) {

    public Session {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(workspace, "workspace must not be null");
    }

    public boolean isOwnedBy(ClientIdentity caller) {
        return owner.equals(caller);
    }
}