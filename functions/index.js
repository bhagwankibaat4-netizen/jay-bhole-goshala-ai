'use strict';

/**
 * Firebase Functions entry point for Jay Bhole Gosala Samiti.
 * Exports the secure Admin-only `testGeminiConnection` diagnostic function.
 */
const { testGeminiConnection } = require('./firebase-diagnostics.js');

exports.testGeminiConnection = testGeminiConnection;
