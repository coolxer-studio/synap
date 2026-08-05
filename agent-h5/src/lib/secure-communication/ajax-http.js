function createRequestError(type, request, url, cause) {
  const error = new Error(`SynapAgent request ${type}: ${url}`);
  error.type = type;
  error.status = request ? request.status : 0;
  error.url = url;
  if (cause) error.cause = cause;
  return error;
}

function request(method, url, { headers, data, timeout = 15000 } = {}) {
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
      xhr.open(method, url, true);
      if (timeout > 0) xhr.timeout = timeout;
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

    } catch (error) {
      reject(createRequestError('exception', xhr, url, error));
    }
  });
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
  get(url, options) {
    return request('GET', url, options);
  },
  post(url, data, options = {}) {
    return request('POST', url, { ...options, data });
  },
  http_get(url, header, callback, error) {
    return withCallbacks(request('GET', url, { headers: header }), callback, error);
  },
  http_post(url, header, data, callback, error) {
    return withCallbacks(request('POST', url, { headers: header, data }), callback, error);
  }
};
