package probe

import "testing"

func TestPortResultIsPositional(t *testing.T) {
	result := (&NetProbe{}).port([]string{"0", "invalid"})
	if len(result) != 2 {
		t.Fatalf("len=%d", len(result))
	}
}

func TestMatchedValuesArePositional(t *testing.T) {
	result := matchValues([]string{"^missing$", "^present$"}, []string{"present"})
	if len(result) != 2 || result[0] != "" || result[1] != "present" {
		t.Fatalf("result=%v", result)
	}
}
