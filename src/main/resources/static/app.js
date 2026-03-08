const yamlInput = document.getElementById("yamlInput");
const output = document.getElementById("propertiesOutput");
const convertBtn = document.getElementById("convertBtn");
const clearBtn = document.getElementById("clearBtn");
const copyBtn = document.getElementById("copyBtn");
const status = document.getElementById("status");

const REQUEST_STATIC_KEY = "YmlProp2026StaticKey9f3b7c1d5a2e8f4b";
const AES_GCM_IV_LENGTH = 12;
const PBKDF2_ITERATIONS = 65536;

let csrfToken = null;
let csrfHeaderName = "X-XSRF-TOKEN";

function setStatus(message, type) {
    status.textContent = message;
    status.className = "status" + (type ? " " + type : "");
}

function bytesToBase64(bytes) {
    let binary = "";
    bytes.forEach((b) => {
        binary += String.fromCharCode(b);
    });
    return btoa(binary);
}

function base64ToBytes(base64) {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i += 1) {
        bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
}

async function deriveStaticAesKey() {
    const encoded = new TextEncoder().encode(REQUEST_STATIC_KEY);
    const digest = await crypto.subtle.digest("SHA-256", encoded);
    return crypto.subtle.importKey("raw", digest, { name: "AES-GCM" }, false, ["encrypt"]);
}

async function encryptRequestWithStaticKey(plainText) {
    const key = await deriveStaticAesKey();
    const iv = crypto.getRandomValues(new Uint8Array(AES_GCM_IV_LENGTH));
    const data = new TextEncoder().encode(plainText);
    const encryptedBuffer = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, data);

    const encrypted = new Uint8Array(encryptedBuffer);
    const payload = new Uint8Array(iv.length + encrypted.length);
    payload.set(iv, 0);
    payload.set(encrypted, iv.length);

    return bytesToBase64(payload);
}

async function deriveDynamicAesKey(responseKeyBase64, responseSaltBase64) {
    const pbkdf2PasswordBytes = new TextEncoder().encode(responseKeyBase64);
    const salt = base64ToBytes(responseSaltBase64);

    const baseKey = await crypto.subtle.importKey("raw", pbkdf2PasswordBytes, "PBKDF2", false, ["deriveKey"]);
    return crypto.subtle.deriveKey(
        {
            name: "PBKDF2",
            salt,
            iterations: PBKDF2_ITERATIONS,
            hash: "SHA-256"
        },
        baseKey,
        { name: "AES-GCM", length: 256 },
        false,
        ["decrypt"]
    );
}

async function decryptResponseWithDynamicKey(encryptedBase64, responseKeyBase64, responseSaltBase64) {
    const key = await deriveDynamicAesKey(responseKeyBase64, responseSaltBase64);
    const payload = base64ToBytes(encryptedBase64);

    if (payload.length <= AES_GCM_IV_LENGTH) {
        throw new Error("Invalid encrypted response payload");
    }

    const iv = payload.slice(0, AES_GCM_IV_LENGTH);
    const cipherText = payload.slice(AES_GCM_IV_LENGTH);
    const plainBuffer = await crypto.subtle.decrypt({ name: "AES-GCM", iv }, key, cipherText);

    return new TextDecoder().decode(plainBuffer);
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
    setStatus("Encrypting request and calling API...", "");

    try {
        if (!csrfToken) {
            await loadCsrfToken();
        }

        const encryptedRequestBody = await encryptRequestWithStaticKey(payload);

        let response = await fetch("/api/yaml/to-properties", {
            method: "POST",
            headers: {
                "Content-Type": "text/plain",
                "Accept": "text/plain",
                [csrfHeaderName]: csrfToken
            },
            credentials: "same-origin",
            body: encryptedRequestBody
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
                body: encryptedRequestBody
            });
        }

        const responseBody = await response.text();

        if (!response.ok) {
            output.value = "";
            setStatus(responseBody || "Conversion failed.", "error");
            return;
        }

        const responseKey = response.headers.get("X-Response-Key");
        const responseSalt = response.headers.get("X-Response-Salt");
        if (!responseKey || !responseSalt) {
            throw new Error("Missing encryption headers in response");
        }

        const decryptedResponse = await decryptResponseWithDynamicKey(responseBody, responseKey, responseSalt);
        output.value = decryptedResponse;
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
