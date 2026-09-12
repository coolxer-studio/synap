import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createCipheriv, createDecipheriv } from 'node:crypto';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const required = [
  'build-profile.json5',
  'oh-package.json5',
  'probe/oh-package.json5',
  'probe/Index.ets',
  'probe/src/main/module.json5',
  'probe/src/main/ets/SynapAgent.ets',
  'probe/src/main/ets/secure/CryptoFrameworkProvider.ets',
  'probe/src/main/ets/secure/SecureClient.ets',
  'probe/src/test/List.test.ets',
  'probe/src/test/SecureClient.test.ets',
  'probe/src/ohosTest/ets/test/Device.test.ets',
  'entry/src/main/module.json5',
  'entry/src/main/ets/pages/Index.ets',
  'entry/src/main/ets/DemoRuntime.ets'
];

const missing = required.filter((name) => !existsSync(join(root, name)));
if (missing.length > 0) {
  throw new Error(`Missing required files: ${missing.join(', ')}`);
}

function filesBelow(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? filesBelow(path) : [path];
  });
}

for (const path of filesBelow(root).filter((value) => /\.json5?$/.test(value))) {
  JSON.parse(readFileSync(path, 'utf8'));
}

const buildProfile = JSON.parse(readFileSync(join(root, 'build-profile.json5'), 'utf8'));
const product = buildProfile.app.products[0];
if (product.compatibleSdkVersion !== '5.0.0(12)' || product.runtimeOS !== 'HarmonyOS') {
  throw new Error('HarmonyOS API 12 compatibility is not configured');
}

const source = readFileSync(join(root, 'probe/src/main/ets/SynapAgent.ets'), 'utf8');
for (const method of [
  'initialize', 'start', 'stop', 'destroy', 'flush', 'userData', 'markAction',
  'getDeviceInfo', 'getRatingInfo', 'getLabelInfo', 'getMetrics'
]) {
  if (!source.includes(`${method}(`)) throw new Error(`Public API ${method} is missing`);
}
if (!source.includes('-harmony-')) throw new Error('Harmony platform header is missing');

const modelSource = readFileSync(join(root, 'probe/src/main/ets/core/Models.ets'), 'utf8');
if (!modelSource.includes("PLATFORM: string = 'harmony'") ||
    !modelSource.includes("SECURE_DEVICE_TYPE: string = 'HARMONY'")) {
  throw new Error('Harmony platform or secure device type is not configured');
}
if (!modelSource.includes('batchId?: string')) {
  throw new Error('Persistent upload batch identity is missing');
}

for (const path of filesBelow(root).filter((value) => value.endsWith('.ets'))) {
  const text = readFileSync(path, 'utf8');
  for (const match of text.matchAll(/from\s+['"](\.{1,2}\/[^'"]+)['"]/g)) {
    const target = resolve(dirname(path), match[1]);
    if (!existsSync(`${target}.ets`) && !existsSync(join(target, 'Index.ets'))) {
      throw new Error(`Unresolved local import ${match[1]} in ${path.slice(root.length + 1)}`);
    }
  }
}

const requestVector = JSON.parse(readFileSync(join(root,
  'probe/src/test/resources/protocol/aes-256-gcm-request.json'), 'utf8'));
if (requestVector.nonceBase64Url !== 'oKGiowAAAAAAAAAB' ||
    requestVector.combinedCiphertextBase64Url !==
      'y43V6x8Hp9N--JdIn9atb-inacLk7T5Q9B7Jzez-vufGdNrX0f2xKw') {
  throw new Error('Protocol request vector is not the SC1 baseline');
}

function decodeBase64Url(value) {
  return Buffer.from(value.replace(/-/g, '+').replace(/_/g, '/'), 'base64');
}

function verifyAesVector(vector) {
  const key = Buffer.from(vector.keyHex, 'hex');
  const nonce = decodeBase64Url(vector.nonceBase64Url);
  const cipher = createCipheriv('aes-256-gcm', key, nonce);
  cipher.setAAD(Buffer.from(vector.aadUtf8, 'utf8'));
  const ciphertext = Buffer.concat([
    cipher.update(Buffer.from(vector.plaintextUtf8, 'utf8')),
    cipher.final(),
    cipher.getAuthTag()
  ]);
  if (ciphertext.toString('base64url') !== vector.combinedCiphertextBase64Url) {
    throw new Error(`AES-GCM vector mismatch: ${vector.description}`);
  }
  const encrypted = decodeBase64Url(vector.combinedCiphertextBase64Url);
  const decipher = createDecipheriv('aes-256-gcm', key, nonce);
  decipher.setAAD(Buffer.from(vector.aadUtf8, 'utf8'));
  decipher.setAuthTag(encrypted.subarray(-16));
  const plaintext = Buffer.concat([decipher.update(encrypted.subarray(0, -16)), decipher.final()]);
  if (plaintext.toString('utf8') !== vector.plaintextUtf8) {
    throw new Error(`AES-GCM vector could not be opened: ${vector.description}`);
  }
}

verifyAesVector(requestVector);
verifyAesVector(JSON.parse(readFileSync(join(root,
  'probe/src/test/resources/protocol/aes-256-gcm-response.json'), 'utf8')));

console.log(`agent-harmony structure and SC1 vectors OK (${required.length} required files)`);
