// 触发应用保护
export default function appProtect(dnf, eventListener) {
  var INSERT_APP_ID = dnf.split(";");
  //校验function
  function checkValid(appId) {
    var check_succ = [];
    for(var j = 0, len = INSERT_APP_ID.length; j < len; j++) {
      if(appId === INSERT_APP_ID[j]) {
        check_succ.push(true);
      } else {
        check_succ.push(false)
      }
    }

    if(!check_succ.every(function(item){return item})) {
      var event = {
        type: "appbind"
      };
      eventListener(event)
    }
  }
  //获取appId function
  function checkBindPackage() {
    let InsertAppId;
    try{
      if(navigator){
        if(/android/i.test(navigator.userAgent)) {
          // 调用Android
          InsertAppId = andriodFun.getAppId('');
          checkValid(InsertAppId);
        } else if(/ipad|iphone|iPod|iOS|mac/i.test(navigator.userAgent)) {
          // 调用IOS
          getAppId(appCallback);
        }
        InsertAppId = undefined;
      }
    }catch(e){
      checkValid('');
    }
  }
  //定义一个回调函数用于ios回调
  function appCallback(appId) {
    checkValid(appId);
  }
  //执行程序
  if (dnf) {
    checkBindPackage();
  }
}
