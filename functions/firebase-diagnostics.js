/**
 * Jay Bhole Gosala Samiti — Secure Diagnostic Cloud Function: `testGeminiConnection`
 *
 * Responsibilities:
 *  1. Reads `GEMINI_API_KEY` strictly from Firebase Secret Manager (`defineSecret('GEMINI_API_KEY')`).
 *  2. Sends a minimal "Hello Goshala" request to the Gemini API (header `x-goog-api-key`, never in URL).
 *  3. Verifies Firebase App Check token validity (`X-Firebase-AppCheck`) via Firebase Admin SDK.
 *  4. Records the exact upstream HTTP Status Code and latency in Cloud Logging and response JSON.
 *  5. Provides distinct Hindi & English diagnostics for HTTP 401, 403, 404, 429, 500/5xx, and Network errors.
 *  6. Never logs or returns any API Key, Secret, Bearer token, or raw App Check JWT (strict redaction).
 *  7. Restricts access strictly to authenticated Admins (`Authorization: Bearer <Firebase ID Token>`).
 *  8. Returns a clear structured test result (`geminiWorking`, `testPassed`, `verdictHi`, `verdictEn`).
 *
 * Security:
 *  - Zero hardcoded API keys; never place any Gemini API key in Android APK, Web frontend, or Git.
 *  - Strict CORS origin allowlist (no wildcard `*` on authenticated endpoints).
 *  - Production App Check & Admin Auth rules remain strictly enforced.
 */

'use strict';

const PROJECT_ID = 'gen-lang-client-0821187064';
const FIRESTORE_DATABASE_ID = 'ai-studio-android-5ee9bbf0-cdf7-4202-8155-b40c385da917';
const DEFAULT_REGION = 'us-central1';
const DEFAULT_PROMPT = 'Hello Goshala';
const DEFAULT_MODEL = 'gemini-3.8-flash';
const ALLOWED_MODELS = Object.freeze(['gemini-3.8-flash', 'gemini-3.5-flash-lite']);
const ALLOWED_ORIGINS = Object.freeze([
  'https://gen-lang-client-0821187064.web.app',
  'https://gen-lang-client-0821187064.firebaseapp.com',
  'http://localhost:5000',
  'http://127.0.0.1:5000'
]);
const ADMIN_EMAILS = Object.freeze(['bhagwankibaat4@gmail.com']);
const DEFAULT_TIMEOUT_MS = 30_000;

/**
 * Redacts API keys, exact secret values, Bearer tokens, and JWTs before any log or HTTP output.
 */
function redactSensitiveText(value, exactSecrets = []) {
  let out = String(value ?? '');
  for (const secret of exactSecrets) {
    const cleanSecret = typeof secret === 'string' ? secret.trim() : '';
    if (cleanSecret.length >= 8) {
      out = out.split(cleanSecret).join('[REDACTED_SECRET]');
    }
  }
  return out
    .replace(/AIza[0-9A-Za-z_-]{16,}/g, '[REDACTED_API_KEY]')
    .replace(/Bearer\s+[A-Za-z0-9._-]{12,}/gi, 'Bearer [REDACTED_TOKEN]')
    .replace(/eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}/g, '[REDACTED_JWT]');
}

/**
 * Validates and bounds diagnostic inputs against strict allowlists.
 */
function sanitizeDiagnosticInput(body) {
  const safeBody = body && typeof body === 'object' ? body : {};
  const rawPrompt = typeof safeBody.prompt === 'string' ? safeBody.prompt.trim() : '';
  const prompt = (rawPrompt || DEFAULT_PROMPT).slice(0, 200);

  const rawModel = typeof safeBody.model === 'string' ? safeBody.model.trim() : DEFAULT_MODEL;
  const model = ALLOWED_MODELS.includes(rawModel) ? rawModel : DEFAULT_MODEL;

  return { prompt, model };
}

/**
 * Provides distinct Hindi & English diagnostics for HTTP 401, 403, 404, 429, 500/5xx, and Network errors.
 */
