package com.atguigu.gmall.index.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.index.config.GmallCache;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.pms.vo.CategoryVO;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class IndexService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private static final String KEY_PREFIX = "index:cates:";

    public List<CategoryEntity> queryLvl1Category() {

        Resp<List<CategoryEntity>> categoriesResp = this.pmsClient.queryCategoriesByLevelOrPid(1, null);

        return categoriesResp.getData();
    }

    @GmallCache(prefix = "index:cates:", timeout = 14400, random = 3600, lock = "lock")
    public List<CategoryVO> queryLvl2WithSubByPid(Long pid) {

        Resp<List<CategoryVO>> listResp = this.pmsClient.queryCategoryWithSubByPid(pid);
        List<CategoryVO> categoryVOS = listResp.getData();

        return categoryVOS;
    }

    public List<CategoryVO> queryLvl2WithSubByPid1(Long pid) {
        // 先查询缓存，缓存中有直接命中返回
        String json = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
        if (StringUtils.isNotBlank(json)){
            return JSON.parseArray(json, CategoryVO.class);
        }

        RLock lock = this.redissonClient.getLock("lock" + pid);
        lock.lock();

        // 再加一次判断
        String json2 = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
        if (StringUtils.isNotBlank(json)){
            lock.unlock();
            return JSON.parseArray(json, CategoryVO.class);
        }

        // 缓存中没有，查询数据库并放入缓存
        Resp<List<CategoryVO>> listResp = this.pmsClient.queryCategoryWithSubByPid(pid);
        List<CategoryVO> categoryVOS = listResp.getData();

        // 判断返回数据是否为空
        if (!CollectionUtils.isEmpty(categoryVOS)) {
            // 给缓存时间添加随机值，防止雪崩的出现
            this.redisTemplate.opsForValue().set(KEY_PREFIX + pid, JSON.toJSONString(categoryVOS), 10 + (int) (Math.random() * 10), TimeUnit.DAYS);
        } else {
            // 即使数据为空，也要缓存。防止缓存的穿透
            this.redisTemplate.opsForValue().set(KEY_PREFIX + pid, JSON.toJSONString(categoryVOS), 5 + (int) (Math.random() * 10), TimeUnit.MINUTES);
        }

        lock.unlock();

        return categoryVOS;
    }

    public void testLock() {

        // 加锁
        RLock lock = this.redissonClient.getFairLock("lock");
        lock.lock(10, TimeUnit.SECONDS);

        String num = this.redisTemplate.opsForValue().get("num");
        if (StringUtils.isEmpty(num)){
            num = "0";
            this.redisTemplate.opsForValue().set("num", num);
        }
        int n = Integer.parseInt(num);
        this.redisTemplate.opsForValue().set("num", String.valueOf(++n));

        // 解锁
//        lock.unlock();
    }

    public void testLock1() {

        // 为每个请求生成唯一标志
        String uuid = UUID.randomUUID().toString();
        // 获取锁，执行setnx命令
        Boolean lock = this.redisTemplate.opsForValue().setIfAbsent("lock", uuid, 30, TimeUnit.SECONDS);
        // 获取到锁，执行业务
        if (lock) {
//            this.redisTemplate.expire("lock", 30, TimeUnit.SECONDS);

            String num = this.redisTemplate.opsForValue().get("num");
            if (StringUtils.isEmpty(num)){
                num = "0";
                this.redisTemplate.opsForValue().set("num", num);
            }
            int n = Integer.parseInt(num);
            this.redisTemplate.opsForValue().set("num", String.valueOf(++n));

            // 执行完成后，释放锁。使用lua脚本保证判断和删除的原子性，防止误删
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] " +
                    "then return redis.call('del', KEYS[1]) " +
                    "else return 0 end";
            this.redisTemplate.execute(new DefaultRedisScript<>(script), Arrays.asList("lock"), uuid);
//            if (StringUtils.equals(uuid, this.redisTemplate.opsForValue().get("lock"))){
//                this.redisTemplate.delete("lock");
//            }
        } else {
            // 获取不到，则重试
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            testLock();
        }
    }

    public void testRead() {
        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("RwLock");
        rwLock.readLock().lock(10, TimeUnit.SECONDS);

        // 执行业务逻辑
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

//        rwLock.readLock().unlock();
    }

    public void testWrite() {
        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("RwLock");
        rwLock.writeLock().lock(10, TimeUnit.SECONDS);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

//        rwLock.writeLock().unlock();
    }

    public void testLatch() throws InterruptedException {
        RCountDownLatch countDownLatch = this.redissonClient.getCountDownLatch("cdLatch");
        countDownLatch.trySetCount(6l);

        countDownLatch.await();
    }

    public void testCountDown() {
        RCountDownLatch countDownLatch = this.redissonClient.getCountDownLatch("cdLatch");

        countDownLatch.countDown();
    }
}
