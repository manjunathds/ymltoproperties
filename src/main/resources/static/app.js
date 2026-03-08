const yamlInput = document.getElementById("yamlInput");
const output = document.getElementById("propertiesOutput");
const convertBtn = document.getElementById("convertBtn");
const clearBtn = document.getElementById("clearBtn");
const copyBtn = document.getElementById("copyBtn");
const status = document.getElementById("status");

let csrfToken = null;
let csrfHeaderName = "X-XSRF-TOKEN";

function setStatus(message, type) {
    status.textContent = message;
    status.className = "status" + (type ? " " + type : "");
}

async function loadCsrfToken() {
    const response = await fetch("/api/csrf", {
        method: "GET",
        headers: { "Accept": "application/json" },
        credentials: "same-origin"
    });

    if (!response.ok) {
        throw new Error("Unable to initialize CSRF token");
    }

    const data = await response.json();
    csrfToken = data.token;
    csrfHeaderName = data.headerName || "X-XSRF-TOKEN";
}

async function convertYaml() {
    const payload = yamlInput.value;
    if (!payload.trim()) {
        setStatus("Paste YAML content first.", "error");
        return;
    }

    convertBtn.disabled = true;
    convertBtn.textContent = "Converting...";
    setStatus("Calling API...", "");

    try {
        if (!csrfToken) {
            await loadCsrfToken();
        }

        let response = await fetch("/api/yaml/to-properties", {
            method: "POST",
            headers: {
                "Content-Type": "text/plain",
                "Accept": "text/plain",
                [csrfHeaderName]: csrfToken
            },
            credentials: "same-origin",
            body: payload
        });

        if (response.status === 403) {
            await loadCsrfToken();
            response = await fetch("/api/yaml/to-properties", {
                method: "POST",
                headers: {
                    "Content-Type": "text/plain",
                    "Accept": "text/plain",
                    [csrfHeaderName]: csrfToken
                },
                credentials: "same-origin",
                body: payload
            });
        }

        const body = await response.text();

        if (!response.ok) {
            output.value = "";
            setStatus(body || "Conversion failed.", "error");
            return;
        }

        output.value = body;
        setStatus("Converted successfully.", "ok");
    } catch (error) {
        output.value = "";
        setStatus("Security/network error: " + error.message, "error");
    } finally {
        convertBtn.disabled = false;
        convertBtn.textContent = "Convert";
    }
}

convertBtn.addEventListener("click", convertYaml);

clearBtn.addEventListener("click", () => {
    yamlInput.value = "";
    output.value = "";
    setStatus("", "");
    yamlInput.focus();
});

copyBtn.addEventListener("click", async () => {
    if (!output.value) {
        setStatus("No output to copy.", "error");
        return;
    }
    try {
        await navigator.clipboard.writeText(output.value);
        setStatus("Output copied to clipboard.", "ok");
    } catch (error) {
        setStatus("Clipboard copy failed.", "error");
    }
});

loadCsrfToken().catch(() => setStatus("Unable to initialize secure session.", "error"));
