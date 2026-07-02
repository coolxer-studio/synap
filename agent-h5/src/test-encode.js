// 编码函数
function encodeString(str) {
  let encodedStr = '';
  for (let char of str) {
    encodedStr += String.fromCharCode(char.charCodeAt(0) + 1);
  }
  return encodedStr;
}

// 解码函数
function decodeString(str) {
  let decodedStr = '';
  for (let char of str) {
    decodedStr += String.fromCharCode(char.charCodeAt(0) - 1);
  }
  return decodedStr;
}

// 执行测试
let array = ['http://127.0.0.1:11099','/sc/h5/attach/start', '/sc/h5/attach/upload', '/sc/h5/cross/info', '/sc/h5/cross/rating', '/sc/h5/cross/label_start'];
for (let i = 0; i < array.length; i++) {
  var original = array[i];
  var encoded = encodeString(original);
  console.log("Encoded: ", encoded);
  var decoded = decodeString(encoded);
  console.log("Decoded: ", decoded);
}
