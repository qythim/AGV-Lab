package com.agvlab.rcs.traffic.reservation.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.List;

@Service
public class RedisService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    public Set<String> getAllKeys() {
        return redisTemplate.keys("*");
    }
    // for Point 
    public Boolean lockPoint (String pointCode, String agvCode) {
        return redisTemplate.opsForValue().setIfAbsent("PointLock:" + pointCode, agvCode);
    }

    public String getPoint(String pointCode) {
        return redisTemplate.opsForValue().get("PointLock:" + pointCode);
    }

    public Boolean isPointExist(String pointCode){
        return redisTemplate.hasKey("PointLock:" + pointCode);
    }

    public Boolean unlockPoint (String pointCode){
        return redisTemplate.delete("PointLock:" +pointCode); 
    }

    //for All PointLock
     public Set<String> getPointsLock(){
        return redisTemplate.opsForSet().members("PointLock");
    }

    public Long addPointLock(String... pointCodes) {
        return redisTemplate.opsForSet().add("PointLock", pointCodes);
    }
    // 方法多載：提供便利方法以支援 List 傳參並委派調用
    public Long addPointLock(List<String> pointCodes) {
        if (pointCodes == null || pointCodes.isEmpty()) return 0L;
        return addPointLock(pointCodes.toArray(String[]::new));
    }

    public Long removePointLock(String... pointCodes) {
        return redisTemplate.opsForSet().remove("PointLock",(Object[]) pointCodes);
    }
    // 方法多載：提供便利方法以支援 List 傳參並委派調用
    public Long removePointLock(List<String> pointCodes) {
        if (pointCodes == null || pointCodes.isEmpty()) return 0L;
        return removePointLock(pointCodes.toArray(String[]::new));
    }

    // for AGV 

    public Long addAgvPoint(String agvCode, String... pointCodes){
        return redisTemplate.opsForSet().add("PointLock.agvCode:" + agvCode, pointCodes);
    } 
    public Long addAgvPoint(String agvCode, List<String> pointCodes) {
        if (pointCodes == null || pointCodes.isEmpty()) return 0L;
        return addAgvPoint(agvCode, pointCodes.toArray(String[]::new));
    }

    public Set<String> getAgvPoints(String agvCode){
        return redisTemplate.opsForSet().members("PointLock.agvCode:" + agvCode);
    }

    public Long removeAgvPoint(String agvCode, String pointCode ){
        return redisTemplate.opsForSet().remove("PointLock.agvCode:" + agvCode, pointCode);
    }

    public Boolean agvHasPoint (String agvCode, String pointCode ){
        return redisTemplate.opsForSet().isMember("PointLock.agvCode:" + agvCode, pointCode);
    }
}
