package com.atguigu.gmall.wms.listener;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.wms.dao.WareSkuDao;
import com.atguigu.gmall.wms.vo.SkuLockVO;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.HashCodeExclude;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StockListener {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private WareSkuDao wareSkuDao;

    private static final String KEY_PREFIX = "wms:lock:";

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "WMS-UNLOCK-QUEUE", durable = "true"),
            exchange = @Exchange(value = "ORDER-EXCHANGE", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"wms.unlock"}
    ))
    public void unlockListener(String orderToken){
        // 获取redis中锁定库存的信息
        String lockJson = this.redisTemplate.opsForValue().get(KEY_PREFIX + orderToken);
        if (StringUtils.isEmpty(lockJson)) {
            return;
        }
        List<SkuLockVO> skuLockVOs = JSON.parseArray(lockJson, SkuLockVO.class);
        // 解锁库存
        skuLockVOs.forEach(skuLockVO -> {
            this.wareSkuDao.unlockStock(skuLockVO.getWareSkuId(), skuLockVO.getCount());
        });
    }
}
