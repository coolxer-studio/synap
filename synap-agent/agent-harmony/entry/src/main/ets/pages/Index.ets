import type { common } from '@kit.AbilityKit';
import { SynapAgent } from '@coolxer/synap-agent-harmony';
import type { EnrollmentTokenProvider, SynapError } from '@coolxer/synap-agent-harmony';
import { DemoRuntime } from '../DemoRuntime';

class DemoTokenProvider implements EnrollmentTokenProvider {
  private readonly token: string;

  constructor(token: string) {
    this.token = token;
  }

  async getToken(): Promise<string | undefined> {
    return this.token.trim().length > 0 ? this.token.trim() : undefined;
  }
}

@Entry
@Component
struct Index {
  @State private baseUrl: string = 'http://127.0.0.1:11099';
  @State private appId: string = 'synap-harmony-demo';
  @State private keyId: string = 'server-key-2026';
  @State private trustAnchor: string = '';
  @State private enrollmentToken: string = '';
  @State private status: string = 'idle';
  @State private output: string = 'Configure a Harmony-enabled mock server, then initialize.';
  private agent?: SynapAgent;
  private tokenProvider?: DemoTokenProvider;

  aboutToDisappear(): void {
    this.agent?.stop({ flush: true }).catch(() => {});
  }

  private async initialize(): Promise<void> {
    const anchors: Record<string, string> = {};
    anchors[this.keyId.trim()] = this.trustAnchor.trim();
    try {
      if (!this.tokenProvider) this.tokenProvider = new DemoTokenProvider(this.enrollmentToken);
      this.agent = await SynapAgent.initialize(getContext(this) as common.UIAbilityContext, {
        baseUrl: this.baseUrl,
        appId: this.appId,
        serverTrustAnchors: anchors,
        enrollmentTokenProvider: this.tokenProvider,
        allowInsecureHttp: true,
        collectionProfile: 'baseline'
      });
      DemoRuntime.agent = this.agent;
      this.status = 'initialized';
      this.output = JSON.stringify(this.agent.getMetrics(), null, 2);
    } catch (error) {
      this.showError(error as Object);
    }
  }

  private async run(label: string, operation: () => Promise<Object>): Promise<void> {
    if (!this.agent) {
      this.output = 'Initialize the SDK first.';
      return;
    }
    try {
      const result: Object = await operation();
      this.status = this.agent.getMetrics().status;
      this.output = `${label}\n${JSON.stringify(result, null, 2)}`;
    } catch (error) {
      this.showError(error as Object);
    }
  }

  private showError(error: Object): void {
    const mapped: SynapError = error as SynapError;
    this.status = 'error';
    this.output = `${mapped.code || 'ERROR'}: ${mapped.message || String(error)}`;
  }

  build() {
    Scroll() {
      Column({ space: 12 }) {
        Text('Synap HarmonyOS Probe')
          .fontSize(28)
          .fontWeight(FontWeight.Bold)
          .fontColor('#E2E8F0')
          .width('100%')

        Text(`Status: ${this.status}`)
          .fontSize(14)
          .fontColor('#7DD3FC')
          .width('100%')

        TextInput({ text: this.baseUrl, placeholder: 'Base URL' })
          .onChange((value: string) => this.baseUrl = value)
        TextInput({ text: this.appId, placeholder: 'App ID' })
          .onChange((value: string) => this.appId = value)
        TextInput({ text: this.keyId, placeholder: 'Server key ID' })
          .onChange((value: string) => this.keyId = value)
        TextArea({ text: this.trustAnchor, placeholder: 'P-256 SPKI Base64URL trust anchor' })
          .height(80)
          .onChange((value: string) => this.trustAnchor = value)
        TextInput({ text: this.enrollmentToken, placeholder: 'One-time enrollment token (memory only)' })
          .type(InputType.Password)
          .onChange((value: string) => this.enrollmentToken = value)

        Button('Initialize').width('100%').onClick(() => this.initialize())
        Row({ space: 8 }) {
          Button('Start').layoutWeight(1).onClick(() => this.run('start', async () => {
            await this.agent!.start();
            return this.agent!.getMetrics();
          }))
          Button('Stop').layoutWeight(1).onClick(() => this.run('stop', async () => {
            await this.agent!.stop({ flush: true });
            return this.agent!.getMetrics();
          }))
          Button('Flush').layoutWeight(1).onClick(() => this.run('flush', async () => ({
            flushed: await this.agent!.flush()
          })))
        }.width('100%')

        Row({ space: 8 }) {
          Button('Device').layoutWeight(1)
            .onClick(() => this.run('device', () => this.agent!.getDeviceInfo()))
          Button('Rating').layoutWeight(1)
            .onClick(() => this.run('rating', () => this.agent!.getRatingInfo()))
          Button('Labels').layoutWeight(1)
            .onClick(() => this.run('labels', () => this.agent!.getLabelInfo()))
        }.width('100%')

        Row({ space: 8 }) {
          Button('Mark action').layoutWeight(1).onClick(() => {
            this.output = `markAction accepted=${this.agent?.markAction('demo-button') || false}`;
          })
          Button('User data').layoutWeight(1).onClick(() => {
            this.output = `userData accepted=${this.agent?.userData('demo-user', { tier: 'demo' }) || false}`;
          })
          Button('Metrics').layoutWeight(1).onClick(() => {
            this.output = JSON.stringify(this.agent?.getMetrics() || {}, null, 2);
          })
        }.width('100%')

        Text(this.output)
          .fontFamily('monospace')
          .fontSize(12)
          .fontColor('#CBD5E1')
          .backgroundColor('#111827')
          .borderRadius(12)
          .padding(12)
          .width('100%')
          .constraintSize({ minHeight: 180 })
      }
      .padding(20)
      .width('100%')
    }
    .backgroundColor('#0F172A')
    .width('100%')
    .height('100%')
  }
}
