package com.wifiin.kv;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import com.google.common.collect.Maps;
import com.wifiin.kv.command.ExpireCommand;

public enum DataType{
    SYSTEM(0) {
        @Override
        public <R extends Result> Command<R> command(byte cmd){
            // TODO Auto-generated method stub
            return null;
        }
    },
    EXPIRE(1) {
        @SuppressWarnings("unchecked")
        @Override
        public <R extends Result> Command<R> command(byte cmd){
            return (Command<R>)ExpireCommand.valueOf(cmd);
        }
    },
    SIMPLE(2) {
        @Override
        public <R extends Result> Command<R> command(byte cmd){
            // TODO Auto-generated method stub
            return null;
        }
    },
    LIST(3) {
        @Override
        public <R extends Result> Command<R> command(byte cmd){
            // TODO Auto-generated method stub
            return null;
        }
    },
    SET(4) {
        @Override
        public <R extends Result> Command<R> command(byte cmd){
            // TODO Auto-generated method stub
            return null;
        }
    },
    ZSET(5) {
        @Override
        public <R extends Result> Command<R> command(byte cmd){
            // TODO Auto-generated method stub
            return null;
        }
    },
    HASH(6) {
        @Override
        public <R extends Result> Command<R> command(byte cmd){
            // TODO Auto-generated method stub
            return null;
        }
    },
    GEOHASH(7) {
        @Override
        public <R extends Result> Command<R> command(byte cmd){
            // TODO Auto-generated method stub
            return null;
        }
    };
    private int value;
    private DataType(int value){
        this.value=value;
    }
    public int value(){
        return value;
    }
    private static final Map<Integer,DataType> DATA_TYPE_MAP;
    static{
        Map<Integer,DataType> map=Maps.newHashMap();
        Arrays.stream(DataType.values()).forEach((t)->{
            map.put(t.value,t);
        });
        DATA_TYPE_MAP=Collections.unmodifiableMap(map);
    }
    public static DataType valueOf(int value){
        return DATA_TYPE_MAP.get(value);
    }
    public abstract <R extends Result> Command<R> command(byte cmd);
}
