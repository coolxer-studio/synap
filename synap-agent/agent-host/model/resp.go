package model

type Resp struct {
	Status   int             `json:"status"`
	Config   ProbeCfg        `json:"config"`
	Agendas  [][]interface{} `json:"agendas"`
	Punishes [][]interface{} `json:"punishes"`
	Ack      *UploadAck      `json:"ack"`
	Risk     interface{}     `json:"risk"`
}

type UploadAck struct {
	BatchID             string         `json:"batchId"`
	AcceptedMessageIDs  []string       `json:"acceptedMessageIds"`
	DuplicateMessageIDs []string       `json:"duplicateMessageIds"`
	RejectedItems       []RejectedItem `json:"rejectedItems"`
}

type RejectedItem struct {
	MessageID string `json:"messageId"`
	Retryable bool   `json:"retryable"`
	Reason    string `json:"reason"`
}
