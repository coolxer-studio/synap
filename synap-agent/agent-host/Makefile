.PHONY: build clean fmt test help
build:
	sh ./build.sh

fmt:
	gofmt -w $$(find . -name '*.go' -not -path './vendor/*')

test:
	go test ./...

help:
	@echo "make fmt 可以帮助格式化代码"

clean:
	rm -f x-probe x-probe.exe
