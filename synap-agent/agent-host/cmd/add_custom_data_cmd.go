package cmd

import (
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"agent-host/probe"
	"agent-host/storage"
	"github.com/spf13/cobra"
)

var addUserDataCmd = &cobra.Command{Use: "data", Short: "Queue custom user data", RunE: addCustomDataCmd}
var customData, userID string

func init() {
	addUserDataCmd.Flags().StringVarP(&userID, "userId", "i", "", "custom user id")
	addUserDataCmd.Flags().StringVarP(&customData, "userData", "d", "", "custom JSON value")
	_ = addUserDataCmd.MarkFlagRequired("userData")
	_ = addUserDataCmd.MarkFlagRequired("userId")
}

func addCustomDataCmd(_ *cobra.Command, _ []string) error {
	spool, err := prepare()
	if err != nil {
		return err
	}
	session, err := spool.LoadSession()
	if err != nil || !storage.SessionActive(options.DataDir, session) {
		return errors.New("探针未运行，请先启动 x-probe")
	}
	var value interface{}
	if err := json.Unmarshal([]byte(customData), &value); err != nil {
		return fmt.Errorf("invalid userData JSON: %w", err)
	}
	fact := probe.UserData{Type: "UserData", UserId: userID, Data: value, BuildDate: time.Now().Format("2006-01-02 15:04:05")}
	encoded, _ := json.Marshal(fact)
	if len(encoded) > maxEnvelopeBytes {
		return errors.New("custom data exceeds 256 KiB request limit")
	}
	if err := spool.EnqueueFact(session, fact); err != nil {
		return err
	}
	fmt.Println("已加入可靠上传队列")
	return nil
}
