function createRequestError(type, request, url, cause) {
  const error = new Error(`SynapAgent request ${type}: ${url}`);
  error.type = type;
  error.status = request ? request.status : 0;
  error.url = url;
  if (cause) error.cause = cause;
  return error;
}

function request(method, url, { headers, data, async = true, timeout = 15000 } = {}) {
  return new Promise((resolve, reject) => {
    const Request = window.XMLHttpRequest || window.ActiveXObject;
    if (!Request) {
      reject(createRequestError('unsupported', null, url));
      return;
    }

    const xhr = window.XMLHttpRequest
      ? new window.XMLHttpRequest()
      : new window.ActiveXObject('Microsoft.XMLHTTP');

    try {
      xhr.open(method, url, async !== false);
      if (async !== false && timeout > 0) xhr.timeout = timeout;
      Object.keys(headers || {}).forEach((key) => xhr.setRequestHeader(key, headers[key]));

      xhr.onload = function () {
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve(xhr.responseText);
        } else {
          reject(createRequestError('http', xhr, url));
        }
      };
      xhr.onerror = function () { reject(createRequestError('network', xhr, url)); };
      xhr.ontimeout = function () { reject(createRequestError('timeout', xhr, url)); };
      xhr.onabort = function () { reject(createRequestError('abort', xhr, url)); };
      xhr.send(data);

      // Synchronous XHR fires handlers before send() returns in supported browsers.
      if (async === false && xhr.readyState === 4) {
        if (xhr.status >= 200 && xhr.status < 300) resolve(xhr.responseText);
        else reject(createRequestError('http', xhr, url));
      }
    } catch (error) {
      reject(createRequestError('exception', xhr, url, error));
    }
  });
}

function requestSync(method, url, { headers, data } = {}) {
  if (!window.XMLHttpRequest && !window.ActiveXObject) {
    throw createRequestError('unsupported', null, url);
  }
  const xhr = window.XMLHttpRequest
    ? new window.XMLHttpRequest()
    : new window.ActiveXObject('Microsoft.XMLHTTP');
  try {
    xhr.open(method, url, false);
    Object.keys(headers || {}).forEach((key) => xhr.setRequestHeader(key, headers[key]));
    xhr.send(data);
  } catch (error) {
    throw createRequestError('exception', xhr, url, error);
  }
  if (xhr.status >= 200 && xhr.status < 300) return xhr.responseText;
  throw createRequestError('http', xhr, url);
}

function withCallbacks(promise, callback, error) {
  promise.then((data) => {
    if (typeof callback === 'function') callback(data);
  }).catch((reason) => {
    if (typeof error === 'function') error(reason);
  });
  return promise;
}

export default {
  request,
  requestSync,
  get(url, options) {
    return request('GET', url, options);
  },
  post(url, data, options = {}) {
    return request('POST', url, { ...options, data });
  },
  http_get(url, header, callback, error, async) {
    if (async === false) {
      try {
        const data = requestSync('GET', url, { headers: header });
        if (typeof callback === 'function') callback(data);
        return data;
      } catch (reason) {
        if (typeof error === 'function') error(reason);
        return undefined;
      }
    }
    return withCallbacks(request('GET', url, { headers: header, async }), callback, error);
  },
  http_post(url, header, data, callback, error, async) {
    if (async === false) {
      try {
        const response = requestSync('POST', url, { headers: header, data });
        if (typeof callback === 'function') callback(response);
        return response;
      } catch (reason) {
        if (typeof error === 'function') error(reason);
        return undefined;
      }
    }
    return withCallbacks(request('POST', url, { headers: header, data, async }), callback, error);
  }
};
