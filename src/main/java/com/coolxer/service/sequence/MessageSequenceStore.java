package com.coolxer.service.sequence;

import com.coolxer.model.fact.Common;

public interface MessageSequenceStore {
    SequenceObservation observe(Common common, long sequence);
}
