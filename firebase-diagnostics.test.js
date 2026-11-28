'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const {
  DEFAULT_PROMPT,
  DEFAULT_MODEL,
  redactSensitiveText,
  sanitizeDiagnosticInput,
  classifyGeminiHttpStatus,
  createTestGeminiConnectionHandler
} = require('./firebase-diagnostics.js');

function createMockRes() {
  return {
    statusCode: 200,
    headers: {},
    body: null,
    set(key, value) {
      this.headers[key] = value;
    },
    status(code) {
      this.statusCode = code;
      return this;
    },
    json(payload) {
      this.body = payload;
      return this;
    },
    send(payload) {
      this.body = payload;
      return this;
    }
  };
}

test('Requirement 7: Only authenticated Admin users can call testGeminiConnection; non-admin & unauth blocked', async () => {
  const logs = [];
  const logger = { info: (m, d) => logs.push({ m, d }), warn: (m, d) => logs.push({ m, d }), error: (m, d) => logs.push({ m, d }) };

  const handler = createTestGeminiConnectionHandler({
    logger,
    verifyIdTokenFn: async () => ({ uid: 'staff_1', email: 'staff@example.com' }),
    lookupAdminRoleFn: async () => false,
    verifyAppCheckFn: async () => ({ appId: 'app-id' }),
    getGeminiApiKey: () => 'AIzaSyFakeSecretValue1234567890123'
  });

  // Missing Authorization header -> 401
  const resNoAuth = createMockRes();
  await handler({ method: 'POST', headers: {} }, resNoAuth);
  assert.equal(resNoAuth.statusCode, 401);
  assert.equal(resNoAuth.body.geminiWorking, false);

  // Non-Admin authenticated user -> 403
  const resStaff = createMockRes();
  await handler({ method: 'POST', headers: { authorization: 'Bearer staff-id-token' } }, resStaff);
  assert.equal(resStaff.statusCode, 403);
  assert.equal(resStaff.body.errorCodeCategory, 'HTTP_403');
  assert.equal(resStaff.body.geminiWorking, false);
});

test('Requirement 3: Missing or invalid App Check token is rejected with HTTP 401 before calling Gemini', async () => {
  let fetchCalled = false;
  const handler = createTestGeminiConnectionHandler({
    logger: { info() {}, warn() {}, error() {} },
    verifyIdTokenFn: async () => ({ uid: 'admin_1', email: 'bhagwankibaat4@gmail.com' }),
    verifyAppCheckFn: async () => { throw new Error('App Check token expired'); },
    getGeminiApiKey: () => 'AIzaSyFakeSecretValue1234567890123',
    fetchImpl: async () => {
      fetchCalled = true;
      throw new Error('Should not reach Gemini when App Check is invalid');
    }
  });

  const resMissing = createMockRes();
  await handler(
    { method: 'POST', headers: { authorization: 'Bearer admin-token' }, body: {} },
    resMissing
  );
  assert.equal(resMissing.statusCode, 401);
  assert.equal(resMissing.body.appCheck.status, 'MISSING');
  assert.equal(fetchCalled, false);

  const resInvalid = createMockRes();
  await handler(
    {
      method: 'POST',
      headers: {
        authorization: 'Bearer admin-token',
        'x-firebase-appcheck': 'eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhIn0.badsig12345678'
      },
      body: {}
    },
    resInvalid
  );
  assert.equal(resInvalid.statusCode, 401);
  assert.equal(resInvalid.body.appCheck.status, 'INVALID');
  assert.equal(resInvalid.body.rootCause, 'APP_CHECK_INVALID_OR_MISSING');
  assert.equal(fetchCalled, false);
});

