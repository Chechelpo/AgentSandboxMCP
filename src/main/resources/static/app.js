const form = document.querySelector("#path-form");
const statusBadge = document.querySelector("#connection-status");
const message = document.querySelector("#message");
const saveButton = document.querySelector("#save-button");
const reloadButton = document.querySelector("#reload-button");
const keyForm = document.querySelector("#key-form");
const keyName = document.querySelector("#key-name");
const keySecret = document.querySelector("#key-secret");
const createKeyButton = document.querySelector("#create-key-button");
const createdKey = document.querySelector("#created-key");
const createdKeyValue = document.querySelector("#created-key-value");
const copyKeyButton = document.querySelector("#copy-key-button");
const keyMessage = document.querySelector("#key-message");
const keyList = document.querySelector("#key-list");
const accountUsername = document.querySelector("#account-username");
const fields = {
    AGENT_WORKSPACES: document.querySelector("#agent-workspaces"),
    STABLE_WORKSPACES: document.querySelector("#stable-workspaces"),
    WORKSPACE_CONFIG_FILE: document.querySelector("#workspace-config")
};

let csrf;

async function request(url, options = {}) {
    const response = await fetch(url, {
        credentials: "same-origin",
        ...options
    });

    if (!response.ok) {
        let detail = `${response.status} ${response.statusText}`;
        try {
            const body = await response.json();
            detail = body.message || body.detail || body.error || detail;
        } catch (_) {
            // The status text is enough for non-JSON error responses.
        }
        throw new Error(detail);
    }

    if (response.status === 204) {
        return undefined;
    }

    return response.json();
}

function setBusy(busy) {
    saveButton.disabled = busy;
    reloadButton.disabled = busy;
    createKeyButton.disabled = busy;
}

function showMessage(text, kind = "") {
    message.textContent = text;
    message.className = kind ? `message message-${kind}` : "message";
}

function setConnection(text, kind) {
    statusBadge.textContent = text;
    statusBadge.className = `status status-${kind}`;
}

async function loadConfiguration() {
    setBusy(true);
    showMessage("Loading configuration…");
    setConnection("Loading", "loading");

    try {
        const [paths, csrfDetails, keys, account] = await Promise.all([
            request("/api/config/paths"),
            request("/api/config/paths/csrf"),
            request("/api/config/keys"),
            request("/api/config/account")
        ]);
        csrf = csrfDetails;

        for (const [name, input] of Object.entries(fields)) {
            input.value = paths[name] || "";
        }
        renderKeys(keys);
        accountUsername.textContent = account.username;

        showMessage("");
        setConnection("Connected", "ready");
    } catch (error) {
        showMessage(`Could not load configuration: ${error.message}`, "error");
        setConnection("Unavailable", "error");
    } finally {
        setBusy(false);
    }
}

function showKeyMessage(text, kind = "") {
    keyMessage.textContent = text;
    keyMessage.className = kind ? `message message-${kind}` : "message";
}

function renderKeys(keys) {
    keyList.replaceChildren();

    if (keys.length === 0) {
        const empty = document.createElement("p");
        empty.className = "empty-state";
        empty.textContent = "No API keys configured. MCP connections will be rejected.";
        keyList.append(empty);
        return;
    }

    for (const key of keys) {
        const row = document.createElement("div");
        row.className = "key-row";

        const identity = document.createElement("div");
        const name = document.createElement("strong");
        name.textContent = key.name;
        const secret = document.createElement("code");
        secret.className = "key-secret-value";
        secret.textContent = key.secret || `${key.prefix}… (not recoverable)`;
        const metadata = document.createElement("span");
        metadata.textContent = new Date(key.createdAt).toLocaleString();
        identity.append(name, secret, metadata);

        const buttons = document.createElement("div");
        buttons.className = "key-row-actions";

        const copy = document.createElement("button");
        copy.className = "button button-secondary";
        copy.type = "button";
        copy.textContent = "Copy";
        copy.disabled = !key.secret;
        copy.title = key.secret
            ? "Copy API key"
            : "This key was created before plaintext storage was enabled. Recreate it to make it copyable.";
        copy.addEventListener("click", () => copyApiKey(key, copy));

        const revoke = document.createElement("button");
        revoke.className = "button button-danger";
        revoke.type = "button";
        revoke.textContent = "Revoke";
        revoke.addEventListener("click", () => revokeKey(key));

        buttons.append(copy, revoke);
        row.append(identity, buttons);
        keyList.append(row);
    }
}

async function refreshKeys() {
    renderKeys(await request("/api/config/keys"));
}

async function createKey(event) {
    event.preventDefault();
    if (!keyForm.reportValidity()) {
        return;
    }

    setBusy(true);
    showKeyMessage("Creating key…");
    createdKey.hidden = true;

    try {
        if (!csrf) {
            csrf = await request("/api/config/paths/csrf");
        }

        const result = await request("/api/config/keys", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                [csrf.headerName]: csrf.token
            },
            body: JSON.stringify({
                name: keyName.value.trim(),
                key: keySecret.value || null
            })
        });

        createdKeyValue.textContent = result.secret;
        createdKey.hidden = false;
        keyForm.reset();
        await refreshKeys();
        showKeyMessage("API key created.", "success");
    } catch (error) {
        showKeyMessage(`Could not create API key: ${error.message}`, "error");
    } finally {
        setBusy(false);
    }
}

async function revokeKey(key) {
    if (!window.confirm(`Revoke the API key for ${key.name}?`)) {
        return;
    }

    setBusy(true);
    showKeyMessage("Revoking key…");

    try {
        if (!csrf) {
            csrf = await request("/api/config/paths/csrf");
        }
        await request(`/api/config/keys/${encodeURIComponent(key.id)}`, {
            method: "DELETE",
            headers: {
                [csrf.headerName]: csrf.token
            }
        });
        await refreshKeys();
        showKeyMessage("API key revoked.", "success");
    } catch (error) {
        showKeyMessage(`Could not revoke API key: ${error.message}`, "error");
    } finally {
        setBusy(false);
    }
}

async function copyCreatedKey() {
    await copyText(createdKeyValue.textContent, copyKeyButton);
}

async function copyApiKey(key, button) {
    if (key.secret) {
        await copyText(key.secret, button);
    }
}

async function copyText(value, button) {
    try {
        await navigator.clipboard.writeText(value);
        button.textContent = "Copied";
        window.setTimeout(() => {
            button.textContent = "Copy";
        }, 1500);
    } catch (_) {
        showKeyMessage("Clipboard access failed. Select the visible key and copy it manually.", "error");
    }
}

async function saveConfiguration(event) {
    event.preventDefault();
    if (!form.reportValidity()) {
        return;
    }

    setBusy(true);
    showMessage("Saving…");

    try {
        if (!csrf) {
            csrf = await request("/api/config/paths/csrf");
        }

        for (const [name, input] of Object.entries(fields)) {
            await request(`/api/config/paths/${encodeURIComponent(name)}`, {
                method: "PUT",
                headers: {
                    "Content-Type": "application/json",
                    [csrf.headerName]: csrf.token
                },
                body: JSON.stringify({path: input.value.trim()})
            });
        }

        showMessage("Paths saved successfully.", "success");
        setConnection("Connected", "ready");
    } catch (error) {
        showMessage(`Could not save configuration: ${error.message}`, "error");
        setConnection("Error", "error");
    } finally {
        setBusy(false);
    }
}

form.addEventListener("submit", saveConfiguration);
reloadButton.addEventListener("click", loadConfiguration);
keyForm.addEventListener("submit", createKey);
copyKeyButton.addEventListener("click", copyCreatedKey);
loadConfiguration();
