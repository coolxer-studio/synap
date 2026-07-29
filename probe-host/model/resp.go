package model

type Resp struct {
	Status int      `json:"status"`
	Config ProbeCfg `json:"config"`
}
