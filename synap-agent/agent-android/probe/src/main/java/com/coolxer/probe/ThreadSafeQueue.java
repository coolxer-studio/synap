package com.coolxer.probe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

public class ThreadSafeQueue {

    private static final LinkedBlockingDeque<String> QUEUE = new LinkedBlockingDeque<>();
    public static ArrayList<String> pullMsg(){
        ArrayList<String> queueAllElement = new ArrayList<>();
        synchronized (QUEUE) {
            QUEUE.drainTo(queueAllElement);
        }
        return queueAllElement;
    }

    public static void pushMsg(String msg){
        synchronized (QUEUE) {
            QUEUE.offer(msg);
        }
    }

    public static void pushFront(List<String> messages){
        synchronized (QUEUE) {
            for (int index = messages.size() - 1; index >= 0; index--) {
                QUEUE.offerFirst(messages.get(index));
            }
        }
    }

}
