// Agent Emulator - 模拟设备数据发送和数据加密工具
// 支持两种运行模式：数据加密模式和数据发送模式
package main

import (
	"bytes"
	"context"
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"time"

	"github.com/google/uuid"
)

// 全局配置变量
var (
	key                         string               // AES 加密密钥，优先从 AGENT_EMULATOR_KEY 环境变量读取
	dataDirectory               = "./data"           // 原始数据目录
	dataEncrypDirectory         = "./data_encryp"    // 加密后的数据目录
	dataEncrypDirectoryInDocker = "/usr/local/data/" // Docker 容器内的数据目录
)

// init 函数在程序启动时初始化密钥
func init() {
	key = os.Getenv("AGENT_EMULATOR_KEY")
	// 如果环境变量未设置，使用默认密钥
	if key == "" {
		key = "16385639" + "xxxxxx" + "jhdstc" + "aabbccddeeff"
	}
}

// encrypt 使用 AES-CFB 模式加密文本
// 参数 text: 待加密的明文字符串
// 参数 key: AES 密钥（16/24/32字节）
// 返回: base64 编码的密文
func encrypt(text string, key []byte) string {
	block, err := aes.NewCipher(key)
	if err != nil {
		log.Fatal(err)
	}

	plaintext := []byte(text)

	// 密文结构: IV(16字节) + 加密数据
	ciphertext := make([]byte, aes.BlockSize+len(plaintext))
	iv := ciphertext[:aes.BlockSize]
	// 生成随机 IV
	if _, err := io.ReadFull(rand.Reader, iv); err != nil {
		log.Fatal(err)
	}

	// 使用 CFB 模式加密
	cfb := cipher.NewCFBEncrypter(block, iv)
	cfb.XORKeyStream(ciphertext[aes.BlockSize:], plaintext)

	return base64.StdEncoding.EncodeToString(ciphertext)
}

// decrypt 使用 AES-CFB 模式解密密文
// 参数 ciphertext: base64 编码的密文
// 参数 key: AES 密钥
// 返回: 解密后的明文字符串
func decrypt(ciphertext string, key []byte) string {
	ciphertextBytes, err := base64.StdEncoding.DecodeString(ciphertext)
	if err != nil {
		log.Fatal(err)
	}

	block, err := aes.NewCipher(key)
	if err != nil {
		log.Fatal(err)
	}

	// 检查密文长度是否至少包含 IV
	if len(ciphertextBytes) < aes.BlockSize {
		log.Fatal("ciphertext too short")
	}

	// 分离 IV 和加密数据
	iv := ciphertextBytes[:aes.BlockSize]
	ciphertextBytes = ciphertextBytes[aes.BlockSize:]

	// 使用 CFB 模式解密
	cfb := cipher.NewCFBDecrypter(block, iv)
	cfb.XORKeyStream(ciphertextBytes, ciphertextBytes)

	return string(ciphertextBytes)
}

// mkdir 递归创建目录，失败则 panic
func mkdir(dirPath string) {
	err := os.MkdirAll(dirPath, os.ModePerm)
	if err != nil {
		panic(err)
	}
}

// readAndEncryptFile 读取文件并加密后写入到输出路径
// 参数 inputPath: 输入文件路径
// 参数 outputPath: 输出文件路径
func readAndEncryptFile(inputPath, outputPath string) {
	data, err := os.ReadFile(inputPath)
	if err != nil {
		fmt.Println("Error read file:", err)
		return
	}
	encryptString := encrypt(string(data), []byte(key))
	err = os.WriteFile(outputPath, []byte(encryptString), 0644)
	if err != nil {
		fmt.Println("Error write file:", err)
	}
}

// performDataEncryption 执行数据加密操作
// 遍历 dataDirectory 目录，加密其中的 start 和 upload 文件
func performDataEncryption() {
	if _, err := os.Stat(dataDirectory); os.IsNotExist(err) {
		fmt.Printf("目录 %s 不存在\n", dataDirectory)
		return
	}

	entries, err := os.ReadDir(dataDirectory)
	if err != nil {
		log.Fatalf("Failed to read data directory: %v", err)
	}

	// 遍历每个数据类型目录
	for _, entry := range entries {
		if !entry.IsDir() {
			continue
		}

		// 创建输出目录
		mkdir(filepath.Join(dataEncrypDirectory, entry.Name()))

		// 加密 start 文件
		startInputFilePath := filepath.Join(dataDirectory, entry.Name(), "start")
		startOutputFilePath := filepath.Join(dataEncrypDirectory, entry.Name(), "start")
		readAndEncryptFile(startInputFilePath, startOutputFilePath)

		// 加密 upload 文件
		uploadInputFilePath := filepath.Join(dataDirectory, entry.Name(), "upload")
		uploadOutputFilePath := filepath.Join(dataEncrypDirectory, entry.Name(), "upload")
		readAndEncryptFile(uploadInputFilePath, uploadOutputFilePath)
	}
}

