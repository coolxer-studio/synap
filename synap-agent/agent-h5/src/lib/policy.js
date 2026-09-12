const REQUIRED_CONFIG_OBJECTS = [
  'deviceConfig', 'selfAppConfig', 'locationConfig', 'runtimeConfig',
  'debugConfig', 'errorConfig', 'eventConfig', 'devToolConfig'
];

export function validatePolicy(policy) {
  if (!policy || typeof policy !== 'object' || !policy.config || typeof policy.config !== 'object') {
    throw new Error('Invalid SynapAgent policy: config is missing');
  }
  const startConfig = policy.config.startConfig;
  if (!Array.isArray(startConfig) || startConfig.length < 6) {
    throw new Error('Invalid SynapAgent policy: startConfig must contain 6 values');
  }
  [0, 2, 3, 4, 5].forEach((index) => {
    if (!Number.isFinite(Number(startConfig[index])) || Number(startConfig[index]) < 0) {
      throw new Error(`Invalid SynapAgent policy: startConfig[${index}]`);
    }
  });
  REQUIRED_CONFIG_OBJECTS.forEach((key) => {
    if (!policy.config[key] || typeof policy.config[key] !== 'object') {
      throw new Error(`Invalid SynapAgent policy: ${key} is missing`);
    }
  });
  return policy;
}

export function decodePolicy(codec, encryptedPolicy) {
  if (!encryptedPolicy) throw new Error('Empty SynapAgent policy response');
  let decoded;
  try {
    decoded = codec.decodeResponse(encryptedPolicy);
  } catch (error) {
    throw new Error(`Unable to decrypt SynapAgent policy: ${error.message || error}`);
  }
  try {
    return validatePolicy(JSON.parse(decoded));
  } catch (error) {
    if (/^Invalid SynapAgent policy:/.test(error.message)) throw error;
    throw new Error(`Unable to parse SynapAgent policy: ${error.message || error}`);
  }
}

