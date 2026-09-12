package main

import (
	"fmt"
	"testing"
	"time"
)

func TestWechatMetadataGeneration(t *testing.T) {
	common := make([]interface{}, 24)
	common[1] = "old-guid"
	common[2] = float64(1)
	common[3] = "1.0.0"
	common[4] = float64(1)
	common[6] = "wx-test"
	common[8] = "wechat"
	start := map[string]interface{}{"common": append([]interface{}{}, common...)}
	upload := map[string]interface{}{
		"common": append([]interface{}{}, common...),
		"data":   []interface{}{map[string]interface{}{"type": "RuntimeData"}},
	}
	startID := time.Now().UnixMilli()
	if err := modifyStartData(start, "new-guid", startID, "now", "wechat"); err != nil {
		t.Fatal(err)
	}
	if err := modifyUploadData(upload, "new-guid", startID, "now", "wechat"); err != nil {
		t.Fatal(err)
	}
	startMeta, ok := start["meta"].(map[string]interface{})
	if !ok || startMeta["protocolVersion"] != 1 {
		t.Fatalf("missing start metadata: %#v", start["meta"])
	}
	uploadMeta, ok := upload["meta"].(map[string]interface{})
	if !ok || uploadMeta["sessionId"] != stringValue(startID) || uploadMeta["batchId"] == "" {
		t.Fatalf("invalid upload metadata: %#v", upload["meta"])
	}
	items := upload["data"].([]interface{})
	item := items[0].(map[string]interface{})
	if item["messageId"] == "" || item["sequence"] != 1 || item["observedAt"] == nil {
		t.Fatalf("invalid message metadata: %#v", item)
	}
}

func TestHeaderCodeUsesPlatformAppAndSDKVersion(t *testing.T) {
	common := make([]interface{}, 24)
	common[1] = "a-b-c-d-e"
	common[3] = "1.0.0"
	common[4] = 1
	common[8] = "wechat"
	value, err := buildHeaderCode(common)
	if err != nil {
		t.Fatal(err)
	}
	if value != "a-b-c-d-e-wechat-1-1.0.0" {
		t.Fatalf("unexpected header: %s", value)
	}
}

func stringValue(value interface{}) string {
	return fmt.Sprint(value)
}