function classifyGeminiHttpStatus(status, responseBodyText = '', appCheckInfo = null) {
  const lower = String(responseBodyText || '').toLowerCase();

  if (status >= 200 && status < 300) {
    return {
      codeCategory: 'OK',
      rootCause: 'HEALTHY',
      severity: 'PASS',
      detailsHi: `HTTP ${status} सफल: Gemini API से वास्तविक उत्तर प्राप्त हुआ है।`,
      detailsEn: `HTTP ${status} OK: Live response received from Gemini API.`
    };
  }

  if (status === 401) {
    const appCheckIssue =
      lower.includes('app check') ||
      lower.includes('appcheck') ||
      (appCheckInfo && !appCheckInfo.valid);
    return {
      codeCategory: 'HTTP_401',
      rootCause: appCheckIssue ? 'APP_CHECK_OR_AUTH_401' : 'API_KEY_OR_AUTH_401',
      severity: 'FAIL',
      detailsHi: appCheckIssue
        ? 'HTTP 401 (Authentication / App Check त्रुटि): प्रमाणन अस्वीकृत हुआ। Firebase App Check टोकन या Secret Manager की GEMINI_API_KEY जाँचें।'
        : 'HTTP 401 (Authentication त्रुटि): GEMINI_API_KEY अमान्य है या प्रमाणन विफल हुआ। Firebase Secret Manager में कुंजी सत्यापित करें।',
      detailsEn: appCheckIssue
        ? 'HTTP 401 UNAUTHENTICATED: Authentication rejected. Verify Firebase App Check token and Secret Manager GEMINI_API_KEY.'
        : 'HTTP 401 UNAUTHENTICATED: GEMINI_API_KEY is invalid or missing in Firebase Secret Manager.'
    };
  }

  if (status === 403) {
    const isServiceDisabled =
      lower.includes('service_disabled') ||
      lower.includes('api has not been used') ||
      lower.includes('not enabled') ||
      lower.includes('billing');
    const isAppCheck =
      lower.includes('app check') ||
      lower.includes('appcheck') ||
      lower.includes('attestation');
    return {
      codeCategory: 'HTTP_403',
      rootCause: isServiceDisabled
        ? 'API_OR_BILLING_DISABLED_403'
        : isAppCheck
          ? 'APP_CHECK_REJECTED_403'
          : 'API_KEY_PERMISSION_DENIED_403',
      severity: 'FAIL',
      detailsHi: isServiceDisabled
        ? 'HTTP 403 (API Permission / Billing त्रुटि): प्रोजेक्ट में Gemini / Generative Language API या बिलिंग अनुमति सक्षम नहीं है।'
        : isAppCheck
          ? 'HTTP 403 (App Check त्रुटि): Firebase App Check सुरक्षा सत्यापन अस्वीकृत हुआ।'
          : 'HTTP 403 (Permission Denied त्रुटि): API Key प्रतिबंधित है या प्रोजेक्ट अनुमति अस्वीकृत हुई।',
      detailsEn: isServiceDisabled
        ? 'HTTP 403 PERMISSION_DENIED: Gemini API or project billing/permission is disabled.'
        : isAppCheck
          ? 'HTTP 403 PERMISSION_DENIED: Firebase App Check attestation rejected.'
          : 'HTTP 403 PERMISSION_DENIED: API key restriction or missing service permission.'
    };
  }

  if (status === 404) {
    return {
      codeCategory: 'HTTP_404',
      rootCause: 'MODEL_OR_ENDPOINT_NOT_FOUND_404',
      severity: 'FAIL',
      detailsHi: 'HTTP 404 (गलत Endpoint या Model त्रुटि): चुना गया Gemini मॉडल या API Endpoint उपलब्ध नहीं है (मॉडल नाम जाँचें: gemini-3.8-flash / gemini-3.5-flash-lite)।',
      detailsEn: 'HTTP 404 NOT_FOUND: Requested Gemini model or endpoint path does not exist.'
    };
  }

  if (status === 429) {
    return {
      codeCategory: 'HTTP_429',
      rootCause: 'QUOTA_OR_RATE_LIMIT_429',
      severity: 'FAIL',
      detailsHi: 'HTTP 429 (API Quota / Rate Limit त्रुटि): API की अनुरोध सीमा (Rate Limit / Quota) पूरी हो गई है। कुछ सेकंड रुककर पुनः प्रयास करें।',
      detailsEn: 'HTTP 429 RESOURCE_EXHAUSTED: Gemini API quota or rate limit exceeded.'
    };
  }

  if (status >= 500) {
    return {
      codeCategory: 'HTTP_500',
      rootCause: 'SERVER_ERROR_5XX',
      severity: 'FAIL',
      detailsHi: `HTTP ${status} (Backend Server त्रुटि): Gemini सर्वर अस्थायी रूप से अनुपलब्ध या ओवरलोड है। कृपया कुछ समय बाद पुनः प्रयास करें।`,
      detailsEn: `HTTP ${status} SERVER_ERROR: Upstream Gemini server encountered a temporary error.`
    };
  }

  if (status === 0) {
    return {
      codeCategory: 'NETWORK_ERROR',
      rootCause: 'NETWORK_OR_TIMEOUT',
      severity: 'FAIL',
      detailsHi: 'Network / Timeout Error: सर्वर से Gemini API तक नेटवर्क कनेक्शन नहीं बन सका या समय समाप्त हो गया।',
      detailsEn: 'Network or timeout error while contacting the Gemini API.'
    };
  }

  return {
    codeCategory: `HTTP_${status}`,
    rootCause: `UNEXPECTED_HTTP_${status}`,
    severity: 'FAIL',
    detailsHi: `HTTP ${status} त्रुटि: Gemini API से अप्रत्याशित प्रतिक्रिया प्राप्त हुई।`,
    detailsEn: `HTTP ${status}: Unexpected response from Gemini API.`
  };
}

