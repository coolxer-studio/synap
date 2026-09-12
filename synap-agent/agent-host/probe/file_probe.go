package probe

import (
	"os"
	"path/filepath"
	"regexp"

	"agent-host/util"
)

const (
	maxFieldBytes = 64 << 10
	maxListItems  = 256
)

var fileProbe = &FileProbe{}

type FileProbe struct{}

func (f *FileProbe) exist(files []string) []int {
	result := make([]int, len(files))
	for index, file := range files {
		if util.FileExists(file) || util.DirExists(file) {
			result[index] = 1
		}
	}
	return result
}

func (f *FileProbe) context(files []string) []string {
	result := make([]string, len(files))
	for index, file := range files {
		value, err := os.ReadFile(filepath.Clean(file))
		if err == nil {
			if len(value) > maxFieldBytes {
				value = value[:maxFieldBytes]
			}
			result[index] = string(value)
		}
	}
	return result
}

func (f *FileProbe) fileMd5(files []string) []string {
	result := make([]string, len(files))
	for index, file := range files {
		result[index], _ = util.FileMd5(file)
	}
	return result
}

func (f *FileProbe) fileList(config []string) [][]string {
	result := make([][]string, len(config))
	for index, entry := range config {
		path, expression, ok := splitPair(entry)
		if !ok {
			continue
		}
		compiled, err := regexp.Compile(expression)
		if err != nil {
			continue
		}
		_ = filepath.WalkDir(path, func(current string, item os.DirEntry, walkErr error) error {
			if walkErr != nil {
				return nil
			}
			if len(result[index]) >= maxListItems {
				return filepath.SkipAll
			}
			if current != path && compiled.MatchString(item.Name()) {
				result[index] = append(result[index], current)
			}
			return nil
		})
	}
	return result
}

func (f *FileProbe) access(files []string) []int {
	result := make([]int, len(files))
	for index, file := range files {
		handle, err := os.Open(filepath.Clean(file))
		if err == nil {
			result[index] = 1
			_ = handle.Close()
		}
	}
	return result
}

func (f *FileProbe) yara(config []string) []string {
	// YARA rules are intentionally disabled until the server defines a stable,
	// auditable rule format. Preserve positional compatibility without guessing.
	return make([]string, len(config))
}

func splitPair(value string) (string, string, bool) {
	for index := 0; index+1 < len(value); index++ {
		if value[index:index+2] == "::" {
			return value[:index], value[index+2:], true
		}
	}
	return "", "", false
}
