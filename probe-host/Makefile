.PHONY: all build clean run check cover lint docker help
.PHONY: clean fmt help build
build:
	sh ./build.sh

fmt:
	@go fmt ./*.go
	@${GOPATH}/bin/goimports -l -w ./*.go

help:
	@echo "make fmt 可以帮助格式化代码"

clean:
	rm x-probe