package audit

import "regexp"

var secretPatterns = []*regexp.Regexp{
	regexp.MustCompile(`(?i)("?(?:password|passwd|pwd|secret|token|authorization|api[_-]?key)"?\s*[=:]\s*"?)[^\s,;"']+`),
	regexp.MustCompile(`(?i)(bearer\s+)[a-z0-9._~+/=-]+`),
}

func redact(value string) string {
	for _, expression := range secretPatterns {
		value = expression.ReplaceAllString(value, `${1}[REDACTED]`)
	}
	return value
}
