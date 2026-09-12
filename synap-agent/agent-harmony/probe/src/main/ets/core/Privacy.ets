import { utf8 } from './Encoding';
import { MAX_USER_DATA_BYTES } from './Models';

const SENSITIVE_KEY: RegExp = /(password|passwd|pwd|token|authorization|cookie|secret|id.?card|credit.?card|card.?number|phone|email|密码|口令|令牌|凭证|授权|秘钥|秘密|cookie|身份证|证件|银行卡|卡号|电话|手机|邮箱|邮件)/i;

function containsSensitiveKey(value: Object, depth: number = 0): boolean {
  if (depth > 6 || value === null || value === undefined) return false;
  if (Array.isArray(value)) {
    return value.some((item: Object) => containsSensitiveKey(item, depth + 1));
  }
  return Object.keys(value).some((key: string) => SENSITIVE_KEY.test(key) ||
    containsSensitiveKey((value as Record<string, Object>)[key], depth + 1));
}

export function validateUserData(userId: string, data: Record<string, Object>): boolean {
  if (userId.length < 1 || userId.length > 256 || data === null || data === undefined) {
    return false;
  }
  if (containsSensitiveKey(data)) return false;
  try {
    return utf8(JSON.stringify(data)).length <= MAX_USER_DATA_BYTES;
  } catch (_error) {
    return false;
  }
}

export function validateAction(action: string, data?: Record<string, Object>): boolean {
  if (action.trim().length < 1 || action.length > 128) return false;
  if (data === undefined) return true;
  if (containsSensitiveKey(data)) return false;
  try {
    return utf8(JSON.stringify(data)).length <= MAX_USER_DATA_BYTES;
  } catch (_error) {
    return false;
  }
}