test('Requirements 1, 2, 4, 6, 8: Reads Secret Manager key, sends Hello Goshala via header, returns clear geminiWorking=true, and never logs key', async () => {
  const secretKey = 'AIzaSySuperSecretDiagnosticTestKey987654';
  const logged = [];
  let capturedUrl = '';
  let capturedInit = null;

  const handler = createTestGeminiConnectionHandler({
    logger: {
      info: (msg, meta) => logged.push({ level: 'info', msg, meta }),
      warn: (msg, meta) => logged.push({ level: 'warn', msg, meta }),
      error: (msg, meta) => logged.push({ level: 'error', msg, meta })
    },
    verifyIdTokenFn: async () => ({ uid: 'admin_1', email: 'bhagwankibaat4@gmail.com' }),
    verifyAppCheckFn: async () => ({ appId: '1:360079036185:android:2613f536cf8f7eb8d03f88' }),
    getGeminiApiKey: () => secretKey,
    fetchImpl: async (url, init) => {
      capturedUrl = url;
      capturedInit = init;
      return {
        ok: true,
        status: 200,
        text: async () => JSON.stringify({
          candidates: [{ content: { parts: [{ text: 'नमस्ते गौशाला! मैं आपकी सहायता के लिए तैयार हूँ।' }] } }]
        })
      };
    }
  });

  const res = createMockRes();
  await handler(
    {
      method: 'POST',
      headers: {
        origin: 'https://gen-lang-client-0821187064.web.app',
        authorization: 'Bearer admin-token',
        'x-firebase-appcheck': 'eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhIn0.goodsig12345678'
      },
      body: {}
    },
    res
  );

  assert.equal(res.statusCode, 200);
  assert.equal(res.body.testPassed, true);
  assert.equal(res.body.geminiWorking, true);
  assert.equal(res.body.httpStatus, 200);
  assert.equal(res.body.secretConfigured, true);
  assert.equal(res.body.appCheck.valid, true);
  assert.equal(res.body.prompt, DEFAULT_PROMPT);
  assert.equal(res.body.model, DEFAULT_MODEL);
  assert.ok(res.body.replyPreview.includes('नमस्ते गौशाला'));

  // API key sent strictly via header, NEVER in URL query string
  assert.equal(capturedUrl.includes('key='), false);
  assert.equal(capturedInit.headers['x-goog-api-key'], secretKey);
  assert.equal(JSON.parse(capturedInit.body).contents[0].parts[0].text, 'Hello Goshala');

  // Verify secret NEVER appears in logs or HTTP response JSON
  assert.equal(JSON.stringify(logged).includes(secretKey), false);
  assert.equal(JSON.stringify(res.body).includes(secretKey), false);
});

test('Requirements 4, 5, 6: Distinct error details for 401, 403, 404, 429, 500 and secret redaction even if upstream echoes key', async () => {
  const secretKey = 'AIzaSyEchoedSecretKeyValue1234567890';
  const logged = [];
  const codes = [
    { status: 401, body: `UNAUTHENTICATED: Invalid key ${secretKey}`, expectedCat: 'HTTP_401' },
    { status: 403, body: 'PERMISSION_DENIED: SERVICE_DISABLED', expectedCat: 'HTTP_403' },
    { status: 404, body: 'NOT_FOUND: model not found', expectedCat: 'HTTP_404' },
    { status: 429, body: 'RESOURCE_EXHAUSTED: quota limit', expectedCat: 'HTTP_429' },
    { status: 500, body: 'INTERNAL: backend error', expectedCat: 'HTTP_500' }
  ];

  for (const item of codes) {
    const handler = createTestGeminiConnectionHandler({
      logger: {
        info: (m, d) => logged.push({ m, d }),
        warn: (m, d) => logged.push({ m, d }),
        error: (m, d) => logged.push({ m, d })
      },
      verifyIdTokenFn: async () => ({ uid: 'admin_1', email: 'bhagwankibaat4@gmail.com' }),
      verifyAppCheckFn: async () => ({ appId: 'app-id' }),
      getGeminiApiKey: () => secretKey,
      fetchImpl: async () => ({
        ok: false,
        status: item.status,
        text: async () => item.body
      })
    });

    const res = createMockRes();
    await handler(
      {
        method: 'POST',
        headers: {
          authorization: 'Bearer admin-token',
          'x-firebase-appcheck': 'eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhIn0.goodsig12345678'
        },
        body: { prompt: 'Hello Goshala' }
      },
      res
    );

    assert.equal(res.statusCode, item.status);
    assert.equal(res.body.geminiWorking, false);
    assert.equal(res.body.httpStatus, item.status);
    assert.equal(res.body.errorCodeCategory, item.expectedCat);
    assert.ok(res.body.errorDetailsHi.includes(`HTTP ${item.status}`));
  }

  // Secret MUST NEVER appear anywhere in logs or responses
  assert.equal(JSON.stringify(logged).includes(secretKey), false);
  assert.equal(redactSensitiveText(`Bad key ${secretKey}`, [secretKey]).includes(secretKey), false);
  assert.equal(sanitizeDiagnosticInput({ model: 'gemini-1.5-pro' }).model, DEFAULT_MODEL);
  assert.equal(classifyGeminiHttpStatus(0, 'ENOTFOUND').codeCategory, 'NETWORK_ERROR');
});

test('Firebase Functions entry point exports testGeminiConnection self-contained inside functions/', () => {
  const functionsEntry = require('./functions/index.js');
  assert.equal(typeof functionsEntry.testGeminiConnection, 'function');
});
