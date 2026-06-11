export default {
  getLocation: function () {
    // 检查浏览器是否支持 geolocation
    if ("geolocation" in navigator) {
      // 获取地理位置信息
      navigator.geolocation.getCurrentPosition(function (position) {
        const latitude = position.coords.latitude; // 纬度
        const longitude = position.coords.longitude; // 经度
      }, function (error) {
        console.error("Error getting geolocation:", error);
      });
    } else {
      console.warn("Geolocation is not supported by this browser.");
    }
  },
  // 获取GPS坐标
  getLbs: function () {
    const lbs = { "0": '', "1": '' };
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(function (position) {
        lbs.lat = position.coords.latitude.toString();
        lbs.lon = position.coords.longitude.toString();
      });
      return lbs;
    }
  }

}