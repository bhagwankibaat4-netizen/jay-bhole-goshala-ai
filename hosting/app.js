/**
 * Jay Bhole Gosala Samiti — Official Web Portal & Public Digital Cow ID Card
 * Routes cleanly:
 *  - `/` (and `/index.html`)             -> Home Dashboard (with Cow ID lookup & Goshala overview)
 *  - `/cattle/<validAnimalCode>`         -> Verified Public Digital Cow ID Card from Firestore
 *  - `/cattle/...` (missing/invalid ID)  -> Dedicated Invalid QR Code screen (with Home link)
 *  - Any other unknown path              -> Clean Page Not Found screen (with Home link)
 */
(function (root) {
  'use strict';

  var FIREBASE_PROJECT_ID = 'gen-lang-client-0821187064';
  var FIRESTORE_DATABASE_ID = 'ai-studio-android-5ee9bbf0-cdf7-4202-8155-b40c385da917';
  var PUBLIC_HOSTING_ORIGIN = 'https://gen-lang-client-0821187064.web.app';
  var ANIMAL_CODE_REGEX = /^[A-Z0-9][A-Z0-9_-]{1,39}$/;

  var I18N = {
    hi: {
      homePageTitle: 'जय भोले गौशाला समिति | आधिकारिक डिजिटल पोर्टल',
      pageTitlePrefix: 'डिजिटल गौ पहचान पत्र',
      toolbarBrand: 'जय भोले गौशाला समिति',
      backHomeNav: '← होम डैशबोर्ड',
      btnGoHome: 'होम डैशबोर्ड पर जाएं',
      homePortalTag: 'आधिकारिक डिजिटल गौशाला पोर्टल',
      homeTitle: 'जय भोले गौशाला समिति',
      homeSloganPrimary: 'महादेव की कृपा, गौमाता की सेवा।',
      homeSloganSecondary: 'बेसहारा गौवंश का सहारा बनना ही हमारा संकल्प है।',
      homePillarText: 'गौ सेवा • जीव रक्षा • समाज सेवा',
      homeLocationBadge: 'मेहदोरा, पोरसा, मुरैना (म. प्र.)',
      homeLookupKicker: 'डिजिटल गौ पहचान पत्र खोजें',
      homeLookupHeading: 'पशु पहचान क्रमांक (Animal ID) से कार्ड खोलें',
      homeLookupDesc: 'यदि आपके पास गौशाला द्वारा जारी पशु पहचान क्रमांक है (जैसे JBGS-G-357), तो नीचे दर्ज करके सीधे सत्यापित PVC डिजिटल गौ पहचान पत्र देखें।',
      homeLookupInputLabel: 'पशु पहचान क्रमांक',
      homeLookupPlaceholder: 'उदाहरण: JBGS-G-357',
      lookupSubmitText: 'पहचान पत्र देखें',
      lookupInvalidMsg: 'कृपया वैध पशु पहचान क्रमांक दर्ज करें (जैसे JBGS-G-357 — केवल अक्षर, अंक, - या _)।',
      homePhotoTitle: 'गौमाता की सेवा एवं संरक्षण',
      homePhotoTag: 'QR सत्यापित',
      featCard1Title: 'डिजिटल गौ पहचान प्रणाली',
      featCard1Desc: 'प्रत्येक पंजीकृत गौवंश के लिए सुरक्षित QR-सत्यापित PVC डिजिटल पहचान पत्र, जिसमें नस्ल, आयु, स्वास्थ्य स्थिति और टीकाकरण विवरण उपलब्ध रहता है।',
      featCard2Title: 'गौ सेवा, आश्रय एवं उपचार',
      featCard2Desc: 'बेसहारा और जरूरतमंद गौवंश के लिए सुरक्षित आश्रय, स्वच्छ चारा-पानी, नियमित पशु चिकित्सा जांच एवं संवेदनशील देखभाल की व्यवस्था।',
      featCard3Title: 'पारदर्शी और सुरक्षित रिकॉर्ड',
      featCard3Desc: 'केवल एडमिन द्वारा स्वीकृत सार्वजनिक विवरण ही ऑनलाइन प्रदर्शित होते हैं, जबकि गौशाला का निजी व आंतरिक डेटा पूर्णतः सुरक्षित रहता है।',
      guideKicker: 'QR सत्यापन प्रक्रिया',
      guideHeading: 'Google Lens या QR स्कैनर से पहचान पत्र कैसे देखें?',
      step1Title: 'आधिकारिक QR स्कैन करें',
      step1Desc: 'गौवंश के टैग या कार्ड पर छपे QR कोड को Google Lens या किसी भी मोबाइल कैमरे से स्कैन करें।',
      step2Title: 'सत्यापित कार्ड तुरंत खुलेगा',
      step2Desc: '/cattle/Animal-ID लिंक पर जाते ही गौमाता का फोटो, पहचान विवरण और स्वास्थ्य रिकॉर्ड दिखाई देगा।',
      step3Title: 'प्रिंट, PDF या शेयर करें',
      step3Desc: 'पहचान पत्र को सीधे प्रिंट करें, PDF के रूप में सहेजें या WhatsApp पर साझा करें।',
      homeFooterOrg: 'जय भोले गौशाला समिति',
      homeFooterAddress: 'ग्राम – मेहदोरा, विकासखंड – पोरसा, जिला – मुरैना, मध्य प्रदेश, भारत।',
      homeFooterTag: 'सुरक्षित डिजिटल गौवंश पहचान एवं प्रबंधन पोर्टल',
      cardTitleSub: 'आधिकारिक डिजिटल गौ पहचान पत्र • Official Digital Cow ID Card',
      chipKicker: 'पशु पहचान क्रमांक',
      verifiedText: 'सत्यापित सार्वजनिक गौ रिकॉर्ड (एडमिन स्वीकृत)',
      qrLabel: 'सत्यापन QR कोड',
      qrHint: 'Google Lens या किसी भी QR स्कैनर से सीधे यह पहचान पत्र खोलें।',
      secIdentity: 'गौवंश पहचान विवरण',
      lblCategory: 'पशु प्रकार',
      lblBreed: 'नस्ल',
      lblAgeDob: 'आयु / जन्म तिथि',
      lblColour: 'रंग',
      lblSex: 'लिंग',
      lblEntryDate: 'पंजीकरण तिथि',
      lblGoshalaFull: 'गौशाला नाम एवं पता',
      secHealth: 'स्वास्थ्य एवं टीकाकरण रिकॉर्ड',
      lblHealthStatus: 'वर्तमान स्वास्थ्य स्थिति',
      lblLastCheckup: 'अंतिम स्वास्थ्य जांच',
      lblVaccination: 'टीकाकरण विवरण',
      secCare: 'विशेष पहचान एवं देखभाल',
      lblIdentMarks: 'पहचान चिन्ह / विशेष देखभाल जानकारी',
      footerVerified: 'जय भोले गौशाला समिति — सुरक्षित डिजिटल गौवंश पहचान प्रणाली',
      btnPrint: 'प्रिंट करें',
      btnPdf: 'PDF डाउनलोड करें',
      btnWhatsapp: 'WhatsApp पर शेयर करें',
      btnRetry: 'पुनः प्रयास करें',
      printNote: 'PDF सहेजने के लिए प्रिंट डायलॉग में “Save as PDF” चुनें।',
      offlineBanner: 'इंटरनेट कनेक्शन उपलब्ध नहीं है। कृपया नेटवर्क जाँचकर पुनः प्रयास करें।',
      loadingTitle: 'डिजिटल गौ पहचान पत्र लोड हो रहा है...',
      loadingSubtitle: 'जय भोले गौशाला समिति के आधिकारिक रिकॉर्ड से विवरण प्राप्त किया जा रहा है।',
      notRecorded: 'रिकॉर्ड उपलब्ध नहीं',
      errInvalidTitle: 'अमान्य QR कोड लिंक',
      errInvalidBody: 'यह लिंक किसी वैध गौ पहचान क्रमांक (जैसे /cattle/JBGS-G-357) की ओर संकेत नहीं करता। कृपया गौशाला के आधिकारिक QR कोड को पुनः स्कैन करें या होम डैशबोर्ड पर जाएं।',
      errNotFoundTitle: 'पेज उपलब्ध नहीं है',
      errNotFoundBody: 'आप जिस वेब पते को खोलने का प्रयास कर रहे हैं वह उपलब्ध नहीं है। कृपया जय भोले गौशाला समिति के मुख्य होम डैशबोर्ड पर जाएं।',
      errPrivateTitle: 'पहचान पत्र सार्वजनिक रूप से उपलब्ध नहीं है',
      errPrivateBody: 'यह गौ रिकॉर्ड अभी एडमिन द्वारा सार्वजनिक प्रदर्शन के लिए स्वीकृत नहीं है या डेटाबेस में मौजूद नहीं है।',
      errOfflineTitle: 'इंटरनेट कनेक्शन नहीं है',
      errOfflineBody: 'गौ पहचान पत्र देखने के लिए इंटरनेट कनेक्शन आवश्यक है। नेटवर्क चालू करके पुनः प्रयास करें।',
      errGeneralTitle: 'डेटा लोड करने में समस्या',
      errGeneralBody: 'सर्वर से गौ विवरण प्राप्त नहीं हो सका। कृपया कुछ समय बाद पुनः प्रयास करें।',
      categoryMap: { COW: 'गाय (Cow)', BULL: 'नंदी / बैल (Bull)', CALF: 'बछड़ा / बछिया (Calf)' },
      sexMap: { FEMALE: 'मादा (Female)', MALE: 'नर (Male)' },
      healthMap: {
        HEALTHY: 'स्वस्थ (Healthy)',
        SICK: 'अस्वस्थ (Needs Care)',
        UNDER_TREATMENT: 'उपचाराधीन (Under Treatment)',
        RECOVERING: 'सुधार पर (Recovering)'
      }
    },
    en: {
      homePageTitle: 'Jay Bhole Gosala Samiti | Official Digital Portal',
      pageTitlePrefix: 'Digital Cow ID Card',
      toolbarBrand: 'Jay Bhole Gosala Samiti',
      backHomeNav: '← Home Dashboard',
      btnGoHome: 'Go to Home Dashboard',
      homePortalTag: 'Official Digital Goshala Portal',
      homeTitle: 'Jay Bhole Gosala Samiti',
      homeSloganPrimary: 'Grace of Mahadev, Service to Gaumata.',
      homeSloganSecondary: 'Supporting destitute cattle is our solemn pledge.',
      homePillarText: 'Cow Care • Animal Protection • Social Service',
      homeLocationBadge: 'Mehdora, Porsa, Morena (M.P.)',
      homeLookupKicker: 'Digital Cow ID Lookup',
      homeLookupHeading: 'Open Verified Card by Animal ID',
      homeLookupDesc: 'If you have an official Animal ID issued by the Goshala (for example JBGS-G-357), enter it below to open the verified PVC Digital Cow ID Card.',
      homeLookupInputLabel: 'Animal ID',
      homeLookupPlaceholder: 'Example: JBGS-G-357',
      lookupSubmitText: 'View ID Card',
      lookupInvalidMsg: 'Please enter a valid Animal ID (for example JBGS-G-357 — letters, numbers, hyphens, or underscores only).',
      homePhotoTitle: 'Cattle Care & Protection',
      homePhotoTag: 'QR Verified',
      featCard1Title: 'Digital Cattle Identity System',
      featCard1Desc: 'Every registered animal receives a QR-verified PVC Digital ID Card displaying breed, age, health status, and vaccination details.',
      featCard2Title: 'Shelter, Care & Veterinary Support',
      featCard2Desc: 'Safe shelter, clean fodder and water, routine veterinary checkups, and compassionate care for rescued and destitute cattle.',
      featCard3Title: 'Transparent & Secure Records',
      featCard3Desc: 'Only Admin-approved public records appear online, while internal Goshala management and private staff data remain fully protected.',
      guideKicker: 'How QR Verification Works',
      guideHeading: 'How to Open a Digital Cow ID Card via Google Lens?',
      step1Title: 'Scan the Official QR Code',
      step1Desc: 'Scan the QR code printed on the animal tag or ID card using Google Lens or any mobile camera.',
      step2Title: 'Verified Card Opens Instantly',
      step2Desc: 'The /cattle/Animal-ID link displays the animal photo, identity details, and live health record.',
      step3Title: 'Print, Save PDF, or Share',
      step3Desc: 'Print the PVC-ready card directly, save it as a PDF, or share the verified link on WhatsApp.',
      homeFooterOrg: 'Jay Bhole Gosala Samiti',
      homeFooterAddress: 'Village – Mehdora, Block – Porsa, District – Morena, Madhya Pradesh, India.',
      homeFooterTag: 'Verified Digital Cattle Identity & Management Portal',
      cardTitleSub: 'Official Digital Cow ID Card • आधिकारिक डिजिटल गौ पहचान पत्र',
      chipKicker: 'Unique Animal ID',
      verifiedText: 'Verified Public Cow Record (Admin Approved)',
      qrLabel: 'Verification QR Code',
      qrHint: 'Scan with Google Lens or any QR scanner to open this official ID card.',
      secIdentity: 'Animal Identity Details',
      lblCategory: 'Animal Type',
      lblBreed: 'Breed',
      lblAgeDob: 'Age / Birth Date',
      lblColour: 'Colour',
      lblSex: 'Sex',
      lblEntryDate: 'Registration Date',
      lblGoshalaFull: 'Goshala Name & Address',
      secHealth: 'Health & Vaccination Record',
      lblHealthStatus: 'Current Health Status',
      lblLastCheckup: 'Last Health Checkup',
      lblVaccination: 'Vaccination Record',
      secCare: 'Identification Marks & Special Care',
      lblIdentMarks: 'Identification Marks & Care Notes',
      footerVerified: 'Jay Bhole Gosala Samiti — Verified Digital Cattle Identity System',
      btnPrint: 'Print Card',
      btnPdf: 'Download PDF',
      btnWhatsapp: 'Share on WhatsApp',
      btnRetry: 'Retry',
      printNote: 'Select "Save as PDF" in the print dialog to download this PVC Digital ID Card as a PDF.',
      offlineBanner: 'You appear to be offline. Please check your internet connection and retry.',
      loadingTitle: 'Loading Digital Cow ID Card...',
      loadingSubtitle: 'Fetching verified details from Jay Bhole Gosala Samiti records.',
      notRecorded: 'Not recorded',
      errInvalidTitle: 'Invalid QR Code URL',
      errInvalidBody: 'This link does not contain a valid Animal ID (for example /cattle/JBGS-G-357). Please scan an official Goshala QR code or return to the Home Dashboard.',
      errNotFoundTitle: 'Page Not Found',
      errNotFoundBody: 'The page you requested does not exist. Please return to the Jay Bhole Gosala Samiti Home Dashboard.',
      errPrivateTitle: 'ID Card Not Publicly Available',
      errPrivateBody: 'This cattle record is either not approved for public display by Admin or does not exist.',
      errOfflineTitle: 'No Internet Connection',
      errOfflineBody: 'An active internet connection is required to view this Digital Cow ID Card. Please reconnect and try again.',
      errGeneralTitle: 'Unable to Load Record',
      errGeneralBody: 'Could not fetch this Digital Cow ID Card right now. Please try again in a moment.',
      categoryMap: { COW: 'Cow', BULL: 'Bull', CALF: 'Calf' },
      sexMap: { FEMALE: 'Female', MALE: 'Male' },
      healthMap: {
        HEALTHY: 'Healthy',
        SICK: 'Sick / Needs Care',
        UNDER_TREATMENT: 'Under Treatment',
        RECOVERING: 'Recovering'
      }
    }
  };

  function normalizeAnimalCode(raw) {
    if (typeof raw !== 'string') return null;
    var cleaned = raw.trim().toUpperCase();
    return ANIMAL_CODE_REGEX.test(cleaned) ? cleaned : null;
  }

  /**
   * Extracts a validated uppercase Animal Code from pathname (/cattle/JBGS-G-357)
   * or query (?id=JBGS-G-357). Rejects malformed paths or suspicious characters.
   */
  function extractAnimalCodeFromLocation(pathname, search) {
    var path = String(pathname || '').trim();
    var match = path.match(/^\/cattle\/([^\/?#]+)\/?$/i);
    if (match && match[1]) {
      try {
        var decoded = decodeURIComponent(match[1]);
        return normalizeAnimalCode(decoded);
      } catch (_) {
        return null;
      }
    }

    if (search) {
      var params = new URLSearchParams(search);
      var candidate = params.get('id') || params.get('code');
      if (candidate) {
        return normalizeAnimalCode(candidate);
      }
    }
    return null;
  }

  /**
   * Strictly separates routes:
   * - '/' or '/index.html' (without id/code query params) -> { view: 'home', animalCode: null }
   * - '/cattle/<validCode>' or '/?id=<validCode>'         -> { view: 'cattle', animalCode: '<CODE>' }
   * - '/cattle', '/cattle/', '/cattle/<invalid>', '/?id=' -> { view: 'invalid_qr', animalCode: null }
   * - Any other path                                      -> { view: 'not_found', animalCode: null }
   */
  function resolveRoute(pathname, search) {
    var path = String(pathname || '/').trim() || '/';
    var params = search ? new URLSearchParams(search) : null;
    var hasQueryId = Boolean(params && (params.has('id') || params.has('code')));

    // 1. Explicit /cattle or /cattle/... QR route
    if (/^\/cattle(\/|$)/i.test(path)) {
      var codeFromPath = extractAnimalCodeFromLocation(path, '');
      if (codeFromPath) {
        return { view: 'cattle', animalCode: codeFromPath };
      }
      return { view: 'invalid_qr', animalCode: null };
    }

    // 2. Root Home Dashboard route ('/' or '/index.html')
    if (path === '/' || path.toLowerCase() === '/index.html') {
      if (hasQueryId) {
        var codeFromQuery = extractAnimalCodeFromLocation('/', search);
        if (codeFromQuery) {
          return { view: 'cattle', animalCode: codeFromQuery };
        }
        return { view: 'invalid_qr', animalCode: null };
      }
      return { view: 'home', animalCode: null };
    }

    // 3. Any other unknown route (separate from Home and Invalid QR)
    return { view: 'not_found', animalCode: null };
  }

  function buildFirestoreDocumentUrl(animalCode) {
    var code = normalizeAnimalCode(animalCode);
    if (!code) throw new Error('Invalid animal code');
    return (
      'https://firestore.googleapis.com/v1/projects/' +
      encodeURIComponent(FIREBASE_PROJECT_ID) +
      '/databases/' +
      encodeURIComponent(FIRESTORE_DATABASE_ID) +
      '/documents/publicCattleCards/' +
      encodeURIComponent(code)
    );
  }

  function readFirestoreValue(fieldObj) {
    if (!fieldObj || typeof fieldObj !== 'object') return null;
    if (typeof fieldObj.stringValue === 'string') return fieldObj.stringValue;
    if (typeof fieldObj.booleanValue === 'boolean') return fieldObj.booleanValue;
    if (typeof fieldObj.integerValue !== 'undefined') return Number(fieldObj.integerValue);
    if (typeof fieldObj.doubleValue !== 'undefined') return Number(fieldObj.doubleValue);
    if (typeof fieldObj.timestampValue === 'string') return fieldObj.timestampValue;
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

  /**
   * Parses Firestore REST document into a sanitized public card model.
   * Returns null if document is missing publicApproved === true or lacks required identity fields.
   */
  function parsePublicCattleDocument(docJson) {
    if (!docJson || typeof docJson !== 'object' || !docJson.fields) return null;
    var f = docJson.fields;
    var approved = readFirestoreValue(f.publicApproved);
    if (approved !== true) return null;

    var animalCode = normalizeAnimalCode(readFirestoreValue(f.animalCode) || '');
    var name = String(readFirestoreValue(f.name) || '').trim();
    if (!animalCode || !name) return null;

    var rawPhotoUrl = String(readFirestoreValue(f.photoUrl) || '').trim();
    var rawPhotoDataUrl = String(readFirestoreValue(f.photoDataUrl) || '').trim();

    return {
      animalCode: animalCode,
      name: name,
      category: String(readFirestoreValue(f.category) || 'COW').trim().toUpperCase(),
      breed: String(readFirestoreValue(f.breed) || '').trim(),
      sex: String(readFirestoreValue(f.sex) || 'FEMALE').trim().toUpperCase(),
      ageText: String(readFirestoreValue(f.ageText) || '').trim(),
      birthDate: String(readFirestoreValue(f.birthDate) || '').trim(),
      colour: String(readFirestoreValue(f.colour) || '').trim(),
      weightKg: Number(readFirestoreValue(f.weightKg) || 0),
      entryDate: String(readFirestoreValue(f.entryDate) || '').trim(),
      healthStatus: String(readFirestoreValue(f.healthStatus) || 'HEALTHY').trim().toUpperCase(),
      vaccinationSummary: String(readFirestoreValue(f.vaccinationSummary) || '').trim(),
      lastHealthCheckup: String(readFirestoreValue(f.lastHealthCheckup) || '').trim(),
      identificationMarks: String(readFirestoreValue(f.identificationMarks) || '').trim(),
      photoUrl: isSafeHttpOrDataImageUrl(rawPhotoUrl) ? rawPhotoUrl : '',
      photoDataUrl: isSafeHttpOrDataImageUrl(rawPhotoDataUrl) ? rawPhotoDataUrl : '',
      goshalaNameHi: String(readFirestoreValue(f.goshalaNameHi) || 'जय भोले गौशाला समिति').trim(),
      goshalaNameEn: String(readFirestoreValue(f.goshalaNameEn) || 'Jay Bhole Gosala Samiti').trim(),
      goshalaAddressHi: String(readFirestoreValue(f.goshalaAddressHi) || 'ग्राम – मेहदोरा, विकासखंड – पोरसा, जिला – मुरैना, मध्य प्रदेश, भारत।').trim(),
      goshalaAddressEn: String(readFirestoreValue(f.goshalaAddressEn) || 'Village – Mehdora, Block – Porsa, District – Morena, Madhya Pradesh, India.').trim(),
      qrUrl: PUBLIC_HOSTING_ORIGIN + '/cattle/' + encodeURIComponent(animalCode),
      publicApproved: true
    };
  }

  function buildWhatsAppShareUrl(card, lang) {
    var isEn = lang === 'en';
    var lines = isEn
      ? [
          card.goshalaNameEn + ' — Digital Cow ID Card',
          'Name: ' + card.name + ' (' + card.animalCode + ')',
          'Breed: ' + (card.breed || 'Not recorded') + ' | Health: ' + (I18N.en.healthMap[card.healthStatus] || card.healthStatus),
          'View ID Card: ' + card.qrUrl
        ]
      : [
          card.goshalaNameHi + ' — डिजिटल गौ पहचान पत्र',
          'नाम: ' + card.name + ' (' + card.animalCode + ')',
          'नस्ल: ' + (card.breed || 'उपलब्ध नहीं') + ' | स्वास्थ्य: ' + (I18N.hi.healthMap[card.healthStatus] || card.healthStatus),
          'डिजिटल कार्ड देखें: ' + card.qrUrl
        ];
    return 'https://wa.me/?text=' + encodeURIComponent(lines.join('\n'));
  }

  // Export helpers for Node unit tests
  var helpers = {
    FIREBASE_PROJECT_ID: FIREBASE_PROJECT_ID,
    FIRESTORE_DATABASE_ID: FIRESTORE_DATABASE_ID,
    PUBLIC_HOSTING_ORIGIN: PUBLIC_HOSTING_ORIGIN,
    normalizeAnimalCode: normalizeAnimalCode,
    extractAnimalCodeFromLocation: extractAnimalCodeFromLocation,
    resolveRoute: resolveRoute,
    buildFirestoreDocumentUrl: buildFirestoreDocumentUrl,
    parsePublicCattleDocument: parsePublicCattleDocument,
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
    errorKind: null, // 'invalid' | 'not_found' | 'private' | 'offline' | 'general'
    animalCode: null,
    lookupError: false
  };

  var activeRequestToken = 0;

  function el(id) {
    return document.getElementById(id);
  }

  function setText(id, text) {
    var node = el(id);
    if (node) node.textContent = text;
  }

  function formatAgeDob(card, dict) {
    var age = card.ageText;
    var dob = card.birthDate;
    if (age && dob) return age + ' (' + dob + ')';
    if (age) return age;
    if (dob) return dob;
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

  function configureCowPhoto(card) {
    var img = el('cowPhoto');
    if (!img) return;
    var candidates = [];
    if (card.photoUrl) candidates.push(card.photoUrl);
    if (card.photoDataUrl && card.photoDataUrl !== card.photoUrl) candidates.push(card.photoDataUrl);
    candidates.push('/assets/default-cow.jpg');

    var idx = 0;
    img.onerror = function () {
      idx += 1;
      if (idx < candidates.length) {
        img.src = candidates[idx];
      } else {
        img.onerror = null;
      }
    };
    img.alt = card.name + ' (' + card.animalCode + ')';
    img.src = candidates[0];
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

    // Show "Back to Home Dashboard" button on non-home routes
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
      var showRetry = state.errorKind === 'offline' || state.errorKind === 'general' || state.errorKind === 'private';
      el('retryBtn').classList.toggle('hidden', !showRetry);

      if (state.errorKind === 'invalid') {
        document.title = dict.errInvalidTitle + ' — ' + dict.toolbarBrand;
        setText('errorIcon', '?');
        setText('errorTitle', dict.errInvalidTitle);
        setText('errorMessage', dict.errInvalidBody);
      } else if (state.errorKind === 'not_found') {
        document.title = dict.errNotFoundTitle + ' — ' + dict.toolbarBrand;
        setText('errorIcon', '404');
        setText('errorTitle', dict.errNotFoundTitle);
        setText('errorMessage', dict.errNotFoundBody);
      } else if (state.errorKind === 'private') {
        document.title = dict.errPrivateTitle + ' — ' + dict.toolbarBrand;
        setText('errorIcon', '🔒');
        setText('errorTitle', dict.errPrivateTitle);
        setText('errorMessage', dict.errPrivateBody);
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

    // 4. Verified Digital Cow ID Card (/cattle/{validCattleId})
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

    setText('photoCowName', card.name);
    setText('photoCowType', categoryText);
    setText('qrLabel', dict.qrLabel);
    setText('qrAnimalCode', card.animalCode);
    setText('qrHint', dict.qrHint);

    setText('cowName', card.name);
    setText('cowCodePill', card.animalCode);

    setText('secIdentity', dict.secIdentity);
    setText('lblCategory', dict.lblCategory);
    setText('valCategory', categoryText);
    setText('lblBreed', dict.lblBreed);
    setText('valBreed', card.breed || dict.notRecorded);
    setText('lblAgeDob', dict.lblAgeDob);
    setText('valAgeDob', formatAgeDob(card, dict));
    setText('lblColour', dict.lblColour);
    setText('valColour', card.colour || dict.notRecorded);
    setText('lblSex', dict.lblSex);
    setText('valSex', sexText);
    setText('lblEntryDate', dict.lblEntryDate);
    setText('valEntryDate', card.entryDate || dict.notRecorded);
    setText('lblGoshalaFull', dict.lblGoshalaFull);
    setText('valGoshalaFull', goshalaName + ', ' + goshalaAddr);

    setText('secHealth', dict.secHealth);
    setText('lblHealthStatus', dict.lblHealthStatus);
    setText('valHealthStatus', healthText);
    setText('lblLastCheckup', dict.lblLastCheckup);
    setText('valLastCheckup', card.lastHealthCheckup || dict.notRecorded);
    setText('lblVaccination', dict.lblVaccination);
    setText('valVaccination', card.vaccinationSummary || dict.notRecorded);

    setText('secCare', dict.secCare);
    setText('lblIdentMarks', dict.lblIdentMarks);
    setText('valIdentMarks', card.identificationMarks || dict.notRecorded);

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

  async function fetchCattleCard(animalCode) {
    var reqToken = ++activeRequestToken;
    state.loading = true;
    state.errorKind = null;
    state.card = null;
    state.animalCode = animalCode;
    renderUI();

    if (typeof navigator !== 'undefined' && navigator.onLine === false) {
      if (reqToken !== activeRequestToken) return;
      state.loading = false;
      state.errorKind = 'offline';
      renderUI();
      return;
    }

    try {
      var url = buildFirestoreDocumentUrl(animalCode);
      var response = await fetch(url, {
        method: 'GET',
        headers: { Accept: 'application/json' },
        cache: 'no-store'
      });

      if (reqToken !== activeRequestToken) return;

      if (response.status === 403 || response.status === 404) {
        state.loading = false;
        state.errorKind = 'private';
        renderUI();
        return;
      }

      if (!response.ok) {
        state.loading = false;
        state.errorKind = 'general';
        renderUI();
        return;
      }

      var docJson = await response.json();
      if (reqToken !== activeRequestToken) return;

      var parsed = parsePublicCattleDocument(docJson);
      if (!parsed) {
        state.loading = false;
        state.errorKind = 'private';
        renderUI();
        return;
      }

      state.loading = false;
      state.card = parsed;
      configureCowPhoto(parsed);
      renderQr(parsed);
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

    // resolved.view === 'cattle' with validated resolved.animalCode
    fetchCattleCard(resolved.animalCode);
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
      var normalized = normalizeAnimalCode(input ? input.value : '');
      if (!normalized) {
        state.lookupError = true;
        renderUI();
        if (input) input.focus();
        return;
      }
      state.lookupError = false;
      input.value = normalized;
      navigateTo('/cattle/' + encodeURIComponent(normalized));
    });

    el('animalCodeInput').addEventListener('input', function () {
      if (state.lookupError) {
        state.lookupError = false;
        renderUI();
      }
    });

    el('retryBtn').addEventListener('click', function () {
      if (state.routeView === 'cattle' && state.animalCode) {
        fetchCattleCard(state.animalCode);
      } else {
        handleRouteChange();
      }
    });
    el('offlineRetryBtn').addEventListener('click', function () {
      if (state.routeView === 'cattle' && state.animalCode) {
        fetchCattleCard(state.animalCode);
      } else {
        updateOfflineBanner();
      }
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
      if (state.routeView === 'cattle' && state.errorKind === 'offline' && state.animalCode) {
        fetchCattleCard(state.animalCode);
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
