//解密方法
import CryptoJS from "./crypto-mode-ecb";
import base64 from "crypto-js/enc-base64";
import AES from "crypto-js/aes";
import MD5 from "crypto-js/md5";
import SM4Util from "./sm4-crypto";
// AES加密key生成
const g_h5_key = function (s) {
    const _ek = '9EqAw+JtHSsyswpL';
    const _salt = '_bsdk_';
    const skey = s + _salt;
    const info = MD5(skey).toString();
    return info ? info.substring(0, 16).toLowerCase() : _ek;
};
const sm4Crypt = new SM4Util();

const mode = 0; // 0代表SM4，1代表AES

const Encrypt = function (word, k) {
    if (mode){
        let srcs = CryptoJS.enc.Utf8.parse(word);
        let encrypted = AES.encrypt(srcs, CryptoJS.enc.Utf8.parse(k), { iv: CryptoJS.enc.Utf8.parse(k), mode: CryptoJS.mode.CBC, padding: CryptoJS.pad.Pkcs7 });
        return encrypted.ciphertext.toString().toUpperCase();
    } else {
        return sm4Crypt.encryptData_CBC(word,k)
    }
}

const Decrypt =function (word, k) {
    if (mode) {
        let encryptedHexStr = CryptoJS.enc.Hex.parse(word);
        let srcs = base64.stringify(encryptedHexStr);
        let decrypt = AES.decrypt(srcs, CryptoJS.enc.Utf8.parse(k), { iv: CryptoJS.enc.Utf8.parse(k), mode: CryptoJS.mode.CBC, padding: CryptoJS.pad.Pkcs7 });
        let decryptedStr = decrypt.toString(CryptoJS.enc.Utf8);
        return decryptedStr.toString();
    } else {
        return sm4Crypt.decryptData_CBC(word,k)
    }
}

wx.ctDecrypt = function (word, k) {
    let encryptedHexStr = CryptoJS.enc.Hex.parse(word);
    let srcs = base64.stringify(encryptedHexStr);
    let decrypt = AES.decrypt(srcs, CryptoJS.enc.Utf8.parse(k), { mode: CryptoJS.mode.ECB, padding: CryptoJS.pad.Pkcs7 });
    let decryptedStr = decrypt.toString(CryptoJS.enc.Utf8);
    return decryptedStr.toString();
}

export default {
    k: g_h5_key,
    Encrypt: Encrypt,
    Decrypt: Decrypt
}
