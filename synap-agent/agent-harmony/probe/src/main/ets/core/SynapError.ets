export class SynapError extends Error {
  readonly code: string;
  readonly httpStatus: number;
  readonly traceId?: string;
  readonly cause?: Object;

  constructor(code: string, message?: string, httpStatus: number = 0,
    traceId?: string, cause?: Object) {
    super(message || code);
    this.name = 'SynapError';
    this.code = code;
    this.httpStatus = httpStatus;
    this.traceId = traceId;
    this.cause = cause;
  }
}

export function asSynapError(error: Object, fallbackCode: string,
  fallbackMessage: string): SynapError {
  if (error instanceof SynapError) {
    return error;
  }
  return new SynapError(fallbackCode, fallbackMessage, 0, undefined, error);
}

