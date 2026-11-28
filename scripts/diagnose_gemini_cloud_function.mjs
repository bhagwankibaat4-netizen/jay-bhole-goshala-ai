#!/usr/bin/env node
/**
 * Jay Bhole Gosala Samiti — Gemini API & Secure Firebase Cloud Function Diagnostic Script
 *
 * Sends a minimal "Hello, Goshala" diagnostic request to the configured Firebase Cloud Function
 * backend (and optionally probes the Firebase AI Logic backend) to verify API connectivity and
 * log exact HTTP status codes — including 401/403 App Check/Auth/Permission errors, 404 undeployed
 * endpoint errors, 429 quota limits, 5xx server failures, and network errors.
 *
 * Security:
 *  - NEVER hardcodes or accepts client-side Gemini API keys in URLs.
 *  - Blocks direct calls to generativelanguage.googleapis.com.
 *  - Redacts any API keys or bearer/JWT tokens before printing logs.
 *
 * Usage:
 *  node scripts/diagnose_gemini_cloud_function.mjs
 *  GOSHALA_CLOUD_FUNCTION_URL="https://us-central1-gen-lang-client-0821187064.cloudfunctions.net/vetAssistant" \
 *    FIREBASE_APP_CHECK_TOKEN="<valid-app-check-jwt>" \
 *    node scripts/diagnose_gemini_cloud_function.mjs
 *
 * Options:
 *  --endpoint <https-url>   Override Firebase Cloud Function endpoint URL
 *  --prompt <text>          Override diagnostic prompt (default: "Hello, Goshala")
 *  --model <model-id>       Target Gemini model (default: "gemini-3.8-flash")
 *  --skip-ai-logic          Skip secondary Firebase AI Logic endpoint check
 *  --timeout-ms <ms>        Request timeout in milliseconds (default: 30000)
 *  --json                   Output structured JSON report
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const DEFAULT_PROJECT_ID = 'gen-lang-client-0821187064';
export const DEFAULT_REGION = 'us-central1';
export const DEFAULT_FUNCTION_NAME = 'vetAssistant';
export const DEFAULT_MODEL = 'gemini-3.8-flash';
export const FALLBACK_MODEL = 'gemini-3.5-flash-lite';
export const DEFAULT_PROMPT = 'Hello, Goshala';
export const DEFAULT_TIMEOUT_MS = 30_000;

export function buildDefaultCloudFunctionUrl(
  projectId = DEFAULT_PROJECT_ID,
  region = DEFAULT_REGION,
  functionName = DEFAULT_FUNCTION_NAME
) {
  return `https://${region}-${projectId}.cloudfunctions.net/${functionName}`;
}

export function redactSecrets(input) {
  return String(input ?? '')
    .replace(/AIza[0-9A-Za-z_-]{16,}/g, '[REDACTED_API_KEY]')
    .replace(/Bearer\s+[A-Za-z0-9._-]{12,}/gi, 'Bearer [REDACTED_TOKEN]')
    .replace(/eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}/g, '[REDACTED_JWT]');
}

export function validateBackendEndpoint(rawUrl) {
  const url = String(rawUrl || '').trim();
  if (!url) {
    return { valid: false, reason: 'missing_endpoint_url' };
  }
  let parsed;
  try {
    parsed = new URL(url);
  } catch {
    return { valid: false, reason: 'invalid_url_format' };
  }
  const isLocalEmulator =
    parsed.protocol === 'http:' &&
    (parsed.hostname === '127.0.0.1' || parsed.hostname === 'localhost');
  if (parsed.protocol !== 'https:' && !isLocalEmulator) {
    return { valid: false, reason: 'https_required' };
  }
  if (/generativelanguage\.googleapis\.com/i.test(parsed.hostname)) {
    return { valid: false, reason: 'direct_client_gemini_api_forbidden' };
  }
  if (parsed.searchParams.has('key') || parsed.searchParams.has('apiKey')) {
    return { valid: false, reason: 'client_api_key_in_url_forbidden' };
  }
  return { valid: true, normalizedUrl: parsed.toString() };
}

export function classifyDiagnosticStatus(status, responseBodyText = '') {
  const bodyLower = String(responseBodyText || '').toLowerCase();

  if (status >= 200 && status < 300) {
    return {
      category: 'OK',
      severity: 'PASS',
      summary: `HTTP ${status} OK — Backend responded successfully.`,
      remediation: 'Connectivity verified.'
    };
  }
  if (status === 401) {
    const isAppCheck =
      bodyLower.includes('app check') ||
      bodyLower.includes('appcheck') ||
      bodyLower.includes('unauthenticated');
    return {
      category: isAppCheck ? 'HTTP_401_APP_CHECK_UNAUTHENTICATED' : 'HTTP_401_UNAUTHORIZED',
      severity: 'FAIL',
      summary: isAppCheck
        ? 'HTTP 401 UNAUTHENTICATED — Missing or invalid Firebase App Check token.'
        : 'HTTP 401 UNAUTHORIZED — Authentication rejected by backend.',
      remediation:
        'Pass a valid X-Firebase-AppCheck JWT (FIREBASE_APP_CHECK_TOKEN) or valid Firebase Auth ID token (FIREBASE_ID_TOKEN), and ensure App Check debug token / reCAPTCHA Enterprise is registered.'
    };
  }
  if (status === 403) {
    const isServiceDisabled =
      bodyLower.includes('service_disabled') ||
      bodyLower.includes('api has not been used') ||
      bodyLower.includes('not enabled') ||
      bodyLower.includes('billing');
    const isAppCheck =
      bodyLower.includes('app check') ||
      bodyLower.includes('appcheck') ||
      bodyLower.includes('attestation');
    return {
      category: isServiceDisabled
        ? 'HTTP_403_SERVICE_DISABLED'
        : isAppCheck
          ? 'HTTP_403_APP_CHECK_REJECTED'
          : 'HTTP_403_FORBIDDEN',
      severity: 'FAIL',
      summary: isServiceDisabled
        ? 'HTTP 403 PERMISSION_DENIED — Required Google/Firebase API or billing is disabled.'
        : isAppCheck
          ? 'HTTP 403 PERMISSION_DENIED — Firebase App Check attestation rejected.'
          : 'HTTP 403 FORBIDDEN — Backend denied permission (IAM invoker, App Check, or API restriction).',
      remediation: isServiceDisabled
        ? 'Verify Firebase AI Logic / Vertex AI API and project permissions in Firebase.'
        : 'Check Cloud Function IAM invoker policy, Firebase App Check enforcement, and registered reCAPTCHA/debug token.'
    };
  }
  if (status === 404) {
    return {
      category: 'HTTP_404_NOT_FOUND',
      severity: 'FAIL',
      summary: 'HTTP 404 NOT_FOUND — Cloud Function endpoint or target Gemini model was not found.',
      remediation:
        'Verify the Firebase Cloud Function is deployed at this exact region/URL (or set GOSHALA_CLOUD_FUNCTION_URL) and confirm the model name is supported (gemini-3.8-flash / gemini-3.5-flash-lite).'
    };
  }
  if (status === 429) {
    return {
      category: 'HTTP_429_QUOTA_OR_RATE_LIMIT',
      severity: 'FAIL',
      summary: 'HTTP 429 RESOURCE_EXHAUSTED — API quota or rate limit exceeded.',
      remediation: 'Wait 30-60 seconds before retrying or verify Gemini quota limits and fallback model routing.'
    };
  }
  if (status >= 500) {
    return {
      category: 'HTTP_5XX_BACKEND_ERROR',
      severity: 'FAIL',
      summary: `HTTP ${status} SERVER_ERROR — Cloud Function or upstream Gemini backend failed.`,
      remediation:
        'Inspect Firebase Cloud Functions logs (`firebase functions:log`) and verify server-side GEMINI_API_KEY secret in Secret Manager.'
    };
  }
  if (status === 0) {
    return {
      category: 'NETWORK_OR_TIMEOUT_ERROR',
      severity: 'FAIL',
      summary: 'Network/Timeout Error — Unable to reach backend endpoint.',
      remediation: 'Check internet connectivity, DNS resolution, firewall rules, and endpoint hostname.'
    };
  }
  return {
    category: `HTTP_${status}_ERROR`,
    severity: 'FAIL',
    summary: `HTTP ${status} — Unexpected response from backend.`,
    remediation: 'Inspect the sanitized response snippet below for details.'
  };
}

export async function diagnoseCloudFunction(options = {}) {
  const endpointUrl =
    options.endpointUrl ||
    process.env.GOSHALA_CLOUD_FUNCTION_URL ||
    buildDefaultCloudFunctionUrl(
      options.projectId || DEFAULT_PROJECT_ID,
      options.region || DEFAULT_REGION,
      options.functionName || DEFAULT_FUNCTION_NAME
    );
  const prompt = String(options.prompt || DEFAULT_PROMPT).trim() || DEFAULT_PROMPT;
  const model = String(options.model || DEFAULT_MODEL).trim() || DEFAULT_MODEL;
  const appCheckToken = String(options.appCheckToken ?? process.env.FIREBASE_APP_CHECK_TOKEN ?? '').trim();
  const idToken = String(options.idToken ?? process.env.FIREBASE_ID_TOKEN ?? '').trim();
  const timeoutMs = Number(options.timeoutMs || DEFAULT_TIMEOUT_MS);
  const fetchImpl = options.fetchImpl || globalThis.fetch;

  const endpointCheck = validateBackendEndpoint(endpointUrl);
  if (!endpointCheck.valid) {
    return {
      target: 'firebase_cloud_function',
      ok: false,
      status: 0,
      endpoint: redactSecrets(endpointUrl),
      prompt,
      model,
      headersAttached: { appCheck: Boolean(appCheckToken), authBearer: Boolean(idToken) },
      latencyMs: 0,
      classification: {
        category: 'INVALID_ENDPOINT',
        severity: 'FAIL',
        summary: `Blocked unsafe or invalid endpoint (${endpointCheck.reason}).`,
        remediation: 'Provide an HTTPS Firebase Cloud Function URL (never generativelanguage.googleapis.com or client API keys).'
      },
      responseSnippet: '',
      error: endpointCheck.reason
    };
  }

  const headers = {
    'Content-Type': 'application/json',
    Accept: 'application/json'
  };
  if (appCheckToken) {
    headers['X-Firebase-AppCheck'] = appCheckToken;
  }
  if (idToken) {
    headers['Authorization'] = `Bearer ${idToken}`;
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const startedAt = Date.now();

  try {
    const response = await fetchImpl(endpointCheck.normalizedUrl, {
      method: 'POST',
      headers,
      body: JSON.stringify({
        prompt,
        model,
        diagnostic: true,
        source: 'jbgs-connectivity-diagnostic'
      }),
      signal: controller.signal
    });
    const latencyMs = Date.now() - startedAt;
    const rawBody = await response.text();
    const safeBody = redactSecrets(rawBody);
    const classification = classifyDiagnosticStatus(response.status, safeBody);

    let parsed = null;
    try {
      parsed = JSON.parse(rawBody);
    } catch {
      parsed = null;
    }

    const replyText = parsed && typeof parsed.text === 'string'
      ? redactSecrets(parsed.text).trim()
      : '';
    const modelUsed = parsed && typeof parsed.modelUsed === 'string' ? parsed.modelUsed : model;
    const sourcesCount = parsed && Array.isArray(parsed.sources) ? parsed.sources.length : 0;

    return {
      target: 'firebase_cloud_function',
      ok: response.ok,
      status: response.status,
      endpoint: endpointCheck.normalizedUrl,
      prompt,
      model,
      modelUsed,
      sourcesCount,
      headersAttached: { appCheck: Boolean(appCheckToken), authBearer: Boolean(idToken) },
      latencyMs,
      classification,
      replyPreview: replyText.slice(0, 240),
      responseSnippet: safeBody.replace(/\s+/g, ' ').trim().slice(0, 320)
    };
  } catch (err) {
    const latencyMs = Date.now() - startedAt;
    const isAbort = err && err.name === 'AbortError';
    const message = isAbort
      ? `Request timed out after ${timeoutMs}ms`
      : redactSecrets(err && err.message ? err.message : String(err));
    return {
      target: 'firebase_cloud_function',
      ok: false,
      status: 0,
      endpoint: endpointCheck.normalizedUrl,
      prompt,
      model,
      headersAttached: { appCheck: Boolean(appCheckToken), authBearer: Boolean(idToken) },
      latencyMs,
      classification: classifyDiagnosticStatus(0, message),
      replyPreview: '',
      responseSnippet: message,
      error: message
    };
  } finally {
    clearTimeout(timer);
  }
}

export function readProjectConfigFromGoogleServices(repoRoot) {
  const filePath = path.join(repoRoot, 'app', 'google-services.json');
  if (!fs.existsSync(filePath)) return null;
  try {
    const parsed = JSON.parse(fs.readFileSync(filePath, 'utf8'));
    const projectId = parsed?.project_info?.project_id || DEFAULT_PROJECT_ID;
    const client = parsed?.client?.[0];
    const packageName = client?.client_info?.android_client_info?.package_name || '';
    const certHash = client?.oauth_client?.find((c) => c?.android_info?.certificate_hash)?.android_info?.certificate_hash || '';
    const firebaseApiKey = client?.api_key?.[0]?.current_key || '';
    return { projectId, packageName, certHash, firebaseApiKey };
  } catch {
    return null;
  }
}

export async function diagnoseAiLogicBackend(options = {}) {
  const projectId = options.projectId || DEFAULT_PROJECT_ID;
  const model = String(options.model || DEFAULT_MODEL).trim() || DEFAULT_MODEL;
  const prompt = String(options.prompt || DEFAULT_PROMPT).trim() || DEFAULT_PROMPT;
  const appCheckToken = String(options.appCheckToken ?? process.env.FIREBASE_APP_CHECK_TOKEN ?? '').trim();
  const firebaseApiKey = String(options.firebaseApiKey || '').trim();
  const packageName = String(options.packageName || '').trim();
  const certHash = String(options.certHash || '').trim();
  const timeoutMs = Number(options.timeoutMs || DEFAULT_TIMEOUT_MS);
  const fetchImpl = options.fetchImpl || globalThis.fetch;

  const url = `https://firebasevertexai.googleapis.com/v1beta/projects/${encodeURIComponent(projectId)}/models/${encodeURIComponent(model)}:generateContent`;
  const headers = {
    'Content-Type': 'application/json',
    Accept: 'application/json'
  };
  if (firebaseApiKey) headers['x-goog-api-key'] = firebaseApiKey;
  if (packageName) headers['X-Android-Package'] = packageName;
  if (certHash) headers['X-Android-Cert'] = certHash;
  if (appCheckToken) headers['X-Firebase-AppCheck'] = appCheckToken;

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const startedAt = Date.now();

  try {
    const response = await fetchImpl(url, {
      method: 'POST',
      headers,
      body: JSON.stringify({
        contents: [{ role: 'user', parts: [{ text: prompt }] }]
      }),
      signal: controller.signal
    });
    const latencyMs = Date.now() - startedAt;
    const rawBody = await response.text();
    const safeBody = redactSecrets(rawBody);
    const classification = classifyDiagnosticStatus(response.status, safeBody);

    let replyPreview = '';
    try {
      const parsed = JSON.parse(rawBody);
      const text = parsed?.candidates?.[0]?.content?.parts?.map((p) => p?.text || '').join('') || '';
      replyPreview = redactSecrets(text).trim().slice(0, 240);
    } catch {
      replyPreview = '';
    }

    return {
      target: 'firebase_ai_logic_backend',
      ok: response.ok,
      status: response.status,
      endpoint: url,
      prompt,
      model,
      headersAttached: {
        appCheck: Boolean(appCheckToken),
        firebaseAppKey: Boolean(firebaseApiKey),
        androidCert: Boolean(packageName && certHash)
      },
      latencyMs,
      classification,
      replyPreview,
      responseSnippet: safeBody.replace(/\s+/g, ' ').trim().slice(0, 320)
    };
  } catch (err) {
    const latencyMs = Date.now() - startedAt;
    const isAbort = err && err.name === 'AbortError';
    const message = isAbort
      ? `Request timed out after ${timeoutMs}ms`
      : redactSecrets(err && err.message ? err.message : String(err));
    return {
      target: 'firebase_ai_logic_backend',
      ok: false,
      status: 0,
      endpoint: url,
      prompt,
      model,
      headersAttached: {
        appCheck: Boolean(appCheckToken),
        firebaseAppKey: Boolean(firebaseApiKey),
        androidCert: Boolean(packageName && certHash)
      },
      latencyMs,
      classification: classifyDiagnosticStatus(0, message),
      replyPreview: '',
      responseSnippet: message,
      error: message
    };
  } finally {
    clearTimeout(timer);
  }
}

function parseCliArgs(argv) {
  const args = {
    endpoint: '',
    prompt: DEFAULT_PROMPT,
    model: DEFAULT_MODEL,
    skipAiLogic: false,
    timeoutMs: DEFAULT_TIMEOUT_MS,
    json: false
  };
  for (let i = 0; i < argv.length; i++) {
    const token = argv[i];
    if (token === '--endpoint' && argv[i + 1]) args.endpoint = argv[++i];
    else if (token === '--prompt' && argv[i + 1]) args.prompt = argv[++i];
    else if (token === '--model' && argv[i + 1]) args.model = argv[++i];
    else if (token === '--timeout-ms' && argv[i + 1]) args.timeoutMs = Number(argv[++i]) || DEFAULT_TIMEOUT_MS;
    else if (token === '--skip-ai-logic') args.skipAiLogic = true;
    else if (token === '--json') args.json = true;
  }
  return args;
}

function formatHumanReport(report) {
  const lines = [];
  lines.push('====================================================================');
  lines.push(' Jay Bhole Gosala Samiti — Gemini API Backend Connectivity Diagnostic');
  lines.push('====================================================================');
  lines.push(`Timestamp : ${report.timestamp}`);
  lines.push(`Prompt    : "${report.prompt}"`);
  lines.push(`Model     : ${report.model}`);
  lines.push('');

  for (const check of report.checks) {
    lines.push(`[${check.classification.severity}] Target: ${check.target}`);
    lines.push(`  Endpoint    : ${check.endpoint}`);
    lines.push(`  HTTP Status : ${check.status} (${check.classification.category}) in ${check.latencyMs}ms`);
    lines.push(`  Headers     : ${JSON.stringify(check.headersAttached)}`);
    lines.push(`  Summary     : ${check.classification.summary}`);
    if (check.replyPreview) {
      lines.push(`  AI Reply    : ${check.replyPreview}`);
    }
    if (check.responseSnippet && !check.ok) {
      lines.push(`  Response Log: ${check.responseSnippet}`);
    }
    lines.push(`  Next Step   : ${check.classification.remediation}`);
    lines.push('');
  }
  return lines.join('\n');
}

async function main() {
  const cli = parseCliArgs(process.argv.slice(2));
  const scriptDir = path.dirname(fileURLToPath(import.meta.url));
  const repoRoot = path.resolve(scriptDir, '..');
  const gsConfig = readProjectConfigFromGoogleServices(repoRoot);
  const projectId = gsConfig?.projectId || DEFAULT_PROJECT_ID;

  const checks = [];
  const cfResult = await diagnoseCloudFunction({
    endpointUrl: cli.endpoint || process.env.GOSHALA_CLOUD_FUNCTION_URL || buildDefaultCloudFunctionUrl(projectId),
    projectId,
    prompt: cli.prompt,
    model: cli.model,
    timeoutMs: cli.timeoutMs
  });
  checks.push(cfResult);

  if (!cli.skipAiLogic) {
    const aiLogicResult = await diagnoseAiLogicBackend({
      projectId,
      model: cli.model,
      prompt: cli.prompt,
      firebaseApiKey: gsConfig?.firebaseApiKey || '',
      packageName: gsConfig?.packageName || '',
      certHash: gsConfig?.certHash || '',
      timeoutMs: cli.timeoutMs
    });
    checks.push(aiLogicResult);
  }

  const report = {
    timestamp: new Date().toISOString(),
    projectId,
    prompt: cli.prompt,
    model: cli.model,
    overallOk: checks.some((c) => c.ok),
    checks
  };

  if (cli.json) {
    console.log(JSON.stringify(report, null, 2));
  } else {
    console.log(formatHumanReport(report));
  }

  if (!report.overallOk) {
    process.exitCode = 1;
  }
}

const isDirectEntry =
  process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isDirectEntry) {
  main();
}
