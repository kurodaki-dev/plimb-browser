'use strict';

/**
 * Lecture des paquets Chrome (.crx) et extraction ZIP.
 * Electron ne sait charger que des extensions "decompressees" : il faut donc
 * retirer l entete CRX puis desarchiver le ZIP.
 */

const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');

/**
 * Retourne la position du debut des donnees ZIP dans un buffer CRX.
 * @param {Buffer} buffer
 * @returns {number}
 */
function crxZipOffset(buffer) {
  if (buffer.length < 16 || buffer.toString('latin1', 0, 4) !== 'Cr24') {
    // Ce n'est pas un CRX : on suppose que le buffer est deja un ZIP.
    return 0;
  }
  const version = buffer.readUInt32LE(4);
  if (version === 2) {
    const publicKeyLength = buffer.readUInt32LE(8);
    const signatureLength = buffer.readUInt32LE(12);
    return 16 + publicKeyLength + signatureLength;
  }
  if (version === 3) {
    const headerLength = buffer.readUInt32LE(8);
    return 12 + headerLength;
  }
  throw new Error(`Version CRX non prise en charge : ${version}`);
}

/**
 * Extrait un buffer ZIP dans un dossier (stocke + deflate).
 * @param {Buffer} zipBuffer
 * @param {string} targetDir
 * @returns {{files: string[]}}
 */
function unzipSync(zipBuffer, targetDir) {
  const files = [];
  const eocdSignature = Buffer.from([0x50, 0x4b, 0x05, 0x06]);
  const eocdIndex = zipBuffer.lastIndexOf(eocdSignature);
  if (eocdIndex < 0) {
    throw new Error('Archive ZIP invalide : fin de repertoire central introuvable.');
  }

  const entriesCount = zipBuffer.readUInt16LE(eocdIndex + 10);
  let offset = zipBuffer.readUInt32LE(eocdIndex + 16);
  const entries = [];

  for (let i = 0; i < entriesCount; i += 1) {
    if (zipBuffer.readUInt32LE(offset) !== 0x02014b50) {
      throw new Error('Archive ZIP invalide : entree centrale corrompue.');
    }
    const compressionMethod = zipBuffer.readUInt16LE(offset + 10);
    const compressedSize = zipBuffer.readUInt32LE(offset + 20);
    const nameLength = zipBuffer.readUInt16LE(offset + 28);
    const extraLength = zipBuffer.readUInt16LE(offset + 30);
    const commentLength = zipBuffer.readUInt16LE(offset + 32);
    const localOffset = zipBuffer.readUInt32LE(offset + 42);
    const name = zipBuffer.toString('utf8', offset + 46, offset + 46 + nameLength);
    entries.push({ name, compressionMethod, compressedSize, localOffset });
    offset += 46 + nameLength + extraLength + commentLength;
  }

  for (const entry of entries) {
    if (entry.name.endsWith('/')) {
      fs.mkdirSync(path.join(targetDir, entry.name), { recursive: true });
      continue;
    }

    if (zipBuffer.readUInt32LE(entry.localOffset) !== 0x04034b50) {
      throw new Error(`Archive ZIP invalide : entete locale absente pour ${entry.name}.`);
    }
    const localNameLength = zipBuffer.readUInt16LE(entry.localOffset + 26);
    const localExtraLength = zipBuffer.readUInt16LE(entry.localOffset + 28);
    const dataStart = entry.localOffset + 30 + localNameLength + localExtraLength;
    const data = zipBuffer.subarray(dataStart, dataStart + entry.compressedSize);

    const output =
      entry.compressionMethod === 0 ? data : zlib.inflateRawSync(data);
    const destination = path.join(targetDir, entry.name);
    fs.mkdirSync(path.dirname(destination), { recursive: true });
    fs.writeFileSync(destination, output);
    files.push(destination);
  }

  return { files };
}

/**
 * Decompresse un fichier .crx dans un dossier.
 * @param {string} crxPath
 * @param {string} targetDir
 */
function unpackCrxSync(crxPath, targetDir) {
  const buffer = fs.readFileSync(crxPath);
  const offset = crxZipOffset(buffer);
  fs.rmSync(targetDir, { recursive: true, force: true });
  fs.mkdirSync(targetDir, { recursive: true });
  unzipSync(buffer.subarray(offset), targetDir);
  return targetDir;
}

/** Lit le manifest.json d un dossier d extension, ou null. */
function readManifest(folder) {
  try {
    return JSON.parse(fs.readFileSync(path.join(folder, 'manifest.json'), 'utf8'));
  } catch (error) {
    return null;
  }
}

module.exports = { crxZipOffset, unzipSync, unpackCrxSync, readManifest };
