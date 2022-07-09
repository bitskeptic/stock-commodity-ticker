package com.musingdaemon.ticker;

import java.util.concurrent.Executor;

public class Invoker implements Executor {
    @Override
    public void execute(Runnable command) {
        new Thread(command).start();
    }
}
