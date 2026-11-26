/**
 * Self-contained ISO/IEC 18004 QR Code encoder (Byte mode, ECC Level M, Versions 1..10)
 * Renders crisp scannable QR codes onto an HTMLCanvasElement without external CDN dependencies.
 */
(function (root) {
  'use strict';

  // Version table for ECC Level M: [version, totalCodewords, ecPerBlock, numBlocksGroup1, dataCodewordsGroup1, numBlocksGroup2, dataCodewordsGroup2, alignmentCenters]
  var VERSION_SPECS = [
    null,
    { v: 1, total: 26, ecPerBlock: 10, g1Blocks: 1, g1Data: 16, g2Blocks: 0, g2Data: 0, align: [] },
    { v: 2, total: 44, ecPerBlock: 16, g1Blocks: 1, g1Data: 28, g2Blocks: 0, g2Data: 0, align: [6, 18] },
    { v: 3, total: 70, ecPerBlock: 26, g1Blocks: 1, g1Data: 44, g2Blocks: 0, g2Data: 0, align: [6, 22] },
    { v: 4, total: 100, ecPerBlock: 18, g1Blocks: 2, g1Data: 32, g2Blocks: 0, g2Data: 0, align: [6, 26] },
    { v: 5, total: 134, ecPerBlock: 24, g1Blocks: 2, g1Data: 43, g2Blocks: 0, g2Data: 0, align: [6, 30] },
    { v: 6, total: 172, ecPerBlock: 16, g1Blocks: 4, g1Data: 27, g2Blocks: 0, g2Data: 0, align: [6, 34] },
    { v: 7, total: 196, ecPerBlock: 18, g1Blocks: 4, g1Data: 31, g2Blocks: 0, g2Data: 0, align: [6, 22, 38] },
    { v: 8, total: 242, ecPerBlock: 22, g1Blocks: 2, g1Data: 38, g2Blocks: 2, g2Data: 39, align: [6, 24, 42] },
    { v: 9, total: 292, ecPerBlock: 22, g1Blocks: 3, g1Data: 36, g2Blocks: 2, g2Data: 37, align: [6, 26, 46] },
    { v: 10, total: 346, ecPerBlock: 26, g1Blocks: 4, g1Data: 43, g2Blocks: 1, g2Data: 44, align: [6, 28, 50] }
  ];

  // GF(256) log/exp tables with primitive polynomial 0x11d
  var GF_EXP = new Uint8Array(512);
  var GF_LOG = new Uint8Array(256);
  (function initGaloisField() {
    var x = 1;
    for (var i = 0; i < 255; i++) {
      GF_EXP[i] = x;
      GF_LOG[x] = i;
      x <<= 1;
      if (x & 0x100) x ^= 0x11d;
    }
    for (var j = 255; j < 512; j++) {
      GF_EXP[j] = GF_EXP[j - 255];
    }
  })();

  function gfMul(a, b) {
    if (a === 0 || b === 0) return 0;
    return GF_EXP[GF_LOG[a] + GF_LOG[b]];
  }

  function rsGeneratorPoly(degree) {
    var poly = [1];
    for (var i = 0; i < degree; i++) {
      var next = new Array(poly.length + 1).fill(0);
      var factor = GF_EXP[i];
      for (var j = 0; j < poly.length; j++) {
        next[j] ^= poly[j];
        next[j + 1] ^= gfMul(poly[j], factor);
      }
      poly = next;
    }
    return poly;
  }

  function rsComputeRemainder(data, ecCount) {
    var gen = rsGeneratorPoly(ecCount);
    var res = new Array(ecCount).fill(0);
    for (var i = 0; i < data.length; i++) {
      var factor = data[i] ^ res.shift();
      res.push(0);
      if (factor !== 0) {
        for (var j = 0; j < ecCount; j++) {
          res[j] ^= gfMul(gen[j + 1], factor);
        }
      }
    }
    return res;
  }

  function utf8Bytes(str) {
    if (typeof TextEncoder !== 'undefined') {
      return Array.from(new TextEncoder().encode(str));
    }
    var bytes = [];
    for (var i = 0; i < str.length; i++) {
      var code = str.charCodeAt(i);
      if (code < 0x80) bytes.push(code);
      else if (code < 0x800) {
        bytes.push(0xc0 | (code >> 6), 0x80 | (code & 0x3f));
      } else {
        bytes.push(0xe0 | (code >> 12), 0x80 | ((code >> 6) & 0x3f), 0x80 | (code & 0x3f));
      }
    }
    return bytes;
  }

  function selectVersion(byteLen) {
    for (var v = 1; v < VERSION_SPECS.length; v++) {
      var spec = VERSION_SPECS[v];
      var dataCap = spec.g1Blocks * spec.g1Data + spec.g2Blocks * spec.g2Data;
      var countBits = v < 10 ? 8 : 16;
      var neededBits = 4 + countBits + byteLen * 8;
      if (neededBits <= dataCap * 8) {
        return spec;
      }
    }
    throw new Error('QR payload too long for supported versions');
  }

  function encodeDataCodewords(bytes, spec) {
    var bits = [];
    function pushBits(val, len) {
      for (var i = len - 1; i >= 0; i--) {
        bits.push((val >> i) & 1);
      }
    }

    var dataCap = spec.g1Blocks * spec.g1Data + spec.g2Blocks * spec.g2Data;
    var capBits = dataCap * 8;
    // Mode 0100 (Byte)
    pushBits(0x4, 4);
    pushBits(bytes.length, spec.v < 10 ? 8 : 16);
    for (var i = 0; i < bytes.length; i++) {
      pushBits(bytes[i], 8);
    }
    // Terminator (up to 4 zero bits)
    var term = Math.min(4, capBits - bits.length);
    for (var t = 0; t < term; t++) bits.push(0);
    // Pad to byte boundary
    while (bits.length % 8 !== 0) bits.push(0);

    var codewords = [];
    for (var b = 0; b < bits.length; b += 8) {
      var val = 0;
      for (var k = 0; k < 8; k++) val = (val << 1) | bits[b + k];
      codewords.push(val);
    }

    var padIdx = 0;
    var padBytes = [0xec, 0x11];
    while (codewords.length < dataCap) {
      codewords.push(padBytes[padIdx % 2]);
      padIdx++;
    }
    return codewords;
  }

  function interleaveBlocks(codewords, spec) {
    var blocks = [];
    var ecBlocks = [];
    var offset = 0;

    for (var i = 0; i < spec.g1Blocks; i++) {
      var chunk1 = codewords.slice(offset, offset + spec.g1Data);
      offset += spec.g1Data;
      blocks.push(chunk1);
      ecBlocks.push(rsComputeRemainder(chunk1, spec.ecPerBlock));
    }
    for (var j = 0; j < spec.g2Blocks; j++) {
      var chunk2 = codewords.slice(offset, offset + spec.g2Data);
      offset += spec.g2Data;
      blocks.push(chunk2);
      ecBlocks.push(rsComputeRemainder(chunk2, spec.ecPerBlock));
    }

    var maxDataLen = Math.max(spec.g1Data, spec.g2Data);
    var out = [];
    for (var d = 0; d < maxDataLen; d++) {
      for (var b = 0; b < blocks.length; b++) {
        if (d < blocks[b].length) out.push(blocks[b][d]);
      }
    }
    for (var e = 0; e < spec.ecPerBlock; e++) {
      for (var eb = 0; eb < ecBlocks.length; eb++) {
        out.push(ecBlocks[eb][e]);
      }
    }
    return out;
  }

  function createMatrix(size) {
    var modules = new Array(size);
    var isFunc = new Array(size);
    for (var r = 0; r < size; r++) {
      modules[r] = new Uint8Array(size);
      isFunc[r] = new Uint8Array(size);
    }
    return { size: size, modules: modules, isFunc: isFunc };
  }

  function setFunctionModule(mat, r, c, dark) {
    mat.modules[r][c] = dark ? 1 : 0;
    mat.isFunc[r][c] = 1;
  }

  function drawFinder(mat, topR, topC) {
    for (var dr = -1; dr <= 7; dr++) {
      for (var dc = -1; dc <= 7; dc++) {
        var r = topR + dr;
        var c = topC + dc;
        if (r < 0 || r >= mat.size || c < 0 || c >= mat.size) continue;
        var dist = Math.max(Math.abs(dr - 3), Math.abs(dc - 3));
        var dark = (dist !== 2 && dist !== 4);
        setFunctionModule(mat, r, c, dark);
      }
    }
  }

  function drawAlignment(mat, centerR, centerC) {
    for (var dr = -2; dr <= 2; dr++) {
      for (var dc = -2; dc <= 2; dc++) {
        var dist = Math.max(Math.abs(dr), Math.abs(dc));
        setFunctionModule(mat, centerR + dr, centerC + dc, dist !== 1);
      }
    }
  }

  function drawFunctionPatterns(mat, spec) {
    var size = mat.size;
    // Timing patterns
    for (var i = 0; i < size; i++) {
      setFunctionModule(mat, 6, i, i % 2 === 0);
      setFunctionModule(mat, i, 6, i % 2 === 0);
    }

    // Finder patterns
    drawFinder(mat, 0, 0);
    drawFinder(mat, 0, size - 7);
    drawFinder(mat, size - 7, 0);

    // Alignment patterns
    var centers = spec.align;
    for (var r = 0; r < centers.length; r++) {
      for (var c = 0; c < centers.length; c++) {
        if ((r === 0 && c === 0) || (r === 0 && c === centers.length - 1) || (r === centers.length - 1 && c === 0)) {
          continue;
        }
        drawAlignment(mat, centers[r], centers[c]);
      }
    }

    // Reserve format info areas
    for (var k = 0; k <= 8; k++) {
      if (k !== 6) {
        setFunctionModule(mat, 8, k, false);
        setFunctionModule(mat, k, 8, false);
      }
    }
    for (var m = 0; m < 8; m++) {
      setFunctionModule(mat, 8, size - 1 - m, false);
      setFunctionModule(mat, size - 1 - m, 8, false);
    }
    // Dark module
    setFunctionModule(mat, size - 8, 8, true);

    // Version info for version >= 7
    if (spec.v >= 7) {
      var rem = spec.v;
      for (var b = 0; b < 12; b++) {
        rem = (rem << 1) ^ ((rem >>> 11) * 0x1f25);
      }
      var verBits = (spec.v << 12) | rem;
      for (var idx = 0; idx < 18; idx++) {
        var bit = ((verBits >>> idx) & 1) !== 0;
        var a = size - 11 + (idx % 3);
        var p = Math.floor(idx / 3);
        setFunctionModule(mat, a, p, bit);
        setFunctionModule(mat, p, a, bit);
      }
    }
  }

  function drawCodewords(mat, data) {
    var size = mat.size;
    var bitIndex = 0;
    var totalBits = data.length * 8;
    for (var right = size - 1; right >= 1; right -= 2) {
      if (right === 6) right = 5;
      for (var vert = 0; vert < size; vert++) {
        var upward = ((right + 1) & 2) === 0;
        var r = upward ? (size - 1 - vert) : vert;
        for (var j = 0; j < 2; j++) {
          var c = right - j;
          if (!mat.isFunc[r][c]) {
            var dark = false;
            if (bitIndex < totalBits) {
              dark = ((data[bitIndex >>> 3] >>> (7 - (bitIndex & 7))) & 1) !== 0;
              bitIndex++;
            }
            mat.modules[r][c] = dark ? 1 : 0;
          }
        }
      }
    }
  }

  function applyMask(mat, maskPattern) {
    var size = mat.size;
    for (var r = 0; r < size; r++) {
      for (var c = 0; c < size; c++) {
        if (mat.isFunc[r][c]) continue;
        var invert = false;
        switch (maskPattern) {
          case 0: invert = (r + c) % 2 === 0; break;
          case 1: invert = r % 2 === 0; break;
          case 2: invert = c % 3 === 0; break;
          case 3: invert = (r + c) % 3 === 0; break;
          case 4: invert = (Math.floor(r / 2) + Math.floor(c / 3)) % 2 === 0; break;
          case 5: invert = ((r * c) % 2 + (r * c) % 3) === 0; break;
          case 6: invert = (((r * c) % 2 + (r * c) % 3) % 2) === 0; break;
          case 7: invert = (((r + c) % 2 + (r * c) % 3) % 2) === 0; break;
        }
        if (invert) mat.modules[r][c] ^= 1;
      }
    }
  }

  function drawFormatBits(mat, maskPattern) {
    // ECC Level M format indicator = 00
    var data = (0x0 << 3) | maskPattern;
    var rem = data;
    for (var i = 0; i < 10; i++) {
      rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
    }
    var bits = ((data << 10) | rem) ^ 0x5412;
    var size = mat.size;

    for (var j = 0; j <= 5; j++) setFunctionModule(mat, 8, j, ((bits >>> (14 - j)) & 1) !== 0);
    setFunctionModule(mat, 8, 7, ((bits >>> 8) & 1) !== 0);
    setFunctionModule(mat, 8, 8, ((bits >>> 7) & 1) !== 0);
    setFunctionModule(mat, 7, 8, ((bits >>> 6) & 1) !== 0);
    for (var k = 9; k < 15; k++) setFunctionModule(mat, 14 - k, 8, ((bits >>> (14 - k)) & 1) !== 0);

    for (var m = 0; m < 8; m++) setFunctionModule(mat, 8, size - 1 - m, ((bits >>> m) & 1) !== 0);
    for (var n = 8; n < 15; n++) setFunctionModule(mat, size - 15 + n, 8, ((bits >>> n) & 1) !== 0);
    setFunctionModule(mat, size - 8, 8, true);
  }

  function evaluatePenalty(mat) {
    var size = mat.size;
    var penalty = 0;
    // Rule 1: adjacent modules in row/col of same color
    for (var r = 0; r < size; r++) {
      var runColor = mat.modules[r][0];
      var runLen = 1;
      for (var c = 1; c < size; c++) {
        if (mat.modules[r][c] === runColor) {
          runLen++;
          if (runLen === 5) penalty += 3;
          else if (runLen > 5) penalty += 1;
        } else {
          runColor = mat.modules[r][c];
          runLen = 1;
        }
      }
    }
    for (var c2 = 0; c2 < size; c2++) {
      var colColor = mat.modules[0][c2];
      var colLen = 1;
      for (var r2 = 1; r2 < size; r2++) {
        if (mat.modules[r2][c2] === colColor) {
          colLen++;
          if (colLen === 5) penalty += 3;
          else if (colLen > 5) penalty += 1;
        } else {
          colColor = mat.modules[r2][c2];
          colLen = 1;
        }
      }
    }
    // Rule 2: 2x2 blocks
    for (var r3 = 0; r3 < size - 1; r3++) {
      for (var c3 = 0; c3 < size - 1; c3++) {
        var color = mat.modules[r3][c3];
        if (color === mat.modules[r3][c3 + 1] && color === mat.modules[r3 + 1][c3] && color === mat.modules[r3 + 1][c3 + 1]) {
          penalty += 3;
        }
      }
    }
    // Rule 4: dark ratio balance
    var darkCount = 0;
    for (var r4 = 0; r4 < size; r4++) {
      for (var c4 = 0; c4 < size; c4++) {
        if (mat.modules[r4][c4]) darkCount++;
      }
    }
    var ratio = Math.abs(darkCount * 20 - size * size * 10);
    penalty += Math.floor(ratio / (size * size)) * 10;
    return penalty;
  }

  function generateMatrix(text) {
    var bytes = utf8Bytes(String(text || ''));
    var spec = selectVersion(bytes.length);
    var size = spec.v * 4 + 17;
    var dataCodewords = encodeDataCodewords(bytes, spec);
    var interleaved = interleaveBlocks(dataCodewords, spec);

    var bestPenalty = Infinity;
    var bestMatrix = null;

    for (var mask = 0; mask < 8; mask++) {
      var mat = createMatrix(size);
      drawFunctionPatterns(mat, spec);
      drawCodewords(mat, interleaved);
      applyMask(mat, mask);
      drawFormatBits(mat, mask);
      var penalty = evaluatePenalty(mat);
      if (penalty < bestPenalty) {
        bestPenalty = penalty;
        bestMatrix = mat;
      }
    }
    return bestMatrix;
  }

  function renderToCanvas(canvas, text, options) {
    var opts = options || {};
    var quietZone = typeof opts.quietZone === 'number' ? opts.quietZone : 4;
    var darkColor = opts.darkColor || '#123524';
    var lightColor = opts.lightColor || '#FFFFFF';
    var targetSize = opts.size || 240;

    var mat = generateMatrix(text);
    var totalModules = mat.size + quietZone * 2;
    var scale = Math.max(2, Math.floor(targetSize / totalModules));
    var pxSize = totalModules * scale;

    canvas.width = pxSize;
    canvas.height = pxSize;
    var ctx = canvas.getContext('2d');
    ctx.fillStyle = lightColor;
    ctx.fillRect(0, 0, pxSize, pxSize);
    ctx.fillStyle = darkColor;
    for (var r = 0; r < mat.size; r++) {
      for (var c = 0; c < mat.size; c++) {
        if (mat.modules[r][c]) {
          ctx.fillRect((c + quietZone) * scale, (r + quietZone) * scale, scale, scale);
        }
      }
    }
    return { version: (mat.size - 17) / 4, size: mat.size, pixelSize: pxSize };
  }

  var api = {
    generateMatrix: generateMatrix,
    renderToCanvas: renderToCanvas
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = api;
  } else {
    root.JBGSQrCode = api;
  }
})(typeof window !== 'undefined' ? window : globalThis);
