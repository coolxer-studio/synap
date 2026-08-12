package probe

import "testing"

func TestSplitCommandRejectsShellSyntax(t *testing.T) {
	arguments, ok := splitCommand(`tool --name "hello world"`)
	if !ok || len(arguments) != 3 || arguments[2] != "hello world" {
		t.Fatalf("arguments=%v ok=%v", arguments, ok)
	}
	for _, value := range []string{"echo ok | sh", "echo $(id)", "echo ok; id"} {
		if _, ok := splitCommand(value); ok {
			t.Fatalf("accepted shell syntax: %s", value)
		}
	}
}

func TestProcessMatchesArePositional(t *testing.T) {
	result := (&RuntimeProbe{}).matchProcess([]string{"[", "^definitely-not-a-real-process-name$"})
	if len(result) != 2 || result[0] != "" || result[1] != "" {
		t.Fatalf("result=%v", result)
	}
}
