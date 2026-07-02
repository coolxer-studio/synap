export default (function () {
  function Ajax() {
  }

  var _callback, _error;

  var request = window.XMLHttpRequest ? new XMLHttpRequest() :
    window.ActiveXObject ? new ActiveXObject("Microsoft.XMLHTTP") : undefined;
  // request.timeout = 61000;
  request.onload = function () {
    var status = request.status;
    if (status >= 200 && status < 300) {
      if (typeof _callback === "function") {
        _callback.call(this, request.responseText);
      }
    } else {
      if (typeof _error === "function") {
        _error.call(this, status);
      }
    }
  };
  // request.ontimeout=function(){
  //   if (typeof _error === "function") {
  //     console.log('超时了：', request);
  //     _error.call(this, request.status);
  //   }
  // };
  request.onerror = function () {
    if (typeof _error === "function") {
      console.error('报错了：', request);
      _error.call(this, request.status);
    }
  };

  Ajax.prototype.http_get = function (url, header, callback, error, async) {
    if (request) {
      _callback = callback;
      _error = error;
      if (async !== undefined) {
        request.open('GET', url, async);
      } else {
        request.open('GET', url, true);
      }
      if (header) {
        for (var key in header) {
          request.setRequestHeader(key, header[key]);
        }
      }
      request.send();
    }
  }, Ajax.prototype.http_post = function (url, header, data, callback, error, async) {
    if (request) {
      _callback = callback;
      _error = error;
      if (async === false) {
        request.open('POST', url, async);
          if (header) {
            for (var key in header) {
              request.setRequestHeader(key, header[key]);
            }
          }
          request.send(data);

      } else {
        request.open('POST', url, true);
        if (header) {
          for (var key in header) {
            request.setRequestHeader(key, header[key]);
          }
        }
        request.send(data);
      }

    }
  };

  return new Ajax();
})();