// readJSONFile 读取并解析 JSON 文件
// 参数 filePath: 文件路径
// 参数 isEncryp: 文件是否已加密
// 返回: 解析后的 map 和错误信息
func readJSONFile(filePath string, isEncryp bool) (map[string]interface{}, error) {
	data, err := os.ReadFile(filePath)
	if err != nil {
		return nil, fmt.Errorf("failed to read JSON file: %v", err)
	}
	// 如果是加密文件，先解密
	if isEncryp {
		decryptString := decrypt(string(data), []byte(key))
		data = []byte(decryptString)
	}
	var result map[string]interface{}
	err = json.Unmarshal(data, &result)
	if err != nil {
		return nil, fmt.Errorf("failed to parse JSON: %v", err)
	}
	return result, nil
}

// getAsSlice 安全地从 map 中获取 slice 类型的值
// 参数 data: 源 map
// 参数 key: 键名
// 返回: slice 和是否成功
func getAsSlice(data map[string]interface{}, key string) ([]interface{}, bool) {
	val, ok := data[key]
	if !ok {
		return nil, false
	}
	slice, ok := val.([]interface{})
	return slice, ok
}

// getAsMap 安全地将 interface{} 转换为 map[string]interface{}
// 参数 data: 源数据
// 返回: map 和是否成功
func getAsMap(data interface{}) (map[string]interface{}, bool) {
	m, ok := data.(map[string]interface{})
	return m, ok
}

// modifyStartData 修改 start 数据中的通用字段
// 参数 startData: 待修改的数据
// 参数 randomUUID: 新的设备 UUID
// 参数 startID: 会话 ID
// 参数 currentTimeFormatted: 当前时间字符串
// 返回: 错误信息
func modifyStartData(startData map[string]interface{}, randomUUID string, startID int64, currentTimeFormatted string) error {
	startDataCommon, ok := getAsSlice(startData, "common")
	if !ok {
		return fmt.Errorf("startData common is not a slice")
	}
	if len(startDataCommon) < 24 {
		return fmt.Errorf("startData common slice too short")
	}
	// 更新 UUID、startID 和时间
	startDataCommon[1] = randomUUID
	startDataCommon[2] = startID
	startDataCommon[23] = currentTimeFormatted
	startData["common"] = startDataCommon
	return nil
}

// modifyUploadData 修改 upload 数据中的通用字段和设备数据
// 参数 uploadData: 待修改的数据
// 参数 randomUUID: 新的设备 UUID
// 参数 startID: 会话 ID
// 参数 currentTimeFormatted: 当前时间字符串
// 参数 dataTypeName: 数据类型名称（ios/android/h5/wechat/host）
// 返回: 错误信息
func modifyUploadData(uploadData map[string]interface{}, randomUUID string, startID int64, currentTimeFormatted string, dataTypeName string) error {
	uploadDataCommon, ok := getAsSlice(uploadData, "common")
	if !ok {
		return fmt.Errorf("uploadData common is not a slice")
	}
	if len(uploadDataCommon) < 24 {
		return fmt.Errorf("uploadData common slice too short")
	}
	// 更新通用字段
	uploadDataCommon[1] = randomUUID
	uploadDataCommon[2] = startID
	uploadDataCommon[23] = currentTimeFormatted
	uploadData["common"] = uploadDataCommon

	// 更新数据字段
	dataSlice, ok := getAsSlice(uploadData, "data")
	if !ok {
		return fmt.Errorf("uploadData data is not a slice")
	}

	for _, item := range dataSlice {
		itemMap, ok := getAsMap(item)
		if !ok {
			continue
		}
		itemMap["buildDate"] = currentTimeFormatted
		// 如果是设备数据，更新对应的 UUID 字段
		if itemMap["type"] == "DeviceData" {
			uuidSlice, ok := getAsSlice(itemMap, "uuid")
			if !ok {
				continue
			}
			var uuidIndex int
			// 根据数据类型确定 UUID 在数组中的位置
			switch dataTypeName {
			case "ios":
				uuidIndex = 3
			case "android":
				uuidIndex = 15
			case "h5":
				uuidIndex = 0
			case "wechat":
				uuidIndex = 0
			case "host":
				uuidIndex = 3
			default:
				continue
			}
			if len(uuidSlice) > uuidIndex {
				uuidSlice[uuidIndex] = randomUUID
			}
		}
	}
	return nil
}