/**
 * Verifies the caller is an authenticated Firebase Admin via `Authorization: Bearer <ID_TOKEN>`.
 */
async function verifyAdminCaller(req, verifyIdTokenFn, lookupAdminRoleFn = null) {
  const authHeader = req && req.headers ? req.headers.authorization || req.headers.Authorization : '';
  const raw = typeof authHeader === 'string' ? authHeader.trim() : '';
  if (!raw.toLowerCase().startsWith('bearer ')) {
    return {
      authorized: false,
      status: 401,
      errorHi: 'HTTP 401: Admin Firebase ID Token आवश्यक है (Authorization: Bearer <token>)।',
      errorEn: 'Missing Authorization Bearer Firebase ID token.'
    };
  }

  const idToken = raw.slice(7).trim();
  if (!idToken || typeof verifyIdTokenFn !== 'function') {
    return {
      authorized: false,
      status: 401,
      errorHi: 'HTTP 401: अमान्य प्रमाणन टोकन या Auth सत्यापन अनुपलब्ध।',
      errorEn: 'Invalid ID token or Firebase Auth verifier unavailable.'
    };
  }

  try {
    const decoded = await verifyIdTokenFn(idToken, true);
    const uid = String(decoded && decoded.uid ? decoded.uid : '');
    const email = String(decoded && decoded.email ? decoded.email : '').toLowerCase();

    let isAdmin = Boolean(decoded && decoded.admin === true) || ADMIN_EMAILS.includes(email);
    if (!isAdmin && uid && typeof lookupAdminRoleFn === 'function') {
      isAdmin = Boolean(await lookupAdminRoleFn(uid));
    }

    if (!isAdmin) {
      return {
        authorized: false,
        status: 403,
        uid,
        errorHi: 'HTTP 403: यह Diagnostic Function केवल अधिकृत Admin के लिए है।',
        errorEn: 'Forbidden: Only authorized Admin users can invoke testGeminiConnection.'
      };
    }

    return { authorized: true, status: 200, uid, email };
  } catch (err) {
    return {
      authorized: false,
      status: 401,
      errorHi: 'HTTP 401: Firebase ID Token अमान्य या समाप्त (expired) है।',
      errorEn: redactSensitiveText(err && err.message ? err.message : 'Invalid Firebase ID token')
    };
  }
}

/**
 * Verifies the incoming `X-Firebase-AppCheck` header via Firebase Admin SDK without logging the token.
 */
async function verifyAppCheckTokenHeader(req, verifyAppCheckFn) {
  const rawHeader = req && req.headers
    ? req.headers['x-firebase-appcheck'] || req.headers['X-Firebase-AppCheck'] || ''
    : '';
  const token = typeof rawHeader === 'string' ? rawHeader.trim() : '';

  if (!token) {
    return {
      present: false,
      valid: false,
      status: 'MISSING',
      appId: null,
      error: 'X-Firebase-AppCheck header is missing.'
    };
  }

  if (typeof verifyAppCheckFn !== 'function') {
    return {
      present: true,
      valid: false,
      status: 'VERIFIER_UNAVAILABLE',
      appId: null,
      error: 'Firebase Admin App Check verifier is not configured.'
    };
  }

  try {
    const decoded = await verifyAppCheckFn(token);
    return {
      present: true,
      valid: true,
      status: 'VERIFIED',
      appId: decoded && decoded.appId ? String(decoded.appId) : null,
      error: null
    };
  } catch (err) {
    return {
      present: true,
      valid: false,
      status: 'INVALID',
      appId: null,
      error: redactSensitiveText(err && err.message ? err.message : 'App Check token verification failed', [token])
    };
  }
}

