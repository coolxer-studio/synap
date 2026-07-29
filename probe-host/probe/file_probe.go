package probe

import (
	"log"
	"probe-host/util"
	"regexp"
	"strings"
)

var fileProbe = &FileProbe{}

type FileProbe struct{}

func (f *FileProbe) exist(files []string) []int {
	var existFiles []int
	for _, file := range files {
		if util.FileExists(file) {
			existFiles = append(existFiles, 1)
		} else {
			existFiles = append(existFiles, 0)
		}
	}
	return existFiles
}

func (f *FileProbe) context(files []string) []string {
	var content []string
	for _, file := range files {
		c, err := util.FileContent(file)
		if err != nil {
			content = append(content, "")
		} else {
			content = append(content, c)
		}
	}
	return content
}

func (f *FileProbe) fileMd5(files []string) []string {
	var size []string
	for _, file := range files {
		s, err := util.FileMd5(file)
		if err != nil {
			size = append(size, "")
		} else {
			size = append(size, s)
		}
	}
	return size
}

func (f *FileProbe) fileList(cfg []string) [][]string {
	var res [][]string
	for _, c := range cfg {
		var list []string
		segs := strings.Split(c, "::")
		if len(segs) < 2 {
			continue
		}
		path := segs[0]
		regexStr := segs[1]
		filesNameList, err := util.ListFiles(path, true)
		if err != nil {
			continue
		}
		re, err := regexp.Compile(regexStr)
		if err != nil {
			log.Fatal(err)
		}
		for _, fileName := range filesNameList {
			if re.MatchString(fileName) {
				list = append(list, fileName)
			}
		}
		res = append(res, list)
	}
	return res
}

// TODO 访问权限
func (f *FileProbe) access(files []string) []string {
	return nil
}
