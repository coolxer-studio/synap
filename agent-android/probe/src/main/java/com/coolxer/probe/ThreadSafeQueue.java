package com.coolxer.probe;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public class ThreadSafeQueue {

    private static LinkedBlockingQueue<String> QUEUE = new LinkedBlockingQueue<>();
    public static ArrayList<String> pullMsg(){
        ArrayList<String> queueAllElement = new ArrayList<>();
        synchronized (QUEUE) {
            QUEUE.drainTo(queueAllElement);
            QUEUE.clear();
        }
        return queueAllElement;
    }

    public static void pushMsg(String msg){
        synchronized (QUEUE) {
            QUEUE.offer(msg);
        }
    }

}