/**
 * Executes the minimal "Hello Goshala" request to Gemini API using the server-side Secret Manager key.
 */
async function executeHelloGoshalaRequest({
  apiKey,
  prompt = DEFAULT_PROMPT,
  model = DEFAULT_MODEL,
  appCheckInfo = null,
  timeoutMs = DEFAULT_TIMEOUT_MS,
  fetchImpl = globalThis.fetch,
  logger = console
} = {}) {
  const cleanKey = typeof apiKey === 'string' ? apiKey.trim() : '';
  const safeModel = ALLOWED_MODELS.includes(model) ? model : DEFAULT_MODEL;
  const endpointUrl = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(safeModel)}:generateContent`;

  if (!cleanKey) {
    const classification = classifyGeminiHttpStatus(401, 'MISSING API KEY in Secret Manager', appCheckInfo);
    const missingResult = {
      ok: false,
      secretConfigured: false,
      model: safeModel,
      httpStatus: 401,
      latencyMs: 0,
      classification,
      replyPreview: '',
      sanitizedResponseSnippet: 'GEMINI_API_KEY secret is not configured in Firebase Secret Manager.'
    };
    logger.error('[testGeminiConnection] GEMINI_API_KEY missing in Secret Manager', {
      secretConfigured: false,
      model: safeModel,
      httpStatus: 401,
      codeCategory: classification.codeCategory
    });
    return missingResult;
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const startedAt = Date.now();

  try {
    const response = await fetchImpl(endpointUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'x-goog-api-key': cleanKey
      },
      body: JSON.stringify({
        contents: [{ role: 'user', parts: [{ text: prompt }] }],
        generationConfig: { maxOutputTokens: 64, temperature: 0.2 }
      }),
      signal: controller.signal
    });

    const latencyMs = Date.now() - startedAt;
    const rawBody = await response.text();
    const safeBody = redactSensitiveText(rawBody, [cleanKey]);
    const classification = classifyGeminiHttpStatus(response.status, safeBody, appCheckInfo);

    let replyText = '';
    try {
      const parsed = JSON.parse(rawBody);
      replyText = parsed?.candidates?.[0]?.content?.parts
        ?.map((p) => (typeof p?.text === 'string' ? p.text : ''))
        .join('')
        .trim() || '';
    } catch {
      replyText = '';
    }

    const safeReply = redactSensitiveText(replyText, [cleanKey]).slice(0, 240);
    const geminiWorking = response.ok && safeReply.length > 0;

    // Structured Cloud Log — strictly NO API keys or secrets logged
    const logPayload = {
      secretConfigured: true,
      model: safeModel,
      httpStatus: response.status,
      latencyMs,
      geminiWorking,
      codeCategory: classification.codeCategory,
      rootCause: classification.rootCause,
      appCheckStatus: appCheckInfo ? appCheckInfo.status : 'UNKNOWN',
      responsePreview: geminiWorking ? safeReply : safeBody.slice(0, 600)
    };
    if (geminiWorking) {
      logger.info('[testGeminiConnection] Gemini API connectivity verified', logPayload);
    } else {
      logger.error('[testGeminiConnection] Gemini API call returned non-working status', logPayload);
    }

    return {
      ok: geminiWorking,
      secretConfigured: true,
      model: safeModel,
      httpStatus: response.status,
      latencyMs,
      classification,
      replyPreview: safeReply,
      sanitizedResponseSnippet: safeBody.slice(0, 600)
    };
  } catch (err) {
    const latencyMs = Date.now() - startedAt;
    const isTimeout = Boolean(err && err.name === 'AbortError');
    const safeErr = isTimeout
      ? `Request timed out after ${timeoutMs}ms`
      : redactSensitiveText(err && err.message ? err.message : String(err), [cleanKey]);
    const classification = classifyGeminiHttpStatus(0, safeErr, appCheckInfo);

    logger.error('[testGeminiConnection] Network/Timeout error calling Gemini API', {
      secretConfigured: true,
      model: safeModel,
      httpStatus: 0,
      latencyMs,
      codeCategory: classification.codeCategory,
      errorName: err && err.name ? String(err.name) : 'Error',
      errorCode: err && err.code ? String(err.code) : '',
      errorMessage: safeErr
    });

    return {
      ok: false,
      secretConfigured: true,
      model: safeModel,
      httpStatus: 0,
      latencyMs,
      classification,
      replyPreview: '',
      sanitizedResponseSnippet: safeErr,
      networkError: {
        name: err && err.name ? String(err.name) : 'Error',
        code: err && err.code ? String(err.code) : '',
        message: safeErr
      }
    };
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Factory for `testGeminiConnection` HTTP handler (injectable for deterministic tests & Cloud Functions).
 */
function createTestGeminiConnectionHandler(deps = {}) {
  const logger = deps.logger || console;
  const fetchImpl = deps.fetchImpl || globalThis.fetch;
  const verifyIdTokenFn = deps.verifyIdTokenFn || null;
  const lookupAdminRoleFn = deps.lookupAdminRoleFn || null;
  const verifyAppCheckFn = deps.verifyAppCheckFn || null;
  const getGeminiApiKey = deps.getGeminiApiKey || (() => process.env.GEMINI_API_KEY || '');
  const requireValidAppCheck = deps.requireValidAppCheck !== false;

  return async function handleTestGeminiConnection(req, res) {
    // 1. Strict allowlisted CORS (no wildcard `*` on authenticated Admin endpoint)
    const origin = req && req.headers ? String(req.headers.origin || '') : '';
    if (origin && ALLOWED_ORIGINS.includes(origin)) {
      res.set('Access-Control-Allow-Origin', origin);
      res.set('Vary', 'Origin');
      res.set('Access-Control-Allow-Methods', 'POST, OPTIONS');
      res.set('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Firebase-AppCheck');
    }
    if (req.method === 'OPTIONS') {
      return res.status(204).send('');
    }
    if (req.method !== 'POST') {
      return res.status(405).json({
        testPassed: false,
        geminiWorking: false,
        httpStatus: 405,
        error: 'method_not_allowed',
        verdictHi: 'केवल POST अनुरोध अनुमत है।',
        verdictEn: 'Only POST requests are allowed.'
      });
    }

    // 2. Enforce Admin-only Firebase Auth
    const adminCheck = await verifyAdminCaller(req, verifyIdTokenFn, lookupAdminRoleFn);
    if (!adminCheck.authorized) {
      logger.warn('[testGeminiConnection] Blocked unauthorized caller', {
        httpStatus: adminCheck.status,
        reason: adminCheck.errorEn
      });
      return res.status(adminCheck.status).json({
        testPassed: false,
        geminiWorking: false,
        httpStatus: adminCheck.status,
        errorCodeCategory: adminCheck.status === 401 ? 'HTTP_401' : 'HTTP_403',
        errorDetailsHi: adminCheck.errorHi,
        errorDetailsEn: adminCheck.errorEn,
        verdictHi: 'असफल: केवल अधिकृत Admin ही यह जाँच चला सकते हैं।',
        verdictEn: 'FAIL: Admin authorization required.'
      });
    }

    // 3. Verify Firebase App Check Token validity
    const appCheckInfo = await verifyAppCheckTokenHeader(req, verifyAppCheckFn);
    logger.info('[testGeminiConnection] App Check token verification', {
      present: appCheckInfo.present,
      valid: appCheckInfo.valid,
      status: appCheckInfo.status,
      appId: appCheckInfo.appId || undefined
    });

    if (requireValidAppCheck && !appCheckInfo.valid) {
      return res.status(401).json({
        testPassed: false,
        geminiWorking: false,
        httpStatus: 401,
        errorCodeCategory: 'HTTP_401',
        rootCause: 'APP_CHECK_INVALID_OR_MISSING',
        errorDetailsHi: 'HTTP 401 (App Check त्रुटि): वैध Firebase App Check Token नहीं मिला या अमान्य है। Production सुरक्षा सक्रिय है।',
        errorDetailsEn: 'HTTP 401 UNAUTHENTICATED: Valid Firebase App Check token is required.',
        verdictHi: 'असफल: Firebase App Check Token सत्यापन विफल होने के कारण अनुरोध रोका गया।',
        verdictEn: 'FAIL: Blocked because Firebase App Check token is missing or invalid.',
        appCheck: appCheckInfo,
        timestamp: new Date().toISOString()
      });
    }

    // 4. Execute minimal "Hello Goshala" call to Gemini API using Secret Manager key
    const { prompt, model } = sanitizeDiagnosticInput(req.body);
    const result = await executeHelloGoshalaRequest({
      apiKey: getGeminiApiKey(),
      prompt,
      model,
      appCheckInfo,
      fetchImpl,
      logger
    });

    const geminiWorking = Boolean(result.ok);
    const responseStatus = geminiWorking ? 200 : result.httpStatus || 502;

    return res.status(responseStatus).json({
      testPassed: geminiWorking && appCheckInfo.valid,
      geminiWorking,
      verdictHi: geminiWorking
        ? 'सफल: Gemini API वास्तव में काम कर रही है और App Check टोकन वैध है।'
        : `असफल: Gemini API से कार्यशील उत्तर नहीं मिला (${result.classification.codeCategory})।`,
      verdictEn: geminiWorking
        ? 'PASS: Gemini API is working and App Check token is valid.'
        : `FAIL: Gemini API did not return a working response (${result.classification.codeCategory}).`,
      httpStatus: result.httpStatus,
      errorCodeCategory: result.classification.codeCategory,
      rootCause: result.classification.rootCause,
      errorDetailsHi: result.classification.detailsHi,
      errorDetailsEn: result.classification.detailsEn,
      secretConfigured: result.secretConfigured,
      appCheck: appCheckInfo,
      prompt,
      model: result.model,
      latencyMs: result.latencyMs,
      replyPreview: result.replyPreview,
      responseSnippet: geminiWorking ? undefined : result.sanitizedResponseSnippet,
      networkError: result.networkError || null,
      timestamp: new Date().toISOString()
    });
  };
}

/**
 * Registers `testGeminiConnection` as a Firebase Cloud Function (v2 HTTPS) when running
 * in the Firebase Functions runtime (`firebase-functions` + `firebase-admin`).
 */
let testGeminiConnection = null;
try {
  const { onRequest } = require('firebase-functions/v2/https');
  const { defineSecret } = require('firebase-functions/params');
  const functionsLogger = require('firebase-functions/logger');
  const admin = require('firebase-admin');
  const { getFirestore } = require('firebase-admin/firestore');

  if (!admin.apps.length) {
    admin.initializeApp();
  }

  const firestoreDb = getFirestore(admin.app(), FIRESTORE_DATABASE_ID);
  const geminiApiKeySecret = defineSecret('GEMINI_API_KEY');

  testGeminiConnection = onRequest(
    {
      region: DEFAULT_REGION,
      invoker: 'public',
      timeoutSeconds: 60,
      secrets: [geminiApiKeySecret],
      cors: false
    },
    createTestGeminiConnectionHandler({
      logger: functionsLogger,
      verifyIdTokenFn: (idToken, checkRevoked) => admin.auth().verifyIdToken(idToken, checkRevoked),
      lookupAdminRoleFn: async (uid) => {
        try {
          const snap = await firestoreDb.collection('users').doc(uid).get();
          return snap.exists && String(snap.data()?.role || '').toUpperCase() === 'ADMIN';
        } catch {
          return false;
        }
      },
      verifyAppCheckFn: (token) => admin.appCheck().verifyToken(token),
      getGeminiApiKey: () => geminiApiKeySecret.value() || '',
      requireValidAppCheck: true
    })
  );
} catch {
  // Outside Firebase Functions runtime (e.g., local Node.js tests), export handler cleanly.
  testGeminiConnection = createTestGeminiConnectionHandler();
}

module.exports = {
  PROJECT_ID,
  FIRESTORE_DATABASE_ID,
  DEFAULT_REGION,
  DEFAULT_PROMPT,
  DEFAULT_MODEL,
  ALLOWED_MODELS,
  redactSensitiveText,
  sanitizeDiagnosticInput,
  classifyGeminiHttpStatus,
  verifyAdminCaller,
  verifyAppCheckTokenHeader,
  executeHelloGoshalaRequest,
  createTestGeminiConnectionHandler,
  testGeminiConnection,
  // Backwards-compatible alias
  diagnoseGeminiBackend: testGeminiConnection
};
