import { AbilityConstant, UIAbility, Want } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { window } from '@kit.ArkUI';
import { DemoRuntime } from '../DemoRuntime';

export default class EntryAbility extends UIAbility {
  onCreate(_want: Want, _launchParam: AbilityConstant.LaunchParam): void {
    hilog.info(0x0000, 'SynapDemo', 'EntryAbility created');
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    windowStage.loadContent('pages/Index', (error) => {
      if (error && error.code) {
        hilog.error(0x0000, 'SynapDemo', 'Failed to load page: %{public}s', JSON.stringify(error));
      }
    });
  }

  onBackground(): void {
    const agent = DemoRuntime.agent;
    DemoRuntime.resumeOnForeground = agent?.getMetrics().status === 'running';
    if (DemoRuntime.resumeOnForeground) agent?.stop({ flush: true }).catch(() => {});
  }

  onForeground(): void {
    if (!DemoRuntime.resumeOnForeground) return;
    DemoRuntime.resumeOnForeground = false;
    DemoRuntime.agent?.start().catch(() => {});
  }
}
