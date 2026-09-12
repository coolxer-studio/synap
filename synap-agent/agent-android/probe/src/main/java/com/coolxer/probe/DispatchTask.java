package com.coolxer.probe;

import java.util.Timer;
import java.util.TimerTask;

public class DispatchTask {
    private String threadName;
    private Runnable task;
    private long interval;
    private long cancelTime;

    public DispatchTask(String threadName, long interval, long cancelTime, Runnable task) {
        this.threadName = threadName;
        this.interval = interval;
        this.cancelTime = cancelTime;
        this.task = task;
    }

    public DispatchTask(String threadName, Runnable task) {
        this.threadName = threadName;
        this.task = task;
    }

    public void start(){
        if(interval == 0 && cancelTime == 0){
            // 单次异步任务
            new Thread(task,threadName).start();
        }else{
            Timer timer = new Timer(threadName);
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    task.run();
                }
            };
            // 定时执行任务
            timer.scheduleAtFixedRate(timerTask, 0, interval);

            // 取消任务
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    timerTask.cancel();
                    timer.cancel();
                }
            }, cancelTime);
        }

    }
}
