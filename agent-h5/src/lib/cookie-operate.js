
const cookieOperate = {
  getItem: function (itemName) {
    if (itemName) {
      var encodedName = encodeURIComponent(itemName).replace(/[\-\.\+\*]/g, "\\$&");
      var encodedValue = document.cookie.replace(new RegExp("(?:(?:^|.*;)\\s*" + encodedName + "\\s*\\=\\s*([^;]*).*$)|^.*$"), "$1");
      var itemValue = decodeURIComponent(encodedValue);
      return itemValue;
    }
    return null;
  }, setItem: function (itemName, itemValue, expires, path, domainName, secure) {
    if (!itemName || /^(?:expires|max\-age|path|domain|secure)$/i.test(itemName))
      return false;
    var expireStr = "";
    if (expires) switch (expires.constructor) {
      case Number:
        if (expires) {
          expireStr = "; expires=" + new Date((new Date()).getTime() + 1e3 * expires).toUTCString();
        } else {
          expireStr = "; expires=Fri, 31 Dec 9999 23:59:59 GMT";
        }
        break;
      case String:
        expireStr = "; expires=" + expires;
        break;
      case Date:
        expireStr = "; expires=" + expires.toUTCString();
    }
    var secureCookie = secure || (window.location && window.location.protocol === 'https:');
    document.cookie = encodeURIComponent(itemName) + "=" + encodeURIComponent(itemValue) + expireStr + (domainName ? "; domain=" + domainName : "") + (path ? "; path=" + path : "") + "; SameSite=Lax" + (secureCookie ? "; Secure" : "");
    return true;
  }, removeItem: function (itemName, path, domain) {
    if (itemName && this.hasItem(itemName)) {
      document.cookie = encodeURIComponent(itemName) + "=; expires=Thu, 01 Jan 1970 00:00:00 GMT" + (domain ? "; domain=" + domain : "") + (path ? "; path=" + path : "");
      return true;
    }
    return false;
  }, hasItem: function (itemName) {
    if (itemName) {
      return new RegExp("(?:^|;\\s*)" + encodeURIComponent(itemName).replace(/[\-\.\+\*]/g, "\\$&") + "\\s*\\=").test(document.cookie);
    }
    return false;
  }, keys: function () {
    var keys = document.cookie.replace(/((?:^|\s*;)[^\=]+)(?=;|$)|^\s*|\s*(?:\=[^;]*)?(?:\1|$)/g, "").split(/\s*(?:\=[^;]*)?;\s*/);
    for (var r = 0; keys.length > r; r++) keys[r] = decodeURIComponent(keys[r]);
    return keys;
  }, getFromSessionCookie: function (key) {
    var value;
    try {
      value = window.sessionStorage.getItem(key);
    } catch (e) {
      value = undefined;
    }
    if (value) {
      return value;
    }
    value = cookieOperate.getItem(key);
    if (value) {
      return value;
    }
    if (window.esl && window.esl[key] !== undefined) return window.esl[key];
    return undefined;
  }, setToSessionCookie: function (key, value) {
    try {
      window.sessionStorage.setItem(key, value);
    } catch (e) {
      if (navigator.cookieEnabled) {
        cookieOperate.setItem(key, value, 0, '/', [window.location.hostname]);
      } else {
        window.esl = window.esl || {};
        window.esl[key] = value;
      }
    }
  }
};
export default {
  getItem: cookieOperate.getItem,
  removeItem: cookieOperate.removeItem,
  hasItem: cookieOperate.hasItem,
  keys: cookieOperate.keys,
  setItem: function (t, e, r, o, a, s) {
    for (var u = 0; u < a.length; u++) if (!/^(\.co\.|\.com\.|\.org\.|\.edu\.|\.net\.)/.test(a[u])) {
      cookieOperate.setItem(t, e, r, o, a[u], s);
      break;
    }
  },
  getFromSessionCookie: cookieOperate.getFromSessionCookie,
  setToSessionCookie: cookieOperate.setToSessionCookie
}
