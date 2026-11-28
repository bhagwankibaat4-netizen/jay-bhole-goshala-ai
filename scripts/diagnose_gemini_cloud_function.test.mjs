import test from 'node:test';
import assert from 'node:assert/strict';
import {
  DEFAULT_PROMPT,
  DEFAULT_MODEL,
  diagnoseCloudFunction,
  diagnoseAiLogicBackend,
  classifyDiagnosticStatus,
  validateBackendEndpoint,
  redactSecrets
} from './diagnose_gemini_cloud_function.mjs';

test('validateBackendEndpoint blocks direct Gemini client API and URL-embedded keys, allows HTTPS Cloud Functions', () => {
  assert.equal(
    validateBackendEndpoint('https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent').valid,
    false
  );
  assert.equal(
    validateBackendEndpoint('https://us-central1-gen-lang-client-0821187064.cloudfunctions.net/vetAssistant?key=AIzaFakeKey1234567890').valid,
    false
  );
  assert.equal(
    validateBackendEndpoint('https://us-central1-gen-lang-client-0821187064.cloudfunctions.net/vetAssistant').valid,
    true
  );
  assert.equal(
    validateBackendEndpoint('http://127.0.0.1:5001/gen-lang-client-0821187064/us-central1/vetAssistant').valid,
    true
  );
});

test('redactSecrets strips API keys, Bearer tokens, and JWTs from logs', () => {
  const sample = 'Error with AIzaSyDummySecretValue1234567890 and Bearer abcdefghijklmnop and eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.sig1234567890';
  const cleaned = redactSecrets(sample);
  assert.equal(cleaned.includes('AIzaSyDummySecretValue'), false);
  assert.equal(cleaned.includes('abcdefghijklmnop'), false);
  assert.equal(cleaned.includes('sig1234567890'), false);
  assert.ok(cleaned.includes('[REDACTED_API_KEY]'));
  assert.ok(cleaned.includes('Bearer [REDACTED_TOKEN]'));
  assert.ok(cleaned.includes('[REDACTED_JWT]'));
});

test('diagnoseCloudFunction sends minimal Hello, Goshala payload with X-Firebase-AppCheck and handles 200, 401, 403, 404, 429, 500, and network failures', async () => {
  const endpoint = 'https://us-central1-gen-lang-client-0821187064.cloudfunctions.net/vetAssistant';
  const jwt = 'eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.signature123456';

  // 1. 200 OK
  let capturedInit = null;
  const okRes = await diagnoseCloudFunction({
    endpointUrl: endpoint,
    appCheckToken: jwt,
    fetchImpl: async (_url, init) => {
      capturedInit = init;
      return {
        ok: true,
        status: 200,
        text: async () => JSON.stringify({
          text: 'नमस्ते! मैं जय भोले गौशाला सहायक हूँ।',
          modelUsed: DEFAULT_MODEL,
          sources: []
        })
      };
    }
  });
  assert.equal(okRes.ok, true);
  assert.equal(okRes.status, 200);
  assert.equal(okRes.classification.category, 'OK');
  assert.equal(capturedInit.headers['X-Firebase-AppCheck'], jwt);
  assert.equal(JSON.parse(capturedInit.body).prompt, DEFAULT_PROMPT);
  assert.ok(okRes.replyPreview.includes('जय भोले गौशाला'));

  // 2. HTTP 401 App Check error
  const res401 = await diagnoseCloudFunction({
    endpointUrl: endpoint,
    fetchImpl: async () => ({
      ok: false,
      status: 401,
      text: async () => JSON.stringify({ error: { code: 401, status: 'UNAUTHENTICATED', message: 'Firebase App Check token is invalid.' } })
    })
  });
  assert.equal(res401.ok, false);
  assert.equal(res401.status, 401);
  assert.equal(res401.classification.category, 'HTTP_401_APP_CHECK_UNAUTHENTICATED');

  // 3. HTTP 403 Service Disabled vs App Check Rejection
  const res403Disabled = await diagnoseCloudFunction({
    endpointUrl: endpoint,
    fetchImpl: async () => ({
      ok: false,
      status: 403,
      text: async () => 'PERMISSION_DENIED: SERVICE_DISABLED API has not been used'
    })
  });
  assert.equal(res403Disabled.status, 403);
  assert.equal(res403Disabled.classification.category, 'HTTP_403_SERVICE_DISABLED');

  const res403AppCheck = await diagnoseCloudFunction({
    endpointUrl: endpoint,
    fetchImpl: async () => ({
      ok: false,
      status: 403,
      text: async () => 'PERMISSION_DENIED: App Check attestation failed'
    })
  });
  assert.equal(res403AppCheck.classification.category, 'HTTP_403_APP_CHECK_REJECTED');

  // 4. HTTP 404, 429, 500, and Network Error
  assert.equal(classifyDiagnosticStatus(404, 'Not Found').category, 'HTTP_404_NOT_FOUND');
  assert.equal(classifyDiagnosticStatus(429, 'RESOURCE_EXHAUSTED').category, 'HTTP_429_QUOTA_OR_RATE_LIMIT');
  assert.equal(classifyDiagnosticStatus(500, 'Internal error').category, 'HTTP_5XX_BACKEND_ERROR');

  const netRes = await diagnoseCloudFunction({
    endpointUrl: endpoint,
    fetchImpl: async () => {
      throw new Error('getaddrinfo ENOTFOUND');
    }
  });
  assert.equal(netRes.ok, false);
  assert.equal(netRes.status, 0);
  assert.equal(netRes.classification.category, 'NETWORK_OR_TIMEOUT_ERROR');
});

test('diagnoseAiLogicBackend reports 401 App Check token error cleanly', async () => {
  const res = await diagnoseAiLogicBackend({
    projectId: 'gen-lang-client-0821187064',
    model: DEFAULT_MODEL,
    fetchImpl: async () => ({
      ok: false,
      status: 401,
      text: async () => JSON.stringify({ error: { code: 401, message: 'Firebase App Check token is invalid.', status: 'UNAUTHENTICATED' } })
    })
  });
  assert.equal(res.ok, false);
  assert.equal(res.status, 401);
  assert.equal(res.classification.category, 'HTTP_401_APP_CHECK_UNAUTHENTICATED');
});