// sendDataToAPI 发送数据到服务器 API
// 参数 host: 服务器地址
// 参数 data: 待发送的数据
// 参数 urlIndex: API 路径索引（1 为 start，2 为 upload）
// 参数 printLog: 是否打印详细日志
func sendDataToAPI(host string, data map[string]interface{}, urlIndex int, printLog bool) {
	url := fmt.Sprintf("http://%s:11099/1/%d", host, urlIndex)
	common, ok := getAsSlice(data, "common")
	if !ok {
		log.Printf("common field is not a slice")
		return
	}
	if len(common) < 4 {
		log.Printf("common slice too short")
		return
	}
	// 构建请求头
	headers := map[string]string{
		"Content-Type": "application/json",
		"code":         fmt.Sprintf("%s-%s-1-230501", common[1], common[3]),
	}
	body, err := json.Marshal(data)
	if err != nil {
		log.Printf("Failed to encode JSON: %v", err)
		return
	}

	// 打印请求日志
	if printLog {
		log.Printf("URL: %s", url)
		log.Printf("Headers: %v", headers)
		log.Printf("Request: %s", string(body))
	}

	// 设置 5 秒超时
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		log.Printf("Failed to create request: %v", err)
		return
	}
	for key, value := range headers {
		req.Header.Set(key, value)
	}

	// 发送请求
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		log.Printf("Failed to send request: %v", err)
		return
	}
	defer resp.Body.Close()

	// 读取响应
	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		log.Printf("Failed to read response body: %v", err)
		return
	}

	// 打印响应日志
	if printLog {
		log.Printf("Response: %s", string(respBody))
	}
}

// performDataSending 执行数据发送操作
// 参数 host: 服务器地址
// 参数 deviceCount: 模拟的设备数量
// 参数 printLog: 是否打印详细日志
func performDataSending(host string, deviceCount int, printLog bool) {
	fmt.Println("Host:", host)
	fmt.Println("Device count:", deviceCount)

	// 获取当前时间和会话 ID
	currentTime := time.Now()
	currentTimeFormatted := currentTime.Format("2006-01-02 15:04:05")
	startID := currentTime.UnixMicro()

	currentDataDirectory := dataDirectory
	isEncryp := false

	// 检查数据目录，按优先级查找
	if _, err := os.Stat(currentDataDirectory); os.IsNotExist(err) {
		fmt.Printf("目录 %s 不存在\n", currentDataDirectory)
		currentDataDirectory = dataEncrypDirectory
		if _, err := os.Stat(currentDataDirectory); os.IsNotExist(err) {
			currentDataDirectory = dataEncrypDirectoryInDocker
		}
		isEncryp = true
	}

	entries, err := os.ReadDir(currentDataDirectory)
	if err != nil {
		log.Fatalf("Failed to read data directory: %v", err)
	}

	// 遍历每个数据类型目录
	for _, entry := range entries {
		if !entry.IsDir() {
			continue
		}

		// 读取 start 和 upload 文件
		startFilePath := filepath.Join(currentDataDirectory, entry.Name(), "start")
		uploadFilePath := filepath.Join(currentDataDirectory, entry.Name(), "upload")
		startData, err := readJSONFile(startFilePath, isEncryp)
		if err != nil {
			log.Printf("Failed to read start message file: %v", startFilePath, err)
			continue
		}
		uploadData, err := readJSONFile(uploadFilePath, isEncryp)
		if err != nil {
			log.Printf("Failed to read upload message file: %v", uploadFilePath, err)
			continue
		}

		// 模拟多个设备发送数据
		for i := 0; i < deviceCount; i++ {
			randomUUID := uuid.NewString()

			// 修改 start 数据
			if err := modifyStartData(startData, randomUUID, startID, currentTimeFormatted); err != nil {
				log.Printf("Failed to modify start data: %v", err)
				continue
			}

			// 修改 upload 数据
			if err := modifyUploadData(uploadData, randomUUID, startID, currentTimeFormatted, entry.Name()); err != nil {
				log.Printf("Failed to modify upload data: %v", err)
				continue
			}

			// 发送 start 和 upload 数据
			for urlIndex, msg := range []map[string]interface{}{startData, uploadData} {
				sendDataToAPI(host, msg, urlIndex+1, printLog)
			}
		}
	}
}

// main 程序入口
// 无参数时执行数据加密，有参数时执行数据发送
func main() {
	log.SetFlags(log.Lshortfile | log.LstdFlags)

	// 定义命令行参数
	host := flag.String("host", "localhost", "The hostname or IP address of the server")
	deviceCount := flag.Int("device_count", 1, "The number of devices to connect to")
	printLog := flag.Bool("print_log", false, "Print log")
	flag.Parse()

	// 根据是否有参数决定运行模式
	if flag.NFlag() == 0 {
		performDataEncryption()
	} else {
		performDataSending(*host, *deviceCount, *printLog)
	}
}
