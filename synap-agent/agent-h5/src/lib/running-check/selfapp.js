export default {
  // 获取域名
  domain: function () {
    let domain = ''
    try {
      // 3种判断是否被嵌入iframe中
      if ((self.frameElement && self.frameElement.tagName == "IFRAME") || (window.frames.length != parent.frames.length) || (self != top)) {
        var url = '';
        try {
          url = window.top.document.referrer;
        } catch (M) {
          if (window.parent) {
            try {
              url = window.parent.document.referrer;
            } catch (L) {
              url = '';
            }
          }
        }
        if (url) {
          var urlArr = url.split('/'); //以“/”进行分割
          if (urlArr[2]) {
            domain = urlArr[0] + '//' + urlArr[1] + urlArr[2];
          } else {
            domain = ''; //如果url不正确就取空
          }
        } else {
          domain = ''
        }
      } else {
        domain = top.location.origin
      }
    } catch {
      domain = ''
    }
    return domain
  }
}