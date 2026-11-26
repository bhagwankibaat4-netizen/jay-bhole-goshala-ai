import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const app = require('./app.js');
const qr = require('./qrcode.js');

test('Scenario 1 & 2: Extracts valid Animal Code from Google Lens /cattle/ URLs', () => {
  assert.equal(app.extractAnimalCodeFromLocation('/cattle/JBGS-G-357', ''), 'JBGS-G-357');
  assert.equal(app.extractAnimalCodeFromLocation('/cattle/jbgs-g-357/', ''), 'JBGS-G-357');
  assert.equal(app.extractAnimalCodeFromLocation('/', '?id=JBGS-G-357'), 'JBGS-G-357');
  assert.equal(
    app.buildFirestoreDocumentUrl('JBGS-G-357'),
    'https://firestore.googleapis.com/v1/projects/gen-lang-client-0821187064/databases/ai-studio-android-5ee9bbf0-cdf7-4202-8155-b40c385da917/documents/publicCattleCards/JBGS-G-357'
  );
});

test('Scenario 7: Rejects invalid QR URLs, path traversal, and malformed IDs', () => {
  assert.equal(app.extractAnimalCodeFromLocation('/', ''), null);
  assert.equal(app.extractAnimalCodeFromLocation('/cattle/', ''), null);
  assert.equal(app.extractAnimalCodeFromLocation('/cattle/a b c', ''), null);
  assert.equal(app.extractAnimalCodeFromLocation('/cattle/../../etc', ''), null);
  assert.equal(app.extractAnimalCodeFromLocation('/other/JBGS-G-357', ''), null);
});

test('Scenario 4 & 5: Parses Admin-approved Firestore public card cleanly without fabricating missing fields', () => {
  const sampleDoc = {
    fields: {
      animalCode: { stringValue: 'JBGS-G-357' },
      name: { stringValue: 'कामधेनु गौमाता' },
      category: { stringValue: 'COW' },
      breed: { stringValue: 'गिर (Gir)' },
      sex: { stringValue: 'FEMALE' },
      ageText: { stringValue: '4 वर्ष' },
      birthDate: { stringValue: '2022-03-18' },
      colour: { stringValue: 'सफेद-लाल' },
      entryDate: { stringValue: '2024-01-10' },
      healthStatus: { stringValue: 'HEALTHY' },
      vaccinationSummary: { stringValue: 'FMD टीका पूर्ण' },
      lastHealthCheckup: { stringValue: '' },
      identificationMarks: { stringValue: 'माथे पर सफेद तिलक' },
      photoUrl: { stringValue: 'https://firebasestorage.googleapis.com/v0/b/gen-lang-client-0821187064.firebasestorage.app/o/cow.jpg?alt=media' },
      publicApproved: { booleanValue: true }
    }
  };

  const card = app.parsePublicCattleDocument(sampleDoc);
  assert.ok(card);
  assert.equal(card.animalCode, 'JBGS-G-357');
  assert.equal(card.name, 'कामधेनु गौमाता');
  assert.equal(card.healthStatus, 'HEALTHY');
  assert.equal(card.lastHealthCheckup, '');
  assert.equal(card.qrUrl, 'https://gen-lang-client-0821187064.web.app/cattle/JBGS-G-357');
  assert.equal(card.goshalaAddressHi, 'ग्राम – मेहदोरा, विकासखंड – पोरसा, जिला – मुरैना, मध्य प्रदेश, भारत।');
});

test('Scenario 6: Blocks unapproved cows and rejects unsafe javascript: image URLs (XSS guard)', () => {
  const unapprovedDoc = {
    fields: {
      animalCode: { stringValue: 'JBGS-G-999' },
      name: { stringValue: 'निजी गाय' },
      publicApproved: { booleanValue: false }
    }
  };
  assert.equal(app.parsePublicCattleDocument(unapprovedDoc), null);

  assert.equal(app.isSafeHttpOrDataImageUrl('javascript:alert(1)'), false);
  assert.equal(app.isSafeHttpOrDataImageUrl('http://insecure.example/a.jpg'), false);
  assert.equal(app.isSafeHttpOrDataImageUrl('https://firebasestorage.googleapis.com/v0/b/x/o/y.jpg'), true);
  assert.equal(app.isSafeHttpOrDataImageUrl('data:image/jpeg;base64,/9j/4AAQSkZJRg=='), true);
});

test('Scenario 10: Builds bilingual WhatsApp share URL and valid QR code matrix', () => {
  const card = {
    animalCode: 'JBGS-G-357',
    name: 'कामधेनु गौमाता',
    breed: 'गिर',
    healthStatus: 'HEALTHY',
    goshalaNameHi: 'जय भोले गौशाला समिति',
    goshalaNameEn: 'Jay Bhole Gosala Samiti',
    qrUrl: 'https://gen-lang-client-0821187064.web.app/cattle/JBGS-G-357'
  };
  const waHi = app.buildWhatsAppShareUrl(card, 'hi');
  assert.ok(waHi.startsWith('https://wa.me/?text='));
  assert.ok(decodeURIComponent(waHi).includes(card.qrUrl));

  const mat = qr.generateMatrix(card.qrUrl);
  assert.ok(mat.size >= 29, 'Expected QR version >= 3 for HTTPS URL');
  assert.equal(mat.modules[0][0], 1, 'Top-left finder corner must be dark');
  assert.equal(mat.modules[0][mat.size - 1], 1, 'Top-right finder corner must be dark');
  assert.equal(mat.modules[mat.size - 1][0], 1, 'Bottom-left finder corner must be dark');
});
