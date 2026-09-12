import { utf8 } from './Encoding';
import type { AgentMessage, UploadBatch, UploadMeta } from './Models';

export interface BatchResult {
  batches: UploadBatch[];
  oversized: AgentMessage[];
}

function wireMessage(message: AgentMessage): Record<string, Object> {
  return {
    type: message.type,
    messageId: message.messageId,
    sequence: message.sequence,
    observedAt: message.observedAt,
    ...message.payload
  };
}

function body(common: Object[], meta: UploadMeta, messages: AgentMessage[]): string {
  return JSON.stringify({ common, meta, data: messages.map(wireMessage) });
}

export function splitUploadBatches(common: Object[], messages: AgentMessage[], batchSize: number,
  maxBodyBytes: number, metaFactory: (index: number) => UploadMeta): BatchResult {
  const batches: UploadBatch[] = [];
  const oversized: AgentMessage[] = [];
  let current: AgentMessage[] = [];
  let meta: UploadMeta | undefined;
  let lockedBatchId: boolean = false;

  const startBatch = (message: AgentMessage): void => {
    meta = metaFactory(batches.length);
    lockedBatchId = message.batchId !== undefined;
    if (message.batchId) meta.batchId = message.batchId;
  };

  const finishBatch = (): void => {
    if (!meta || current.length === 0) return;
    current.forEach((message: AgentMessage) => message.batchId = meta?.batchId);
    batches.push({ batchId: meta.batchId, body: body(common, meta, current), messages: current });
    current = [];
    meta = undefined;
    lockedBatchId = false;
  };

  messages.forEach((message: AgentMessage) => {
    if (!meta) startBatch(message);
    if (current.length > 0 && meta &&
      ((message.batchId !== undefined && message.batchId !== meta.batchId) ||
        (message.batchId === undefined && lockedBatchId))) {
      finishBatch();
      startBatch(message);
    }
    if (!meta) return;
    const singleBody: string = body(common, meta, [message]);
    if (utf8(singleBody).length > maxBodyBytes) {
      oversized.push(message);
      if (current.length === 0) {
        meta = undefined;
        lockedBatchId = false;
      }
      return;
    }
    const candidate: AgentMessage[] = current.concat([message]);
    const candidateBody: string = body(common, meta, candidate);
    if (current.length > 0 &&
      ((!lockedBatchId && candidate.length > batchSize) ||
        utf8(candidateBody).length > maxBodyBytes)) {
      finishBatch();
      startBatch(message);
      if (!meta) return;
      current = [message];
      return;
    }
    current = candidate;
  });

  finishBatch();
  return { batches, oversized };
}

export function retryableMessages(batch: UploadBatch, ackBatchId: string,
  acceptedMessageIds: string[], duplicateMessageIds: string[],
  rejectedItems: Array<{ messageId: string, retryable: boolean }>): AgentMessage[] {
  if (ackBatchId !== batch.batchId) {
    throw new Error('upload ACK batchId does not match request');
  }
  const expected: Set<string> = new Set<string>(
    batch.messages.map((message: AgentMessage) => message.messageId));
  const completed: Set<string> = new Set<string>(acceptedMessageIds.concat(duplicateMessageIds));
  const retryable: Set<string> = new Set<string>();
  const permanent: Set<string> = new Set<string>();
  rejectedItems.forEach((item: { messageId: string, retryable: boolean }) => {
    if (item.retryable) retryable.add(item.messageId);
    else permanent.add(item.messageId);
  });
  const classified: string[] = acceptedMessageIds.concat(duplicateMessageIds)
    .concat(rejectedItems.map((item: { messageId: string, retryable: boolean }) => item.messageId));
  const seen: Set<string> = new Set<string>();
  classified.forEach((messageId: string) => {
    if (!expected.has(messageId) || seen.has(messageId)) {
      throw new Error('upload ACK contains an unknown or duplicate messageId');
    }
    seen.add(messageId);
  });
  return batch.messages.filter((message: AgentMessage) => retryable.has(message.messageId) ||
    (!completed.has(message.messageId) && !permanent.has(message.messageId)));
}
