package com.wifiin.cron;

import java.io.IOException;

import com.wifiin.cron.exception.CronException;
import com.wifiin.util.net.http.HttpClient;

/**
 * http定时任务，触发时发起http请求
 * @author Running
 *
 */
public interface HttpCronTask extends CronTask{
    public String url();
    public default void execute(){
        try{
            new HttpClient(url()).get();
        }catch(IOException e){
            throw new CronException(e);
        }
    }
}

