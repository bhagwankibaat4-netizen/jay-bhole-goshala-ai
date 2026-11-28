/**
 * Jay Bhole Gosala Samiti — Official Web Portal & Public Digital Cattle Profile
 * Routes cleanly:
 *  - `/` (and `/index.html`)             -> Home Dashboard (with Cow ID lookup & Goshala overview)
 *  - `/cattle/<validAnimalCode>`         -> Full Public Digital Cattle Profile from Firestore & Storage
 *  - `/cattle/...` (missing/invalid ID)  -> Dedicated Invalid QR Code screen (with Home link)
 *  - Any other unknown path              -> Clean Page Not Found screen (with Home link)
 */
(function (root) {
  'use strict';

  var FIREBASE_PROJECT_ID = 'gen-lang-client-0821187064';
  var FIRESTORE_DATABASE_ID = 'ai-studio-android-5ee9bbf0-cdf7-4202-8155-b40c385da917';
  var FIREBASE_STORAGE_BUCKET = 'gen-lang-client-0821187064.firebasestorage.app';
  var PUBLIC_HOSTING_ORIGIN = 'https://gen-lang-client-0821187064.web.app';
  var ANIMAL_CODE_REGEX = /^[A-Z0-9][A-Z0-9_-]{1,39}$/;
  var CATTLE_ID_REGEX = /^[A-Za-z0-9][A-Za-z0-9_-]{1,127}$/;

  /**
   * Firebase Web App Check Configuration (Zero Client-Side Gemini API Keys).
   * - Production always enforces reCAPTCHA Enterprise / v3 App Check attestation.
   * - Localhost debug token is strictly restricted to localhost / 127.0.0.1.
   * - All Gemini API calls from Web MUST go through a server-side endpoint or Firebase AI Logic
   *   with X-Firebase-AppCheck attestation — never with a client-embedded Gemini API key.
   */
  var WEB_APP_CHECK_CONFIG = Object.freeze({
    projectId: FIREBASE_PROJECT_ID,
    provider: 'recaptcha-enterprise',
    isTokenAutoRefreshEnabled: true,
    enforceInProduction: true,
    allowLocalhostDebugToken: true,
    headerName: 'X-Firebase-AppCheck'
  });

  var webAppCheckState = {
    initialized: false,
    provider: 'none',
    instance: null,
    getTokenFn: null,
    lastError: null
  };

  function isLocalhostHostname(hostname) {
    var host = String(hostname || '').trim().toLowerCase();
    return host === 'localhost' || host === '127.0.0.1' || host === '[::1]';
  }

  function isValidJwtLikeToken(token) {
    if (typeof token !== 'string') return false;
    var clean = token.trim();
    if (clean.length < 16) return false;
    return clean.split('.').length >= 3;
  }

  function initWebAppCheck(firebaseSdk, webConfig, hostnameOverride) {
    var host =
      typeof hostnameOverride === 'string'
        ? hostnameOverride
        : (typeof window !== 'undefined' && window.location ? window.location.hostname : '');
    var cfg = webConfig || (root && root.__JBGS_FIREBASE_WEB_CONFIG__) || null;

    // Localhost-only debug token support (NEVER enabled on production web origins)
    if (cfg && cfg.debugToken && isLocalhostHostname(host) && WEB_APP_CHECK_CONFIG.allowLocalhostDebugToken) {
      if (typeof self !== 'undefined') {
        self.FIREBASE_APPCHECK_DEBUG_TOKEN = cfg.debugToken;
      }
    }

    if (!firebaseSdk || !cfg || !cfg.recaptchaSiteKey) {
      webAppCheckState.initialized = false;
      webAppCheckState.provider = 'unconfigured';
      webAppCheckState.instance = null;
      webAppCheckState.getTokenFn = null;
      webAppCheckState.lastError = !cfg || !cfg.recaptchaSiteKey ? 'missing_web_recaptcha_site_key' : 'missing_sdk';
      return { initialized: false, provider: webAppCheckState.provider, reason: webAppCheckState.lastError };
    }

    try {
      var providerInstance = null;
      var providerName = 'recaptcha-enterprise';
      if (typeof firebaseSdk.ReCaptchaEnterpriseProvider === 'function') {
        providerInstance = new firebaseSdk.ReCaptchaEnterpriseProvider(cfg.recaptchaSiteKey);
      } else if (typeof firebaseSdk.ReCaptchaV3Provider === 'function') {
        providerInstance = new firebaseSdk.ReCaptchaV3Provider(cfg.recaptchaSiteKey);
        providerName = 'recaptcha-v3';
      } else {
        throw new Error('No supported Web App Check reCAPTCHA provider available in SDK');
      }

      var appCheckInstance = firebaseSdk.initializeAppCheck(firebaseSdk.app, {
        provider: providerInstance,
        isTokenAutoRefreshEnabled: WEB_APP_CHECK_CONFIG.isTokenAutoRefreshEnabled
      });

      webAppCheckState.initialized = true;
      webAppCheckState.provider = providerName;
      webAppCheckState.instance = appCheckInstance;
      webAppCheckState.getTokenFn =
        typeof firebaseSdk.getToken === 'function'
          ? function (forceRefresh) {
              return firebaseSdk.getToken(appCheckInstance, Boolean(forceRefresh));
            }
          : null;
      webAppCheckState.lastError = null;
      return { initialized: true, provider: providerName, reason: null };
    } catch (err) {
      webAppCheckState.initialized = false;
      webAppCheckState.provider = 'error';
      webAppCheckState.instance = null;
      webAppCheckState.getTokenFn = null;
      webAppCheckState.lastError = err && err.message ? String(err.message) : 'app_check_init_failed';
      return { initialized: false, provider: 'error', reason: webAppCheckState.lastError };
    }
  }

  async function getWebAppCheckToken(forceRefresh) {
    if (!webAppCheckState.initialized || typeof webAppCheckState.getTokenFn !== 'function') {
      return null;
    }
    try {
      var result = await webAppCheckState.getTokenFn(Boolean(forceRefresh));
      var token = result && typeof result.token === 'string' ? result.token.trim() : '';
      return isValidJwtLikeToken(token) ? token : null;
    } catch (err) {
      webAppCheckState.lastError = err && err.message ? String(err.message) : 'token_fetch_failed';
      return null;
    }
  }

  function buildFirestoreRequestHeaders(extraHeaders, appCheckToken) {
    var headers = { Accept: 'application/json' };
    if (extraHeaders && typeof extraHeaders === 'object') {
      var keys = Object.keys(extraHeaders);
      for (var i = 0; i < keys.length; i++) {
        headers[keys[i]] = extraHeaders[keys[i]];
      }
    }
    if (isValidJwtLikeToken(appCheckToken)) {
      headers[WEB_APP_CHECK_CONFIG.headerName] = appCheckToken.trim();
    }
    return headers;
  }

  function parseGroundedVetSources(rawSources, fallbackDate) {
    if (!Array.isArray(rawSources)) return [];
    var today = String(fallbackDate || new Date().toISOString().slice(0, 10));
    var out = [];
    var seen = {};
    for (var i = 0; i < rawSources.length && out.length < 10; i++) {
      var item = rawSources[i];
      if (!item || typeof item !== 'object') continue;
      var url = String(item.url || item.uri || '').trim();
      if (!/^https:\/\//i.test(url)) continue;
      if (seen[url]) continue;
      seen[url] = true;
      var domain = String(item.domain || '').trim();
      if (!domain) {
        try {
          domain = new URL(url).hostname.replace(/^www\./i, '');
        } catch (_) {
          domain = '';
        }
      }
      out.push({
        title: String(item.title || domain || 'Veterinary Source').trim(),
        url: url,
        domain: domain,
        retrievedDate: String(item.retrievedDate || item.date || today).trim()
      });
    }
    return out;
  }

  /**
   * Server-side-only Web AI Veterinary request helper:
   * - Blocks direct client calls to generativelanguage.googleapis.com or client-side API keys.
   * - Sends X-Firebase-AppCheck attestation token to a secure server-side endpoint.
   * - Automatically refreshes App Check token once on HTTP 401 before surfacing diagnostic error.
   */
  async function requestServerSideVetAdvice(endpointUrl, payload, fetchImpl, options) {
    var fetcher = fetchImpl || (typeof fetch === 'function' ? fetch : null);
    if (!fetcher) return { ok: false, status: 0, error: 'fetch_unavailable' };
    var url = String(endpointUrl || '').trim();
    if (!url || (!/^https:\/\//i.test(url) && !/^\/api\//i.test(url))) {
      return { ok: false, status: 400, error: 'invalid_server_endpoint' };
    }
    if (/generativelanguage\.googleapis\.com/i.test(url) || /[?&]key=/i.test(url)) {
      return { ok: false, status: 403, error: 'client_side_api_key_forbidden' };
    }

    var opts = options || {};
    var tokenProvider =
      typeof opts.getToken === 'function'
        ? opts.getToken
        : function (force) {
            return opts.appCheckToken && !force ? Promise.resolve(opts.appCheckToken) : getWebAppCheckToken(force);
          };

    for (var attempt = 0; attempt < 2; attempt++) {
      var token = await tokenProvider(attempt > 0);
      if (!isValidJwtLikeToken(token)) {
        return {
          ok: false,
          status: 401,
          error: 'missing_app_check_token',
          message: 'Firebase App Check token is required for AI Veterinary requests.'
        };
      }
      var headers = buildFirestoreRequestHeaders({ 'Content-Type': 'application/json' }, token);
      var res = await fetcher(url, {
        method: 'POST',
        headers: headers,
        body: JSON.stringify(payload || {}),
        cache: 'no-store'
      });

      if (res.status === 401 && attempt === 0) {
        continue;
      }

      if (!res.ok) {
        return {
          ok: false,
          status: res.status,
          error: res.status === 401 ? 'app_check_unauthorized' : 'server_ai_error'
        };
      }

      var data = await res.json();
      return {
        ok: true,
        status: res.status,
        text: String(data.text || '').trim(),
        modelUsed: String(data.modelUsed || 'gemini-3.8-flash'),
        usedFallbackModel: Boolean(data.usedFallbackModel),
        usedGoogleSearch: Boolean(data.usedGoogleSearch),
        sources: parseGroundedVetSources(data.sources, data.retrievedDate)
      };
    }

    return { ok: false, status: 401, error: 'app_check_unauthorized' };
  }

  var I18N = {
    hi: {
      homePageTitle: 'जय भोले गौशाला समिति | आधिकारिक डिजिटल पोर्टल',
      pageTitlePrefix: 'संपूर्ण डिजिटल गौ प्रोफ़ाइल',
      toolbarBrand: 'जय भोले गौशाला समिति',
      backHomeNav: '← होम डैशबोर्ड',
      btnGoHome: 'होम डैशबोर्ड पर जाएं',
      homePortalTag: 'आधिकारिक डिजिटल गौशाला पोर्टल',
      homeTitle: 'जय भोले गौशाला समिति',
      homeSloganPrimary: 'महादेव की कृपा, गौमाता की सेवा।',
      homeSloganSecondary: 'बेसहारा गौवंश का सहारा बनना ही हमारा संकल्प है।',
      homePillarText: 'गौ सेवा • जीव रक्षा • समाज सेवा',
      homeLocationBadge: 'मेहदोरा, पोरसा, मुरैना (म. प्र.)',
      homeLookupKicker: 'डिजिटल गौ प्रोफ़ाइल खोजें',
      homeLookupHeading: 'पशु पहचान क्रमांक (Animal ID) से पूरा विवरण देखें',
      homeLookupDesc: 'यदि आपके पास गौशाला द्वारा जारी पशु पहचान क्रमांक है (जैसे JBGS-G-357), तो नीचे दर्ज करके सीधे गौमाता की पूरी फोटो, स्वास्थ्य, टीकाकरण व चारा प्रोफ़ाइल देखें।',
      homeLookupInputLabel: 'पशु पहचान क्रमांक',
      homeLookupPlaceholder: 'उदाहरण: JBGS-G-357',
      lookupSubmitText: 'गौ प्रोफ़ाइल देखें',
      lookupInvalidMsg: 'कृपया वैध पशु पहचान क्रमांक दर्ज करें (जैसे JBGS-G-357 — केवल अक्षर, अंक, - या _)।',
      homePhotoTitle: 'गौमाता की सेवा एवं संरक्षण',
      homePhotoTag: 'QR सत्यापित',
      featCard1Title: 'संपूर्ण डिजिटल गौ प्रोफ़ाइल',
      featCard1Desc: 'प्रत्येक पंजीकृत गौवंश की बड़ी फोटो, नस्ल, आयु, वज़न, शेड, स्वास्थ्य, बीमारी, उपचार, टीकाकरण और चारा विवरण एक साथ।',
      featCard2Title: 'गौ सेवा, आश्रय एवं उपचार',
      featCard2Desc: 'बेसहारा और जरूरतमंद गौवंश के लिए सुरक्षित आश्रय, स्वच्छ चारा-पानी, नियमित पशु चिकित्सा जांच एवं संवेदनशील देखभाल की व्यवस्था।',
      featCard3Title: 'पारदर्शी और सुरक्षित रिकॉर्ड',
      featCard3Desc: 'केवल एडमिन द्वारा स्वीकृत सार्वजनिक विवरण ही ऑनलाइन प्रदर्शित होते हैं, जबकि गौशाला का निजी व आंतरिक डेटा पूर्णतः सुरक्षित रहता है।',
      guideKicker: 'QR सत्यापन प्रक्रिया',
      guideHeading: 'Google Lens या QR स्कैनर से गौ प्रोफ़ाइल कैसे देखें?',
      step1Title: 'आधिकारिक QR स्कैन करें',
      step1Desc: 'गौवंश के टैग या कार्ड पर छपे QR कोड को Google Lens या किसी भी मोबाइल कैमरे से स्कैन करें।',
      step2Title: 'पूरी प्रोफ़ाइल तुरंत खुलेगी',
      step2Desc: '/cattle/Animal-ID लिंक पर जाते ही गौमाता की बड़ी फोटो, पहचान, स्वास्थ्य, उपचार और चारा रिकॉर्ड दिखाई देगा।',
      step3Title: 'प्रिंट, PDF या शेयर करें',
      step3Desc: 'गौ प्रोफ़ाइल को सीधे प्रिंट करें, PDF के रूप में सहेजें या WhatsApp पर साझा करें।',
      homeFooterOrg: 'जय भोले गौशाला समिति',
      homeFooterAddress: 'ग्राम – मेहदोरा, विकासखंड – पोरसा, जिला – मुरैना, मध्य प्रदेश, भारत।',
      homeFooterTag: 'सुरक्षित डिजिटल गौवंश पहचान एवं प्रबंधन पोर्टल',
      cardTitleSub: 'संपूर्ण डिजिटल गौ प्रोफ़ाइल • Official Digital Cattle Profile',
      chipKicker: 'पशु पहचान क्रमांक',
      verifiedText: 'सत्यापित सार्वजनिक गौ रिकॉर्ड (एडमिन स्वीकृत)',
      photoPlaceholderNotice: 'गौमाता की वास्तविक फोटो अभी अपलोड नहीं की गई है',
      photoZoomHint: 'पूरा फोटो देखने के लिए टैप करें',
      qrLabel: 'सत्यापन QR कोड',
      qrHint: 'Google Lens या किसी भी QR स्कैनर से सीधे यह प्रोफ़ाइल खोलें।',
      secIdentity: 'गौवंश संपूर्ण पहचान विवरण',
      lblAnimalCode: 'यूनिक आईडी (Unique ID)',
      lblCategory: 'पशु प्रकार (Category)',
      lblBreed: 'नस्ल (Breed)',
      lblAgeDob: 'आयु / जन्म तिथि',
      lblSex: 'लिंग (Sex)',
      lblColour: 'रंग (Colour)',
      lblWeight: 'वज़न (Weight)',
      lblEntryDate: 'पंजीकरण / प्रवेश तिथि',
      lblShedNumber: 'शेड क्रमांक (Shed No.)',
      lblGoshalaFull: 'गौशाला नाम एवं पता',
      secHealth: 'स्वास्थ्य, टीकाकरण, बीमारी एवं उपचार रिकॉर्ड',
      lblHealthStatus: 'वर्तमान स्वास्थ्य स्थिति',
      lblLastCheckup: 'अंतिम स्वास्थ्य जांच',
      lblVaccination: 'टीकाकरण विवरण (Vaccination)',
      lblHealthTimeline: 'बीमारी, उपचार एवं चिकित्सा इतिहास',
      healthHistoryEmpty: 'अभी कोई अतिरिक्त बीमारी, टीकाकरण या उपचार इतिहास दर्ज नहीं है।',
      lblMedicine: 'दवा / उपचार',
      lblVetName: 'पशु चिकित्सक',
      lblFollowUp: 'अगली जांच',
      lblWeightRecorded: 'दर्ज वज़न',
      secFeed: 'चारा, दाना एवं दैनिक देखभाल रिकॉर्ड',
      feedDatePrefix: 'दिनांक: ',
      lblGreenFodder: 'हरा चारा',
      lblDryFodder: 'सूखा चारा / भूसा',
      lblConcentrate: 'दाना / खली',
      lblWaterLiters: 'स्वच्छ पानी',
      lblShedCleaned: 'शेड स्वच्छता',
      shedCleanedYes: 'पूर्ण ✓',
      shedCleanedNo: 'प्रतीक्षारत',
      feedEmptyMsg: 'चारा व दैनिक देखभाल का नवीनतम रिकॉर्ड अभी उपलब्ध नहीं है।',
      secCare: 'विशेष पहचान, गौ-सेवा एवं गौशाला विवरण',
      lblIdentMarks: 'पहचान चिन्ह / विशेष देखभाल जानकारी',
      lblSponsorship: 'गौ-सेवा / गोद सहायता (Sponsorship)',
      sponsorAvailableYes: 'गोद सेवा के लिए उपलब्ध',
      sponsorMonthlyFormat: '₹{amount} / माह (गौ-सेवा सहायता उपलब्ध)',
      sponsorNotAvailable: 'वर्तमान में सामान्य गौशाला देखभाल में',
      sponsorBadgeText: 'गौ-सेवा उपलब्ध',
      unitKg: 'किग्रा',
      unitLiters: 'लीटर',
      footerVerified: 'जय भोले गौशाला समिति — सुरक्षित डिजिटल गौवंश पहचान प्रणाली',
      btnPrint: 'प्रिंट करें',
      btnPdf: 'PDF डाउनलोड करें',
      btnWhatsapp: 'WhatsApp पर शेयर करें',
      btnRetry: 'पुनः प्रयास करें',
      printNote: 'PDF सहेजने के लिए प्रिंट डायलॉग में “Save as PDF” चुनें।',
      offlineBanner: 'इंटरनेट कनेक्शन उपलब्ध नहीं है। कृपया नेटवर्क जाँचकर पुनः प्रयास करें।',
      loadingTitle: 'गौमाता का पूरा विवरण लोड हो रहा है...',
      loadingSubtitle: 'जय भोले गौशाला समिति के आधिकारिक डेटाबेस से फोटो और संपूर्ण रिकॉर्ड प्राप्त किया जा रहा है।',
      notRecorded: 'रिकॉर्ड उपलब्ध नहीं',
      errInvalidTitle: 'अमान्य QR कोड लिंक',
      errInvalidBody: 'यह लिंक किसी वैध गौ पहचान क्रमांक (जैसे /cattle/JBGS-G-357) की ओर संकेत नहीं करता। कृपया गौशाला के आधिकारिक QR कोड को पुनः स्कैन करें या होम डैशबोर्ड पर जाएं।',
      errNotFoundTitle: 'पेज उपलब्ध नहीं है',
      errNotFoundBody: 'आप जिस वेब पते को खोलने का प्रयास कर रहे हैं वह उपलब्ध नहीं है। कृपया जय भोले गौशाला समिति के मुख्य होम डैशबोर्ड पर जाएं।',
      errCattleNotFoundTitle: 'गौ रिकॉर्ड डेटाबेस में नहीं मिला',
      errCattleNotFoundBody: 'पशु पहचान क्रमांक "{code}" का सार्वजनिक रिकॉर्ड अभी डेटाबेस में उपलब्ध नहीं है। यदि यह गाय अभी पंजीकृत हुई है, तो एडमिन ऐप में इसे सार्वजनिक स्वीकृति (Public Approved) देकर सिंक करें।',
      errPrivateTitle: 'प्रोफ़ाइल सार्वजनिक रूप से उपलब्ध नहीं है',
      errPrivateBody: 'पशु पहचान क्रमांक "{code}" का रिकॉर्ड अभी एडमिन द्वारा सार्वजनिक प्रदर्शन के लिए स्वीकृत नहीं है।',
      errOfflineTitle: 'इंटरनेट कनेक्शन नहीं है',
      errOfflineBody: 'गौ प्रोफ़ाइल देखने के लिए इंटरनेट कनेक्शन आवश्यक है। नेटवर्क चालू करके पुनः प्रयास करें।',
      errGeneralTitle: 'डेटा लोड करने में समस्या',
      errGeneralBody: 'सर्वर से गौ विवरण प्राप्त नहीं हो सका। कृपया कुछ समय बाद पुनः प्रयास करें।',
      categoryMap: { COW: 'गाय (Cow)', BULL: 'नंदी / बैल (Bull)', CALF: 'बछड़ा / बछिया (Calf)' },
      sexMap: { FEMALE: 'मादा (Female)', MALE: 'नर (Male)' },
      healthMap: {
        HEALTHY: 'स्वस्थ (Healthy)',
        SICK: 'अस्वस्थ / बीमारी (Needs Care)',
        UNDER_TREATMENT: 'उपचाराधीन (Under Treatment)',
        RECOVERING: 'सुधार पर (Recovering)'
      },
      recordTypeMap: {
        VACCINATION: 'टीकाकरण',
        TREATMENT: 'बीमारी / उपचार',
        VET_VISIT: 'चिकित्सा जांच',
        WEIGHT_CHECK: 'वज़न जांच'
      }
    },
    en: {
      homePageTitle: 'Jay Bhole Gosala Samiti | Official Digital Portal',
      pageTitlePrefix: 'Digital Cattle Profile',
      toolbarBrand: 'Jay Bhole Gosala Samiti',
      backHomeNav: '← Home Dashboard',
      btnGoHome: 'Go to Home Dashboard',
      homePortalTag: 'Official Digital Goshala Portal',
      homeTitle: 'Jay Bhole Gosala Samiti',
      homeSloganPrimary: 'Grace of Mahadev, Service to Gaumata.',
      homeSloganSecondary: 'Supporting destitute cattle is our solemn pledge.',
      homePillarText: 'Cow Care • Animal Protection • Social Service',
      homeLocationBadge: 'Mehdora, Porsa, Morena (M.P.)',
      homeLookupKicker: 'Digital Cattle Profile Lookup',
      homeLookupHeading: 'Open Complete Cattle Profile by Animal ID',
      homeLookupDesc: 'Enter an official Animal ID issued by the Goshala (for example JBGS-G-357) to view the full photo, identity, health, vaccination, treatment, and fodder details.',
      homeLookupInputLabel: 'Animal ID',
      homeLookupPlaceholder: 'Example: JBGS-G-357',
      lookupSubmitText: 'View Profile',
      lookupInvalidMsg: 'Please enter a valid Animal ID (for example JBGS-G-357 — letters, numbers, hyphens, or underscores only).',
      homePhotoTitle: 'Cattle Care & Protection',
      homePhotoTag: 'QR Verified',
      featCard1Title: 'Complete Digital Cattle Profile',
      featCard1Desc: 'View every registered animal’s full photo, breed, age, weight, shed, health status, illness/treatment history, vaccination, and daily fodder details.',
      featCard2Title: 'Shelter, Care & Veterinary Support',
      featCard2Desc: 'Safe shelter, clean fodder and water, routine veterinary checkups, and compassionate care for rescued and destitute cattle.',
      featCard3Title: 'Transparent & Secure Records',
      featCard3Desc: 'Only Admin-approved public records appear online, while internal Goshala management and private staff data remain fully protected.',
      guideKicker: 'How QR Verification Works',
      guideHeading: 'How to Open a Cattle Profile via Google Lens?',
      step1Title: 'Scan the Official QR Code',
      step1Desc: 'Scan the QR code printed on the animal tag or ID card using Google Lens or any mobile camera.',
      step2Title: 'Full Profile Opens Instantly',
      step2Desc: 'The /cattle/Animal-ID link displays the full photo, identity details, health/treatment timeline, and daily fodder record.',
      step3Title: 'Print, Save PDF, or Share',
      step3Desc: 'Print the cattle profile directly, save it as a PDF, or share the verified link on WhatsApp.',
      homeFooterOrg: 'Jay Bhole Gosala Samiti',
      homeFooterAddress: 'Village – Mehdora, Block – Porsa, District – Morena, Madhya Pradesh, India.',
      homeFooterTag: 'Verified Digital Cattle Identity & Management Portal',
      cardTitleSub: 'Official Digital Cattle Profile • संपूर्ण डिजिटल गौ प्रोफ़ाइल',
      chipKicker: 'Unique Animal ID',
      verifiedText: 'Verified Public Cow Record (Admin Approved)',
      photoPlaceholderNotice: 'Real cow photo has not been uploaded yet',
      photoZoomHint: 'Tap to view full photo',
      qrLabel: 'Verification QR Code',
      qrHint: 'Scan with Google Lens or any QR scanner to open this full profile.',
      secIdentity: 'Complete Animal Identity Details',
      lblAnimalCode: 'Unique ID',
      lblCategory: 'Animal Category',
      lblBreed: 'Breed',
      lblAgeDob: 'Age / Birth Date',
      lblSex: 'Sex',
      lblColour: 'Colour',
      lblWeight: 'Weight',
      lblEntryDate: 'Registration / Entry Date',
      lblShedNumber: 'Shed Number',
      lblGoshalaFull: 'Goshala Name & Address',
      secHealth: 'Health, Vaccination, Illness & Treatment Record',
      lblHealthStatus: 'Current Health Status',
      lblLastCheckup: 'Last Health Checkup',
      lblVaccination: 'Vaccination Summary',
      lblHealthTimeline: 'Illness, Treatment & Veterinary History',
      healthHistoryEmpty: 'No additional illness, vaccination, or treatment history recorded yet.',
      lblMedicine: 'Medicine / Treatment',
      lblVetName: 'Veterinarian',
      lblFollowUp: 'Next Follow-up',
      lblWeightRecorded: 'Recorded Weight',
      secFeed: 'Fodder, Feed & Daily Care Record',
      feedDatePrefix: 'Date: ',
      lblGreenFodder: 'Green Fodder',
      lblDryFodder: 'Dry Fodder / Bhusa',
      lblConcentrate: 'Concentrate / Khali',
      lblWaterLiters: 'Clean Water',
      lblShedCleaned: 'Shed Hygiene',
      shedCleanedYes: 'Cleaned ✓',
      shedCleanedNo: 'Pending',
      feedEmptyMsg: 'Latest fodder and daily care record is not available yet.',
      secCare: 'Identification Marks, Sponsorship & Goshala Details',
      lblIdentMarks: 'Identification Marks & Special Care',
      lblSponsorship: 'Gau-Seva Sponsorship',
      sponsorAvailableYes: 'Available for Sponsorship',
      sponsorMonthlyFormat: '₹{amount} / month (Available for Gau-Seva Sponsorship)',
      sponsorNotAvailable: 'Currently under regular Goshala care',
      sponsorBadgeText: 'Sponsorship Open',
      unitKg: 'kg',
      unitLiters: 'L',
      footerVerified: 'Jay Bhole Gosala Samiti — Verified Digital Cattle Identity System',
      btnPrint: 'Print Profile',
      btnPdf: 'Download PDF',
      btnWhatsapp: 'Share on WhatsApp',
      btnRetry: 'Retry',
      printNote: 'Select "Save as PDF" in the print dialog to download this Cattle Profile as a PDF.',
      offlineBanner: 'You appear to be offline. Please check your internet connection and retry.',
      loadingTitle: 'Loading Cattle Profile...',
      loadingSubtitle: 'Fetching verified photo and complete details from Jay Bhole Gosala Samiti database.',
      notRecorded: 'Not recorded',
      errInvalidTitle: 'Invalid QR Code URL',
      errInvalidBody: 'This link does not contain a valid Animal ID (for example /cattle/JBGS-G-357). Please scan an official Goshala QR code or return to the Home Dashboard.',
      errNotFoundTitle: 'Page Not Found',
      errNotFoundBody: 'The page you requested does not exist. Please return to the Jay Bhole Gosala Samiti Home Dashboard.',
      errCattleNotFoundTitle: 'Cattle Record Not Found',
      errCattleNotFoundBody: 'No public profile was found in the database for Animal ID "{code}". If this animal was recently registered, please approve it for public display in the Admin app.',
      errPrivateTitle: 'Profile Not Publicly Approved',
      errPrivateBody: 'Animal ID "{code}" is not currently approved by the Goshala Admin for public display.',
      errOfflineTitle: 'No Internet Connection',
      errOfflineBody: 'An active internet connection is required to view this Cattle Profile. Please reconnect and try again.',
      errGeneralTitle: 'Unable to Load Record',
      errGeneralBody: 'Could not fetch this Cattle Profile right now. Please try again in a moment.',
      categoryMap: { COW: 'Cow', BULL: 'Bull', CALF: 'Calf' },
      sexMap: { FEMALE: 'Female', MALE: 'Male' },
      healthMap: {
        HEALTHY: 'Healthy',
        SICK: 'Sick / Needs Care',
        UNDER_TREATMENT: 'Under Treatment',
        RECOVERING: 'Recovering'
      },
      recordTypeMap: {
        VACCINATION: 'Vaccination',
        TREATMENT: 'Illness / Treatment',
        VET_VISIT: 'Vet Checkup',
        WEIGHT_CHECK: 'Weight Check'
      }
    }
  };

  function normalizeAnimalCode(raw) {
    if (typeof raw !== 'string') return null;
    var cleaned = raw.trim().toUpperCase();
    return ANIMAL_CODE_REGEX.test(cleaned) ? cleaned : null;
  }

  /**
   * Extracts a validated, case-preserved cattleId from pathname (/cattle/<cattleId>)
   * using path splitting (e.g. pathname.split('/cattle/')[1]) or query (?id=<cattleId>).
   * Rejects empty IDs, extra path segments, path traversal, or disallowed characters.
   */
  function extractCattleIdFromPath(pathname, search) {
    var path = String(pathname || '').trim();
    if (/^\/cattle\//i.test(path)) {
      var afterPrefix = path.split(/\/cattle\//i)[1];
      if (!afterPrefix) return null;
      var withoutTrailingSlash = afterPrefix.replace(/\/$/, '');
      if (!withoutTrailingSlash || withoutTrailingSlash.indexOf('/') !== -1) {
        return null;
      }
      try {
        var decoded = decodeURIComponent(withoutTrailingSlash).trim();
        return CATTLE_ID_REGEX.test(decoded) ? decoded : null;
      } catch (_) {
        return null;
      }
    }

    if (search) {
      var params = new URLSearchParams(search);
      var candidate = params.get('id') || params.get('code');
      if (candidate) {
        var trimmed = candidate.trim();
        return CATTLE_ID_REGEX.test(trimmed) ? trimmed : null;
      }
    }
    return null;
  }

  /**
   * Extracts a validated uppercase Animal Code from pathname (/cattle/JBGS-G-357)
   * or query (?id=JBGS-G-357). Rejects malformed paths or suspicious characters.
   */
  function extractAnimalCodeFromLocation(pathname, search) {
    var cattleId = extractCattleIdFromPath(pathname, search);
    return cattleId ? normalizeAnimalCode(cattleId) : null;
  }

  /**
   * Strictly separates routes:
   * - '/' or '/index.html' (without id/code query params) -> { view: 'home', animalCode: null, cattleId: null }
   * - '/cattle/<validId>' or '/?id=<validId>'             -> { view: 'cattle', animalCode: '<CODE|null>', cattleId: '<id>' }
   * - '/cattle', '/cattle/', '/cattle/<invalid>', '/?id=' -> { view: 'invalid_qr', animalCode: null, cattleId: null }
   * - Any other path                                      -> { view: 'not_found', animalCode: null, cattleId: null }
   */
  function resolveRoute(pathname, search) {
    var path = String(pathname || '/').trim() || '/';
    var params = search ? new URLSearchParams(search) : null;
    var hasQueryId = Boolean(params && (params.has('id') || params.has('code')));

    if (/^\/cattle(\/|$)/i.test(path)) {
      var cattleIdFromPath = extractCattleIdFromPath(path, '');
      if (cattleIdFromPath) {
        return {
          view: 'cattle',
          animalCode: normalizeAnimalCode(cattleIdFromPath),
          cattleId: cattleIdFromPath
        };
      }
      return { view: 'invalid_qr', animalCode: null, cattleId: null };
    }

    if (path === '/' || path.toLowerCase() === '/index.html') {
      if (hasQueryId) {
        var cattleIdFromQuery = extractCattleIdFromPath('/', search);
        if (cattleIdFromQuery) {
          return {
            view: 'cattle',
            animalCode: normalizeAnimalCode(cattleIdFromQuery),
            cattleId: cattleIdFromQuery
          };
        }
        return { view: 'invalid_qr', animalCode: null, cattleId: null };
      }
      return { view: 'home', animalCode: null, cattleId: null };
    }

    return { view: 'not_found', animalCode: null, cattleId: null };
  }

  function buildFirestoreDocumentsBaseUrl() {
    return (
      'https://firestore.googleapis.com/v1/projects/' +
      encodeURIComponent(FIREBASE_PROJECT_ID) +
      '/databases/' +
      encodeURIComponent(FIRESTORE_DATABASE_ID) +
      '/documents'
    );
  }

  function buildFirestoreDocumentUrl(animalCode) {
    var code = normalizeAnimalCode(animalCode);
    if (!code) throw new Error('Invalid animal code');
    return buildFirestoreDocumentsBaseUrl() + '/publicCattleCards/' + encodeURIComponent(code);
  }

  function buildCattleDocumentUrl(cattleId) {
    var cleanId = String(cattleId || '').trim();
    if (!CATTLE_ID_REGEX.test(cleanId)) {
      throw new Error('Invalid cattle document ID');
    }
    return buildFirestoreDocumentsBaseUrl() + '/cattle/' + encodeURIComponent(cleanId);
  }

  function buildCattleCollectionQueryUrl() {
    return buildFirestoreDocumentsBaseUrl() + ':runQuery';
  }

  /**
   * Builds a Firestore REST structuredQuery targeting the 'cattle' collection
   * filtered strictly by animalCode AND publicApproved == true.
   */
  function buildCattleQueryPayload(animalCode) {
    var code = normalizeAnimalCode(animalCode);
    if (!code) throw new Error('Invalid animal code');
    return {
      structuredQuery: {
        from: [{ collectionId: 'cattle' }],
        where: {
          compositeFilter: {
            op: 'AND',
            filters: [
              {
                fieldFilter: {
                  field: { fieldPath: 'animalCode' },
                  op: 'EQUAL',
                  value: { stringValue: code }
                }
              },
              {
                fieldFilter: {
                  field: { fieldPath: 'publicApproved' },
                  op: 'EQUAL',
                  value: { booleanValue: true }
                }
              }
            ]
          }
        },
        limit: 1
      }
    };
  }

  /**
   * Converts a Firebase Storage relative object path (e.g. cattle_photos/<id>/photo.jpg)
   * or gs:// bucket URI into a safe HTTPS Firebase Storage media URL.
   */
  function buildFirebaseStorageMediaUrl(storagePath) {
    if (typeof storagePath !== 'string') return '';
    var trimmed = storagePath.trim();
    if (!trimmed) return '';

    var bucket = FIREBASE_STORAGE_BUCKET;
    var objectPath = '';
    if (/^gs:\/\//i.test(trimmed)) {
      var withoutScheme = trimmed.slice(5);
      var slashIdx = withoutScheme.indexOf('/');
      if (slashIdx <= 0 || slashIdx >= withoutScheme.length - 1) return '';
      bucket = withoutScheme.slice(0, slashIdx).trim();
      objectPath = withoutScheme.slice(slashIdx + 1).trim();
    } else if (/^cattle_photos\//i.test(trimmed)) {
      objectPath = trimmed.replace(/^\/+/, '');
    } else {
      return '';
    }

    if (!objectPath || objectPath.indexOf('..') !== -1) return '';
    return (
      'https://firebasestorage.googleapis.com/v0/b/' +
      encodeURIComponent(bucket) +
      '/o/' +
      encodeURIComponent(objectPath) +
      '?alt=media'
    );
  }

  /**
   * Recursively decodes Firestore REST Value objects (primitives, mapValue, arrayValue).
   */
  function readFirestoreValue(fieldObj) {
    if (!fieldObj || typeof fieldObj !== 'object') return null;
    if (typeof fieldObj.stringValue === 'string') return fieldObj.stringValue;
    if (typeof fieldObj.booleanValue === 'boolean') return fieldObj.booleanValue;
    if (typeof fieldObj.integerValue !== 'undefined') return Number(fieldObj.integerValue);
    if (typeof fieldObj.doubleValue !== 'undefined') return Number(fieldObj.doubleValue);
    if (typeof fieldObj.timestampValue === 'string') return fieldObj.timestampValue;
    if (fieldObj.nullValue !== undefined) return null;
    if (fieldObj.mapValue && typeof fieldObj.mapValue === 'object') {
      var out = {};
      var mapFields = fieldObj.mapValue.fields || {};
      var keys = Object.keys(mapFields);
      for (var i = 0; i < keys.length; i++) {
        out[keys[i]] = readFirestoreValue(mapFields[keys[i]]);
      }
      return out;
    }
    if (fieldObj.arrayValue && typeof fieldObj.arrayValue === 'object') {
      var arr = fieldObj.arrayValue.values || [];
      var list = [];
      for (var j = 0; j < arr.length; j++) {
        list.push(readFirestoreValue(arr[j]));
      }
      return list;
    }
    return null;
  }

  function isSafeHttpOrDataImageUrl(url) {
    if (typeof url !== 'string') return false;
    var trimmed = url.trim();
    if (!trimmed) return false;
    if (/^https:\/\//i.test(trimmed)) return true;
    if (/^data:image\/(jpeg|jpg|png|webp);base64,[A-Za-z0-9+/=]+$/i.test(trimmed)) return true;
    return false;
  }

  function parseHealthHistory(fieldObj) {
    var raw = readFirestoreValue(fieldObj);
    if (!Array.isArray(raw)) return [];
    var items = [];
    for (var i = 0; i < raw.length && items.length < 15; i++) {
      var entry = raw[i];
      if (!entry || typeof entry !== 'object') continue;
      var title = String(entry.title || '').trim();
      var recordType = String(entry.recordType || 'VET_VISIT').trim().toUpperCase();
      var medicine = String(entry.medicine || '').trim();
      var vetName = String(entry.vetName || '').trim();
      var dateStr = String(entry.dateStr || '').trim();
      var nextFollowUpDate = String(entry.nextFollowUpDate || '').trim();
      var weightVal = Number(entry.weightKg || 0);
      if (!title && !medicine && !nextFollowUpDate) continue;
      items.push({
        recordType: recordType,
        title: title,
        medicine: medicine,
        vetName: vetName,
        weightKg: isFinite(weightVal) && weightVal > 0 ? weightVal : 0,
        nextFollowUpDate: nextFollowUpDate,
        dateStr: dateStr
      });
    }
    return items;
  }

  function parseFeedSummary(fieldObj) {
    var raw = readFirestoreValue(fieldObj);
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null;
    var logDate = String(raw.logDate || '').trim();
    var greenFodderKg = Number(raw.greenFodderKg || 0);
    var dryFodderBhusaKg = Number(raw.dryFodderBhusaKg || 0);
    var concentrateKg = Number(raw.concentrateKg || 0);
    var waterLiters = Number(raw.waterLiters || 0);
    var shedCleaned = raw.shedCleaned === true;
    if (!logDate && greenFodderKg <= 0 && dryFodderBhusaKg <= 0 && concentrateKg <= 0 && waterLiters <= 0) {
      return null;
    }
    return {
      logDate: logDate,
      greenFodderKg: isFinite(greenFodderKg) && greenFodderKg >= 0 ? greenFodderKg : 0,
      dryFodderBhusaKg: isFinite(dryFodderBhusaKg) && dryFodderBhusaKg >= 0 ? dryFodderBhusaKg : 0,
      concentrateKg: isFinite(concentrateKg) && concentrateKg >= 0 ? concentrateKg : 0,
      waterLiters: isFinite(waterLiters) && waterLiters >= 0 ? waterLiters : 0,
      shedCleaned: shedCleaned
    };
  }

  /**
   * Parses a Firestore REST document from either `publicCattleCards/{animalCode}` OR `cattle/{docId}`
   * into a strictly sanitized, public-safe cattle profile model.
   * Returns null if document is missing publicApproved === true or lacks required identity fields.
   * Never copies internal private fields (notes, costInr, recordedBy, createdBy, phone, doc ID).
   */
  function parsePublicCattleDocument(docJson, fallbackCattleId) {
    if (!docJson || typeof docJson !== 'object' || !docJson.fields) return null;
    var f = docJson.fields;
    var approved = readFirestoreValue(f.publicApproved);
    if (approved !== true) return null;

    var animalCode =
      normalizeAnimalCode(readFirestoreValue(f.animalCode) || '') ||
      normalizeAnimalCode(fallbackCattleId || '') ||
      (typeof fallbackCattleId === 'string' && CATTLE_ID_REGEX.test(fallbackCattleId.trim())
        ? fallbackCattleId.trim()
        : null);
    var name = String(readFirestoreValue(f.name) || '').trim();
    if (!animalCode || !name) return null;

    var rawPhotoUrl = String(readFirestoreValue(f.photoUrl) || readFirestoreValue(f.photoUri) || '').trim();
    var rawStoragePath = String(readFirestoreValue(f.photoStoragePath) || '').trim();
    var resolvedPhotoUrl = '';
    if (isSafeHttpOrDataImageUrl(rawPhotoUrl)) {
      resolvedPhotoUrl = rawPhotoUrl;
    } else if (rawStoragePath) {
      var mediaUrl = buildFirebaseStorageMediaUrl(rawStoragePath);
      if (isSafeHttpOrDataImageUrl(mediaUrl)) resolvedPhotoUrl = mediaUrl;
    }

    var rawPhotoDataUrl = String(readFirestoreValue(f.photoDataUrl) || '').trim();
    var weightVal = Number(readFirestoreValue(f.weightKg) || 0);
    var monthlySupportVal = Number(readFirestoreValue(f.monthlySupportInr) || 0);

    return {
      animalCode: animalCode,
      name: name,
      category: String(readFirestoreValue(f.category) || 'COW').trim().toUpperCase(),
      breed: String(readFirestoreValue(f.breed) || '').trim(),
      sex: String(readFirestoreValue(f.sex) || 'FEMALE').trim().toUpperCase(),
      ageText: String(readFirestoreValue(f.ageText) || '').trim(),
      birthDate: String(readFirestoreValue(f.birthDate) || '').trim(),
      colour: String(readFirestoreValue(f.colour) || '').trim(),
      weightKg: isFinite(weightVal) && weightVal > 0 ? weightVal : 0,
      entryDate: String(readFirestoreValue(f.entryDate) || '').trim(),
      shedNumber: String(readFirestoreValue(f.shedNumber) || '').trim(),
      healthStatus: String(readFirestoreValue(f.healthStatus) || 'HEALTHY').trim().toUpperCase(),
      vaccinationSummary: String(readFirestoreValue(f.vaccinationSummary) || '').trim(),
      lastHealthCheckup: String(readFirestoreValue(f.lastHealthCheckup) || '').trim(),
      identificationMarks: String(readFirestoreValue(f.identificationMarks) || '').trim(),
      sponsorshipAvailable: readFirestoreValue(f.sponsorshipAvailable) === true,
      monthlySupportInr: isFinite(monthlySupportVal) && monthlySupportVal > 0 ? Math.round(monthlySupportVal) : 0,
      healthHistory: parseHealthHistory(f.healthHistory),
      feedSummary: parseFeedSummary(f.feedSummary),
      photoUrl: resolvedPhotoUrl,
      photoDataUrl: isSafeHttpOrDataImageUrl(rawPhotoDataUrl) ? rawPhotoDataUrl : '',
      goshalaNameHi: String(readFirestoreValue(f.goshalaNameHi) || 'जय भोले गौशाला समिति').trim(),
      goshalaNameEn: String(readFirestoreValue(f.goshalaNameEn) || 'Jay Bhole Gosala Samiti').trim(),
      goshalaAddressHi: String(readFirestoreValue(f.goshalaAddressHi) || 'ग्राम – मेहदोरा, विकासखंड – पोरसा, जिला – मुरैना, मध्य प्रदेश, भारत।').trim(),
      goshalaAddressEn: String(readFirestoreValue(f.goshalaAddressEn) || 'Village – Mehdora, Block – Porsa, District – Morena, Madhya Pradesh, India.').trim(),
      qrUrl: PUBLIC_HOSTING_ORIGIN + '/cattle/' + encodeURIComponent(animalCode),
      publicApproved: true
    };
  }

  /**
   * Fetches the real cattle profile from Firestore:
   * 1. Primary: GET /publicCattleCards/{animalCode} (public-safe projection with photo, healthHistory & feedSummary).
   * 2. Secondary fallback (on 404): Queries the 'cattle' collection by animalCode + publicApproved == true,
   *    and if empty, fetches direct GET /cattle/{rawCattleId} (preserving case-sensitive Firestore doc IDs),
   *    stripping all private fields via parsePublicCattleDocument.
   * Returns { status: 'ok', card } | { status: 'not_found_cattle' | 'private' | 'general', card: null }.
   */
  async function fetchCattleRecordFromFirestore(cattleId, fetchImpl, options) {
    var fetcher = fetchImpl || (typeof fetch === 'function' ? fetch : null);
    if (!fetcher) return { status: 'general', card: null };

    var rawCattleId = String(cattleId || '').trim();
    if (!CATTLE_ID_REGEX.test(rawCattleId)) {
      return { status: 'invalid', card: null };
    }
    var normalizedCode = normalizeAnimalCode(rawCattleId);
    var opts = options || {};
    var appCheckToken =
      typeof opts.appCheckToken === 'string'
        ? opts.appCheckToken
        : await getWebAppCheckToken(false);

    // 1. Primary lookup in publicCattleCards/{animalCode} when valid Animal Code
    if (normalizedCode) {
      var publicUrl = buildFirestoreDocumentUrl(normalizedCode);
      var publicRes = await fetcher(publicUrl, {
        method: 'GET',
        headers: buildFirestoreRequestHeaders(null, appCheckToken),
        cache: 'no-store'
      });

      if (publicRes.status === 401) {
        var refreshedToken =
          typeof opts.refreshAppCheckToken === 'function'
            ? await opts.refreshAppCheckToken()
            : await getWebAppCheckToken(true);
        if (isValidJwtLikeToken(refreshedToken)) {
          appCheckToken = refreshedToken;
          publicRes = await fetcher(publicUrl, {
            method: 'GET',
            headers: buildFirestoreRequestHeaders(null, appCheckToken),
            cache: 'no-store'
          });
        }
      }

      if (publicRes.ok) {
        var publicDoc = await publicRes.json();
        var parsedPublic = parsePublicCattleDocument(publicDoc, normalizedCode);
        if (parsedPublic) return { status: 'ok', card: parsedPublic };
        return { status: 'private', card: null };
      }

      if (publicRes.status === 401) {
        return { status: 'app_check_required', card: null };
      }

      if (publicRes.status === 403) {
        return { status: 'private', card: null };
      }

      if (publicRes.status !== 404) {
        return { status: 'general', card: null };
      }

      // 2. Fallback lookup in 'cattle' collection (query by animalCode + publicApproved == true)
      try {
        var queryRes = await fetcher(buildCattleCollectionQueryUrl(), {
          method: 'POST',
          headers: buildFirestoreRequestHeaders({ 'Content-Type': 'application/json' }, appCheckToken),
          body: JSON.stringify(buildCattleQueryPayload(normalizedCode)),
          cache: 'no-store'
        });

        if (queryRes.ok) {
          var rows = await queryRes.json();
          if (Array.isArray(rows)) {
            for (var i = 0; i < rows.length; i++) {
              if (rows[i] && rows[i].document) {
                var parsedFromQuery = parsePublicCattleDocument(rows[i].document, normalizedCode);
                if (parsedFromQuery) return { status: 'ok', card: parsedFromQuery };
              }
            }
          }
        }
      } catch (_) {
        // Ignore fallback network/rule rejection and proceed to direct doc check
      }
    }

    // 3. Direct document lookup in 'cattle/{cattleId}' (preserves case-sensitive Firestore doc IDs)
    try {
      var directRes = await fetcher(buildCattleDocumentUrl(rawCattleId), {
        method: 'GET',
        headers: buildFirestoreRequestHeaders(null, appCheckToken),
        cache: 'no-store'
      });
      if (directRes.ok) {
        var directDoc = await directRes.json();
        var parsedDirect = parsePublicCattleDocument(directDoc, rawCattleId);
        if (parsedDirect) return { status: 'ok', card: parsedDirect };
        return { status: 'private', card: null };
      }
    } catch (_) {
      // Fall through to clean not_found_cattle result
    }

    return { status: 'not_found_cattle', card: null };
  }

  function buildWhatsAppShareUrl(card, lang) {
    var isEn = lang === 'en';
    var lines = isEn
      ? [
          card.goshalaNameEn + ' — Digital Cattle Profile',
          'Name: ' + card.name + ' (' + card.animalCode + ')',
          'Breed: ' + (card.breed || 'Not recorded') + ' | Health: ' + (I18N.en.healthMap[card.healthStatus] || card.healthStatus),
          'View Full Profile: ' + card.qrUrl
        ]
      : [
          card.goshalaNameHi + ' — संपूर्ण डिजिटल गौ प्रोफ़ाइल',
          'नाम: ' + card.name + ' (' + card.animalCode + ')',
          'नस्ल: ' + (card.breed || 'उपलब्ध नहीं') + ' | स्वास्थ्य: ' + (I18N.hi.healthMap[card.healthStatus] || card.healthStatus),
          'पूरी प्रोफ़ाइल देखें: ' + card.qrUrl
        ];
    return 'https://wa.me/?text=' + encodeURIComponent(lines.join('\n'));
  }

  // Export helpers for Node unit tests
  var helpers = {
    FIREBASE_PROJECT_ID: FIREBASE_PROJECT_ID,
    FIRESTORE_DATABASE_ID: FIRESTORE_DATABASE_ID,
    FIREBASE_STORAGE_BUCKET: FIREBASE_STORAGE_BUCKET,
    PUBLIC_HOSTING_ORIGIN: PUBLIC_HOSTING_ORIGIN,
    WEB_APP_CHECK_CONFIG: WEB_APP_CHECK_CONFIG,
    isLocalhostHostname: isLocalhostHostname,
    isValidJwtLikeToken: isValidJwtLikeToken,
    initWebAppCheck: initWebAppCheck,
    getWebAppCheckToken: getWebAppCheckToken,
    buildFirestoreRequestHeaders: buildFirestoreRequestHeaders,
    parseGroundedVetSources: parseGroundedVetSources,
    requestServerSideVetAdvice: requestServerSideVetAdvice,
    normalizeAnimalCode: normalizeAnimalCode,
    extractCattleIdFromPath: extractCattleIdFromPath,
    extractAnimalCodeFromLocation: extractAnimalCodeFromLocation,
    resolveRoute: resolveRoute,
    buildFirestoreDocumentUrl: buildFirestoreDocumentUrl,
    buildCattleDocumentUrl: buildCattleDocumentUrl,
    buildCattleCollectionQueryUrl: buildCattleCollectionQueryUrl,
    buildCattleQueryPayload: buildCattleQueryPayload,
    buildFirebaseStorageMediaUrl: buildFirebaseStorageMediaUrl,
    parsePublicCattleDocument: parsePublicCattleDocument,
    fetchCattleRecordFromFirestore: fetchCattleRecordFromFirestore,
    buildWhatsAppShareUrl: buildWhatsAppShareUrl,
    isSafeHttpOrDataImageUrl: isSafeHttpOrDataImageUrl
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = helpers;
    return;
  }

  // Browser UI Controller
  var state = {
    lang: 'hi',
    routeView: 'home', // 'home' | 'cattle' | 'invalid_qr' | 'not_found'
    loading: false,
    card: null,
    errorKind: null, // 'invalid' | 'not_found' | 'not_found_cattle' | 'private' | 'offline' | 'general'
    animalCode: null,
    cattleId: null,
    lookupError: false,
    usingPlaceholderPhoto: true,
    activePhotoSrc: '/assets/default-cow.jpg'
  };

  var activeRequestToken = 0;

  function el(id) {
    return document.getElementById(id);
  }

  function setText(id, text) {
    var node = el(id);
    if (node) node.textContent = text;
  }

  function formatNumber(val) {
    if (!isFinite(val)) return '0';
    return Number.isInteger(val) ? String(val) : val.toFixed(1).replace(/\.0$/, '');
  }

  function formatAgeDob(card, dict) {
    var age = card.ageText;
    var dob = card.birthDate;
    if (age && dob) return age + ' (' + dob + ')';
    if (age) return age;
    if (dob) return dob;
    return dict.notRecorded;
  }

  function formatWeight(weightKg, dict) {
    if (weightKg && weightKg > 0) {
      return formatNumber(weightKg) + ' ' + dict.unitKg;
    }
    return dict.notRecorded;
  }

  function applyHealthBadgeStyle(healthStatus) {
    var badge = el('healthBadge');
    if (!badge) return;
    badge.classList.remove('health-healthy', 'health-care', 'health-alert');
    if (healthStatus === 'HEALTHY') {
      badge.classList.add('health-healthy');
    } else if (healthStatus === 'SICK') {
      badge.classList.add('health-alert');
    } else {
      badge.classList.add('health-care');
    }
  }

  function updatePhotoOverlayBadges(dict) {
    var notice = el('photoPlaceholderNotice');
    var zoomHint = el('photoZoomHint');
    if (notice) {
      notice.textContent = dict.photoPlaceholderNotice;
      notice.classList.toggle('hidden', !state.usingPlaceholderPhoto);
    }
    if (zoomHint) {
      zoomHint.textContent = dict.photoZoomHint;
      zoomHint.classList.toggle('hidden', state.usingPlaceholderPhoto);
    }
  }

  function configureCowPhoto(card) {
    var img = el('cowPhoto');
    if (!img) return;
    var candidates = [];
    if (card.photoUrl) candidates.push({ src: card.photoUrl, isPlaceholder: false });
    if (card.photoDataUrl && card.photoDataUrl !== card.photoUrl) {
      candidates.push({ src: card.photoDataUrl, isPlaceholder: false });
    }
    candidates.push({ src: '/assets/default-cow.jpg', isPlaceholder: true });

    var idx = 0;
    function applyCurrent() {
      var item = candidates[idx];
      state.usingPlaceholderPhoto = item.isPlaceholder;
      state.activePhotoSrc = item.src;
      updatePhotoOverlayBadges(I18N[state.lang] || I18N.hi);
      img.src = item.src;
    }

    img.onerror = function () {
      idx += 1;
      if (idx < candidates.length) {
        applyCurrent();
      } else {
        img.onerror = null;
      }
    };
    img.alt = card.name + ' (' + card.animalCode + ')';
    applyCurrent();
  }

  function openPhotoLightbox() {
    if (!state.card || state.usingPlaceholderPhoto) return;
    var modal = el('photoLightbox');
    var modalImg = el('lightboxImg');
    var caption = el('lightboxCaption');
    if (!modal || !modalImg) return;
    modalImg.src = state.activePhotoSrc;
    modalImg.alt = state.card.name + ' (' + state.card.animalCode + ')';
    if (caption) {
      caption.textContent = state.card.name + ' • ' + state.card.animalCode;
    }
    modal.classList.remove('hidden');
  }

  function closePhotoLightbox() {
    var modal = el('photoLightbox');
    if (modal) modal.classList.add('hidden');
  }

  function renderHealthHistory(card, dict) {
    var listEl = el('healthHistoryList');
    var emptyEl = el('healthHistoryEmpty');
    if (!listEl || !emptyEl) return;

    while (listEl.firstChild) {
      listEl.removeChild(listEl.firstChild);
    }

    var history = Array.isArray(card.healthHistory) ? card.healthHistory : [];
    if (history.length === 0) {
      emptyEl.textContent = dict.healthHistoryEmpty;
      emptyEl.classList.remove('hidden');
      return;
    }

    emptyEl.classList.add('hidden');
    for (var i = 0; i < history.length; i++) {
      var rec = history[i];
      var itemEl = document.createElement('article');
      itemEl.className = 'timeline-item';

      var topRow = document.createElement('div');
      topRow.className = 'timeline-item-top';

      var typeBadge = document.createElement('span');
      var typeClass = 'type-checkup';
      if (rec.recordType === 'VACCINATION') typeClass = 'type-vaccine';
      else if (rec.recordType === 'TREATMENT') typeClass = 'type-treatment';
      else if (rec.recordType === 'WEIGHT_CHECK') typeClass = 'type-weight';
      typeBadge.className = 'record-type-badge ' + typeClass;
      typeBadge.textContent = dict.recordTypeMap[rec.recordType] || rec.recordType;
      topRow.appendChild(typeBadge);

      if (rec.dateStr) {
        var dateSpan = document.createElement('span');
        dateSpan.className = 'timeline-date';
        dateSpan.textContent = rec.dateStr;
        topRow.appendChild(dateSpan);
      }
      itemEl.appendChild(topRow);

      if (rec.title) {
        var titleEl = document.createElement('h4');
        titleEl.className = 'timeline-title';
        titleEl.textContent = rec.title;
        itemEl.appendChild(titleEl);
      }

      var metaParts = [];
      if (rec.medicine) metaParts.push(dict.lblMedicine + ': ' + rec.medicine);
      if (rec.vetName) metaParts.push(dict.lblVetName + ': ' + rec.vetName);
      if (rec.weightKg > 0) metaParts.push(dict.lblWeightRecorded + ': ' + formatNumber(rec.weightKg) + ' ' + dict.unitKg);
      if (rec.nextFollowUpDate) metaParts.push(dict.lblFollowUp + ': ' + rec.nextFollowUpDate);

      if (metaParts.length > 0) {
        var metaWrap = document.createElement('div');
        metaWrap.className = 'timeline-meta';
        for (var m = 0; m < metaParts.length; m++) {
          var chip = document.createElement('span');
          chip.className = 'timeline-meta-chip';
          chip.textContent = metaParts[m];
          metaWrap.appendChild(chip);
        }
        itemEl.appendChild(metaWrap);
      }

      listEl.appendChild(itemEl);
    }
  }

  function renderFeedSummary(card, dict) {
    var dateEl = el('valFeedDate');
    var gridEl = el('feedMetricsGrid');
    var emptyEl = el('feedEmptyMsg');
    if (!gridEl || !emptyEl) return;

    setText('secFeed', dict.secFeed);
    setText('lblGreenFodder', dict.lblGreenFodder);
    setText('lblDryFodder', dict.lblDryFodder);
    setText('lblConcentrate', dict.lblConcentrate);
    setText('lblWaterLiters', dict.lblWaterLiters);
    setText('lblShedCleaned', dict.lblShedCleaned);

    var feed = card.feedSummary;
    if (!feed) {
      gridEl.classList.add('hidden');
      if (dateEl) dateEl.classList.add('hidden');
      emptyEl.textContent = dict.feedEmptyMsg;
      emptyEl.classList.remove('hidden');
      return;
    }

    emptyEl.classList.add('hidden');
    gridEl.classList.remove('hidden');
    if (dateEl) {
      if (feed.logDate) {
        dateEl.textContent = dict.feedDatePrefix + feed.logDate;
        dateEl.classList.remove('hidden');
      } else {
        dateEl.classList.add('hidden');
      }
    }

    setText('valGreenFodder', formatNumber(feed.greenFodderKg) + ' ' + dict.unitKg);
    setText('valDryFodder', formatNumber(feed.dryFodderBhusaKg) + ' ' + dict.unitKg);
    setText('valConcentrate', formatNumber(feed.concentrateKg) + ' ' + dict.unitKg);
    setText('valWaterLiters', formatNumber(feed.waterLiters) + ' ' + dict.unitLiters);
    setText('valShedCleaned', feed.shedCleaned ? dict.shedCleanedYes : dict.shedCleanedNo);
  }

  function renderQr(card) {
    var canvas = el('qrCanvas');
    if (!canvas || !root.JBGSQrCode) return;
    try {
      root.JBGSQrCode.renderToCanvas(canvas, card.qrUrl, {
        size: 160,
        quietZone: 3,
        darkColor: '#124B32',
        lightColor: '#FFFFFF'
      });
    } catch (_) {
      // Keep canvas clean if rendering fails
    }
  }

  function renderHomeContent(dict) {
    document.title = dict.homePageTitle;

    setText('homePortalTag', dict.homePortalTag);
    setText('homeTitle', dict.homeTitle);
    setText('homeSloganPrimary', dict.homeSloganPrimary);
    setText('homeSloganSecondary', dict.homeSloganSecondary);
    setText('homePillarText', dict.homePillarText);
    setText('homeLocationBadge', dict.homeLocationBadge);
    setText('homeLookupKicker', dict.homeLookupKicker);
    setText('homeLookupHeading', dict.homeLookupHeading);
    setText('homeLookupDesc', dict.homeLookupDesc);
    setText('homeLookupInputLabel', dict.homeLookupInputLabel);
    setText('lookupSubmitText', dict.lookupSubmitText);
    setText('homePhotoTitle', dict.homePhotoTitle);
    setText('homePhotoTag', dict.homePhotoTag);
    setText('featCard1Title', dict.featCard1Title);
    setText('featCard1Desc', dict.featCard1Desc);
    setText('featCard2Title', dict.featCard2Title);
    setText('featCard2Desc', dict.featCard2Desc);
    setText('featCard3Title', dict.featCard3Title);
    setText('featCard3Desc', dict.featCard3Desc);
    setText('guideKicker', dict.guideKicker);
    setText('guideHeading', dict.guideHeading);
    setText('step1Title', dict.step1Title);
    setText('step1Desc', dict.step1Desc);
    setText('step2Title', dict.step2Title);
    setText('step2Desc', dict.step2Desc);
    setText('step3Title', dict.step3Title);
    setText('step3Desc', dict.step3Desc);
    setText('homeFooterOrg', dict.homeFooterOrg);
    setText('homeFooterAddress', dict.homeFooterAddress);
    setText('homeFooterTag', dict.homeFooterTag);

    var input = el('animalCodeInput');
    if (input) input.placeholder = dict.homeLookupPlaceholder;

    var lookupErr = el('lookupErrorText');
    if (lookupErr) {
      lookupErr.textContent = state.lookupError ? dict.lookupInvalidMsg : '';
      lookupErr.classList.toggle('hidden', !state.lookupError);
    }
  }

  function hideAllViews() {
    el('homeDashboard').classList.add('hidden');
    el('loadingState').classList.add('hidden');
    el('errorState').classList.add('hidden');
    el('cowCard').classList.add('hidden');
    el('actionBar').classList.add('hidden');
    el('printNote').classList.add('hidden');
    closePhotoLightbox();
  }

  function renderUI() {
    var dict = I18N[state.lang] || I18N.hi;
    document.documentElement.lang = state.lang;

    el('langHiBtn').classList.toggle('active', state.lang === 'hi');
    el('langEnBtn').classList.toggle('active', state.lang === 'en');

    setText('toolbarBrand', dict.toolbarBrand);
    setText('backHomeNavText', dict.backHomeNav);
    setText('offlineBannerText', dict.offlineBanner);
    setText('offlineRetryBtn', dict.btnRetry);
    setText('loadingTitle', dict.loadingTitle);
    setText('loadingSubtitle', dict.loadingSubtitle);
    setText('retryBtn', dict.btnRetry);
    setText('errorHomeBtn', dict.btnGoHome);

    el('backHomeNavBtn').classList.toggle('hidden', state.routeView === 'home');

    hideAllViews();

    // 1. Home Dashboard Route (/)
    if (state.routeView === 'home') {
      renderHomeContent(dict);
      el('homeDashboard').classList.remove('hidden');
      return;
    }

    // 2. Loading State for valid /cattle/{id}
    if (state.loading) {
      el('loadingState').classList.remove('hidden');
      return;
    }

    // 3. Error / Invalid QR / Not Found State
    if (state.errorKind) {
      el('errorState').classList.remove('hidden');
      var showRetry =
        state.errorKind === 'offline' ||
        state.errorKind === 'general' ||
        state.errorKind === 'private' ||
        state.errorKind === 'not_found_cattle';
      el('retryBtn').classList.toggle('hidden', !showRetry);

      var codeLabel = state.animalCode || state.cattleId || '--';
      if (state.errorKind === 'invalid') {
        document.title = dict.errInvalidTitle + ' — ' + dict.toolbarBrand;
        setText('errorIcon', '?');
        setText('errorTitle', dict.errInvalidTitle);
        setText('errorMessage', dict.errInvalidBody);
      } else if (state.errorKind === 'not_found_cattle') {
        document.title = dict.errCattleNotFoundTitle + ' — ' + dict.toolbarBrand;
        setText('errorIcon', '404');
        setText('errorTitle', dict.errCattleNotFoundTitle);
        setText('errorMessage', dict.errCattleNotFoundBody.replace('{code}', codeLabel));
      } else if (state.errorKind === 'not_found') {
        document.title = dict.errNotFoundTitle + ' — ' + dict.toolbarBrand;
        setText('errorIcon', '404');
        setText('errorTitle', dict.errNotFoundTitle);
        setText('errorMessage', dict.errNotFoundBody);
      } else if (state.errorKind === 'private') {
        document.title = dict.errPrivateTitle + ' — ' + dict.toolbarBrand;
        setText('errorIcon', '🔒');
        setText('errorTitle', dict.errPrivateTitle);
        setText('errorMessage', dict.errPrivateBody.replace('{code}', codeLabel));
      } else if (state.errorKind === 'offline') {
        document.title = dict.errOfflineTitle + ' — ' + dict.toolbarBrand;
        setText('errorIcon', '⚡');
        setText('errorTitle', dict.errOfflineTitle);
        setText('errorMessage', dict.errOfflineBody);
      } else {
        document.title = dict.errGeneralTitle + ' — ' + dict.toolbarBrand;
        setText('errorIcon', '!');
        setText('errorTitle', dict.errGeneralTitle);
        setText('errorMessage', dict.errGeneralBody);
      }
      return;
    }

    // 4. Verified Full Cattle Profile (/cattle/{validCattleId})
    if (!state.card) return;

    var card = state.card;
    var isEn = state.lang === 'en';
    var goshalaName = isEn ? card.goshalaNameEn : card.goshalaNameHi;
    var goshalaAddr = isEn ? card.goshalaAddressEn : card.goshalaAddressHi;
    var categoryText = dict.categoryMap[card.category] || card.category;
    var sexText = dict.sexMap[card.sex] || card.sex;
    var healthText = dict.healthMap[card.healthStatus] || card.healthStatus;

    document.title = card.name + ' (' + card.animalCode + ') — ' + dict.pageTitlePrefix;

    el('cowCard').classList.remove('hidden');
    el('actionBar').classList.remove('hidden');
    el('printNote').classList.remove('hidden');

    setText('goshalaName', goshalaName);
    setText('cardTitleSub', dict.cardTitleSub);
    setText('goshalaAddressHeader', goshalaAddr);
    setText('chipKicker', dict.chipKicker);
    setText('headerAnimalCode', card.animalCode);

    setText('verifiedText', dict.verifiedText);
    setText('healthBadge', healthText);
    applyHealthBadgeStyle(card.healthStatus);

    var sponsorPill = el('sponsorRibbonBadge');
    if (sponsorPill) {
      if (card.sponsorshipAvailable) {
        sponsorPill.textContent = dict.sponsorBadgeText;
        sponsorPill.classList.remove('hidden');
      } else {
        sponsorPill.classList.add('hidden');
      }
    }

    // Top Photo Hero Overlay
    setText('photoCowName', card.name);
    setText('photoCowCode', card.animalCode);
    setText('photoCowType', categoryText);
    var breedChip = el('photoCowBreed');
    if (breedChip) {
      if (card.breed) {
        breedChip.textContent = card.breed;
        breedChip.classList.remove('hidden');
      } else {
        breedChip.classList.add('hidden');
      }
    }
    updatePhotoOverlayBadges(dict);

    // Core Identity Grid
    setText('cowName', card.name);
    setText('cowCodePill', card.animalCode);
    setText('secIdentity', dict.secIdentity);
    setText('lblAnimalCode', dict.lblAnimalCode);
    setText('valAnimalCode', card.animalCode);
    setText('lblCategory', dict.lblCategory);
    setText('valCategory', categoryText);
    setText('lblBreed', dict.lblBreed);
    setText('valBreed', card.breed || dict.notRecorded);
    setText('lblAgeDob', dict.lblAgeDob);
    setText('valAgeDob', formatAgeDob(card, dict));
    setText('lblSex', dict.lblSex);
    setText('valSex', sexText);
    setText('lblColour', dict.lblColour);
    setText('valColour', card.colour || dict.notRecorded);
    setText('lblWeight', dict.lblWeight);
    setText('valWeight', formatWeight(card.weightKg, dict));
    setText('lblEntryDate', dict.lblEntryDate);
    setText('valEntryDate', card.entryDate || dict.notRecorded);
    setText('lblShedNumber', dict.lblShedNumber);
    setText('valShedNumber', card.shedNumber || dict.notRecorded);

    // Health, Vaccination & Treatment Timeline
    setText('secHealth', dict.secHealth);
    setText('lblHealthStatus', dict.lblHealthStatus);
    setText('valHealthStatus', healthText);
    setText('lblLastCheckup', dict.lblLastCheckup);
    setText('valLastCheckup', card.lastHealthCheckup || dict.notRecorded);
    setText('lblVaccination', dict.lblVaccination);
    setText('valVaccination', card.vaccinationSummary || dict.notRecorded);
    setText('lblHealthTimeline', dict.lblHealthTimeline);
    renderHealthHistory(card, dict);

    // Fodder & Daily Care
    renderFeedSummary(card, dict);

    // Identification Marks, Sponsorship, Goshala Info & QR Code
    setText('secCare', dict.secCare);
    setText('lblIdentMarks', dict.lblIdentMarks);
    setText('valIdentMarks', card.identificationMarks || dict.notRecorded);
    setText('lblSponsorship', dict.lblSponsorship);
    var sponsorshipText = dict.sponsorNotAvailable;
    if (card.sponsorshipAvailable) {
      sponsorshipText = card.monthlySupportInr > 0
        ? dict.sponsorMonthlyFormat.replace('{amount}', String(card.monthlySupportInr))
        : dict.sponsorAvailableYes;
    }
    setText('valSponsorship', sponsorshipText);
    setText('lblGoshalaFull', dict.lblGoshalaFull);
    setText('valGoshalaFull', goshalaName + ', ' + goshalaAddr);

    setText('qrLabel', dict.qrLabel);
    setText('qrAnimalCode', card.animalCode);
    setText('qrHint', dict.qrHint);

    setText('footerVerified', dict.footerVerified);
    setText('footerUrl', card.qrUrl);

    el('printBtn').querySelector('span').textContent = dict.btnPrint;
    el('pdfBtn').querySelector('span').textContent = dict.btnPdf;
    var waBtn = el('whatsappBtn');
    waBtn.querySelector('span').textContent = dict.btnWhatsapp;
    waBtn.href = buildWhatsAppShareUrl(card, state.lang);
    setText('printNote', dict.printNote);
  }

  function updateOfflineBanner() {
    var offline = typeof navigator !== 'undefined' && navigator.onLine === false;
    el('offlineBanner').classList.toggle('hidden', !offline);
  }

  function navigateTo(path) {
    if (window.location.pathname + window.location.search !== path) {
      window.history.pushState({}, '', path);
    }
    handleRouteChange();
  }

  async function fetchCattleCard(cattleId, animalCode) {
    var reqToken = ++activeRequestToken;
    state.loading = true;
    state.errorKind = null;
    state.card = null;
    state.cattleId = cattleId;
    state.animalCode = animalCode || normalizeAnimalCode(cattleId);
    renderUI();

    if (typeof navigator !== 'undefined' && navigator.onLine === false) {
      if (reqToken !== activeRequestToken) return;
      state.loading = false;
      state.errorKind = 'offline';
      renderUI();
      return;
    }

    try {
      var result = await fetchCattleRecordFromFirestore(cattleId);
      if (reqToken !== activeRequestToken) return;

      if (result.status !== 'ok' || !result.card) {
        state.loading = false;
        state.errorKind = result.status || 'general';
        renderUI();
        return;
      }

      state.loading = false;
      state.card = result.card;
      state.animalCode = result.card.animalCode;
      configureCowPhoto(result.card);
      renderQr(result.card);
      renderUI();
    } catch (_) {
      if (reqToken !== activeRequestToken) return;
      state.loading = false;
      state.errorKind = (typeof navigator !== 'undefined' && navigator.onLine === false) ? 'offline' : 'general';
      renderUI();
    }
  }

  function handleRouteChange() {
    updateOfflineBanner();
    activeRequestToken += 1;
    state.lookupError = false;

    var resolved = resolveRoute(window.location.pathname, window.location.search);
    state.routeView = resolved.view;
    state.animalCode = resolved.animalCode;
    state.cattleId = resolved.cattleId;
    state.card = null;
    state.loading = false;

    if (resolved.view === 'home') {
      state.errorKind = null;
      renderUI();
      return;
    }

    if (resolved.view === 'invalid_qr') {
      state.errorKind = 'invalid';
      renderUI();
      return;
    }

    if (resolved.view === 'not_found') {
      state.errorKind = 'not_found';
      renderUI();
      return;
    }

    // resolved.view === 'cattle' with validated resolved.cattleId
    fetchCattleCard(resolved.cattleId, resolved.animalCode);
  }

  function bindEvents() {
    el('langHiBtn').addEventListener('click', function () {
      state.lang = 'hi';
      renderUI();
    });
    el('langEnBtn').addEventListener('click', function () {
      state.lang = 'en';
      renderUI();
    });

    el('brandHomeLink').addEventListener('click', function (evt) {
      evt.preventDefault();
      navigateTo('/');
    });
    el('backHomeNavBtn').addEventListener('click', function () {
      navigateTo('/');
    });
    el('errorHomeBtn').addEventListener('click', function () {
      navigateTo('/');
    });

    el('cattleLookupForm').addEventListener('submit', function (evt) {
      evt.preventDefault();
      var input = el('animalCodeInput');
      var rawInput = input ? input.value.trim() : '';
      var normalized = normalizeAnimalCode(rawInput);
      var targetId = normalized || (CATTLE_ID_REGEX.test(rawInput) ? rawInput : null);
      if (!targetId) {
        state.lookupError = true;
        renderUI();
        if (input) input.focus();
        return;
      }
      state.lookupError = false;
      input.value = targetId;
      navigateTo('/cattle/' + encodeURIComponent(targetId));
    });

    el('animalCodeInput').addEventListener('input', function () {
      if (state.lookupError) {
        state.lookupError = false;
        renderUI();
      }
    });

    el('retryBtn').addEventListener('click', function () {
      if (state.routeView === 'cattle' && (state.cattleId || state.animalCode)) {
        fetchCattleCard(state.cattleId || state.animalCode, state.animalCode);
      } else {
        handleRouteChange();
      }
    });
    el('offlineRetryBtn').addEventListener('click', function () {
      if (state.routeView === 'cattle' && (state.cattleId || state.animalCode)) {
        fetchCattleCard(state.cattleId || state.animalCode, state.animalCode);
      } else {
        updateOfflineBanner();
      }
    });

    var photoFrame = el('cowPhotoFrame');
    if (photoFrame) {
      photoFrame.addEventListener('click', openPhotoLightbox);
      photoFrame.addEventListener('keydown', function (evt) {
        if (evt.key === 'Enter' || evt.key === ' ') {
          evt.preventDefault();
          openPhotoLightbox();
        }
      });
    }

    var lightboxCloseBtn = el('lightboxCloseBtn');
    if (lightboxCloseBtn) {
      lightboxCloseBtn.addEventListener('click', closePhotoLightbox);
    }
    var lightbox = el('photoLightbox');
    if (lightbox) {
      lightbox.addEventListener('click', function (evt) {
        if (evt.target === lightbox) closePhotoLightbox();
      });
    }
    window.addEventListener('keydown', function (evt) {
      if (evt.key === 'Escape') closePhotoLightbox();
    });

    el('printBtn').addEventListener('click', function () {
      window.print();
    });
    el('pdfBtn').addEventListener('click', function () {
      window.print();
    });

    window.addEventListener('popstate', handleRouteChange);
    window.addEventListener('online', function () {
      updateOfflineBanner();
      if (state.routeView === 'cattle' && state.errorKind === 'offline' && (state.cattleId || state.animalCode)) {
        fetchCattleCard(state.cattleId || state.animalCode, state.animalCode);
      }
    });
    window.addEventListener('offline', updateOfflineBanner);
  }

  if (typeof document !== 'undefined') {
    document.addEventListener('DOMContentLoaded', function () {
      bindEvents();
      handleRouteChange();
    });
  }
})(typeof window !== 'undefined' ? window : globalThis);
