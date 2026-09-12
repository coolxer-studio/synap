import { http } from '@kit.NetworkKit';
import type {
  Transport,
  TransportRequest,
  TransportResponse
} from '../core/Models';
import { SynapError } from '../core/SynapError';

function contentType(headers: Object): string {
  const values: Record<string, Object> = headers as Record<string, Object>;
  const key: string | undefined = Object.keys(values)
    .find((name: string) => name.toLowerCase() === 'content-type');
  if (!key) return '';
  const raw: Object = values[key];
  return Array.isArray(raw) ? String(raw[0] || '') : String(raw || '');
}

export class HttpTransport implements Transport {
  async post(request: TransportRequest): Promise<TransportResponse> {
    const client: http.HttpRequest = http.createHttp();
    try {
      const response: http.HttpResponse = await client.request(request.url, {
        method: http.RequestMethod.POST,
        header: {
          'Content-Type': request.contentType,
          'Accept': request.accept,
          'Cache-Control': 'no-store'
        },
        extraData: request.body,
        connectTimeout: request.timeoutMs,
        readTimeout: request.timeoutMs,
        expectDataType: http.HttpDataType.STRING
      });
      return {
        status: response.responseCode,
        contentType: contentType(response.header),
        body: typeof response.result === 'string' ? response.result : String(response.result)
      };
    } catch (error) {
      throw new SynapError('SC_NETWORK_FAILED', 'Secure network request failed',
        0, undefined, error as Object);
    } finally {
      client.destroy();
    }
  }
}

